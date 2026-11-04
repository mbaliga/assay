package dev.assay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.assay.ConsoleUiState
import dev.assay.Overlay
import dev.assay.RepoTarget
import dev.assay.RunDisplay
import dev.assay.TargetState
import dev.assay.UiMessage

enum class Tab(val label: String, val glyph: Glyph) {
    HOME("Home", Glyph.CIRCLE_OUTLINE),
    REPOS("Repos", Glyph.TRIANGLE),
    RUNS("Runs", Glyph.PLAY),
    CANDIDATES("Candidates", Glyph.DIAMOND_OUTLINE),
    SETTINGS("Settings", Glyph.DOT),
}

/** Everything the UI can ask for. Network-touching members are called only from button taps. */
interface ConsoleActions {
    fun pickApk()
    fun pickSnapshot()
    fun openSample()
    fun closeOverlay()
    fun openUrl(url: String)

    /** Returns true when the token was stored, so the field can be cleared. */
    fun saveToken(token: String): Boolean
    fun clearToken()
    fun verifyToken()
    fun dismissBanner()

    fun addTarget(ownerRepo: String, branch: String)
    fun removeTarget(key: String)
    fun selectTarget(key: String)
    fun checkWorkflow(key: String)
    fun proposeWorkflow(key: String)
    fun runAudit(key: String, scanners: String, buildApk: Boolean)
    fun refresh(key: String)
    fun loadCandidates(key: String)
}

@Composable
fun AssayRoot(ui: ConsoleUiState, actions: ConsoleActions, initialTab: Tab = Tab.HOME) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val overlay = ui.overlay
    BackHandler(enabled = overlay != null) { actions.closeOverlay() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (overlay == null) TabBar(tab) { tab = it } },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ui.banner?.let { Banner(it, actions) }
            when (overlay) {
                is Overlay.Loading -> LoadingScreen(overlay)
                is Overlay.LocalReport -> LocalReportScreen(overlay.report, actions)
                is Overlay.Snapshot -> SnapshotScreen(overlay.snapshot, overlay.trust, actions)
                null -> when (tab) {
                    Tab.HOME -> HomeScreen(ui, actions) { tab = it }
                    Tab.REPOS -> ReposScreen(ui, actions)
                    Tab.RUNS -> RunsScreen(ui, actions)
                    Tab.CANDIDATES -> CandidatesScreen(ui, actions)
                    Tab.SETTINGS -> SettingsScreen(ui, actions)
                }
            }
        }
    }
}

/**
 * Bottom tabs. A scrollable row rather than Material's fixed-width bar so labels stay whole at
 * 200 % font size. The selected tab is marked by a border and bold weight, not by colour.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { entry ->
                val isSelected = entry == selected
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(entry) })
                        .then(if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    GlyphMark(entry.glyph, MaterialTheme.colorScheme.onSurface)
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Banner(message: UiMessage, actions: ConsoleActions) {
    MessageLine(message.text, message.isError)
    TextButton(onClick = actions::dismissBanner) { Text("Dismiss") }
}

@Composable
private fun LoadingScreen(overlay: Overlay.Loading) {
    ScreenTitle(overlay.title)
    Muted(overlay.body)
    VerticalGap(16)
    Text("Working on this phone…", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    VerticalGap(6)
}

@Composable
private fun HomeScreen(ui: ConsoleUiState, actions: ConsoleActions, goTo: (Tab) -> Unit) {
    Text("ASSAY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    VerticalGap(10)
    ScreenTitle("Audit your Android repos")
    Muted("Start an audit from here, watch it, and hand the findings to Fonebrew. Findings are never browsed on this phone.")

    SectionTitle("Start here")
    ActionRow(
        title = "Scan an APK on this phone",
        detail = "Local quick check. Nothing leaves the phone.",
        button = "Choose APK",
        onClick = actions::pickApk,
    )
    Hairline()
    ActionRow(
        title = "Open a verified audit snapshot",
        detail = "A console.json produced by a trusted runner.",
        button = "Open file",
        onClick = actions::pickSnapshot,
    )
    Hairline()
    ActionRow(
        title = "Explore sample data",
        detail = "Clearly labelled sample. Not evidence.",
        button = "Explore",
        onClick = actions::openSample,
    )

    SectionTitle("Control plane")
    val connection = when {
        ui.account != null -> "GitHub: signed in as ${ui.account}"
        ui.tokenSaved -> "GitHub: token saved, not checked yet"
        else -> "GitHub: not connected"
    }
    Text(connection, style = MaterialTheme.typography.bodyLarge)
    VerticalGap(4)
    Muted("${ui.targets.size} repositor${if (ui.targets.size == 1) "y" else "ies"} added")
    VerticalGap(12)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { goTo(Tab.REPOS) }) { Text("Repos") }
        OutlinedButton(onClick = { goTo(Tab.SETTINGS) }) { Text("Settings") }
    }

    SectionTitle("About the two kinds of check")
    InfoLine(
        "Quick check vs runner audit",
        "The quick check inspects one APK on this phone. A runner audit runs Gitleaks, Semgrep, " +
            "OSV-Scanner and MobSF on the repository in GitHub Actions and publishes tamper-evident " +
            "evidence. Only the runner audit produces findings Fonebrew can act on.",
    )
}

@Composable
private fun ActionRow(title: String, detail: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Muted(detail)
        VerticalGap(10)
        OutlinedButton(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun ReposScreen(ui: ConsoleUiState, actions: ConsoleActions) {
    ScreenTitle("Repos")
    Muted("Repositories you audit, and the branch that receives each one's .assay/ output.")

    var ownerRepo by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf(RepoTarget.DEFAULT_BRANCH) }
    SectionTitle("Add a repository")
    OutlinedTextField(
        value = ownerRepo,
        onValueChange = { ownerRepo = it },
        label = { Text("owner/repo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    VerticalGap(8)
    OutlinedTextField(
        value = branch,
        onValueChange = { branch = it },
        label = { Text("Output branch") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    VerticalGap(10)
    Button(onClick = { actions.addTarget(ownerRepo, branch); ownerRepo = "" }) { Text("Add repository") }

    SectionTitle("Your repositories")
    if (ui.targets.isEmpty()) {
        Muted("None yet. Add one above.")
    }
    ui.targets.forEachIndexed { index, state ->
        if (index > 0) Hairline()
        RepoRow(state, actions)
    }
}

@Composable
private fun RepoRow(state: TargetState, actions: ConsoleActions) {
    val key = state.target.key
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(state.target.repo.slug, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Mono("output branch: ${state.target.outputBranch}")
        VerticalGap(6)
        Text(
            when (state.hasWorkflow) {
                true -> "Assay workflow: present"
                false -> "Assay workflow: missing"
                null -> "Assay workflow: not checked"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        VerticalGap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { actions.checkWorkflow(key) }, enabled = !state.busy) { Text("Check") }
            Button(onClick = { actions.proposeWorkflow(key) }, enabled = !state.busy && state.hasWorkflow == false) {
                Text("Add workflow")
            }
            TextButton(onClick = { actions.removeTarget(key) }) { Text("Remove") }
        }
        if (state.busy) Muted("Working with GitHub…", Modifier.padding(top = 6.dp))
        TargetMessage(state, actions)
    }
}

@Composable
private fun TargetMessage(state: TargetState, actions: ConsoleActions) {
    state.message?.let { message ->
        MessageLine(message.text, message.isError)
        message.url?.let { url -> TextButton(onClick = { actions.openUrl(url) }) { Text("Open in GitHub") } }
    }
}

@Composable
private fun RunsScreen(ui: ConsoleUiState, actions: ConsoleActions) {
    ScreenTitle("Runs")
    Muted("Start an audit, then tap Refresh to follow it. Nothing here updates by itself.")
    if (ui.targets.isEmpty()) {
        VerticalGap(16)
        Muted("Add a repository first.")
        return
    }
    ui.targets.forEachIndexed { index, state ->
        if (index > 0) Hairline()
        RunBlock(state, actions)
    }
}

@Composable
private fun RunBlock(state: TargetState, actions: ConsoleActions) {
    val key = state.target.key
    var withMobSf by rememberSaveable(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        Text(state.target.repo.slug, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Mono("output branch: ${state.target.outputBranch}")
        VerticalGap(10)
        StatusChip(state.display)
        state.latestRun?.let { run ->
            VerticalGap(6)
            Muted("Run #${run.runNumber} · ${run.createdAt}")
            TextButton(onClick = { actions.openUrl(run.htmlUrl) }) { Text("Open run in GitHub") }
        }
        state.index?.let { index ->
            VerticalGap(6)
            Text("${index.findingCount} finding(s) · ${index.provingTestCount} proving test(s)", style = MaterialTheme.typography.bodyLarge)
            Mono("completeness ${index.completeness} · finished ${index.finishedAt}")
            index.explanation?.let { Muted(it, Modifier.padding(top = 4.dp)) }
        }
        VerticalGap(10)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = withMobSf, onCheckedChange = { withMobSf = it })
            Text("Also run MobSF (builds the debug APK first)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        VerticalGap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    actions.runAudit(key, if (withMobSf) "gitleaks,semgrep,osv,mobsf" else "gitleaks,semgrep,osv", withMobSf)
                },
                enabled = !state.busy,
            ) { Text("Run audit now") }
            OutlinedButton(onClick = { actions.refresh(key) }, enabled = !state.busy) { Text("Refresh") }
        }
        if (state.busy) Muted("Working with GitHub…", Modifier.padding(top = 6.dp))
        TargetMessage(state, actions)
        if (state.index != null || state.display == RunDisplay.COMPLETE || state.display == RunDisplay.PARTIAL) {
            VerticalGap(10)
            Muted(
                "Open in Fonebrew Audit: choose ${state.target.repo.slug}, then Check branch " +
                    "“${state.target.outputBranch}”.",
            )
        }
    }
}

@Composable
private fun CandidatesScreen(ui: ConsoleUiState, actions: ConsoleActions) {
    ScreenTitle("Candidates")
    Muted("Proposed fixes for findings that scanners already reported.")
    val selected = ui.targets.firstOrNull { it.target.key == ui.selectedKey } ?: ui.targets.firstOrNull()
    if (selected == null) {
        VerticalGap(16)
        Muted("Add a repository first.")
        return
    }
    if (ui.targets.size > 1) {
        SectionTitle("Repository")
        ui.targets.forEach { state ->
            val isSelected = state.target.key == selected.target.key
            TextButton(onClick = { actions.selectTarget(state.target.key) }) {
                Text((if (isSelected) "● " else "○ ") + state.target.repo.slug + if (isSelected) " (selected)" else "")
            }
        }
    }
    SectionTitle(selected.target.repo.slug)
    Button(onClick = { actions.loadCandidates(selected.target.key) }, enabled = !selected.busy) { Text("Load candidates") }
    if (selected.busy) Muted("Working with GitHub…", Modifier.padding(top = 6.dp))
    TargetMessage(selected, actions)
    selected.candidatesNote?.let { Muted(it, Modifier.padding(top = 8.dp)) }
    selected.candidates?.let { snapshot -> CandidateList(snapshot) }
    VerticalGap(16)
    InfoLine(
        "Where are the findings?",
        "Findings are not browsed here. Open Fonebrew Audit, choose the repository, and tap Check branch " +
            "to see them with their scanner, rule and proving test. Review and approval controls are not " +
            "part of this build.",
    )
}

@Composable
private fun SettingsScreen(ui: ConsoleUiState, actions: ConsoleActions) {
    ScreenTitle("Settings")
    SectionTitle("GitHub token")
    Muted(
        when {
            ui.account != null -> "Signed in as ${ui.account}."
            ui.tokenSaved -> "A token is saved on this device. Tap Check token to confirm it works."
            else -> "No token saved. Without one the console works offline: quick check and snapshots only."
        },
    )
    // Deliberately `remember`, not `rememberSaveable`: a token must never be written into the
    // saved-instance-state bundle, only into the Keystore-backed store.
    var token by remember { mutableStateOf("") }
    VerticalGap(10)
    OutlinedTextField(
        value = token,
        onValueChange = { token = it },
        label = { Text("Fine-grained personal access token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    VerticalGap(10)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { if (actions.saveToken(token)) token = "" }, enabled = token.isNotBlank()) { Text("Save token") }
        OutlinedButton(onClick = actions::verifyToken, enabled = ui.tokenSaved) { Text("Check token") }
        TextButton(onClick = actions::clearToken, enabled = ui.tokenSaved) { Text("Remove") }
    }
    VerticalGap(8)
    InfoLine(
        "What the token needs",
        "Contents: read and write, and Actions: write, on the repositories you audit. It is encrypted " +
            "with a key in the Android Keystore, kept out of backups, and sent only to api.github.com " +
            "when you tap a button that says GitHub.",
    )
    SectionTitle("Privacy")
    Muted("No analytics and no background network activity. The only network calls are the ones you start.")
}
