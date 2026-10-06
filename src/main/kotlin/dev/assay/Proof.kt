package dev.assay

object ProofGate {
    private val EMPTY_SHA256 = Fingerprints.sha256(ByteArray(0))

    fun validate(evidence: ProofEvidence): Result<ProofEvidence> = runCatching {
        require(evidence.provingId.matches(ContractValidator.PROVING_ID)) { "invalid proving id" }
        require(evidence.findingFingerprint.matches(SHA256)) { "invalid finding fingerprint" }
        require(evidence.provingId.endsWith("-${evidence.findingFingerprint.take(16)}")) {
            "proving id is not bound to the finding fingerprint"
        }
        require(evidence.sourceCommit.matches(SHA1)) { "invalid source commit" }
        require(evidence.beforeTreeRef.matches(SHA1)) { "invalid before tree ref" }
        require(evidence.afterTreeRef.matches(SHA1)) { "invalid after tree ref" }
        require(evidence.beforeTreeRef == evidence.sourceCommit) { "before tree is not the audited source commit" }
        require(evidence.afterTreeRef != evidence.beforeTreeRef) { "proof has no tree delta" }
        listOf(
            evidence.patchDigest,
            evidence.testDigest,
            evidence.scannerConfigDigest,
            evidence.toolchainDigest,
            evidence.scannerReplayBeforeDigest,
            evidence.scannerReplayAfterDigest,
        ).forEach { require(it.matches(SHA256)) { "invalid proof digest" } }
        require(evidence.patchDigest != EMPTY_SHA256) { "empty patch rejected" }
        require(evidence.testDigest != EMPTY_SHA256) { "empty proving test rejected" }
        require(evidence.testName.isNotBlank()) { "named proving test is required" }
        require(evidence.beforeAttempts.size >= 2 && evidence.afterAttempts.size >= 2) {
            "proof requires repeated pre/post test execution"
        }
        require(evidence.beforeAttempts.map { it.resultDigest }.distinct().size == 1) {
            "pre-fix test result is flaky"
        }
        require(evidence.afterAttempts.map { it.resultDigest }.distinct().size == 1) {
            "post-fix test result is flaky"
        }
        require(evidence.beforeAttempts.first().resultDigest != evidence.afterAttempts.first().resultDigest) {
            "pre/post structured test evidence is identical"
        }
        require(evidence.scannerReplayBeforeDigest != evidence.scannerReplayAfterDigest) {
            "scanner replay evidence did not change"
        }
        require(evidence.passed) { "proof did not establish assertion-fail-before/pass-after and scanner clearance" }
        evidence
    }

    private val SHA1 = Regex("[0-9a-f]{40}")
    private val SHA256 = Regex("[0-9a-f]{64}")
}

object ProofCodec {
    fun encode(evidence: ProofEvidence): String = Json.stringify(
        Json.obj(
            "schemaVersion" to Json.str("1.1.0"),
            "provingId" to Json.str(evidence.provingId),
            "findingFingerprint" to Json.str(evidence.findingFingerprint),
            "sourceCommit" to Json.str(evidence.sourceCommit),
            "beforeTreeRef" to Json.str(evidence.beforeTreeRef),
            "afterTreeRef" to Json.str(evidence.afterTreeRef),
            "patchDigest" to Json.str(evidence.patchDigest),
            "testDigest" to Json.str(evidence.testDigest),
            "scannerConfigDigest" to Json.str(evidence.scannerConfigDigest),
            "toolchainDigest" to Json.str(evidence.toolchainDigest),
            "testName" to Json.str(evidence.testName),
            "beforeAttempts" to attempts(evidence.beforeAttempts),
            "afterAttempts" to attempts(evidence.afterAttempts),
            "scannerReplay" to Json.obj(
                "beforeDigest" to Json.str(evidence.scannerReplayBeforeDigest),
                "afterDigest" to Json.str(evidence.scannerReplayAfterDigest),
                "foundBefore" to Json.bool(evidence.replayFoundBefore),
                "clearedAfter" to Json.bool(evidence.replayClearedAfter),
            ),
            "gate" to Json.str(if (evidence.passed) "passed" else "failed"),
        ),
        pretty = true,
    )

    private fun attempts(values: List<TestAttemptEvidence>): JsonValue.Arr = Json.arr(values.map { attempt ->
        Json.obj(
            "exitCode" to Json.num(attempt.exitCode),
            "resultDigest" to Json.str(attempt.resultDigest),
            "namedTestRan" to Json.bool(attempt.namedTestRan),
            "assertionFailure" to Json.bool(attempt.assertionFailure),
        )
    })
}

class LifecycleMachine(initial: Lifecycle = Lifecycle.DETECTED) {
    var state: Lifecycle = initial
        private set

    fun move(next: Lifecycle) {
        val allowed = when (state) {
            Lifecycle.DETECTED -> setOf(Lifecycle.PROPOSED, Lifecycle.OBSOLETE)
            Lifecycle.PROPOSED -> setOf(
                Lifecycle.PROOF_PASSED,
                Lifecycle.PROOF_FAILED,
                Lifecycle.WITHDRAWN,
                Lifecycle.SUPERSEDED,
                Lifecycle.STALE,
            )
            Lifecycle.PROOF_FAILED -> setOf(Lifecycle.PROPOSED, Lifecycle.WITHDRAWN, Lifecycle.SUPERSEDED)
            Lifecycle.PROOF_PASSED -> setOf(Lifecycle.APPROVED, Lifecycle.REJECTED, Lifecycle.STALE)
            Lifecycle.APPROVED -> setOf(Lifecycle.APPLIED, Lifecycle.STALE)
            Lifecycle.STALE -> setOf(Lifecycle.PROPOSED, Lifecycle.OBSOLETE, Lifecycle.SUPERSEDED)
            else -> emptySet()
        }
        require(next in allowed) { "illegal lifecycle transition: $state -> $next" }
        state = next
    }
}
