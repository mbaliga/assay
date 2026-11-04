package dev.assay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
    // Every write to `ui` goes through [mutate], so the main thread and IO coroutines cannot
    // overwrite each other's read-modify-write.
    private val lock = Any()

    var ui by mutableStateOf(
        ConsoleUiState(
            tokenSaved = tokens.get() != null,
            targets = repos.list().map { TargetState(it) },
        ),
    )
        private set

    private fun mutate(change: (ConsoleUiState) -> ConsoleUiState) {
        synchronized(lock) { ui = change(ui) }
    }

    // ---- local state only: no network ----

    /** Returns true when the token was accepted and stored, so the caller can clear its text field. */
    fun saveToken(token: String): Boolean {
        val trimmed = token.trim()
        if (trimmed.length < 20 || trimmed.any { it.isWhitespace() || it.isISOControl() }) {
            mutate { it.copy(banner = UiMessage("That does not look like a GitHub token.", isError = true)) }
            return false
        }
        return try {
            tokens.put(trimmed)
            mutate { it.copy(tokenSaved = true, account = null, banner = UiMessage("Token saved on this device.", false)) }
            true
        } catch (e: Exception) {
            mutate {
                it.copy(banner = UiMessage("The token could not be stored securely on this device (${e::class.java.simpleName}).", true))
            }
            false
        }
    }

    fun clearToken() {
        try {
            tokens.clear()
            mutate { it.copy(tokenSaved = false, account = null, banner = UiMessage("Token removed.", false)) }
        } catch (e: Exception) {
            mutate { it.copy(banner = UiMessage("The token could not be removed (${e::class.java.simpleName}).", true)) }
        }
    }

    fun addTarget(ownerRepo: String, branch: String) {
        val target = runCatching { RepoTarget(RepoRef.parse(ownerRepo), branch.trim().ifBlank { RepoTarget.DEFAULT_BRANCH }) }
            .getOrElse { error ->
                mutate { it.copy(banner = UiMessage(error.message ?: "Invalid repository.", isError = true)) }
                return
            }
        repos.add(target)
        mutate { state ->
            state.copy(
                targets = repos.list().map { saved -> state.targets.firstOrNull { it.target.key == saved.key } ?: TargetState(saved) },
                selectedKey = state.selectedKey ?: target.key,
                banner = null,
            )
        }
    }

    fun removeTarget(key: String) {
        mutate { state ->
            val target = state.targets.firstOrNull { it.target.key == key }?.target ?: return@mutate state
            repos.remove(target)
            state.copy(
                targets = state.targets.filterNot { it.target.key == key },
                selectedKey = state.selectedKey.takeIf { it != key }
                    ?: state.targets.firstOrNull { it.target.key != key }?.target?.key,
            )
        }
    }

    fun selectTarget(key: String) {
        mutate { it.copy(selectedKey = key) }
    }

    fun showOverlay(overlay: Overlay?) {
        mutate { it.copy(overlay = overlay) }
    }

    fun dismissBanner() {
        mutate { it.copy(banner = null) }
    }

    fun showError(text: String) {
        mutate { it.copy(banner = UiMessage(text, isError = true)) }
    }

    // ---- network: only ever called from a user tap ----

    @NetworkAction
    fun verifyToken() {
        viewModelScope.launch(io) {
            try {
                val user = client.currentUser()
                mutate { it.copy(account = user.login, banner = UiMessage("Signed in as ${user.login}.", false)) }
            } catch (e: GitHubException) {
                mutate { it.copy(account = null, banner = UiMessage(e.message.orEmpty(), true)) }
            } catch (e: Exception) {
                mutate { it.copy(account = null, banner = UiMessage(unexpected(e), true)) }
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
        update(key) { it.copy(hasWorkflow = true, message = UiMessage("Pull request opened. Review and merge it in GitHub.", false, url)) }
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

    /**
     * Runs [block] for the target on the IO dispatcher. A second request for a target that is
     * already busy is ignored, so a double tap cannot start two audits.
     */
    private fun withTarget(key: String, block: (RepoTarget) -> Unit) {
        val target = synchronized(lock) {
            val state = ui.targets.firstOrNull { it.target.key == key } ?: return
            if (state.busy) return
            ui = ui.copy(targets = ui.targets.map { if (it.target.key == key) it.copy(busy = true, message = null) else it })
            state.target
        }
        viewModelScope.launch(io) {
            try {
                block(target)
            } catch (e: GitHubException) {
                update(key) { it.copy(message = UiMessage(e.message.orEmpty(), true)) }
            } catch (e: IllegalArgumentException) {
                update(key) { it.copy(message = UiMessage(e.message ?: "Invalid request.", true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update(key) { it.copy(message = UiMessage(unexpected(e), true)) }
            } finally {
                update(key) { it.copy(busy = false) }
            }
        }
    }

    private fun unexpected(e: Exception) = "Something unexpected went wrong (${e::class.java.simpleName})."

    private fun update(key: String, change: (TargetState) -> TargetState) {
        mutate { state -> state.copy(targets = state.targets.map { if (it.target.key == key) change(it) else it }) }
    }

    companion object {
        const val INTERNAL_BUS_BRANCH = "assay/audit"

        /**
         * The commit of mbaliga/assay whose reusable workflow the proposed caller runs. Pinned to
         * a commit that passed the hosted sample end-to-end run, never a branch: the caller grants
         * that workflow a write token, so it must not change underneath a merged pull request.
         * Update it deliberately when a newer workflow is verified.
         */
        const val ASSAY_REF = "17e51fde6c08bd5f4aac68e12403a0254abf83a7"
    }
}
