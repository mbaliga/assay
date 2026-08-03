package dev.assay

object ProofGate {
    fun validate(evidence: ProofEvidence): Result<ProofEvidence> {
        if (!evidence.provingId.matches(Regex("pt1-(gitleaks|semgrep|osv|mobsf)-[a-z0-9-]{1,40}-[0-9a-f]{16}"))) {
            return Result.failure(IllegalArgumentException("invalid proving id"))
        }
        if (!evidence.passed) return Result.failure(IllegalStateException("proof did not establish fail-before/pass-after"))
        listOf(
            evidence.findingFingerprint,
            evidence.patchDigest,
            evidence.testDigest,
            evidence.scannerConfigDigest,
            evidence.toolchainDigest,
        ).forEach { if (!it.matches(Regex("[0-9a-f]{64}"))) return Result.failure(IllegalArgumentException("invalid digest")) }
        if (!evidence.sourceCommit.matches(Regex("[0-9a-f]{40}"))) return Result.failure(IllegalArgumentException("invalid source commit"))
        return Result.success(evidence)
    }
}

class LifecycleMachine(initial: Lifecycle = Lifecycle.DETECTED) {
    var state: Lifecycle = initial; private set

    fun move(next: Lifecycle) {
        val allowed = when (state) {
            Lifecycle.DETECTED -> setOf(Lifecycle.PROPOSED, Lifecycle.OBSOLETE)
            Lifecycle.PROPOSED -> setOf(Lifecycle.PROOF_PASSED, Lifecycle.PROOF_FAILED, Lifecycle.WITHDRAWN, Lifecycle.SUPERSEDED)
            Lifecycle.PROOF_PASSED -> setOf(Lifecycle.APPROVED, Lifecycle.REJECTED, Lifecycle.STALE)
            Lifecycle.APPROVED -> setOf(Lifecycle.APPLIED, Lifecycle.STALE)
            else -> emptySet()
        }
        require(next in allowed) { "illegal lifecycle transition: $state -> $next" }
        state = next
    }
}
