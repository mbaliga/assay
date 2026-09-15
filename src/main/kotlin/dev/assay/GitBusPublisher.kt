package dev.assay

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object RemoteBusCli {
    private const val COMMAND = "publish-bus-git"

    fun handles(command: String?): Boolean = command == COMMAND

    fun run(args: Array<String>) {
        require(args.firstOrNull() == COMMAND) { "unsupported remote bus command" }
        val options = Options(args.drop(1))
        val expected = options.required("--expected-remote").let { value ->
            if (value == "absent") null else value.also {
                require(it.matches(Regex("[0-9a-f]{40}"))) { "invalid expected remote commit" }
            }
        }
        val commit = GitBusPublisher(Path.of(options.required("--repo"))).publish(
            busRoot = Path.of(options.required("--bus")),
            remote = options.optional("--remote") ?: "origin",
            expectedRemoteCommit = expected,
            expectedSourceCommit = options.required("--source-commit"),
        )
        println(commit)
    }

    private class Options(args: List<String>) {
        private val values: Map<String, String>

        init {
            val parsed = linkedMapOf<String, String>()
            var index = 0
            while (index < args.size) {
                val name = args[index]
                require(name.startsWith("--")) { "unexpected argument '$name'" }
                require(index + 1 < args.size && !args[index + 1].startsWith("--")) { "missing value for $name" }
                require(name !in parsed) { "duplicate option $name" }
                parsed[name] = args[index + 1]
                index += 2
            }
            values = parsed
        }

        fun required(name: String): String = values[name] ?: error("missing $name")
        fun optional(name: String): String? = values[name]
    }
}

/**
 * Publishes a locally-staged tree to a branch of a Git remote via `commit-tree` + `push
 * --force-with-lease`, giving every publish a verifiable, tamper-evident, linear history without
 * ever touching the caller's working tree or index.
 *
 * [branch] is the **output branch** the caller selects — per
 * docs/ratified/ASSAY_REPO_CONTRACT_V1.md (INT-018), Core's contract is "a user-selected output
 * branch," not a name Assay is allowed to hardcode. The default of `"assay/audit"` is kept only
 * as a convenience for Assay's own internal trust-engine bus ([publish], `Bus.kt`'s multi-run
 * `index.json`/`run.json`/`status.json` shape) — every other caller, in particular
 * [publishV1] callers writing the ratified `.assay/assay-index.v1.json` shape, MUST pass the
 * branch the user actually chose. Orphan-branch behaviour (no parent when the remote ref is
 * absent) falls out of the same `commit-tree`/`push` plumbing regardless of branch name — it is
 * an implementation detail, not a contract requirement.
 */
class GitBusPublisher(
    repository: Path,
    private val branch: String = "assay/audit",
) {
    private val root = repository.toAbsolutePath().normalize()

    init {
        require(branch.isNotBlank()) { "output branch name must not be blank" }
        require(branch.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}"))) { "invalid output branch name" }
        require(!branch.contains("..") && !branch.endsWith("/") && !branch.endsWith(".lock")) {
            "invalid output branch name"
        }
        require(Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { "Git repository does not exist" }
        require(!Files.isSymbolicLink(root)) { "Git repository must not be a symlink" }
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "Git repository is not a directory" }
        require(git(listOf("rev-parse", "--is-inside-work-tree")).text().trim() == "true") {
            "path is not a Git worktree"
        }
    }

    fun publish(
        busRoot: Path,
        remote: String,
        expectedRemoteCommit: String?,
        expectedSourceCommit: String,
    ): String {
        require(remote.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "remote must be a configured Git remote name" }
        require(expectedSourceCommit.matches(Regex("[0-9a-f]{40}"))) { "invalid expected source commit" }
        val bus = busRoot.toAbsolutePath().normalize()
        validateBusTree(bus)
        val run = requireNotNull((BusReader(bus).read(expectedSourceCommit) as? BusState.Ready)?.run) {
            "bus is not ready for remote publication"
        }

        val remoteRef = "refs/heads/$branch"
        val observed = remoteHead(remote, remoteRef)
        require(observed == expectedRemoteCommit) {
            "remote audit branch moved: expected ${expectedRemoteCommit ?: "absent"}, observed ${observed ?: "absent"}"
        }
        if (observed != null) {
            git(listOf("fetch", "--no-tags", remote, "+$remoteRef:$remoteRef"))
            require(git(listOf("cat-file", "-e", "$observed^{commit}"), allowed = setOf(0, 1)).exitCode == 0) {
                "expected remote audit commit is unavailable locally"
            }
        }

        val indexPath = Files.createTempFile("assay-audit-index-", ".gitindex")
        Files.deleteIfExists(indexPath)
        try {
            val indexEnvironment = mapOf("GIT_INDEX_FILE" to indexPath.toString())
            git(listOf("read-tree", "--empty"), environment = indexEnvironment)
            git(
                listOf("--work-tree=$bus", "add", "--all", "--", "."),
                environment = indexEnvironment,
            )
            val tree = git(listOf("write-tree"), environment = indexEnvironment).text().trim()
            require(tree.matches(Regex("[0-9a-f]{40}"))) { "invalid audit tree id" }

            val commitArguments = mutableListOf("commit-tree", tree)
            if (observed != null) commitArguments += listOf("-p", observed)
            commitArguments += listOf("-m", "Assay audit ${run.runId} ${run.sourceCommit}")
            val timestamp = run.createdAt.toString()
            val commitEnvironment = indexEnvironment + mapOf(
                "GIT_AUTHOR_NAME" to "Assay",
                "GIT_AUTHOR_EMAIL" to "assay@localhost.invalid",
                "GIT_AUTHOR_DATE" to timestamp,
                "GIT_COMMITTER_NAME" to "Assay",
                "GIT_COMMITTER_EMAIL" to "assay@localhost.invalid",
                "GIT_COMMITTER_DATE" to timestamp,
            )
            val commit = git(commitArguments, environment = commitEnvironment).text().trim()
            require(commit.matches(Regex("[0-9a-f]{40}"))) { "invalid audit commit id" }

            val lease = if (expectedRemoteCommit == null) {
                "--force-with-lease=$remoteRef:"
            } else {
                "--force-with-lease=$remoteRef:$expectedRemoteCommit"
            }
            git(listOf("push", lease, remote, "$commit:$remoteRef"))
            require(remoteHead(remote, remoteRef) == commit) { "remote audit publication could not be verified" }
            return commit
        } finally {
            Files.deleteIfExists(indexPath)
        }
    }

    /**
     * Publishes a ratified-shape `.assay/` staging tree (see [AssayOutputWriterV1]) to [branch],
     * satisfying `assay-index.v1.json`'s required `assayCommit` field
     * (docs/ratified/ASSAY_REPO_CONTRACT_V1.md §2) as precisely as a cryptographic hash can name
     * the commit that carries it.
     *
     * `assayCommit` is required to be "the commit ON the Assay output branch that carries this
     * exact index" — but a SHA cannot generally contain its own value (the index's bytes are
     * part of the tree the commit hashes over), so exact self-reference is impossible without a
     * hash preimage. This resolves it in two passes, both folded into a *single* push:
     *
     *  1. Stage [busRoot] as-is (its `assay-index.v1.json` still carries [assayCommitPlaceholder])
     *     and build a provisional commit `P` on top of the current branch tip.
     *  2. Rewrite only the placeholder occurrence in `assay-index.v1.json` to `P`'s hash, restage,
     *     and build a corrected commit `F` with `P` as its parent.
     *
     * Only `F` is pushed as the new branch tip; `P` travels with it as `F`'s parent (`git push`
     * transfers every object reachable from the pushed ref). The result: `assayCommit` names a
     * real, fetchable, immediate-parent commit `P` whose tree is byte-identical to `F`'s except
     * for that one field — the closest a content-addressed system can get to naming itself.
     * `F` itself (the branch tip a "Check branch" read actually lands on) carries the same
     * `runs/<runId>/` evidence unchanged.
     */
    fun publishV1(
        busRoot: Path,
        remote: String,
        expectedRemoteCommit: String?,
        indexRelativePath: String,
        assayCommitPlaceholder: String,
        commitMessage: String,
        now: Instant = Instant.now(),
    ): String {
        require(remote.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "remote must be a configured Git remote name" }
        require(assayCommitPlaceholder.matches(Regex("[0-9a-f]{40}"))) { "invalid assayCommit placeholder" }
        val bus = busRoot.toAbsolutePath().normalize()
        validateBusTree(bus)
        val indexFile = bus.resolve(indexRelativePath).normalize()
        require(indexFile.startsWith(bus)) { "index path escapes staged output" }
        require(Files.isRegularFile(indexFile, LinkOption.NOFOLLOW_LINKS)) {
            "assay-index.v1.json is missing from staged output"
        }
        val originalText = Files.readString(indexFile)
        require(originalText.contains(assayCommitPlaceholder)) {
            "staged assay-index.v1.json does not carry the assayCommit placeholder"
        }

        val remoteRef = "refs/heads/$branch"
        val observed = remoteHead(remote, remoteRef)
        require(observed == expectedRemoteCommit) {
            "remote output branch moved: expected ${expectedRemoteCommit ?: "absent"}, observed ${observed ?: "absent"}"
        }
        if (observed != null) {
            git(listOf("fetch", "--no-tags", remote, "+$remoteRef:$remoteRef"))
            require(git(listOf("cat-file", "-e", "$observed^{commit}"), allowed = setOf(0, 1)).exitCode == 0) {
                "expected remote output commit is unavailable locally"
            }
        }

        val timestamp = now.toString()
        val commitEnvironment = mapOf(
            "GIT_AUTHOR_NAME" to "Assay",
            "GIT_AUTHOR_EMAIL" to "assay@localhost.invalid",
            "GIT_AUTHOR_DATE" to timestamp,
            "GIT_COMMITTER_NAME" to "Assay",
            "GIT_COMMITTER_EMAIL" to "assay@localhost.invalid",
            "GIT_COMMITTER_DATE" to timestamp,
        )

        fun stageTree(): String {
            val indexPath = Files.createTempFile("assay-v1-index-", ".gitindex")
            Files.deleteIfExists(indexPath)
            try {
                val env = mapOf("GIT_INDEX_FILE" to indexPath.toString())
                git(listOf("read-tree", "--empty"), environment = env)
                git(listOf("--work-tree=$bus", "add", "--all", "--", "."), environment = env)
                val tree = git(listOf("write-tree"), environment = env).text().trim()
                require(tree.matches(Regex("[0-9a-f]{40}"))) { "invalid output tree id" }
                return tree
            } finally {
                Files.deleteIfExists(indexPath)
            }
        }

        val provisionalTree = stageTree()
        val provisionalArguments = mutableListOf("commit-tree", provisionalTree)
        if (observed != null) provisionalArguments += listOf("-p", observed)
        provisionalArguments += listOf("-m", "$commitMessage (provisional: assayCommit self-reference)")
        val provisionalCommit = git(provisionalArguments, environment = commitEnvironment).text().trim()
        require(provisionalCommit.matches(Regex("[0-9a-f]{40}"))) { "invalid provisional commit id" }

        val correctedText = originalText.replace(assayCommitPlaceholder, provisionalCommit)
        require(correctedText != originalText) { "assayCommit placeholder substitution had no effect" }
        Files.writeString(indexFile, correctedText)
        val correctedTree = stageTree()
        val correctedCommit = git(
            listOf("commit-tree", correctedTree, "-p", provisionalCommit, "-m", commitMessage),
            environment = commitEnvironment,
        ).text().trim()
        require(correctedCommit.matches(Regex("[0-9a-f]{40}"))) { "invalid corrected commit id" }

        val lease = if (expectedRemoteCommit == null) {
            "--force-with-lease=$remoteRef:"
        } else {
            "--force-with-lease=$remoteRef:$expectedRemoteCommit"
        }
        git(listOf("push", lease, remote, "$correctedCommit:$remoteRef"))
        require(remoteHead(remote, remoteRef) == correctedCommit) { "remote output publication could not be verified" }
        return correctedCommit
    }

    private fun remoteHead(remote: String, remoteRef: String): String? {
        val result = git(listOf("ls-remote", "--heads", remote, remoteRef))
        val lines = result.text().lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size <= 1) { "remote returned duplicate audit refs" }
        if (lines.isEmpty()) return null
        val parts = lines.single().split(Regex("\\s+"))
        require(parts.size == 2 && parts[1] == remoteRef && parts[0].matches(Regex("[0-9a-f]{40}"))) {
            "invalid remote audit ref response"
        }
        return parts[0]
    }

    private fun validateBusTree(bus: Path) {
        require(Files.exists(bus, LinkOption.NOFOLLOW_LINKS)) { "bus root does not exist" }
        require(!Files.isSymbolicLink(bus)) { "bus root must not be a symlink" }
        require(Files.isDirectory(bus, LinkOption.NOFOLLOW_LINKS)) { "bus root is not a directory" }
        var total = 0L
        var files = 0
        Files.walk(bus).use { stream ->
            stream.forEach { path ->
                require(!Files.isSymbolicLink(path)) { "symlink in audit bus: $path" }
                require(path.fileName?.toString() != ".git") { "nested .git path in audit bus" }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    val size = Files.size(path)
                    require(size in 1..32L * 1024 * 1024) { "audit artifact size rejected: $path" }
                    total += size
                    files++
                    require(total <= 256L * 1024 * 1024) { "audit bus exceeds publication size limit" }
                    require(files <= 10_000) { "audit bus contains too many files" }
                }
            }
        }
        require(files > 0) { "audit bus is empty" }
    }

    private fun git(
        arguments: List<String>,
        allowed: Set<Int> = setOf(0),
        environment: Map<String, String> = emptyMap(),
    ): GitResult {
        val processBuilder = ProcessBuilder(listOf("git", "-C", root.toString()) + arguments)
            .redirectErrorStream(true)
        processBuilder.environment().putAll(environment)
        val process = processBuilder.start()
        val executor = Executors.newSingleThreadExecutor()
        val output = executor.submit<ByteArray> { process.inputStream.use { it.readAllBytes() } }
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Git audit command timed out")
            }
            val bytes = output.get(5, TimeUnit.SECONDS)
            val result = GitResult(process.exitValue(), bytes)
            require(result.exitCode in allowed) {
                val safe = Redaction.sanitize(result.text()).take(4096)
                "Git audit command failed (${result.exitCode}): ${arguments.joinToString(" ")}\n$safe"
            }
            return result
        } finally {
            executor.shutdownNow()
        }
    }

    private data class GitResult(val exitCode: Int, val bytes: ByteArray) {
        fun text(): String = bytes.toString(Charsets.UTF_8)
    }
}
