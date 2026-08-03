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

class GitBusPublisher(
    repository: Path,
    private val branch: String = "assay/evidence",
) {
    private val root = repository.toAbsolutePath().normalize()

    init {
        require(branch == "assay/evidence") { "evidence branch name is fixed by contract" }
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
        val state = BusReader(bus).read(expectedSourceCommit)
        require(state is BusState.Ready && state.run != null) { "bus is not ready for remote publication" }
        val run = state.run

        val remoteRef = "refs/heads/$branch"
        val observed = remoteHead(remote, remoteRef)
        require(observed == expectedRemoteCommit) {
            "remote evidence branch moved: expected ${expectedRemoteCommit ?: "absent"}, observed ${observed ?: "absent"}"
        }
        if (observed != null) {
            git(listOf("fetch", "--no-tags", remote, "$remoteRef:$remoteRef"))
            require(git(listOf("cat-file", "-e", "$observed^{commit}"), allowed = setOf(0, 1)).exitCode == 0) {
                "expected remote evidence commit is unavailable locally"
            }
        }

        val indexPath = Files.createTempFile("assay-evidence-index-", ".gitindex")
        Files.deleteIfExists(indexPath)
        try {
            val indexEnvironment = mapOf("GIT_INDEX_FILE" to indexPath.toString())
            git(listOf("read-tree", "--empty"), environment = indexEnvironment)
            git(
                listOf("--work-tree=${bus}", "add", "--all", "--", "."),
                environment = indexEnvironment,
            )
            val tree = git(listOf("write-tree"), environment = indexEnvironment).text().trim()
            require(tree.matches(Regex("[0-9a-f]{40}"))) { "invalid evidence tree id" }

            val commitArguments = mutableListOf("commit-tree", tree)
            if (observed != null) commitArguments += listOf("-p", observed)
            commitArguments += listOf("-m", "Assay evidence ${run.runId} ${run.sourceCommit}")
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
            require(commit.matches(Regex("[0-9a-f]{40}"))) { "invalid evidence commit id" }

            val lease = if (expectedRemoteCommit == null) {
                "--force-with-lease=$remoteRef:"
            } else {
                "--force-with-lease=$remoteRef:$expectedRemoteCommit"
            }
            git(listOf("push", lease, remote, "$commit:$remoteRef"))
            require(remoteHead(remote, remoteRef) == commit) { "remote evidence publication could not be verified" }
            return commit
        } finally {
            Files.deleteIfExists(indexPath)
        }
    }

    private fun remoteHead(remote: String, remoteRef: String): String? {
        val result = git(listOf("ls-remote", "--heads", remote, remoteRef))
        val lines = result.text().lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size <= 1) { "remote returned duplicate evidence refs" }
        if (lines.isEmpty()) return null
        val parts = lines.single().split(Regex("\\s+"))
        require(parts.size == 2 && parts[1] == remoteRef && parts[0].matches(Regex("[0-9a-f]{40}"))) {
            "invalid remote evidence ref response"
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
                require(!Files.isSymbolicLink(path)) { "symlink in evidence bus: $path" }
                require(path.fileName?.toString() != ".git") { "nested .git path in evidence bus" }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    val size = Files.size(path)
                    require(size in 1..32L * 1024 * 1024) { "evidence artifact size rejected: $path" }
                    total += size
                    files++
                    require(total <= 256L * 1024 * 1024) { "evidence bus exceeds publication size limit" }
                    require(files <= 10_000) { "evidence bus contains too many files" }
                }
            }
        }
        require(files > 0) { "evidence bus is empty" }
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
                error("Git evidence command timed out")
            }
            val bytes = output.get(5, TimeUnit.SECONDS)
            val result = GitResult(process.exitValue(), bytes)
            require(result.exitCode in allowed) {
                val safe = Redaction.sanitize(result.text()).take(4096)
                "Git evidence command failed (${result.exitCode}): ${arguments.joinToString(" ")}\n$safe"
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
