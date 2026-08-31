package dev.assay

object ContractValidator {
    private const val VERSION = "1.1.0"

    fun validateIndex(index: JsonValue.Obj) {
        requireExactKeys(
            index,
            setOf("schemaVersion", "producer", "sourceRepo", "latestComplete", "activeRuns", "runs"),
            "index",
        )
        require(index.string("schemaVersion") == VERSION) { "unsupported index schema" }
        require(index.string("sourceRepo").isNotBlank()) { "sourceRepo is blank" }
        val producer = index.required("producer").requireObject("$.producer")
        requireExactKeys(producer, setOf("app", "runnerContract"), "producer")
        require(producer.string("app") == "Assay") { "unexpected producer" }
        require(producer.string("runnerContract") == VERSION) { "runner contract mismatch" }
        index.required("activeRuns").requireArray("$.activeRuns").values.forEachIndexed { i, value ->
            val run = value.requireObject("$.activeRuns[$i]")
            require(run.string("status") == "running") { "active run is not running" }
        }
        val seen = mutableSetOf<String>()
        index.required("runs").requireArray("$.runs").values.forEachIndexed { i, value ->
            val entry = value.requireObject("$.runs[$i]")
            requireExactKeys(
                entry,
                setOf(
                    "runId", "sourceCommit", "createdAt", "status", "path", "runRef", "findingsRef",
                    "statusRef", "manifestRef", "manifestDigest", "summary", "gate",
                ),
                "run entry",
            )
            val runId = entry.string("runId")
            require(runId.matches(Regex("[0-9]{8}T[0-9]{6}Z-[0-9a-f]{7}"))) { "invalid run id" }
            require(seen.add(runId)) { "duplicate run id" }
            require(entry.string("sourceCommit").matches(Regex("[0-9a-f]{40}"))) { "invalid source commit" }
            require(runCatching { java.time.Instant.parse(entry.string("createdAt")) }.isSuccess) { "invalid createdAt" }
            require(entry.string("status") in setOf("complete", "failed")) { "invalid completed run status" }
            require(entry.string("path") == "runs/$runId") { "run entry path mismatch" }
            require(entry.string("runRef") == "runs/$runId/run.json") { "runRef mismatch" }
            require(entry.string("findingsRef") == "runs/$runId/findings.sarif") { "findingsRef mismatch" }
            require(entry.string("statusRef") == "runs/$runId/status.json") { "statusRef mismatch" }
            require(entry.string("manifestRef") == "runs/$runId/manifest.sha256") { "manifestRef mismatch" }
            require(entry.string("manifestDigest").matches(Regex("[0-9a-f]{64}"))) { "invalid manifest digest" }
            validateSummary(entry.required("summary").requireObject())
            validateGate(entry.required("gate").requireObject())
        }
        when (val latest = index.required("latestComplete")) {
            JsonValue.Null -> Unit
            is JsonValue.Str -> require(latest.value in seen) { "latestComplete does not reference a run" }
            else -> error("latestComplete must be string or null")
        }
    }

    fun validateRun(run: JsonValue.Obj, runId: String, sourceRepo: String, sourceCommit: String) {
        requireExactKeys(
            run,
            setOf(
                "schemaVersion", "runId", "sourceRepo", "sourceCommit", "createdAt", "status",
                "findingsRef", "statusRef", "manifestRef", "severityMappingVersion", "scanners",
            ),
            "run metadata",
        )
        require(run.string("schemaVersion") == VERSION) { "unsupported run schema" }
        require(run.string("runId") == runId) { "run id mismatch" }
        require(run.string("sourceRepo") == sourceRepo) { "source repo mismatch" }
        require(run.string("sourceCommit") == sourceCommit) { "source commit mismatch" }
        require(run.string("status") == "complete") { "run metadata is not complete" }
        require(run.string("findingsRef") == "runs/$runId/findings.sarif") { "run findingsRef mismatch" }
        require(run.string("statusRef") == "runs/$runId/status.json") { "run statusRef mismatch" }
        require(run.string("manifestRef") == "runs/$runId/manifest.sha256") { "run manifestRef mismatch" }
        require(run.string("severityMappingVersion") == SeverityPolicy.VERSION) { "severity mapping version mismatch" }
        require(runCatching { java.time.Instant.parse(run.string("createdAt")) }.isSuccess) { "invalid run createdAt" }
        val seen = mutableSetOf<String>()
        run.required("scanners").requireArray("$.scanners").values.forEachIndexed { i, value ->
            val scanner = value.requireObject("$.scanners[$i]")
            requireExactKeys(
                scanner,
                setOf("name", "version", "executableDigest", "configDigest", "exitCode", "format", "artifactRef"),
                "scanner metadata",
            )
            val name = scanner.string("name")
            Scanner.fromWireName(name)
            require(seen.add(name)) { "duplicate scanner metadata" }
            require(scanner.string("version").isNotBlank()) { "scanner version is blank" }
            require(scanner.string("executableDigest").matches(Regex("[0-9a-f]{64}"))) { "invalid executable digest" }
            require(scanner.string("configDigest").matches(Regex("[0-9a-f]{64}"))) { "invalid config digest" }
            require(scanner.required("exitCode").doubleOrNull()?.toInt() in setOf(0, 1)) { "invalid scanner exit" }
            val format = scanner.string("format")
            require(format in setOf("sarif", "json")) { "invalid scanner format" }
            require(scanner.string("artifactRef") == "runs/$runId/scanners/$name.$format") { "scanner artifactRef mismatch" }
        }
    }

    fun validateStatus(status: JsonValue.Obj) {
        requireExactKeys(status, setOf("schemaVersion", "findings"), "status")
        require(status.string("schemaVersion") == VERSION) { "unsupported status schema" }
        val seen = mutableSetOf<String>()
        status.required("findings").requireArray("$.findings").values.forEachIndexed { i, value ->
            val finding = value.requireObject("$.findings[$i]")
            requireExactKeys(
                finding,
                setOf("findingFingerprint", "scanner", "ruleId", "severity", "lifecycle", "provingId"),
                "status finding",
            )
            val fingerprint = finding.string("findingFingerprint")
            require(fingerprint.matches(Regex("[0-9a-f]{64}"))) { "invalid status fingerprint" }
            require(seen.add(fingerprint)) { "duplicate status fingerprint" }
            Scanner.fromWireName(finding.string("scanner"))
            require(finding.string("ruleId").isNotBlank()) { "blank status rule id" }
            require(runCatching { Severity.valueOf(finding.string("severity")) }.isSuccess) { "invalid status severity" }
            require(finding.string("lifecycle") in setOf(
                "detected", "proposed", "proof_passed", "proof_failed", "approved", "rejected", "applied",
                "stale", "superseded", "withdrawn", "obsolete",
            )) { "invalid lifecycle" }
            when (val provingId = finding.required("provingId")) {
                JsonValue.Null -> Unit
                is JsonValue.Str -> require(provingId.value.matches(PROVING_ID)) { "invalid proving id" }
                else -> error("provingId must be string or null")
            }
        }
    }

    fun validateStatusAgainstFindings(status: JsonValue.Obj, findings: List<Finding>) {
        val statusRecords = status.required("findings").requireArray().values.map { it.requireObject() }
        val byFingerprint = statusRecords.associateBy { it.string("findingFingerprint") }
        require(byFingerprint.keys == findings.map { it.fingerprint }.toSet()) { "status/findings fingerprint mismatch" }
        findings.forEach { finding ->
            val record = byFingerprint.getValue(finding.fingerprint)
            require(record.string("scanner") == finding.scanner.wireName) { "status scanner mismatch" }
            require(record.string("ruleId") == finding.ruleId) { "status rule mismatch" }
            require(record.string("severity") == finding.severity.name) { "status severity mismatch" }
        }
    }

    fun validateIndexEntryAgainstFindings(entry: JsonValue.Obj, findings: List<Finding>) {
        val expected = RunSummary.from(findings)
        val summary = entry.required("summary").requireObject("$.runs[].summary")
        require(summary.required("sev1").doubleOrNull()?.toInt() == expected.sev1) { "SEV1 summary mismatch" }
        require(summary.required("sev2").doubleOrNull()?.toInt() == expected.sev2) { "SEV2 summary mismatch" }
        require(summary.required("sev3").doubleOrNull()?.toInt() == expected.sev3) { "SEV3 summary mismatch" }
        require(summary.required("sev4").doubleOrNull()?.toInt() == expected.sev4) { "SEV4 summary mismatch" }
        val gate = entry.required("gate").requireObject("$.runs[].gate")
        val required = gate.required("provingTestsRequired").doubleOrNull()?.toInt()
            ?: error("invalid provingTestsRequired")
        val passed = gate.required("provingTestsPassed").doubleOrNull()?.toInt()
            ?: error("invalid provingTestsPassed")
        require(required == findings.size) { "proving-test requirement does not cover every open finding" }
        require(passed == 0) { "detected findings cannot be marked proven without proof artifacts" }
        require(gate.required("passed").booleanOrNull() == findings.isEmpty()) { "run gate contradicts open findings" }
    }

    private fun validateSummary(summary: JsonValue.Obj) {
        requireExactKeys(summary, setOf("sev1", "sev2", "sev3", "sev4"), "summary")
        listOf("sev1", "sev2", "sev3", "sev4").forEach { key ->
            val value = summary.required(key).doubleOrNull()
            require(value != null && value >= 0 && value % 1.0 == 0.0) { "invalid summary count" }
        }
    }

    private fun validateGate(gate: JsonValue.Obj) {
        requireExactKeys(gate, setOf("provingTestsRequired", "provingTestsPassed", "passed"), "gate")
        val required = gate.required("provingTestsRequired").doubleOrNull()?.toInt() ?: error("invalid required count")
        val passed = gate.required("provingTestsPassed").doubleOrNull()?.toInt() ?: error("invalid passed count")
        require(required >= 0 && passed in 0..required) { "invalid proving-test counts" }
        require(gate.required("passed").booleanOrNull() == (passed == required)) { "gate boolean mismatch" }
    }

    private fun requireExactKeys(value: JsonValue.Obj, expected: Set<String>, name: String) {
        require(value.values.keys == expected) {
            "$name keys mismatch: expected ${expected.sorted()}, got ${value.values.keys.sorted()}"
        }
    }

    val PROVING_ID = Regex("pt1-(gitleaks|semgrep|osv|mobsf)-[a-z0-9-]{1,40}-[0-9a-f]{16}")
}
