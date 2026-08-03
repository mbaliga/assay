package dev.assay

object DemoData {
    fun snapshot(): ConsoleSnapshot {
        val firstFingerprint = "a".repeat(64)
        val secondFingerprint = "b".repeat(64)
        val candidateId = "cand1-${"c".repeat(20)}"
        return ConsoleSnapshot(
            generatedAt = "2026-08-03T12:00:00Z",
            availability = ConsoleAvailability.READY,
            reason = null,
            sourceRepo = "sample/compass",
            sourceCommit = "d".repeat(40),
            runId = "run-sample-android-001",
            findings = listOf(
                ConsoleFinding(
                    fingerprint = firstFingerprint,
                    scanner = "semgrep",
                    ruleId = "android-webview-javascript-interface",
                    severity = "SEV2",
                    message = "A JavaScript interface is exposed to WebView content. Confirm that only trusted content can reach the interface.",
                    file = "app/src/main/java/sample/BrowserActivity.kt",
                    startLine = 84,
                    candidateIds = listOf(candidateId),
                ),
                ConsoleFinding(
                    fingerprint = secondFingerprint,
                    scanner = "osv",
                    ruleId = "GHSA-sample-1234",
                    severity = "SEV3",
                    message = "A sample dependency is affected by a known vulnerability and should be upgraded after compatibility review.",
                    file = "gradle/libs.versions.toml",
                    startLine = 17,
                    candidateIds = emptyList(),
                ),
            ),
            candidates = listOf(
                ConsoleCandidate(
                    candidateId = candidateId,
                    findingFingerprint = firstFingerprint,
                    lifecycle = "proof_passed",
                    revision = 3,
                    fixBranch = "assay/fix/$candidateId",
                    humanApproved = false,
                ),
            ),
        )
    }
}
