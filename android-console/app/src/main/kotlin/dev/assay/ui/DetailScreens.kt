package dev.assay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.assay.ConsoleAvailability
import dev.assay.ConsoleSnapshot
import dev.assay.LocalApkReport
import dev.assay.LocalSeverity
import dev.assay.R
import dev.assay.SnapshotTrust

@Composable
private fun Back(actions: ConsoleActions) {
    TextButton(onClick = actions::closeOverlay) { Text("← Back") }
}

@Composable
private fun TrustBanner(label: String, body: String, glyph: Glyph) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(top = 3.dp)) { GlyphMark(glyph, MaterialTheme.colorScheme.onSurface) }
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Muted(body)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Muted(label)
        if (mono) Mono(value) else Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LocalReportScreen(report: LocalApkReport, actions: ConsoleActions) {
    Back(actions)
    ScreenTitle(report.appLabel)
    Mono(report.packageName)
    TrustBanner(stringResource(R.string.local_trust_label), stringResource(R.string.local_trust_body), Glyph.CIRCLE_DASHED)

    SectionTitle("Overview")
    LabelValue("File", report.displayName)
    LabelValue("Version", "${report.versionName} (${report.versionCode})")
    LabelValue("SDK range", "min ${report.minSdk}, target ${report.targetSdk}")
    LabelValue("Size", "${report.apkBytes} bytes")
    LabelValue("Signers", report.signerCount.toString())
    LabelValue("Permissions", report.requestedPermissions.size.toString())
    LabelValue("Exported components", report.exportedComponentCount.toString())
    LabelValue("Native libraries", report.nativeLibraryCount.toString())
    LabelValue("SHA-256", report.sha256, mono = true)

    SectionTitle("Quick-check results")
    if (report.findings.isEmpty()) {
        Muted("The quick check found nothing to report. That does not mean the app is clean: it checks the package, not the source.")
    }
    report.findings.forEachIndexed { index, finding ->
        if (index > 0) Hairline()
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            LocalSeverityChip(finding.severity)
            VerticalGap(8)
            Text(finding.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Muted(finding.message)
            Mono(finding.ruleId)
        }
    }
    VerticalGap(16)
    InfoLine(
        "This is not a full audit",
        "The quick check reviews the package on this phone. It does not run Gitleaks, Semgrep, " +
            "OSV-Scanner or MobSF and produces no tamper-evident evidence. Add the repository under Repos " +
            "to run a full audit.",
    )
    VerticalGap(12)
    OutlinedButton(onClick = actions::pickApk) { Text("Scan another APK") }
}

@Composable
fun LocalSeverityChip(severity: LocalSeverity, modifier: Modifier = Modifier) {
    when (severity) {
        LocalSeverity.CRITICAL -> SeverityChip("SEV1", modifier)
        LocalSeverity.HIGH -> SeverityChip("SEV2", modifier)
        LocalSeverity.MEDIUM -> SeverityChip("SEV3", modifier)
        LocalSeverity.LOW, LocalSeverity.INFO -> SeverityChip("SEV4", modifier)
    }
    Text(
        "quick-check ${severity.name.lowercase()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
fun SnapshotScreen(snapshot: ConsoleSnapshot, trust: SnapshotTrust, actions: ConsoleActions) {
    Back(actions)
    ScreenTitle(snapshot.sourceRepo ?: "Assay evidence")
    when (trust) {
        SnapshotTrust.VERIFIED -> TrustBanner(
            stringResource(R.string.runner_trust_label), stringResource(R.string.runner_trust_body), Glyph.CHECK_CIRCLE,
        )
        SnapshotTrust.SAMPLE -> TrustBanner(
            stringResource(R.string.sample_trust_label), stringResource(R.string.sample_trust_body), Glyph.CIRCLE_DASHED,
        )
    }
    if (snapshot.availability != ConsoleAvailability.READY) {
        MessageLine(snapshot.reason ?: "Evidence is not available.", isError = true)
        return
    }

    SectionTitle("Overview")
    LabelValue("Repository", snapshot.sourceRepo ?: "not available")
    LabelValue("Commit", snapshot.sourceCommit ?: "not available", mono = true)
    LabelValue("Run", snapshot.runId ?: "not available", mono = true)
    LabelValue("Generated", snapshot.generatedAt)
    LabelValue("Unresolved findings", snapshot.unresolvedFindings.toString())

    SectionTitle("Findings by severity")
    Muted("${snapshot.findings.size} finding(s). Open Fonebrew Audit to read them.")
    VerticalGap(8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("SEV1", "SEV2", "SEV3", "SEV4").forEach { severity ->
            val count = snapshot.findings.count { it.severity == severity }
            Column { SeverityChip(severity); Text("$count", Modifier.padding(start = 10.dp, top = 4.dp)) }
        }
    }

    SectionTitle("Candidates")
    CandidateList(snapshot)
}

@Composable
fun CandidateList(snapshot: ConsoleSnapshot) {
    if (snapshot.candidates.isEmpty()) {
        Muted("No candidates yet.")
        return
    }
    snapshot.candidates.forEachIndexed { index, candidate ->
        if (index > 0) Hairline()
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            LifecycleChip(candidate.lifecycle)
            VerticalGap(8)
            Mono(candidate.candidateId)
            Muted("revision ${candidate.revision}")
            if (candidate.humanApproved) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlyphMark(Glyph.CHECK_CIRCLE, MaterialTheme.colorScheme.onSurface)
                    Text("approved by a person", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
