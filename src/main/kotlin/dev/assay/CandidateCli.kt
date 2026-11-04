package dev.assay

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object CandidateCli {
    val commandNames: Set<String> = setOf(
        "candidate-create",
        "candidate-list",
        "candidate-show",
        "candidate-propose",
        "candidate-prepare",
        "candidate-proof",
        "candidate-approve",
        "candidate-reject",
        "candidate-apply",
        "candidate-stale",
    )

    fun handles(command: String?): Boolean = command != null && command in commandNames

    fun run(args: Array<String>) {
        require(args.isNotEmpty() && handles(args[0])) { "unsupported candidate command" }
        val options = Options(args.drop(1))
        when (args[0]) {
            "candidate-create" -> create(options)
            "candidate-list" -> list(options)
            "candidate-show" -> show(options)
            "candidate-propose" -> propose(options)
            "candidate-prepare" -> prepare(options)
            "candidate-proof" -> proof(options)
            "candidate-approve" -> approve(options)
            "candidate-reject" -> reject(options)
            "candidate-apply" -> apply(options)
            "candidate-stale" -> stale(options)
        }
    }

    private fun create(options: Options) {
        val identity = CandidateIdentity.create(
            sourceRepo = options.required("--source-repo"),
            sourceCommit = options.required("--source-commit"),
            findingFingerprint = options.required("--finding-fingerprint"),
            provingId = options.required("--proving-id"),
            patchDigest = options.required("--patch-digest"),
            testDigest = options.required("--test-digest"),
        )
        val record = CandidateLedger.create(identity, "system:assay", options.instant()).snapshot()
        store(options).create(record)
        printRecord(record)
    }

    private fun list(options: Options) {
        val records = store(options).list()
        println(Json.stringify(Json.arr(records.map { Json.parse(CandidateCodec.encode(it)) }), pretty = true))
    }

    private fun show(options: Options) {
        printRecord(store(options).read(options.required("--candidate")))
    }

    private fun propose(options: Options) = mutate(options) { ledger, revision ->
        ledger.propose(revision, options.required("--actor"), options.instant())
    }

    private fun prepare(options: Options) {
        val record = store(options).read(options.required("--candidate"))
        require(record.lifecycle == Lifecycle.PROPOSED) { "candidate must be proposed before preparing its fix branch" }
        CandidateGitWorkspace(Path.of(options.required("--repo"))).prepare(
            record.identity,
            Path.of(options.required("--patch")),
        )
        printRecord(record)
    }

    private fun proof(options: Options) {
        val candidateStore = store(options)
        val candidateId = options.required("--candidate")
        val revision = options.integer("--revision")
        val current = candidateStore.read(candidateId)
        require(current.revision == revision) {
            "stale candidate revision: expected $revision, actual ${current.revision}"
        }
        val proofText = readBounded(Path.of(options.required("--proof")), 4_194_304)
        val evidence = ProofDocument.decode(proofText)
        ProofGate.validate(evidence).getOrThrow()
        bindProof(current.identity, evidence)
        CandidateGitWorkspace(Path.of(options.required("--repo"))).verifyFixCommit(
            current.identity,
            evidence.afterTreeRef,
        )
        val canonicalProof = ProofCodec.encode(evidence)
        val digest = Fingerprints.sha256(canonicalProof.toByteArray(Charsets.UTF_8))
        val next = CandidateLedger.restore(current).recordProof(
            expectedRevision = revision,
            actor = "system:proof-runner",
            at = options.instant(),
            proofDigest = digest,
            proofPassed = evidence.passed,
        )
        candidateStore.update(next, revision)
        printRecord(next)
    }

    private fun approve(options: Options) = mutate(options) { ledger, revision ->
        ledger.approve(revision, options.required("--actor"), options.instant())
    }

    private fun reject(options: Options) = mutate(options) { ledger, revision ->
        ledger.reject(revision, options.required("--actor"), options.instant())
    }

    private fun apply(options: Options) {
        val candidateStore = store(options)
        val candidateId = options.required("--candidate")
        val revision = options.integer("--revision")
        val current = candidateStore.read(candidateId)
        require(current.revision == revision) {
            "stale candidate revision: expected $revision, actual ${current.revision}"
        }
        val fixCommit = CandidateGitWorkspace(Path.of(options.required("--repo"))).verifiedHead(current.identity)
        val next = CandidateLedger.restore(current).apply(
            expectedRevision = revision,
            actor = "system:executor",
            at = options.instant(),
            sourceCommit = current.identity.sourceCommit,
            fixCommit = fixCommit,
            fixBranch = current.identity.fixBranch,
        )
        candidateStore.update(next, revision)
        printRecord(next)
    }

    private fun stale(options: Options) = mutate(options) { ledger, revision ->
        ledger.markStale(
            revision,
            "system:source-watch",
            options.instant(),
            options.required("--current-source-commit"),
        )
    }

    private fun mutate(
        options: Options,
        operation: (CandidateLedger, Int) -> CandidateRecord,
    ) {
        val candidateStore = store(options)
        val candidateId = options.required("--candidate")
        val revision = options.integer("--revision")
        val current = candidateStore.read(candidateId)
        require(current.revision == revision) {
            "stale candidate revision: expected $revision, actual ${current.revision}"
        }
        val next = operation(CandidateLedger.restore(current), revision)
        candidateStore.update(next, revision)
        printRecord(next)
    }

    private fun bindProof(identity: CandidateIdentity, evidence: ProofEvidence) {
        require(evidence.provingId == identity.provingId) { "proof proving id does not match candidate" }
        require(evidence.findingFingerprint == identity.findingFingerprint) { "proof finding does not match candidate" }
        require(evidence.sourceCommit == identity.sourceCommit) { "proof source commit does not match candidate" }
        require(evidence.patchDigest == identity.patchDigest) { "proof patch does not match candidate" }
        require(evidence.testDigest == identity.testDigest) { "proof test does not match candidate" }
    }

    private fun store(options: Options): CandidateStore = CandidateStore(Path.of(options.required("--store")))

    private fun printRecord(record: CandidateRecord) = print(CandidateCodec.encode(record))

    private fun readBounded(path: Path, maxBytes: Long): String {
        val absolute = path.toAbsolutePath().normalize()
        require(Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) { "file does not exist: $absolute" }
        require(!Files.isSymbolicLink(absolute)) { "file must not be a symlink: $absolute" }
        require(Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) { "path is not a regular file: $absolute" }
        require(Files.size(absolute) in 1..maxBytes) { "file size rejected: $absolute" }
        return Files.readString(absolute)
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
        fun integer(name: String): Int = required(name).toIntOrNull()?.takeIf { it >= 1 }
            ?: error("invalid positive integer for $name")
        fun instant(): Instant = values["--at"]?.let(Instant::parse) ?: Instant.now()
    }
}

class CandidateGitWorkspace(private val repository: Path) {
    private val root: Path = repository.toAbsolutePath().normalize()

    init {
        require(Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { "repository does not exist" }
        require(!Files.isSymbolicLink(root)) { "repository must not be a symlink" }
        require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) { "repository is not a directory" }
        require(git("rev-parse", "--is-inside-work-tree").text().trim() == "true") { "path is not a Git worktree" }
    }

    fun prepare(identity: CandidateIdentity, patch: Path) {
        requireClean()
        val patchPath = patch.toAbsolutePath().normalize()
        val patchBytes = secureBytes(patchPath, 16_777_216)
        require(Fingerprints.sha256(patchBytes) == identity.patchDigest) { "patch digest does not match candidate" }
        require(git("cat-file", "-e", "${identity.sourceCommit}^{commit}", allowed = setOf(0, 1)).exitCode == 0) {
            "candidate source commit is unavailable"
        }
        require(git("show-ref", "--verify", "--quiet", "refs/heads/${identity.fixBranch}", allowed = setOf(0, 1)).exitCode == 1) {
            "candidate fix branch already exists"
        }
        val originalHead = git("rev-parse", "HEAD").text().trim()
        val originalBranchResult = git("symbolic-ref", "--quiet", "--short", "HEAD", allowed = setOf(0, 1))
        val originalBranch = originalBranchResult.text().trim().takeIf { originalBranchResult.exitCode == 0 && it.isNotEmpty() }
        try {
            git("switch", "-c", identity.fixBranch, identity.sourceCommit)
            git("apply", "--check", "--index", patchPath.toString())
            git("apply", "--index", patchPath.toString())
        } catch (failure: Throwable) {
            git("reset", "--hard", originalHead, allowed = setOf(0, 128))
            if (originalBranch != null) {
                git("switch", originalBranch, allowed = setOf(0, 1, 128))
            } else {
                git("switch", "--detach", originalHead, allowed = setOf(0, 1, 128))
            }
            git("branch", "-D", identity.fixBranch, allowed = setOf(0, 1, 128))
            throw failure
        }
    }

    fun verifyFixCommit(identity: CandidateIdentity, expectedHead: String) {
        require(expectedHead.matches(Regex("[0-9a-f]{40}"))) { "invalid expected fix commit" }
        val actual = verifiedHead(identity)
        require(actual == expectedHead) { "proof after-tree does not match fix branch HEAD" }
    }

    fun verifiedHead(identity: CandidateIdentity): String {
        requireClean()
        val branch = git("symbolic-ref", "--quiet", "--short", "HEAD", allowed = setOf(0, 1)).text().trim()
        require(branch == identity.fixBranch) { "worktree is not on the candidate fix branch" }
        val head = git("rev-parse", "HEAD").text().trim()
        require(head.matches(Regex("[0-9a-f]{40}")) && head != identity.sourceCommit) { "invalid candidate fix commit" }
        require(git("merge-base", "--is-ancestor", identity.sourceCommit, head, allowed = setOf(0, 1)).exitCode == 0) {
            "candidate fix commit is not descended from the audited source"
        }
        val canonicalPatch = git("diff", "--binary", "--full-index", identity.sourceCommit, head).bytes
        require(canonicalPatch.isNotEmpty()) { "candidate fix commit contains no patch" }
        require(Fingerprints.sha256(canonicalPatch) == identity.patchDigest) { "fix commit patch does not match candidate" }
        return head
    }

    private fun requireClean() {
        require(git("status", "--porcelain=v1", "--untracked-files=normal").text().isBlank()) {
            "Git worktree must be clean"
        }
    }

    private fun secureBytes(path: Path, maxBytes: Long): ByteArray {
        require(Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "patch does not exist" }
        require(!Files.isSymbolicLink(path)) { "patch must not be a symlink" }
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "patch is not a regular file" }
        require(Files.size(path) in 1..maxBytes) { "patch size rejected" }
        return Files.readAllBytes(path)
    }

    private fun git(vararg arguments: String, allowed: Set<Int> = setOf(0)): GitResult {
        val command = listOf("git", "-C", root.toString()) + arguments
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val executor = Executors.newSingleThreadExecutor()
        val output = executor.submit<ByteArray> { process.inputStream.use { it.readAllBytes() } }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Git command timed out")
            }
            val bytes = output.get(5, TimeUnit.SECONDS)
            val result = GitResult(process.exitValue(), bytes)
            require(result.exitCode in allowed) {
                val safe = Redaction.sanitize(result.text()).take(4096)
                "Git command failed (${result.exitCode}): ${arguments.joinToString(" ")}\n$safe"
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
