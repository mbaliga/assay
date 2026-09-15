package dev.assay

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

fun main() {
    val workspace = Files.createTempDirectory("assay-cli-")
    val repository = workspace.resolve("repo")
    val storeRoot = workspace.resolve("candidates")
    val patchFile = workspace.resolve("fix.patch")
    val proofFile = workspace.resolve("proof.json")

    git(workspace, "init", "-b", "main", repository.toString())
    git(repository, "config", "user.email", "assay@example.invalid")
    git(repository, "config", "user.name", "Assay Acceptance")
    Files.writeString(repository.resolve("app.txt"), "vulnerable\n")
    git(repository, "add", "app.txt")
    git(repository, "commit", "-m", "source")
    val sourceCommit = git(repository, "rev-parse", "HEAD").trim()

    Files.writeString(repository.resolve("app.txt"), "fixed\n")
    git(repository, "add", "app.txt")
    git(repository, "commit", "-m", "temporary patch source")
    val temporaryFix = git(repository, "rev-parse", "HEAD").trim()
    val patchBytes = gitBytes(repository, "diff", "--binary", "--full-index", sourceCommit, temporaryFix)
    Files.write(patchFile, patchBytes)
    git(repository, "reset", "--hard", sourceCommit)

    val finding = "a".repeat(64)
    val patchDigest = Fingerprints.sha256(patchBytes)
    val testDigest = Fingerprints.sha256("candidate proving test".toByteArray())
    val provingId = "pt1-semgrep-assay-rule-${finding.take(16)}"
    val identity = CandidateIdentity.create(
        "mbaliga/example",
        sourceCommit,
        finding,
        provingId,
        patchDigest,
        testDigest,
    )

    cli(
        "candidate-create",
        "--store", storeRoot.toString(),
        "--source-repo", identity.sourceRepo,
        "--source-commit", identity.sourceCommit,
        "--finding-fingerprint", identity.findingFingerprint,
        "--proving-id", identity.provingId,
        "--patch-digest", identity.patchDigest,
        "--test-digest", identity.testDigest,
        "--at", "2026-08-03T10:00:00Z",
    )
    check(CandidateStore(storeRoot).read(identity.candidateId).lifecycle == Lifecycle.DETECTED)

    cli(
        "candidate-propose",
        "--store", storeRoot.toString(),
        "--candidate", identity.candidateId,
        "--revision", "1",
        "--actor", "system:assay",
        "--at", "2026-08-03T10:01:00Z",
    )
    cli(
        "candidate-prepare",
        "--store", storeRoot.toString(),
        "--candidate", identity.candidateId,
        "--repo", repository.toString(),
        "--patch", patchFile.toString(),
    )
    check(git(repository, "symbolic-ref", "--short", "HEAD").trim() == identity.fixBranch)
    git(repository, "commit", "-m", "apply deterministic candidate")
    val fixCommit = git(repository, "rev-parse", "HEAD").trim()

    val beforeResult = Fingerprints.sha256("assertion failed".toByteArray())
    val afterResult = Fingerprints.sha256("assertion passed".toByteArray())
    val evidence = ProofEvidence(
        provingId = identity.provingId,
        findingFingerprint = identity.findingFingerprint,
        sourceCommit = identity.sourceCommit,
        beforeTreeRef = identity.sourceCommit,
        afterTreeRef = fixCommit,
        patchDigest = identity.patchDigest,
        testDigest = identity.testDigest,
        scannerConfigDigest = "b".repeat(64),
        toolchainDigest = "c".repeat(64),
        testName = "candidate-removes-finding",
        beforeAttempts = listOf(
            TestAttemptEvidence(1, beforeResult, namedTestRan = true, assertionFailure = true),
            TestAttemptEvidence(1, beforeResult, namedTestRan = true, assertionFailure = true),
        ),
        afterAttempts = listOf(
            TestAttemptEvidence(0, afterResult, namedTestRan = true, assertionFailure = false),
            TestAttemptEvidence(0, afterResult, namedTestRan = true, assertionFailure = false),
        ),
        scannerReplayBeforeDigest = "d".repeat(64),
        scannerReplayAfterDigest = "e".repeat(64),
        replayFoundBefore = true,
        replayClearedAfter = true,
    )
    Files.writeString(proofFile, ProofCodec.encode(evidence))

    cli(
        "candidate-proof",
        "--store", storeRoot.toString(),
        "--candidate", identity.candidateId,
        "--revision", "2",
        "--repo", repository.toString(),
        "--proof", proofFile.toString(),
        "--at", "2026-08-03T10:02:00Z",
    )
    cli(
        "candidate-approve",
        "--store", storeRoot.toString(),
        "--candidate", identity.candidateId,
        "--revision", "3",
        "--actor", "human:reviewer@example.com",
        "--at", "2026-08-03T10:03:00Z",
    )
    cli(
        "candidate-apply",
        "--store", storeRoot.toString(),
        "--candidate", identity.candidateId,
        "--revision", "4",
        "--repo", repository.toString(),
        "--at", "2026-08-03T10:04:00Z",
    )

    val applied = CandidateStore(storeRoot).read(identity.candidateId)
    check(applied.lifecycle == Lifecycle.APPLIED)
    check(applied.application?.fixCommit == fixCommit)
    check(applied.revision == 5)
    println("PASS candidate CLI executes the full Git-bound lifecycle")

    val rejectedIdentity = CandidateIdentity.create(
        "mbaliga/example",
        sourceCommit,
        "f".repeat(64),
        "pt1-gitleaks-reject-rule-${"f".repeat(16)}",
        patchDigest,
        Fingerprints.sha256("rejection test".toByteArray()),
    )
    val rejectedLedger = CandidateLedger.create(rejectedIdentity, "system:assay", Instant.parse("2026-08-03T11:00:00Z"))
    rejectedLedger.propose(1, "system:assay", Instant.parse("2026-08-03T11:01:00Z"))
    val proofPassed = rejectedLedger.recordProof(
        2,
        "system:proof-runner",
        Instant.parse("2026-08-03T11:02:00Z"),
        "9".repeat(64),
        true,
    )
    CandidateStore(storeRoot).create(proofPassed)
    cli(
        "candidate-reject",
        "--store", storeRoot.toString(),
        "--candidate", rejectedIdentity.candidateId,
        "--revision", "3",
        "--actor", "human:reviewer@example.com",
        "--at", "2026-08-03T11:03:00Z",
    )
    check(CandidateStore(storeRoot).read(rejectedIdentity.candidateId).lifecycle == Lifecycle.REJECTED)
    println("PASS candidate CLI enforces human rejection")

    val badProof = ProofCodec.encode(evidence).replace("\"gate\": \"passed\"", "\"gate\": \"failed\"")
    check(runCatching { ProofDocument.decode(badProof) }.isFailure)
    println("PASS strict proof decoder rejects contradictory gate fields")
}

private fun cli(vararg args: String) = CandidateCli.run(arrayOf(*args))

private fun git(repository: Path, vararg args: String): String =
    gitBytes(repository, *args).toString(Charsets.UTF_8)

private fun gitBytes(repository: Path, vararg args: String): ByteArray {
    val process = ProcessBuilder(listOf("git", "-C", repository.toString()) + args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.use { it.readAllBytes() }
    check(process.waitFor() == 0) {
        "git ${args.joinToString(" ")} failed: ${output.toString(Charsets.UTF_8)}"
    }
    return output
}
