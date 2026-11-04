package dev.assay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RunDisplayTest {
    private val sha = "a".repeat(40)

    private fun run(status: String, conclusion: String? = null, head: String = sha) =
        WorkflowRun(1, status, conclusion, "https://github.com/o/r/actions/runs/1", head, "2026-11-04T00:00:00Z", 1)

    private fun index(completeness: String, commit: String = sha) = IndexSummary(
        runId = "r", sourceCommit = commit, completeness = completeness, explanation = null,
        finishedAt = "2026-11-04T00:01:00Z", findingCount = 3, provingTestCount = 0,
    )

    @Test
    fun `queued and running map from the Actions status`() {
        listOf("queued", "waiting", "requested", "pending").forEach {
            assertEquals(RunDisplay.QUEUED, RunDisplayMapper.map(run(it), null))
        }
        assertEquals(RunDisplay.RUNNING, RunDisplayMapper.map(run("in_progress"), null))
    }

    @Test
    fun `success is COMPLETE and a failed audit with a matching PARTIAL index is PARTIAL`() {
        assertEquals(RunDisplay.COMPLETE, RunDisplayMapper.map(run("completed", "success"), index("COMPLETE")))
        // The audit workflow exits non-zero (2) on partial, so Actions reports failure.
        assertEquals(RunDisplay.PARTIAL, RunDisplayMapper.map(run("completed", "failure"), index("PARTIAL")))
        assertEquals(RunDisplay.FAILED, RunDisplayMapper.map(run("completed", "failure"), index("FAILED")))
    }

    @Test
    fun `a stale index from another commit is ignored`() {
        val stale = index("PARTIAL", commit = "b".repeat(40))
        assertEquals(RunDisplay.FAILED, RunDisplayMapper.map(run("completed", "failure"), stale))
    }

    @Test
    fun `cancelled, timed out and no run are never shown as success`() {
        assertEquals(RunDisplay.FAILED, RunDisplayMapper.map(run("completed", "cancelled"), null))
        assertEquals(RunDisplay.FAILED, RunDisplayMapper.map(run("completed", "timed_out"), null))
        assertEquals(RunDisplay.UNKNOWN, RunDisplayMapper.map(null, index("COMPLETE")))
    }

    @Test
    fun `the index parser keeps counts and rejects an unknown completeness`() {
        val json = """{"schemaVersion":"1.0.0","runId":"20261104T000000Z-aaaaaaa","sourceCommit":"$sha",
            "completeness":"PARTIAL","explanation":"osv failed","finishedAt":"2026-11-04T00:01:00Z",
            "findingFiles":[{"path":"findings.sarif","sha256":"${"c".repeat(64)}","count":7},{"path":"b","sha256":"${"d".repeat(64)}","count":2}],
            "provingTests":{"path":"proving-tests.v1.json","count":1}}"""
        val parsed = IndexSummaryParser.parse(json)
        assertEquals(9, parsed.findingCount)
        assertEquals(1, parsed.provingTestCount)
        assertEquals("osv failed", parsed.explanation)
        assertThrows(IllegalArgumentException::class.java) {
            IndexSummaryParser.parse(json.replace("PARTIAL", "ALMOST"))
        }
    }
}
