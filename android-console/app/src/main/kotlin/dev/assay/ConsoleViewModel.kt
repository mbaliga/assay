package dev.assay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Marks a function that touches the network. These run only in response to a user tap: the
 * NetworkPolicyTest fails the build if one is referenced from a lifecycle callback, an `init`
 * block or a Compose effect.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class NetworkAction

enum class SnapshotTrust { VERIFIED, SAMPLE }

data class UiMessage(val text: String, val isError: Boolean, val url: String? = null)

data class TargetState(
    val target: RepoTarget,
    val busy: Boolean = false,
    val hasWorkflow: Boolean? = null,
    val latestRun: WorkflowRun? = null,
    val index: IndexSummary? = null,
    val display: RunDisplay = RunDisplay.UNKNOWN,
    val candidates: ConsoleSnapshot? = null,
    val candidatesNote: String? = null,
    val message: UiMessage? = null,
)

sealed interface Overlay {
    data class Loading(val title: String, val body: String) : Overlay
    data class LocalReport(val report: LocalApkReport) : Overlay
    data class Snapshot(val snapshot: ConsoleSnapshot, val trust: SnapshotTrust) : Overlay
}

data class ConsoleUiState(
    val tokenSaved: Boolean = false,
    val account: String? = null,
    val targets: List<TargetState> = emptyList(),
    val selectedKey: String? = null,
    val overlay: Overlay? = null,
    val banner: UiMessage? = null,
)

class ConsoleViewModel(
    private val tokens: TokenStore,
    private val repos: RepoStore,
    private val client: GitHubClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    var ui by mutableStateOf(
        ConsoleUiState(
            tokenSaved = tokens.get() != null,
            targets = repos.list().map { TargetState(it) },
        ),
    )
        private set

    // ---- local state only: no network ----

    fun saveToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.length < 20 || trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            ui = ui.copy(banner = UiMessage("That does not look like a GitHub token.", isError = true))
            return
        }
        tokens.put(trimmed)
        ui = ui.copy(tokenSaved = true, account = null, banner = UiMessage("Token saved on this device.", false))
    }

    fun clearToken() {
        tokens.clear()
        ui = ui.copy(tokenSaved = false, account = null, banner = UiMessage("Token removed.", false))
    }

    fun addTarget(ownerRepo: String, branch: String) {
        val target = runCatching { RepoTarget(RepoRef.parse(ownerRepo), branch.trim().ifBlank { RepoTarget.DEFAULT_BRANCH }) }
            .getOrElse {
                ui = ui.copy(banner = UiMessage(it.message ?: "Invalid repository.", isError = true))
                return
            }
        repos.add(target)
        ui = ui.copy(
            targets = repos.list().map { saved -> ui.targets.firstOrNull { it.target.key == saved.key } ?: TargetState(saved) },
            selectedKey = ui.selectedKey ?: target.key,
            banner = null,
        )
    }

    fun removeTarget(key: String) {
        val target = ui.targets.firstOrNull { it.target.key == key }?.target ?: return
        repos.remove(target)
        ui = ui.copy(
            targets = ui.targets.filterNot { it.target.key == key },
            selectedKey = ui.selectedKey.takeIf { it != key } ?: ui.targets.firstOrNull { it.target.key != key }?.target?.key,
        )
    }

    fun selectTarget(key: String) {
        ui = ui.copy(selectedKey = key)
    }

    fun showOverlay(overlay: Overlay?) {
        ui = ui.copy(overlay = overlay)
    }

    fun dismissBanner() {
        ui = ui.copy(banner = null)
    }

    fun showError(text: String) {
        ui = ui.copy(banner = UiMessage(text, isError = true))
    }

    // ---- network: only ever called from a user tap ----

    @NetworkAction
    fun verifyToken() {
        viewModelScope.launch(io) {
            try {
                val user = client.currentUser()
                ui = ui.copy(account = user.login, banner = UiMessage("Signed in as ${user.login}.", false))
            } catch (e: GitHubException) {
                ui = ui.copy(account = null, banner = UiMessage(e.message.orEmpty(), true))
            }
        }
    }

    @NetworkAction
    fun checkWorkflow(key: String) = withTarget(key) { target ->
        val branch = client.defaultBranch(target.repo)
        val present = client.readFile(target.repo, CallerWorkflow.PATH, branch) != null
        update(key) {
            it.copy(
                hasWorkflow = present,
                message = UiMessage(
                    if (present) "The Assay workflow is already in ${target.repo.slug}."
                    else "${target.repo.slug} has no Assay workflow yet.",
                    isError = false,
                ),
            )
        }
    }

    @NetworkAction
    fun proposeWorkflow(key: String) = withTarget(key) { target ->
        val url = client.proposeCallerWorkflow(target.repo, CallerWorkflow.PATH, CallerWorkflow.render(ASSAY_REF))
        update(key) { it.copy(message = UiMessage("Pull request opened. Review and merge it in GitHub.", false, url)) }
    }

    @NetworkAction
    fun runAudit(key: String, scanners: String, buildApk: Boolean) = withTarget(key) { target ->
        val branch = client.defaultBranch(target.repo)
        client.dispatchWorkflow(
            target.repo,
            CallerWorkflow.FILE,
            branch,
            mapOf(
                "output_branch" to target.outputBranch,
                "scanners" to scanners,
                "build_apk" to buildApk.toString(),
            ),
        )
        update(key) {
            it.copy(message = UiMessage("Audit requested. Tap Refresh to follow it.", false), display = RunDisplay.QUEUED)
        }
    }

    @NetworkAction
    fun refresh(key: String) = withTarget(key) { target ->
        val run = client.listWorkflowRuns(target.repo, CallerWorkflow.FILE, limit = 1, perPage = 1).firstOrNull()
        val index = client.readFile(target.repo, ".assay/assay-index.v1.json", target.outputBranch)
            ?.let { runCatching { IndexSummaryParser.parse(it) }.getOrNull() }
        update(key) {
            it.copy(
                latestRun = run,
                index = index,
                display = RunDisplayMapper.map(run, index),
                message = if (run == null) UiMessage("No audit run found. Start one first.", false) else null,
            )
        }
    }

    @NetworkAction
    fun loadCandidates(key: String) = withTarget(key) { target ->
        val text = client.readFile(target.repo, "console.json", INTERNAL_BUS_BRANCH)
        update(key) {
            if (text == null) {
                it.copy(candidates = null, candidatesNote = "No candidate data has been published to $INTERNAL_BUS_BRANCH yet.")
            } else {
                runCatching { ConsoleSnapshotParser.parse(text) }.fold(
                    onSuccess = { snapshot -> it.copy(candidates = snapshot, candidatesNote = null) },
                    onFailure = { error ->
                        it.copy(candidates = null, candidatesNote = "Candidate data failed validation: ${error.message}")
                    },
                )
            }
        }
    }

    private fun withTarget(key: String, block: (RepoTarget) -> Unit) {
        val target = ui.targets.firstOrNull { it.target.key == key }?.target ?: return
        update(key) { it.copy(busy = true, message = null) }
        viewModelScope.launch(io) {
            try {
                block(target)
            } catch (e: GitHubException) {
                update(key) { it.copy(message = UiMessage(e.message.orEmpty(), true)) }
            } catch (e: IllegalArgumentException) {
                update(key) { it.copy(message = UiMessage(e.message ?: "Invalid request.", true)) }
            } finally {
                update(key) { it.copy(busy = false) }
            }
        }
    }

    @Synchronized
    private fun update(key: String, change: (TargetState) -> TargetState) {
        ui = ui.copy(targets = ui.targets.map { if (it.target.key == key) change(it) else it })
    }

    companion object {
        const val INTERNAL_BUS_BRANCH = "assay/audit"

        /** Ref of mbaliga/assay the proposed caller workflow is built from; callers should pin a SHA. */
        const val ASSAY_REF = "main"
    }
}
