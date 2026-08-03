package dev.assay

import java.nio.file.Files
import java.time.Instant

fun main() {
    var passed = 0
    fun test(name: String, block: () -> Unit) {
        block(); passed++; println("PASS $name")
    }

    test("stable fingerprint") {
        val f1 = Finding(Scanner.GITLEAKS, "hardcoded-secret", "m", Location("src/A.kt", 4), "token=[REDACTED]")
        val f2 = Finding(Scanner.GITLEAKS, "hardcoded-secret", "m", Location("src/A.kt", 4), "token=[REDACTED]")
        check(f1.fingerprint == f2.fingerprint)
        check(Fingerprints.provingId(f1).matches(Regex("pt1-gitleaks-hardcoded-secret-[0-9a-f]{16}")))
    }
    test("redaction") {
        val raw = "api_key=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY"
        val safe = Redaction.sanitize(raw)
        check("FAKE_CREDENTIAL" !in safe)
        check(!Redaction.containsSecret(safe))
    }
    test("deterministic scan and safe publication") {
        val repo = Files.createTempDirectory("assay-repo")
        Files.writeString(repo.resolve("config.txt"), "token=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY\n")
        val findings = DeterministicSecretScanner.scan(repo)
        check(findings.size == 1)
        val bus = Files.createTempDirectory("assay-bus")
        BusWriter(bus).publish("example/repo", "a".repeat(40), findings, Instant.parse("2026-08-03T09:00:00Z"))
        val all = Files.walk(bus).filter { Files.isRegularFile(it) }.toList().joinToString("\n") { Files.readString(it) }
        check("FAKE_CREDENTIAL" !in all)
        check(BusReader(bus).read() is BusState.Ready)
    }
    test("bus fails closed on tamper") {
        val repo = Files.createTempDirectory("assay-repo")
        Files.writeString(repo.resolve("config.txt"), "secret=FAKE_CREDENTIAL_FOR_ASSAY_TEST_ONLY\n")
        val bus = Files.createTempDirectory("assay-bus")
        val record = BusWriter(bus).publish("example/repo", "b".repeat(40), DeterministicSecretScanner.scan(repo), Instant.parse("2026-08-03T09:01:00Z"))
        Files.writeString(bus.resolve(record.findingsRef), "tampered")
        check(BusReader(bus).read() is BusState.Invalid)
    }
    test("typed unavailable states") {
        check(BusReader(null).read() is BusState.NotConfigured)
        check(BusReader(Files.createTempDirectory("x").resolve("missing")).read() is BusState.Unavailable)
    }
    test("proof requires meaningful failure and replay") {
        val digest = "c".repeat(64)
        val evidence = ProofEvidence(
            "pt1-semgrep-sql-injection-${"d".repeat(16)}", digest, "e".repeat(40), digest, digest, digest, digest,
            beforeExit = 1, afterExit = 0, beforeAssertionFailure = true, namedTestRanBefore = true,
            namedTestRanAfter = true, replayClearedFinding = true,
        )
        check(ProofGate.validate(evidence).isSuccess)
        check(ProofGate.validate(evidence.copy(beforeAssertionFailure = false)).isFailure)
        check(ProofGate.validate(evidence.copy(replayClearedFinding = false)).isFailure)
    }
    test("approval lifecycle") {
        val machine = LifecycleMachine()
        machine.move(Lifecycle.PROPOSED)
        machine.move(Lifecycle.PROOF_PASSED)
        machine.move(Lifecycle.APPROVED)
        machine.move(Lifecycle.APPLIED)
        check(machine.state == Lifecycle.APPLIED)
        check(runCatching { machine.move(Lifecycle.DETECTED) }.isFailure)
    }
    println("$passed acceptance checks passed")
}
