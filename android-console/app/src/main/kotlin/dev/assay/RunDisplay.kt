package dev.assay

import org.json.JSONObject

/** What the Runs screen shows for one target. `UNKNOWN` is never rendered as success or zero. */
enum class RunDisplay(val label: String) {
    QUEUED("Queued"),
    RUNNING("Running"),
    COMPLETE("Complete"),
    PARTIAL("Partial"),
    FAILED("Failed"),
    UNKNOWN("No run yet"),
}

/** The few fields of `.assay/assay-index.v1.json` the console shows. Counts only: never findings. */
data class IndexSummary(
    val runId: String,
    val sourceCommit: String,
    val completeness: String,
    val explanation: String?,
    val finishedAt: String,
    val findingCount: Int,
    val provingTestCount: Int,
)

object IndexSummaryParser {
    fun parse(text: String): IndexSummary {
        require(text.length <= 2 * 1024 * 1024) { "Index is too large" }
        val root = JSONObject(text)
        val completeness = root.getString("completeness")
        require(completeness in setOf("COMPLETE", "PARTIAL", "FAILED")) { "Unknown completeness" }
        val files = root.getJSONArray("findingFiles")
        var total = 0
        for (index in 0 until files.length()) total += files.getJSONObject(index).getInt("count")
        return IndexSummary(
            runId = root.getString("runId"),
            sourceCommit = root.getString("sourceCommit"),
            completeness = completeness,
            explanation = if (root.isNull("explanation")) null else root.optString("explanation").ifBlank { null },
            finishedAt = root.getString("finishedAt"),
            findingCount = total,
            provingTestCount = root.getJSONObject("provingTests").getInt("count"),
        )
    }
}

object RunDisplayMapper {
    /**
     * Combines the Actions run with the ratified index on the output branch.
     *
     * The audit workflow deliberately fails (exit 2) when a scanner failed, so an Actions
     * conclusion of "failure" alone cannot tell PARTIAL from FAILED. The index can, but only when
     * it describes this run's source commit; a stale index from an earlier run is ignored.
     */
    fun map(run: WorkflowRun?, index: IndexSummary?): RunDisplay {
        if (run == null) return RunDisplay.UNKNOWN
        val matching = index?.takeIf { it.sourceCommit == run.headSha }
        return when (run.status) {
            "queued", "waiting", "requested", "pending" -> RunDisplay.QUEUED
            "in_progress" -> RunDisplay.RUNNING
            "completed" -> when (run.conclusion) {
                "success" -> if (matching?.completeness == "PARTIAL") RunDisplay.PARTIAL else RunDisplay.COMPLETE
                "failure" -> if (matching?.completeness == "PARTIAL") RunDisplay.PARTIAL else RunDisplay.FAILED
                else -> RunDisplay.FAILED
            }
            else -> RunDisplay.UNKNOWN
        }
    }
}
