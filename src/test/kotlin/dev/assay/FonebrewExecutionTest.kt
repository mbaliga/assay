package dev.assay

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FonebrewExecutionTest {

    private val sha = "a".repeat(64)

    @Test
    fun acceptsMatchingResultWithExpectedArtifacts() {
        val request = AssayExecutionRequest(
            requestId = "req-1",
            sourceRepo = "owner/repo",
            sourceCommit = "abcdef1234567",
            workspaceHandle = "content://workspace/tree",
            command = listOf("./gradlew", "test"),
            requiredCapabilities = setOf(ExecutionCapability.JVM, ExecutionCapability.GRADLE),
            expectedArtifacts = listOf("build/test-results/test.xml"),
        )
        val result = AssayExecutionResult(
            requestId = "req-1",
            runtimeProviderId = "linux-capsule",
            exitCode = 0,
            stdoutDigest = sha,
            stderrDigest = sha,
            artifactDigests = mapOf("build/test-results/test.xml" to sha),
            startedAtEpochMillis = 10,
            finishedAtEpochMillis = 20,
        )

        assertTrue(FonebrewExecutionGate.validate(request, result).acceptedForFurtherVerification)
    }

    @Test
    fun exitZeroWithoutRequiredArtifactIsNotAcceptedAsEvidenceInput() {
        val request = AssayExecutionRequest(
            requestId = "req-2",
            sourceRepo = "owner/repo",
            sourceCommit = "abcdef1234567",
            workspaceHandle = "content://workspace/tree",
            command = listOf("selenium-test"),
            requiredCapabilities = setOf(ExecutionCapability.BROWSER_HEADLESS, ExecutionCapability.SELENIUM),
            expectedArtifacts = listOf("artifacts/browser-trace.zip"),
        )
        val result = AssayExecutionResult(
            requestId = "req-2",
            runtimeProviderId = "remote",
            exitCode = 0,
            stdoutDigest = sha,
            stderrDigest = sha,
            artifactDigests = emptyMap(),
            startedAtEpochMillis = 10,
            finishedAtEpochMillis = 20,
        )

        assertFalse(FonebrewExecutionGate.validate(request, result).acceptedForFurtherVerification)
    }
}
