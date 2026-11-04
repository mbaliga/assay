package dev.assay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.aarso.crashrecovery.CrashRecovery
import dev.assay.ui.AssayRoot
import dev.assay.ui.AssayTheme
import dev.assay.ui.ConsoleActions
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class AssayHomeActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()

    private val viewModel: ConsoleViewModel by viewModels {
        viewModelFactory {
            initializer {
                val tokens = KeystoreTokenStore(applicationContext)
                ConsoleViewModel(tokens, PreferencesRepoStore(applicationContext), GitHubClient({ tokens.get() }))
            }
        }
    }

    private val openApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runLocalAudit(uri)
    }

    private val openSnapshot = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSnapshot(uri)
    }

    private val actions = object : ConsoleActions {
        override fun pickApk() = openApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        override fun pickSnapshot() = openSnapshot.launch(arrayOf("application/json", "text/plain"))
        override fun openSample() = viewModel.showOverlay(Overlay.Snapshot(DemoData.snapshot(), SnapshotTrust.SAMPLE))
        override fun closeOverlay() = viewModel.showOverlay(null)
        override fun openUrl(url: String) = openGitHubUrl(url)

        override fun saveToken(token: String): Boolean = viewModel.saveToken(token)
        override fun clearToken() = viewModel.clearToken()
        override fun verifyToken() = viewModel.verifyToken()
        override fun dismissBanner() = viewModel.dismissBanner()

        override fun addTarget(ownerRepo: String, branch: String) = viewModel.addTarget(ownerRepo, branch)
        override fun removeTarget(key: String) = viewModel.removeTarget(key)
        override fun selectTarget(key: String) = viewModel.selectTarget(key)
        override fun checkWorkflow(key: String) = viewModel.checkWorkflow(key)
        override fun proposeWorkflow(key: String) = viewModel.proposeWorkflow(key)
        override fun runAudit(key: String, scanners: String, buildApk: Boolean) = viewModel.runAudit(key, scanners, buildApk)
        override fun refresh(key: String) = viewModel.refresh(key)
        override fun loadCandidates(key: String) = viewModel.loadCandidates(key)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashRecovery.maybeShowRecovery(this, "Assay")) return
        enableEdgeToEdge()
        setContent {
            AssayTheme { AssayRoot(viewModel.ui, actions) }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun runLocalAudit(uri: Uri) {
        viewModel.showOverlay(Overlay.Loading(getString(R.string.inspecting_apk), getString(R.string.inspecting_apk_body)))
        worker.execute {
            runCatching { LocalApkAuditor.audit(this, uri) }
                .onSuccess { report -> runOnUiThread { viewModel.showOverlay(Overlay.LocalReport(report)) } }
                .onFailure { error ->
                    runOnUiThread {
                        viewModel.showOverlay(null)
                        viewModel.showError(error.message?.take(2_048) ?: getString(R.string.local_apk_parse_failed))
                    }
                }
        }
    }

    private fun loadSnapshot(uri: Uri) {
        viewModel.showOverlay(Overlay.Loading(getString(R.string.validating_snapshot), getString(R.string.validating_snapshot_body)))
        worker.execute {
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use(::readBounded)
                    ?: error(getString(R.string.snapshot_open_failed))
                ConsoleSnapshotParser.parse(text)
            }.onSuccess { snapshot ->
                runOnUiThread { viewModel.showOverlay(Overlay.Snapshot(snapshot, SnapshotTrust.VERIFIED)) }
            }.onFailure { error ->
                runOnUiThread {
                    viewModel.showOverlay(null)
                    viewModel.showError(error.message?.take(2_048) ?: getString(R.string.snapshot_invalid))
                }
            }
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= 8 * 1024 * 1024) { getString(R.string.snapshot_too_large) }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    /** Opens a GitHub page in the browser. Only github.com https links, only after a tap. */
    private fun openGitHubUrl(url: String) {
        val uri = Uri.parse(url)
        if (uri.scheme == "https" && uri.host == "github.com") {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
