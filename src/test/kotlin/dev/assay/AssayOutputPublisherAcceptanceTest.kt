package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

fun main() {
    val workspace = Files.createTempDirectory("assay-output-git-")
    val repository = workspace.resolve("source")
    val remote = workspace.resolve("output.git")

    gitOutput(workspace, setOf(0), "init", "-b", "main", repository.toString())
    gitOutput(repository, setOf(0), "config", "user.email", "assay@example.invalid")
    gitOutput(repository, setOf(0), "config", "user.name", "Assay Acceptance")
    Files.writeString(repository.resolve("README.md"), "source\n")
    gitOutput(repository, setOf(0), "add", "README.md")
    gitOutput(repository, setOf(0), "commit", "-m", "source")
    val sourceCommit = gitOutput(repository, setOf(0), "rev-parse", "HEAD").output.trim()

    gitOutput(workspace, setOf(0), "init", "--bare", remote.toString())
    gitOutput(repository, setOf(0), "remote", "add", "output", remote.toString())

    // Fonebrew Core's ratified contract (docs/ratified/ASSAY_REPO_CONTRACT_V1.md, INT-018) is a
    // *user-selected* output branch — deliberately not "assay/audit" here, to prove the branch
    // name is no longer hardcoded.
    val publisher = GitBusPublisher(repository, branch = "release/v1-audit")

    fun stageRun(runId: String, findings: List<Finding>): Path {
        val bus = Files.createTempDirectory("assay-output-bus-")
        AssayOutputWriterV1(bus).write(
            runId = runId,
            projectRef = AssayProjectRefV1("https://github.com/mbaliga/example.git", "core-mobile"),
            sourceCommit = sourceCommit,
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            tool = AssayToolRefV1("assay-cli", "1.1.0"),
            startedAt = Instant.parse("2026-08-28T02:00:00Z"),
            finishedAt = Instant.parse("2026-08-28T02:14:00Z"),
            findings = findings,
            completeness = AssayCompletenessV1.COMPLETE,
        )
        return bus
    }

    fun publish(runId: String, findings: List<Finding>, expected: String?): String {
        val bus = stageRun(runId, findings)
        return publisher.publishV1(
            busRoot = bus,
            remote = "output",
            expectedRemoteCommit = expected,
            indexRelativePath = ".assay/assay-index.v1.json",
            assayCommitPlaceholder = AssayOutputWriterV1.ASSAY_COMMIT_PLACEHOLDER,
            commitMessage = "Assay output $runId $sourceCommit",
            now = Instant.parse("2026-08-28T02:15:00Z"),
        )
    }

    val firstFinding = Finding(
        scanner = Scanner.GITLEAKS,
        ruleId = "assay-test-secret",
        message = "fixture finding",
        location = Location("README.md", 1),
        context = "fixture-context",
    )

    val first = publish("01J9E0000000000000000RN1", listOf(firstFinding), null)
    check(first.matches(Regex("[0-9a-f]{40}")))
    check(remoteOutputHead(repository, "release/v1-audit") == first)
    println("PASS first output publication lands on the user-selected branch")

    check(gitOutput(repository, setOf(0, 1), "merge-base", "--is-ancestor", sourceCommit, first).exitCode == 1)
    println("PASS output publication is disconnected from source history (orphan behaviour preserved)")

    val decodedFirst = AssayIndexV1Codec.decode(
        gitOutput(repository, setOf(0), "show", "$first:.assay/assay-index.v1.json").output,
    )
    check(decodedFirst.runId == "01J9E0000000000000000RN1")
    check(decodedFirst.assayCommit != first) { "assayCommit cannot literally equal its own commit (hash self-reference is impossible)" }
    val parent = gitOutput(repository, setOf(0), "rev-parse", "$first^").output.trim()
    check(decodedFirst.assayCommit == parent) {
        "assayCommit must name the real, fetchable parent commit that carries the same run evidence"
    }
    println("PASS assayCommit names a real, reachable, corrected-parent commit")

    val evidenceAtParent = gitOutput(repository, setOf(0), "show", "$parent:.assay/runs/01J9E0000000000000000RN1/findings.sarif").output
    val evidenceAtHead = gitOutput(repository, setOf(0), "show", "$first:.assay/runs/01J9E0000000000000000RN1/findings.sarif").output
    check(evidenceAtParent == evidenceAtHead) { "run evidence must be byte-identical between the provisional and corrected commit" }
    println("PASS run evidence is unchanged between the provisional and corrected commit")

    val secondFinding = firstFinding.copy(message = "fixture finding v2")
    val second = publish("01J9E0000000000000000RN2", listOf(secondFinding), first)
    check(second != first)
    check(remoteOutputHead(repository, "release/v1-audit") == second)
    val secondParent = gitOutput(repository, setOf(0), "rev-parse", "$second^").output.trim()
    val secondGrandparent = gitOutput(repository, setOf(0), "rev-parse", "$second^^").output.trim()
    check(secondGrandparent == first) { "each publish chains its provisional commit onto the previous corrected commit" }
    val decodedSecond = AssayIndexV1Codec.decode(
        gitOutput(repository, setOf(0), "show", "$second:.assay/assay-index.v1.json").output,
    )
    check(decodedSecond.assayCommit == secondParent)
    println("PASS subsequent publications chain correctly onto the prior corrected commit")

    val staleLease = runCatching { publish("01J9E0000000000000000RN3", listOf(secondFinding), first) }
    check(staleLease.isFailure)
    check(remoteOutputHead(repository, "release/v1-audit") == second)
    println("PASS stale output publication lease is rejected")
}

private fun remoteOutputHead(repository: Path, branch: String): String? {
    val output = gitOutput(repository, setOf(0), "ls-remote", "--heads", "output", "refs/heads/$branch").output.trim()
    return output.takeIf { it.isNotEmpty() }?.substringBefore('\t')
}

private data class OutputGitResult(val exitCode: Int, val output: String)

private fun gitOutput(repository: Path, allowed: Set<Int>, vararg args: String): OutputGitResult {
    val process = ProcessBuilder(listOf("git", "-C", repository.toString()) + args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8)
    val exit = process.waitFor()
    check(exit in allowed) { "git ${args.joinToString(" ")} failed ($exit): $output" }
    return OutputGitResult(exit, output)
}
