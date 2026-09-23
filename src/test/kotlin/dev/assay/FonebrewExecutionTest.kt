package dev.assay

fun main() {
    val sha = "a".repeat(64)
    var passed = 0

    fun test(name: String, body: () -> Unit) {
        body()
        passed++
        println("PASS $name")
    }

    test("accepts matching result with expected artifacts") {
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

        check(FonebrewExecutionGate.validate(request, result).acceptedForFurtherVerification)
    }

    test("exit zero without required artifact is not accepted as evidence input") {
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

        check(!FonebrewExecutionGate.validate(request, result).acceptedForFurtherVerification)
    }

    println("$passed Fonebrew execution checks passed")
}
