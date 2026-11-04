package dev.assay

import dev.assay.ui.ConsoleActions

/** Deterministic UI state for screenshot and accessibility tests. No network, no clock. */
object TestFixtures {
    private val sha = "a".repeat(40)

    private fun run(number: Int, status: String, conclusion: String?) = WorkflowRun(
        id = number.toLong(), status = status, conclusion = conclusion,
        htmlUrl = "https://github.com/octo/widget/actions/runs/$number", headSha = sha,
        createdAt = "2026-11-04T01:00:00Z", runNumber = number,
    )

    private fun index(completeness: String, explanation: String?, findings: Int) = IndexSummary(
        runId = "20261104T010000Z-aaaaaaa", sourceCommit = sha, completeness = completeness,
        explanation = explanation, finishedAt = "2026-11-04T01:09:00Z", findingCount = findings, provingTestCount = 0,
    )

    val state: ConsoleUiState = ConsoleUiState(
        tokenSaved = true,
        account = "octocat",
        selectedKey = "octo/widget@assay/output",
        targets = listOf(
            TargetState(
                target = RepoTarget(RepoRef("octo", "widget"), "assay/output"),
                hasWorkflow = true,
                latestRun = run(12, "completed", "success"),
                index = index("COMPLETE", null, 70),
                display = RunDisplay.COMPLETE,
                candidates = DemoData.snapshot(),
            ),
            TargetState(
                target = RepoTarget(RepoRef("octo", "gadget"), "assay/output"),
                hasWorkflow = true,
                latestRun = run(4, "completed", "failure"),
                index = index("PARTIAL", "mobsf failed: MobSF did not become ready", 67),
                display = RunDisplay.PARTIAL,
                message = UiMessage("Audit requested. Tap Refresh to follow it.", false),
            ),
            TargetState(
                target = RepoTarget(RepoRef("octo", "gizmo"), "assay/output"),
                hasWorkflow = false,
                latestRun = run(1, "in_progress", null),
                display = RunDisplay.RUNNING,
                message = UiMessage("Could not reach GitHub: timeout", true),
            ),
        ),
    )

    object NoopActions : ConsoleActions {
        override fun pickApk() = Unit
        override fun pickSnapshot() = Unit
        override fun openSample() = Unit
        override fun closeOverlay() = Unit
        override fun openUrl(url: String) = Unit
        override fun saveToken(token: String) = Unit
        override fun clearToken() = Unit
        override fun verifyToken() = Unit
        override fun dismissBanner() = Unit
        override fun addTarget(ownerRepo: String, branch: String) = Unit
        override fun removeTarget(key: String) = Unit
        override fun selectTarget(key: String) = Unit
        override fun checkWorkflow(key: String) = Unit
        override fun proposeWorkflow(key: String) = Unit
        override fun runAudit(key: String, scanners: String, buildApk: Boolean) = Unit
        override fun refresh(key: String) = Unit
        override fun loadCandidates(key: String) = Unit
    }
}
