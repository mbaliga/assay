package dev.assay

import java.time.Instant

enum class Scanner(val wireName: String) { GITLEAKS("gitleaks"), SEMGREP("semgrep"), OSV("osv"), MOBSF("mobsf") }
enum class RunState { RUNNING, COMPLETE, FAILED }
enum class Lifecycle { DETECTED, PROPOSED, PROOF_PASSED, PROOF_FAILED, APPROVED, REJECTED, APPLIED, STALE, SUPERSEDED, WITHDRAWN, OBSOLETE }

data class Location(val file: String, val startLine: Int, val endLine: Int = startLine)
data class Finding(
    val scanner: Scanner,
    val ruleId: String,
    val message: String,
    val location: Location,
    val context: String,
    val fingerprint: String = Fingerprints.finding(scanner, ruleId, location, context),
)

data class RunSummary(val sev1: Int = 0, val sev2: Int = 0, val sev3: Int = 0, val sev4: Int = 0)
data class RunRecord(
    val runId: String,
    val sourceRepo: String,
    val sourceCommit: String,
    val createdAt: Instant,
    val status: RunState,
    val findingsRef: String,
    val manifestRef: String,
    val manifestDigest: String,
    val summary: RunSummary,
)

data class ProofEvidence(
    val provingId: String,
    val findingFingerprint: String,
    val sourceCommit: String,
    val patchDigest: String,
    val testDigest: String,
    val scannerConfigDigest: String,
    val toolchainDigest: String,
    val beforeExit: Int,
    val afterExit: Int,
    val beforeAssertionFailure: Boolean,
    val namedTestRanBefore: Boolean,
    val namedTestRanAfter: Boolean,
    val replayClearedFinding: Boolean,
) {
    val passed: Boolean get() = beforeExit != 0 && afterExit == 0 && beforeAssertionFailure && namedTestRanBefore && namedTestRanAfter && replayClearedFinding
}

sealed interface BusState {
    data object NotConfigured : BusState
    data class Unavailable(val reason: String) : BusState
    data class Invalid(val reason: String) : BusState
    data class Ready(val run: RunRecord?, val findings: List<Finding>) : BusState
}
