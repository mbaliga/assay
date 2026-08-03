package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

fun main() {
    val workspace = Files.createTempDirectory("assay-evidence-git-")
    val repository = workspace.resolve("source")
    val remote = workspace.resolve("evidence.git")
    val bus = workspace.resolve("bus")

    gitPublisher(workspace, setOf(0), "init", "-b", "main", repository.toString())
    gitPublisher(repository, setOf(0), "config", "user.email", "assay@example.invalid")
    gitPublisher(repository, setOf(0), "config", "user.name", "Assay Acceptance")
    Files.writeString(repository.resolve("README.md"), "source\n")
    gitPublisher(repository, setOf(0), "add", "README.md")
    gitPublisher(repository, setOf(0), "commit", "-m", "source")
    val sourceCommit = gitPublisher(repository, setOf(0), "rev-parse", "HEAD").output.trim()

    gitPublisher(workspace, setOf(0), "init", "--bare", remote.toString())
    gitPublisher(repository, setOf(0), "remote", "add", "evidence", remote.toString())

    val finding = Finding(
        scanner = Scanner.GITLEAKS,
        ruleId = "assay-test-secret",
        message = "fixture finding",
        location = Location("README.md", 1),
        context = "fixture-context",
    )
    BusWriter(bus).publish(
        sourceRepo = "mbaliga/example",
        sourceCommit = sourceCommit,
        findings = listOf(finding),
        now = Instant.parse("2026-08-03T12:00:00Z"),
    )

    val publisher = GitBusPublisher(repository)
    val first = publisher.publish(bus, "evidence", null, sourceCommit)
    check(first.matches(Regex("[0-9a-f]{40}")))
    check(remoteHead(repository, "evidence") == first)
    check(gitPublisher(repository, setOf(0, 1), "merge-base", "--is-ancestor", sourceCommit, first).exitCode == 1)
    check(gitPublisher(repository, setOf(0), "show", "$first:index.json").output.contains("mbaliga/example"))
    println("PASS first evidence publication is disconnected from source history")

    BusWriter(bus).publish(
        sourceRepo = "mbaliga/example",
        sourceCommit = sourceCommit,
        findings = emptyList(),
        now = Instant.parse("2026-08-03T12:01:00Z"),
    )
    val second = publisher.publish(bus, "evidence", first, sourceCommit)
    check(second != first)
    check(remoteHead(repository, "evidence") == second)
    check(gitPublisher(repository, setOf(0), "rev-parse", "$second^").output.trim() == first)
    println("PASS subsequent evidence publication preserves isolated branch history")

    val staleLease = runCatching { publisher.publish(bus, "evidence", first, sourceCommit) }
    check(staleLease.isFailure)
    check(remoteHead(repository, "evidence") == second)
    println("PASS stale evidence publication lease is rejected")
}

private fun remoteHead(repository: Path, remote: String): String? {
    val output = gitPublisher(
        repository,
        setOf(0),
        "ls-remote",
        "--heads",
        remote,
        "refs/heads/assay/evidence",
    ).output.trim()
    return output.takeIf { it.isNotEmpty() }?.substringBefore('\t')
}

private data class PublisherGitResult(val exitCode: Int, val output: String)

private fun gitPublisher(
    repository: Path,
    allowed: Set<Int>,
    vararg args: String,
): PublisherGitResult {
    val process = ProcessBuilder(listOf("git", "-C", repository.toString()) + args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.use { it.readAllBytes() }.toString(Charsets.UTF_8)
    val exit = process.waitFor()
    check(exit in allowed) { "git ${args.joinToString(" ")} failed ($exit): $output" }
    return PublisherGitResult(exit, output)
}
