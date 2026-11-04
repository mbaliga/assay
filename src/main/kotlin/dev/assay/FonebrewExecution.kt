package dev.assay

/**
 * Narrow execution request accepted from Fonebrew.
 *
 * This does NOT make Fonebrew a proof writer. It only describes the environment and command Assay
 * wants executed. Assay still validates source identity, consumes artifacts, runs its deterministic
 * proof rules and is the only component allowed to publish Assay evidence.
 */
enum class ExecutionCapability {
    SHELL,
    JVM,
    GRADLE,
    PYTHON,
    NODE,
    BROWSER_HEADLESS,
    SELENIUM,
    ANDROID_SDK,
    NETWORK,
    VM_ISOLATION,
}

enum class ExecutionIsolation { NONE, PROCESS, VM }

data class AssayExecutionRequest(
    val requestId: String,
    val sourceRepo: String,
    val sourceCommit: String,
    val workspaceHandle: String,
    val command: List<String>,
    val requiredCapabilities: Set<ExecutionCapability>,
    val isolation: ExecutionIsolation = ExecutionIsolation.PROCESS,
    val expectedArtifacts: List<String> = emptyList(),
) {
    init {
        require(requestId.isNotBlank()) { "requestId must be non-blank" }
        require(sourceRepo.isNotBlank()) { "sourceRepo must be non-blank" }
        require(sourceCommit.matches(Regex("^[0-9a-fA-F]{7,64}$"))) { "sourceCommit must be a git-like hex revision" }
        require(workspaceHandle.isNotBlank()) { "workspaceHandle must be non-blank" }
        require(command.isNotEmpty() && command.all { it.isNotBlank() }) { "command must contain non-blank argv elements" }
        expectedArtifacts.forEach { Fingerprints.normalizePath(it) }
    }
}

data class AssayExecutionResult(
    val requestId: String,
    val runtimeProviderId: String,
    val exitCode: Int,
    val stdoutDigest: String,
    val stderrDigest: String,
    val artifactDigests: Map<String, String>,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must be non-blank" }
        require(runtimeProviderId.isNotBlank()) { "runtimeProviderId must be non-blank" }
        require(stdoutDigest.matches(Regex("^[0-9a-f]{64}$"))) { "stdoutDigest must be sha256" }
        require(stderrDigest.matches(Regex("^[0-9a-f]{64}$"))) { "stderrDigest must be sha256" }
        require(finishedAtEpochMillis >= startedAtEpochMillis) { "finish must not predate start" }
        artifactDigests.forEach { (path, digest) ->
            Fingerprints.normalizePath(path)
            require(digest.matches(Regex("^[0-9a-f]{64}$"))) { "artifact digest must be sha256" }
        }
    }
}

/**
 * What Assay can trust from a Fonebrew execution result before proof-specific validation.
 * Exit 0 alone is never proof; requested artifacts must be present and digest-shaped.
 */
object FonebrewExecutionGate {
    data class Verdict(val acceptedForFurtherVerification: Boolean, val reasons: List<String>)

    fun validate(request: AssayExecutionRequest, result: AssayExecutionResult): Verdict {
        val reasons = mutableListOf<String>()
        if (request.requestId != result.requestId) reasons += "request id mismatch"
        val missing = request.expectedArtifacts.filterNot { it in result.artifactDigests }
        if (missing.isNotEmpty()) reasons += "missing expected artifacts: " + missing.joinToString()
        if (result.finishedAtEpochMillis < result.startedAtEpochMillis) reasons += "invalid timing"
        return Verdict(reasons.isEmpty(), reasons)
    }
}
