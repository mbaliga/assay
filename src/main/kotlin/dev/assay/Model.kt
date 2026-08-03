package dev.assay

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

enum class Scanner(val wireName: String) {
    GITLEAKS("gitleaks"),
    SEMGREP("semgrep"),
    OSV("osv"),
    MOBSF("mobsf");

    companion object {
        fun fromWireName(value: String): Scanner = entries.firstOrNull { it.wireName == value.lowercase() }
            ?: error("unsupported scanner '$value'")
    }
}

enum class RunState { RUNNING, COMPLETE, FAILED }
enum class Severity { SEV1, SEV2, SEV3, SEV4 }
enum class SarifLevel(val wireName: String) {
    ERROR("error"), WARNING("warning"), NOTE("note"), NONE("none");

    companion object {
        fun parse(value: String?): SarifLevel = entries.firstOrNull { it.wireName == value?.lowercase() } ?: WARNING
    }
}

enum class Lifecycle {
    DETECTED, PROPOSED, PROOF_PASSED, PROOF_FAILED, APPROVED, REJECTED, APPLIED,
    STALE, SUPERSEDED, WITHDRAWN, OBSOLETE,
}

data class Location(val file: String, val startLine: Int, val endLine: Int = startLine) {
    init {
        require(file.isNotBlank()) { "finding location file is blank" }
        require(startLine >= 1) { "start line must be positive" }
        require(endLine >= startLine) { "end line precedes start line" }
    }
}

data class Finding(
    val scanner: Scanner,
    val ruleId: String,
    val message: String,
    val location: Location,
    val context: String,
    val level: SarifLevel = SarifLevel.WARNING,
    val securitySeverity: Double? = null,
    val properties: Map<String, String> = emptyMap(),
    val fingerprint: String = Fingerprints.finding(scanner, ruleId, location, context),
) {
    init {
        require(ruleId.isNotBlank()) { "rule id is blank" }
        require(message.isNotBlank()) { "finding message is blank" }
        require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "invalid finding fingerprint" }
        require(securitySeverity == null || securitySeverity in 0.0..10.0) { "invalid security severity" }
    }

    val severity: Severity get() = SeverityPolicy.map(this)
}

data class RunSummary(val sev1: Int = 0, val sev2: Int = 0, val sev3: Int = 0, val sev4: Int = 0) {
    val total: Int get() = sev1 + sev2 + sev3 + sev4

    companion object {
        fun from(findings: List<Finding>): RunSummary = RunSummary(
            sev1 = findings.count { it.severity == Severity.SEV1 },
            sev2 = findings.count { it.severity == Severity.SEV2 },
            sev3 = findings.count { it.severity == Severity.SEV3 },
            sev4 = findings.count { it.severity == Severity.SEV4 },
        )
    }
}

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

data class ToolPin(
    val scanner: Scanner,
    val version: String,
    val sha256: String,
) {
    init {
        require(version.isNotBlank()) { "tool version is blank" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "tool digest must be lowercase SHA-256" }
    }
}

data class ToolInstallation(
    val pin: ToolPin,
    val executable: Path,
)

data class ScannerCommand(
    val executable: Path,
    val arguments: List<String>,
    val workingDirectory: Path,
    val environment: Map<String, String> = emptyMap(),
    val allowedExitCodes: Set<Int> = setOf(0),
    val timeout: Duration = Duration.ofMinutes(10),
) {
    init {
        require(arguments.none { '\u0000' in it }) { "NUL in scanner argument" }
        require(timeout > Duration.ZERO) { "scanner timeout must be positive" }
    }
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val duration: Duration,
)

data class ScannerArtifact(
    val scanner: Scanner,
    val version: String,
    val executableDigest: String,
    val configDigest: String,
    val exitCode: Int,
    val format: String,
    val content: String,
    val findings: List<Finding>,
) {
    init {
        require(executableDigest.matches(Regex("[0-9a-f]{64}"))) { "invalid executable digest" }
        require(configDigest.matches(Regex("[0-9a-f]{64}"))) { "invalid config digest" }
        require(format in setOf("sarif", "json")) { "unsupported scanner artifact format" }
    }
}

data class ScanBundle(
    val sourceRepo: String,
    val sourceCommit: String,
    val scannerArtifacts: List<ScannerArtifact>,
    val findings: List<Finding> = Sarif.merge(scannerArtifacts.flatMap { it.findings }),
) {
    init {
        require(sourceRepo.isNotBlank()) { "source repo is blank" }
        require(sourceCommit.matches(Regex("[0-9a-f]{40}"))) { "source commit must be full lowercase SHA-1" }
        require(scannerArtifacts.map { it.scanner }.distinct().size == scannerArtifacts.size) { "duplicate scanner artifact" }
    }
}

data class TestAttemptEvidence(
    val exitCode: Int,
    val resultDigest: String,
    val namedTestRan: Boolean,
    val assertionFailure: Boolean,
) {
    init {
        require(resultDigest.matches(Regex("[0-9a-f]{64}"))) { "invalid test result digest" }
    }
}

data class ProofEvidence(
    val provingId: String,
    val findingFingerprint: String,
    val sourceCommit: String,
    val beforeTreeRef: String,
    val afterTreeRef: String,
    val patchDigest: String,
    val testDigest: String,
    val scannerConfigDigest: String,
    val toolchainDigest: String,
    val testName: String,
    val beforeAttempts: List<TestAttemptEvidence>,
    val afterAttempts: List<TestAttemptEvidence>,
    val scannerReplayBeforeDigest: String,
    val scannerReplayAfterDigest: String,
    val replayFoundBefore: Boolean,
    val replayClearedAfter: Boolean,
) {
    val passed: Boolean get() =
        beforeAttempts.size >= 2 && afterAttempts.size >= 2 &&
            beforeAttempts.all { it.exitCode != 0 && it.namedTestRan && it.assertionFailure } &&
            afterAttempts.all { it.exitCode == 0 && it.namedTestRan && !it.assertionFailure } &&
            replayFoundBefore && replayClearedAfter
}

sealed interface BusState {
    data object NotConfigured : BusState
    data class Unavailable(val reason: String) : BusState
    data class Invalid(val reason: String) : BusState
    data class Ready(val run: RunRecord?, val findings: List<Finding>) : BusState
}

sealed interface ScannerOutcome {
    val scanner: Scanner
    data class Success(override val scanner: Scanner, val artifact: ScannerArtifact) : ScannerOutcome
    data class Unavailable(override val scanner: Scanner, val reason: String) : ScannerOutcome
    data class Failed(override val scanner: Scanner, val reason: String, val exitCode: Int? = null) : ScannerOutcome
}
