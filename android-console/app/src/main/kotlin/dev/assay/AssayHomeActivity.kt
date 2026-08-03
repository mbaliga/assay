package dev.assay

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.aarso.hyle.tokens.HyleTokens
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class AssayHomeActivity : ComponentActivity() {
    private lateinit var palette: HomePalette
    private lateinit var content: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()

    private val openApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runLocalAudit(uri)
    }

    private val openSnapshot = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSnapshot(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = HomePalette.from(this)
        setContentView(buildShell())
        showHome()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildShell(): View {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(HyleTokens.Dimension.spacing5), dp(22), dp(HyleTokens.Dimension.spacing5), dp(HyleTokens.Dimension.spacing10))
            setBackgroundColor(palette.background)
        }
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(palette.background)
            addView(
                content,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
    }

    private fun showHome(error: String? = null) {
        content.removeAllViews()
        content.addView(brand())
        content.addView(
            TextView(this).apply {
                text = getString(R.string.home_headline)
                textSize = 28f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.primaryText)
                setPadding(0, dp(30), 0, dp(HyleTokens.Dimension.spacing2))
            },
        )
        content.addView(
            bodyText(getString(R.string.home_intro)),
            matchWrap(bottom = 22),
        )
        if (error != null) {
            content.addView(messageCard(getString(R.string.action_failed), error, palette.dangerSurface, palette.danger))
        }

        content.addView(sectionTitle(getString(R.string.start_here)))
        content.addView(
            actionCard(
                eyebrow = getString(R.string.on_device),
                title = getString(R.string.scan_apk_title),
                description = getString(R.string.scan_apk_body),
                accent = palette.accent,
            ) {
                openApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
            },
            matchWrap(bottom = 12),
        )
        content.addView(
            actionCard(
                eyebrow = getString(R.string.trusted_runner),
                title = getString(R.string.open_verified_title),
                description = getString(R.string.open_verified_body),
                accent = palette.success,
            ) {
                openSnapshot.launch(arrayOf("application/json", "text/plain"))
            },
            matchWrap(bottom = 12),
        )
        content.addView(
            actionCard(
                eyebrow = getString(R.string.guided_preview),
                title = getString(R.string.explore_sample_title),
                description = getString(R.string.explore_sample_body),
                accent = palette.warning,
            ) {
                showSnapshot(DemoData.snapshot(), SnapshotTrust.SAMPLE)
            },
            matchWrap(bottom = 24),
        )

        content.addView(sectionTitle(getString(R.string.what_assay_checks)))
        content.addView(
            messageCard(
                getString(R.string.quick_check_title),
                getString(R.string.quick_check_body),
                palette.surface,
                palette.primaryText,
            ),
            matchWrap(bottom = 12),
        )
        content.addView(
            messageCard(
                getString(R.string.verified_audit_title),
                getString(R.string.verified_audit_body),
                palette.surface,
                palette.primaryText,
            ),
        )
    }

    private fun runLocalAudit(uri: Uri) {
        showLoading(getString(R.string.inspecting_apk), getString(R.string.inspecting_apk_body))
        worker.execute {
            runCatching { LocalApkAuditor.audit(this, uri) }
                .onSuccess { report -> runOnUiThread { showLocalReport(report) } }
                .onFailure { error ->
                    runOnUiThread {
                        showHome(error.message?.take(2_048) ?: getString(R.string.local_apk_parse_failed))
                    }
                }
        }
    }

    private fun loadSnapshot(uri: Uri) {
        showLoading(getString(R.string.validating_snapshot), getString(R.string.validating_snapshot_body))
        worker.execute {
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use(::readBounded)
                    ?: error(getString(R.string.snapshot_open_failed))
                ConsoleSnapshotParser.parse(text)
            }.onSuccess { snapshot ->
                runOnUiThread { showSnapshot(snapshot, SnapshotTrust.VERIFIED) }
            }.onFailure { error ->
                runOnUiThread {
                    showHome(error.message?.take(2_048) ?: getString(R.string.snapshot_invalid))
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

    private fun showLoading(title: String, description: String) {
        content.removeAllViews()
        content.addView(backLink())
        content.addView(pageTitle(title))
        content.addView(bodyText(description), matchWrap(bottom = 24))
        content.addView(
            card().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(ProgressBar(this@AssayHomeActivity))
                addView(
                    bodyText(getString(R.string.working_locally)).apply { gravity = Gravity.CENTER },
                    matchWrap(top = 14),
                )
            },
        )
    }

    private fun showLocalReport(report: LocalApkReport) {
        content.removeAllViews()
        content.addView(backLink())
        content.addView(pageTitle(report.appLabel))
        content.addView(monoText(report.packageName), matchWrap(bottom = 12))
        content.addView(
            trustBanner(
                getString(R.string.local_trust_label),
                getString(R.string.local_trust_body),
                palette.warningSurface,
                palette.warning,
            ),
            matchWrap(bottom = 18),
        )
        content.addView(sectionTitle(getString(R.string.overview)))
        content.addView(
            card().apply {
                addView(labelValue(getString(R.string.file_name), report.displayName))
                addView(labelValue(getString(R.string.version), getString(R.string.version_value, report.versionName, report.versionCode)))
                addView(labelValue(getString(R.string.sdk_range), getString(R.string.sdk_value, report.minSdk, report.targetSdk)))
                addView(labelValue(getString(R.string.apk_size), formatBytes(report.apkBytes)))
                addView(labelValue(getString(R.string.signers), report.signerCount.toString()))
                addView(labelValue(getString(R.string.permissions), report.requestedPermissions.size.toString()))
                addView(labelValue(getString(R.string.exported_components), report.exportedComponentCount.toString()))
                addView(labelValue(getString(R.string.native_libraries), report.nativeLibraryCount.toString()))
                addView(labelValue(getString(R.string.sha256), report.sha256, monospace = true))
            },
            matchWrap(bottom = 20),
        )

        val actionable = report.findings.count { it.severity != LocalSeverity.INFO }
        content.addView(sectionTitle(getString(R.string.findings)))
        content.addView(
            metricStrip(
                listOf(
                    getString(R.string.total) to report.findings.size.toString(),
                    getString(R.string.actionable) to actionable.toString(),
                    getString(R.string.high) to report.findings.count { it.severity == LocalSeverity.HIGH || it.severity == LocalSeverity.CRITICAL }.toString(),
                ),
            ),
            matchWrap(bottom = 12),
        )
        if (report.findings.isEmpty()) {
            content.addView(
                messageCard(
                    getString(R.string.local_no_findings_title),
                    getString(R.string.local_no_findings_body),
                    palette.successSurface,
                    palette.success,
                ),
                matchWrap(bottom = 16),
            )
        } else {
            report.findings.forEach { finding ->
                content.addView(localFindingCard(finding), matchWrap(bottom = 10))
            }
        }

        content.addView(
            messageCard(
                getString(R.string.next_full_audit_title),
                getString(R.string.next_full_audit_body),
                palette.accentSurface,
                palette.accent,
            ),
            matchWrap(top = 10, bottom = 14),
        )
        content.addView(primaryButton(getString(R.string.scan_another_apk)) {
            openApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
        })
        content.addView(secondaryButton(getString(R.string.open_verified_title)) {
            openSnapshot.launch(arrayOf("application/json", "text/plain"))
        }, matchWrap(top = 10))
    }

    private fun showSnapshot(snapshot: ConsoleSnapshot, trust: SnapshotTrust) {
        content.removeAllViews()
        content.addView(backLink())
        content.addView(pageTitle(snapshot.sourceRepo ?: getString(R.string.assay_evidence)))
        val trustTitle = when (trust) {
            SnapshotTrust.VERIFIED -> getString(R.string.runner_trust_label)
            SnapshotTrust.SAMPLE -> getString(R.string.sample_trust_label)
        }
        val trustBody = when (trust) {
            SnapshotTrust.VERIFIED -> getString(R.string.runner_trust_body)
            SnapshotTrust.SAMPLE -> getString(R.string.sample_trust_body)
        }
        val trustSurface = if (trust == SnapshotTrust.VERIFIED) palette.successSurface else palette.warningSurface
        val trustColor = if (trust == SnapshotTrust.VERIFIED) palette.success else palette.warning
        content.addView(trustBanner(trustTitle, trustBody, trustSurface, trustColor), matchWrap(bottom = 18))

        if (snapshot.availability != ConsoleAvailability.READY) {
            content.addView(
                messageCard(
                    getString(R.string.evidence_unavailable),
                    snapshot.reason ?: getString(R.string.not_available),
                    palette.dangerSurface,
                    palette.danger,
                ),
            )
            return
        }

        content.addView(sectionTitle(getString(R.string.overview)))
        content.addView(
            card().apply {
                addView(labelValue(getString(R.string.repository), snapshot.sourceRepo ?: getString(R.string.not_available)))
                addView(labelValue(getString(R.string.commit), snapshot.sourceCommit ?: getString(R.string.not_available), monospace = true))
                addView(labelValue(getString(R.string.run), snapshot.runId ?: getString(R.string.not_available), monospace = true))
                addView(labelValue(getString(R.string.finding_count), snapshot.findings.size.toString()))
                addView(labelValue(getString(R.string.unresolved_count), snapshot.unresolvedFindings.toString()))
                addView(labelValue(getString(R.string.candidate_count), snapshot.candidates.size.toString()))
                addView(labelValue(getString(R.string.generated), snapshot.generatedAt))
            },
            matchWrap(bottom = 20),
        )

        content.addView(sectionTitle(getString(R.string.findings)))
        if (snapshot.findings.isEmpty()) {
            content.addView(
                messageCard(
                    getString(R.string.no_findings),
                    getString(R.string.no_findings_body),
                    palette.successSurface,
                    palette.success,
                ),
                matchWrap(bottom = 18),
            )
        } else {
            snapshot.findings.forEach { finding ->
                content.addView(consoleFindingCard(finding, snapshot), matchWrap(bottom = 10))
            }
        }

        content.addView(sectionTitle(getString(R.string.candidates)), matchWrap(top = 12))
        if (snapshot.candidates.isEmpty()) {
            content.addView(
                messageCard(
                    getString(R.string.no_candidates),
                    getString(R.string.no_candidates_body),
                    palette.surface,
                    palette.primaryText,
                ),
            )
        } else {
            snapshot.candidates.forEach { candidate ->
                content.addView(candidateCard(candidate), matchWrap(bottom = 10))
            }
        }

        if (trust == SnapshotTrust.SAMPLE) {
            content.addView(primaryButton(getString(R.string.try_your_apk)) {
                openApk.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
            }, matchWrap(top = 18))
        }
    }

    private fun localFindingCard(finding: LocalApkFinding): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(chip(finding.severity.name, localSeverityColor(finding.severity)))
        addView(cardTitle(finding.title), matchWrap(top = 10, bottom = 5))
        addView(bodyText(finding.message))
        addView(monoText(finding.ruleId), matchWrap(top = 10))
        setOnClickListener {
            AlertDialog.Builder(this@AssayHomeActivity)
                .setTitle(finding.title)
                .setMessage(getString(R.string.finding_detail_format, finding.message, finding.evidence, finding.ruleId))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun consoleFindingCard(finding: ConsoleFinding, snapshot: ConsoleSnapshot): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(chip(finding.severity, consoleSeverityColor(finding.severity)))
        addView(cardTitle(finding.ruleId), matchWrap(top = 10, bottom = 5))
        addView(bodyText(finding.message))
        addView(monoText(getString(R.string.location_format, finding.file, finding.startLine)), matchWrap(top = 10))
        setOnClickListener {
            val related = snapshot.candidates.count { it.findingFingerprint == finding.fingerprint }
            AlertDialog.Builder(this@AssayHomeActivity)
                .setTitle(finding.ruleId)
                .setMessage(
                    getString(
                        R.string.console_finding_detail_format,
                        finding.message,
                        finding.scanner,
                        finding.severity,
                        getString(R.string.location_format, finding.file, finding.startLine),
                        related,
                        finding.fingerprint,
                    ),
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun candidateCard(candidate: ConsoleCandidate): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(chip(candidate.lifecycle.replace('_', ' '), lifecycleColor(candidate.lifecycle)))
        addView(monoText(candidate.candidateId), matchWrap(top = 10, bottom = 5))
        addView(bodyText(getString(R.string.revision_format, candidate.revision)))
        if (candidate.humanApproved) {
            addView(bodyText(getString(R.string.human_approved)).apply { setTextColor(palette.success) }, matchWrap(top = 6))
        }
        setOnClickListener { showCandidate(candidate) }
    }

    private fun showCandidate(candidate: ConsoleCandidate) {
        val builder = AlertDialog.Builder(this)
            .setTitle(candidate.candidateId)
            .setMessage(
                getString(
                    R.string.candidate_detail_format,
                    candidate.lifecycle.replace('_', ' '),
                    candidate.revision,
                    candidate.fixBranch,
                    candidate.humanApproved.toString(),
                    candidate.findingFingerprint,
                ),
            )
            .setPositiveButton(android.R.string.ok, null)
        if (candidate.lifecycle == "proof_passed") {
            builder.setNeutralButton(R.string.copy_approval_command) { _, _ -> copyDecision(candidate, approve = true) }
            builder.setNegativeButton(R.string.copy_rejection_command) { _, _ -> copyDecision(candidate, approve = false) }
        }
        builder.show()
    }

    private fun copyDecision(candidate: ConsoleCandidate, approve: Boolean) {
        val action = if (approve) "candidate-approve" else "candidate-reject"
        val command = "assay $action --store <candidate-store> --candidate ${candidate.candidateId} " +
            "--revision ${candidate.revision} --actor human:<reviewer>"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.assay_decision), command))
        Toast.makeText(this, R.string.command_copied, Toast.LENGTH_SHORT).show()
    }

    private fun brand(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(this@AssayHomeActivity).apply {
                text = getString(R.string.app_name)
                textSize = 34f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.primaryText)
            },
        )
        addView(bodyText(getString(R.string.app_subtitle)), matchWrap(top = 4))
    }

    private fun backLink(): View = TextView(this).apply {
        text = getString(R.string.back_to_assay)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.accent)
        setPadding(0, dp(HyleTokens.Dimension.spacing1), 0, dp(HyleTokens.Dimension.spacing4))
        isClickable = true
        isFocusable = true
        setOnClickListener { showHome() }
    }

    private fun pageTitle(value: String): View = TextView(this).apply {
        text = value
        textSize = 28f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.primaryText)
        setPadding(0, 0, 0, dp(7))
    }

    private fun sectionTitle(value: String): View = TextView(this).apply {
        text = value
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.secondaryText)
        setPadding(0, dp(10), 0, dp(9))
    }

    private fun cardTitle(value: String): View = TextView(this).apply {
        text = value
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.primaryText)
    }

    private fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(palette.secondaryText)
        setLineSpacing(0f, 1.08f)
    }

    private fun monoText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(palette.tertiaryText)
        setTextIsSelectable(true)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(HyleTokens.Dimension.spacing4), dp(17), dp(HyleTokens.Dimension.spacing4))
        background = rounded(palette.surface, dp(18).toFloat(), palette.border)
    }

    private fun actionCard(
        eyebrow: String,
        title: String,
        description: String,
        accent: Int,
        onClick: () -> Unit,
    ): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(chip(eyebrow, accent))
        addView(cardTitle(title), matchWrap(top = 12, bottom = 6))
        addView(bodyText(description))
        addView(
            TextView(this@AssayHomeActivity).apply {
                text = getString(R.string.open_action)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accent)
                setPadding(0, dp(14), 0, 0)
            },
        )
        setOnClickListener { onClick() }
    }

    private fun messageCard(title: String, description: String, surface: Int, titleColor: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(HyleTokens.Dimension.spacing4), dp(17), dp(HyleTokens.Dimension.spacing4))
            background = rounded(surface, dp(18).toFloat(), palette.border)
            addView(cardTitle(title).apply { setTextColor(titleColor) })
            addView(bodyText(description), matchWrap(top = 7))
        }

    private fun trustBanner(title: String, description: String, surface: Int, color: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            // dp(16) previously — exact match to HyleTokens.Dimension.radiusXl.
            background = rounded(surface, dp(HyleTokens.Dimension.radiusXl).toFloat(), color)
            addView(cardTitle(title).apply {
                textSize = 15f
                setTextColor(color)
            })
            addView(bodyText(description), matchWrap(top = 5))
        }

    private fun metricStrip(values: List<Pair<String, String>>): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        values.forEachIndexed { index, item ->
            addView(
                LinearLayout(this@AssayHomeActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(HyleTokens.Dimension.spacing2), dp(13), dp(HyleTokens.Dimension.spacing2), dp(13))
                    background = rounded(palette.surface, dp(15).toFloat(), palette.border)
                    addView(TextView(this@AssayHomeActivity).apply {
                        text = item.second
                        textSize = 23f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(palette.primaryText)
                        gravity = Gravity.CENTER
                    })
                    addView(TextView(this@AssayHomeActivity).apply {
                        text = item.first
                        textSize = 12f
                        setTextColor(palette.secondaryText)
                        gravity = Gravity.CENTER
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(HyleTokens.Dimension.spacing2)
                },
            )
        }
    }

    private fun labelValue(label: String, value: String, monospace: Boolean = false): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(5), 0, dp(HyleTokens.Dimension.spacing2))
            addView(TextView(this@AssayHomeActivity).apply {
                text = label
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.tertiaryText)
            })
            addView(TextView(this@AssayHomeActivity).apply {
                text = value
                textSize = if (monospace) 12f else 15f
                if (monospace) typeface = Typeface.MONOSPACE
                setTextColor(palette.primaryText)
                setTextIsSelectable(monospace)
            }, matchWrap(top = 2))
        }

    private fun chip(value: String, color: Int): View = TextView(this).apply {
        text = value.uppercase()
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        setPadding(dp(9), dp(5), dp(9), dp(5))
        // A pill this short is already fully rounded at dp(20); radiusFull is the more honest
        // token for "capsule" and renders identically here since GradientDrawable clamps corner
        // radius to half the shape's shorter side.
        background = rounded(withAlpha(color, 34), dp(HyleTokens.Dimension.radiusFull).toFloat(), color)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun primaryButton(value: String, onClick: () -> Unit): View = TextView(this).apply {
        text = value
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.onAccent)
        gravity = Gravity.CENTER
        minHeight = dp(54)
        setPadding(dp(HyleTokens.Dimension.spacing4), dp(HyleTokens.Dimension.spacing3), dp(HyleTokens.Dimension.spacing4), dp(HyleTokens.Dimension.spacing3))
        background = rounded(palette.accent, dp(15).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun secondaryButton(value: String, onClick: () -> Unit): View = TextView(this).apply {
        text = value
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.primaryText)
        gravity = Gravity.CENTER
        minHeight = dp(54)
        setPadding(dp(HyleTokens.Dimension.spacing4), dp(HyleTokens.Dimension.spacing3), dp(HyleTokens.Dimension.spacing4), dp(HyleTokens.Dimension.spacing3))
        background = rounded(palette.surface, dp(15).toFloat(), palette.border)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
        if (stroke != null) setStroke(dp(HyleTokens.Dimension.sizeBorderThin), stroke)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun localSeverityColor(severity: LocalSeverity): Int = when (severity) {
        LocalSeverity.CRITICAL -> palette.danger
        LocalSeverity.HIGH -> palette.danger
        LocalSeverity.MEDIUM -> palette.warning
        LocalSeverity.LOW -> palette.accent
        LocalSeverity.INFO -> palette.secondaryText
    }

    private fun consoleSeverityColor(severity: String): Int = when (severity) {
        "SEV1" -> palette.danger
        "SEV2" -> palette.danger
        "SEV3" -> palette.warning
        else -> palette.accent
    }

    private fun lifecycleColor(lifecycle: String): Int = when (lifecycle) {
        "approved", "applied" -> palette.success
        "proof_passed" -> palette.accent
        "rejected", "proof_failed", "stale" -> palette.danger
        else -> palette.warning
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> getString(R.string.gigabytes_value, bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> getString(R.string.megabytes_value, bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> getString(R.string.kilobytes_value, bytes / 1024.0)
        else -> getString(R.string.bytes_value, bytes)
    }

    private fun matchWrap(
        top: Int = 0,
        bottom: Int = 0,
    ): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class SnapshotTrust { VERIFIED, SAMPLE }
}

/** An ARGB Hyle token (`0xAARRGGBB` as a `Long`, see [dev.aarso.hyle.Argb]) as an Android color Int. */
private fun Long.toColorInt(): Int = toInt()

/**
 * A translucent wash of [color] suitable as a card/banner fill over Hyle's near-black
 * backgrounds — the same technique [AssayHomeActivity.withAlpha] already uses for chip fills,
 * just a lower, surface-scale alpha. Hyle's token set has no separate "X surface" background
 * per feedback colour (only the solid hue), so this derives one rather than inventing new hex.
 */
private fun surfaceTint(color: Int): Int =
    Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))

private data class HomePalette(
    val background: Int,
    val surface: Int,
    val border: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val tertiaryText: Int,
    val accent: Int,
    val onAccent: Int,
    val accentSurface: Int,
    val success: Int,
    val successSurface: Int,
    val warning: Int,
    val warningSurface: Int,
    val danger: Int,
    val dangerSurface: Int,
) {
    companion object {
        // Hyle (dev.aarso:hyle) is a single dark/AMOLED-black design system: its token set
        // (hyle-design-system/tokens/color.json) defines no light-theme colours. The dark
        // palette below is now sourced entirely from HyleTokens.Color. The light palette is
        // intentionally left as this app's own pre-existing values — there is no Hyle token to
        // substitute there, and inventing new light-mode hex isn't "replace hardcoded colours
        // with the shared token source". Flagged in the PR description as an open question
        // (keep a bespoke light theme forever, or follow Hyle to dark-only?).
        fun from(context: Context): HomePalette {
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return if (dark) {
                HomePalette(
                    background = HyleTokens.Color.colorBackgroundField.toColorInt(),
                    surface = HyleTokens.Color.colorBackgroundSurface.toColorInt(),
                    border = HyleTokens.Color.colorBorderHairline.toColorInt(),
                    primaryText = HyleTokens.Color.colorTextPrimary.toColorInt(),
                    secondaryText = HyleTokens.Color.colorTextSecondary.toColorInt(),
                    tertiaryText = HyleTokens.Color.colorTextFaint.toColorInt(),
                    accent = HyleTokens.Color.colorActionPrimary.toColorInt(),
                    onAccent = HyleTokens.Color.colorActionOnPrimary.toColorInt(),
                    accentSurface = surfaceTint(HyleTokens.Color.colorActionPrimary.toColorInt()),
                    success = HyleTokens.Color.colorFeedbackSuccess.toColorInt(),
                    successSurface = surfaceTint(HyleTokens.Color.colorFeedbackSuccess.toColorInt()),
                    warning = HyleTokens.Color.colorFeedbackWarning.toColorInt(),
                    warningSurface = surfaceTint(HyleTokens.Color.colorFeedbackWarning.toColorInt()),
                    danger = HyleTokens.Color.colorFeedbackDanger.toColorInt(),
                    dangerSurface = surfaceTint(HyleTokens.Color.colorFeedbackDanger.toColorInt()),
                )
            } else {
                HomePalette(
                    background = Color.parseColor("#F5F6F8"),
                    surface = Color.WHITE,
                    border = Color.parseColor("#DDE1E8"),
                    primaryText = Color.parseColor("#171A21"),
                    secondaryText = Color.parseColor("#505867"),
                    tertiaryText = Color.parseColor("#747D8D"),
                    accent = Color.parseColor("#3659C9"),
                    onAccent = Color.WHITE,
                    accentSurface = Color.parseColor("#E8EDFF"),
                    success = Color.parseColor("#147A52"),
                    successSurface = Color.parseColor("#E3F5EC"),
                    warning = Color.parseColor("#94630A"),
                    warningSurface = Color.parseColor("#FFF2D8"),
                    danger = Color.parseColor("#B52C3B"),
                    dangerSurface = Color.parseColor("#FBE7E9"),
                )
            }
        }
    }
}
