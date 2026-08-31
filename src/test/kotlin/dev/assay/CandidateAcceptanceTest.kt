package dev.assay

import java.time.Instant

fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) {
        block()
        passed++
        println("PASS $name")
    }

    val source = "a".repeat(40)
    val fingerprint = "b".repeat(64)
    val patch = "c".repeat(64)
    val testDigest = "d".repeat(64)
    val provingId = "pt1-semgrep-assay-rule-${fingerprint.take(16)}"

    test("candidate identity is deterministic and branch-bound") {
        val first = CandidateIdentity.create("example/repo", source, fingerprint, provingId, patch, testDigest)
        val second = CandidateIdentity.create("example/repo", source, fingerprint, provingId, patch, testDigest)
        check(first == second)
        check(first.fixBranch == "assay/fix/${first.candidateId}")
        check(runCatching { first.copy(fixBranch = "main") }.isFailure)
    }

    test("candidate requires revision-safe fail-before approval flow") {
        val identity = CandidateIdentity.create("example/repo", source, fingerprint, provingId, patch, testDigest)
        val ledger = CandidateLedger.create(identity, "system:assay", Instant.parse("2026-08-03T10:00:00Z"))
        ledger.propose(1, "system:assay", Instant.parse("2026-08-03T10:01:00Z"))
        val proof = "e".repeat(64)
        ledger.recordProof(2, "system:proof-runner", Instant.parse("2026-08-03T10:02:00Z"), proof, true)
        check(runCatching {
            ledger.approve(3, "system:assay", Instant.parse("2026-08-03T10:03:00Z"))
        }.isFailure)
        ledger.approve(3, "human:reviewer@example.com", Instant.parse("2026-08-03T10:03:00Z"))
        val applied = ledger.apply(
            expectedRevision = 4,
            actor = "system:executor",
            at = Instant.parse("2026-08-03T10:04:00Z"),
            sourceCommit = source,
            fixCommit = "f".repeat(40),
            fixBranch = identity.fixBranch,
        )
        check(applied.lifecycle == Lifecycle.APPLIED)
        check(applied.events.size == 5)
        CandidateLedger.validate(applied)
    }

    test("candidate rejects stale revisions, wrong branches, and source drift") {
        val identity = CandidateIdentity.create("example/repo", source, fingerprint, provingId, patch, testDigest)
        val ledger = CandidateLedger.create(identity, "system:assay", Instant.parse("2026-08-03T11:00:00Z"))
        check(runCatching {
            ledger.propose(0, "system:assay", Instant.parse("2026-08-03T11:01:00Z"))
        }.isFailure)
        ledger.propose(1, "system:assay", Instant.parse("2026-08-03T11:01:00Z"))
        val stale = ledger.markStale(
            2,
            "system:source-watch",
            Instant.parse("2026-08-03T11:02:00Z"),
            "1".repeat(40),
        )
        check(stale.lifecycle == Lifecycle.STALE)
        CandidateLedger.validate(stale)
    }

    test("event chain detects tampering") {
        val identity = CandidateIdentity.create("example/repo", source, fingerprint, provingId, patch, testDigest)
        val record = CandidateLedger.create(identity, "system:assay", Instant.parse("2026-08-03T12:00:00Z")).snapshot()
        val forged = record.copy(events = record.events.map { it.copy(actor = "human:attacker") })
        check(runCatching { CandidateLedger.validate(forged) }.isFailure)
    }

    println("$passed candidate acceptance checks passed")
}
