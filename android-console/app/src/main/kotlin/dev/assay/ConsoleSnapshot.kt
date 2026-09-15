package dev.assay

import org.json.JSONArray
import org.json.JSONObject

enum class ConsoleAvailability { NOT_CONFIGURED, UNAVAILABLE, INVALID, READY }

data class ConsoleFinding(
    val fingerprint: String,
    val scanner: String,
    val ruleId: String,
    val severity: String,
    val message: String,
    val file: String,
    val startLine: Int,
    val candidateIds: List<String>,
)

data class ConsoleCandidate(
    val candidateId: String,
    val findingFingerprint: String,
    val lifecycle: String,
    val revision: Int,
    val fixBranch: String,
    val humanApproved: Boolean,
)

data class ConsoleSnapshot(
    val generatedAt: String,
    val availability: ConsoleAvailability,
    val reason: String?,
    val sourceRepo: String?,
    val sourceCommit: String?,
    val runId: String?,
    val findings: List<ConsoleFinding>,
    val candidates: List<ConsoleCandidate>,
) {
    val unresolvedFindings: Int = findings.count { finding ->
        val states = candidates.filter { it.findingFingerprint == finding.fingerprint }.map { it.lifecycle }
        states.none { it == "applied" || it == "obsolete" }
    }
}

object ConsoleSnapshotParser {
    private val sha256 = Regex("[0-9a-f]{64}")
    private val sha1 = Regex("[0-9a-f]{40}")
    private val candidateId = Regex("cand1-[0-9a-f]{20}")
    private val repository = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")

    fun parse(text: String): ConsoleSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size in 2..8 * 1024 * 1024) { "Snapshot size rejected" }
        val root = JSONObject(text)
        exact(
            root,
            setOf(
                "schemaVersion", "generatedAt", "availability", "reason", "sourceRepo",
                "sourceCommit", "runId", "findings", "candidates",
            ),
            "$",
        )
        require(root.getString("schemaVersion") == "1.1.0") { "Unsupported snapshot schema" }
        val availability = enumValueOf<ConsoleAvailability>(root.getString("availability").uppercase())
        val findings = findings(root.getJSONArray("findings"))
        val candidates = candidates(root.getJSONArray("candidates"))
        require(findings.map { it.fingerprint }.distinct().size == findings.size) { "Duplicate finding" }
        require(candidates.map { it.candidateId }.distinct().size == candidates.size) { "Duplicate candidate" }
        val candidateIds = candidates.map { it.candidateId }.toSet()
        findings.forEach { finding ->
            require(finding.candidateIds.all { it in candidateIds }) { "Finding references an unknown candidate" }
        }
        candidates.forEach { candidate ->
            require(findings.any { it.fingerprint == candidate.findingFingerprint }) {
                "Candidate references an unknown finding"
            }
        }
        val sourceRepo = nullableString(root, "sourceRepo", 256)?.also {
            require(repository.matches(it)) { "Invalid source repository" }
        }
        val sourceCommit = nullableString(root, "sourceCommit", 64)?.also {
            require(sha1.matches(it)) { "Invalid source commit" }
        }
        val reason = nullableString(root, "reason", 2_048)
        if (availability == ConsoleAvailability.READY) {
            require(reason == null) { "Ready snapshot contains an error reason" }
        } else {
            require(findings.isEmpty() && candidates.isEmpty()) { "Unavailable snapshot contains evidence" }
        }
        return ConsoleSnapshot(
            generatedAt = safe(root.getString("generatedAt"), 128),
            availability = availability,
            reason = reason,
            sourceRepo = sourceRepo,
            sourceCommit = sourceCommit,
            runId = nullableString(root, "runId", 128),
            findings = findings,
            candidates = candidates,
        )
    }

    private fun findings(array: JSONArray): List<ConsoleFinding> {
        require(array.length() <= 20_000) { "Too many findings" }
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            exact(
                item,
                setOf("fingerprint", "scanner", "ruleId", "severity", "message", "file", "startLine", "candidates"),
                "$.findings[$index]",
            )
            val fingerprint = item.getString("fingerprint")
            require(sha256.matches(fingerprint)) { "Invalid finding fingerprint" }
            val startLine = item.getInt("startLine")
            require(startLine >= 1) { "Invalid finding line" }
            val severity = item.getString("severity")
            require(severity in setOf("SEV1", "SEV2", "SEV3", "SEV4")) { "Invalid severity" }
            val ids = item.getJSONArray("candidates")
            require(ids.length() <= 100) { "Too many candidates for finding" }
            ConsoleFinding(
                fingerprint = fingerprint,
                scanner = safe(item.getString("scanner"), 32),
                ruleId = safe(item.getString("ruleId"), 256),
                severity = severity,
                message = safe(item.getString("message"), 4_096),
                file = safe(item.getString("file"), 1_024),
                startLine = startLine,
                candidateIds = List(ids.length()) { candidateIndex ->
                    ids.getString(candidateIndex).also { id ->
                        require(candidateId.matches(id)) { "Invalid candidate id" }
                    }
                },
            )
        }
    }

    private fun candidates(array: JSONArray): List<ConsoleCandidate> {
        require(array.length() <= 20_000) { "Too many candidates" }
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            exact(
                item,
                setOf("candidateId", "findingFingerprint", "lifecycle", "revision", "fixBranch", "humanApproved"),
                "$.candidates[$index]",
            )
            val id = item.getString("candidateId")
            require(candidateId.matches(id)) { "Invalid candidate id" }
            val fingerprint = item.getString("findingFingerprint")
            require(sha256.matches(fingerprint)) { "Invalid candidate finding fingerprint" }
            val lifecycle = item.getString("lifecycle")
            require(
                lifecycle in setOf(
                    "detected", "proposed", "proof_passed", "proof_failed", "approved", "rejected",
                    "applied", "stale", "superseded", "withdrawn", "obsolete",
                ),
            ) { "Invalid candidate lifecycle" }
            val revision = item.getInt("revision")
            require(revision >= 1) { "Invalid candidate revision" }
            val branch = item.getString("fixBranch")
            require(branch == "assay/fix/$id") { "Invalid candidate branch" }
            ConsoleCandidate(
                candidateId = id,
                findingFingerprint = fingerprint,
                lifecycle = lifecycle,
                revision = revision,
                fixBranch = branch,
                humanApproved = item.getBoolean("humanApproved"),
            )
        }
    }

    private fun nullableString(root: JSONObject, key: String, maxLength: Int): String? =
        if (root.isNull(key)) null else safe(root.getString(key), maxLength)

    private fun safe(value: String, maxLength: Int): String {
        require(value.isNotBlank() && value.length <= maxLength) { "Invalid text field" }
        require(value.none { it.code < 0x20 && it !in setOf('\n', '\r', '\t') }) { "Control character in text field" }
        return value
    }

    private fun exact(value: JSONObject, expected: Set<String>, path: String) {
        val actual = value.keys().asSequence().toSet()
        require(actual == expected) { "Unexpected keys at $path" }
    }
}
