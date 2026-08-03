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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.aarso.hyle.tokens.HyleTokens
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var palette: Palette
    private lateinit var status: TextView
    private lateinit var content: LinearLayout

    private val openSnapshot = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) load(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.from(this)
        setContentView(buildScreen())
        renderEmpty()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
            setBackgroundColor(palette.background)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.primaryText)
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 14f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(4), 0, dp(16))
        })
        root.addView(Button(this).apply {
            text = getString(R.string.open_snapshot)
            contentDescription = getString(R.string.open_snapshot_description)
            isAllCaps = false
            setOnClickListener { openSnapshot.launch(arrayOf("application/json", "text/plain")) }
        }, matchWrap())
        status = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(status, matchWrap(top = 12))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(24))
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun load(uri: Uri) {
        runCatching {
            val text = contentResolver.openInputStream(uri)?.use(::readBounded)
                ?: error(getString(R.string.snapshot_open_failed))
            ConsoleSnapshotParser.parse(text)
        }.onSuccess(::render).onFailure { error ->
            renderError(error.message ?: getString(R.string.snapshot_invalid))
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

    private fun renderEmpty() {
        status.text = getString(R.string.status_not_loaded)
        status.setTextColor(palette.secondaryText)
        status.background = rounded(palette.surface)
        content.removeAllViews()
        content.addView(emptyState(getString(R.string.empty_title), getString(R.string.empty_body)))
    }

    private fun renderError(reason: String) {
        status.text = getString(R.string.status_invalid)
        status.setTextColor(palette.danger)
        status.background = rounded(palette.dangerSurface)
        content.removeAllViews()
        content.addView(emptyState(getString(R.string.invalid_title), reason.take(2_048)))
    }

    private fun render(snapshot: ConsoleSnapshot) {
        content.removeAllViews()
        val stateText = snapshot.availability.name.lowercase().replace('_', ' ')
        status.text = getString(R.string.status_format, stateText)
        val ready = snapshot.availability == ConsoleAvailability.READY
        status.setTextColor(if (ready) palette.success else palette.warning)
        status.background = rounded(if (ready) palette.successSurface else palette.warningSurface)
        if (!ready) {
            content.addView(emptyState(getString(R.string.evidence_unavailable), snapshot.reason.orEmpty()))
            return
        }

        content.addView(sectionTitle(getString(R.string.overview)))
        content.addView(summaryCard(snapshot))
        content.addView(sectionTitle(getString(R.string.findings)))
        if (snapshot.findings.isEmpty()) {
            content.addView(emptyState(getString(R.string.no_findings), getString(R.string.no_findings_body)))
        } else {
            snapshot.findings.take(DISPLAY_LIMIT).forEach { finding ->
                content.addView(findingCard(finding, snapshot))
            }
            addLimitNotice(snapshot.findings.size, getString(R.string.findings))
        }

        content.addView(sectionTitle(getString(R.string.candidates)))
        if (snapshot.candidates.isEmpty()) {
            content.addView(emptyState(getString(R.string.no_candidates), getString(R.string.no_candidates_body)))
        } else {
            snapshot.candidates.take(DISPLAY_LIMIT).forEach { candidate ->
                content.addView(candidateCard(candidate))
            }
            addLimitNotice(snapshot.candidates.size, getString(R.string.candidates))
        }
    }

    private fun summaryCard(snapshot: ConsoleSnapshot): View = card().apply {
        addView(labelValue(getString(R.string.repository), snapshot.sourceRepo ?: getString(R.string.not_available)))
        addView(labelValue(getString(R.string.commit), snapshot.sourceCommit ?: getString(R.string.not_available), monospace = true))
        addView(labelValue(getString(R.string.run), snapshot.runId ?: getString(R.string.not_available), monospace = true))
        addView(labelValue(getString(R.string.finding_count), snapshot.findings.size.toString()))
        addView(labelValue(getString(R.string.unresolved_count), snapshot.unresolvedFindings.toString()))
        addView(labelValue(getString(R.string.candidate_count), snapshot.candidates.size.toString()))
        addView(labelValue(getString(R.string.generated), snapshot.generatedAt))
    }

    private fun findingCard(finding: ConsoleFinding, snapshot: ConsoleSnapshot): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(row().apply {
            addView(badge(finding.severity, severityColor(finding.severity)))
            addView(TextView(this@MainActivity).apply {
                text = finding.scanner
                textSize = 12f
                setTextColor(palette.secondaryText)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, 0, 0)
            })
        })
        addView(TextView(this@MainActivity).apply {
            text = finding.ruleId
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.primaryText)
            setPadding(0, dp(10), 0, dp(4))
        })
        addView(TextView(this@MainActivity).apply {
            text = finding.message
            textSize = 14f
            maxLines = 3
            setTextColor(palette.secondaryText)
        })
        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.location_format, finding.file, finding.startLine)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(palette.tertiaryText)
            setPadding(0, dp(8), 0, 0)
        })
        setOnClickListener { showFinding(finding, snapshot) }
    }

    private fun candidateCard(candidate: ConsoleCandidate): View = card().apply {
        isClickable = true
        isFocusable = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        addView(row().apply {
            addView(badge(candidate.lifecycle.replace('_', ' '), lifecycleColor(candidate.lifecycle)))
            if (candidate.humanApproved) {
                addView(TextView(this@MainActivity).apply {
                    text = getString(R.string.human_approved)
                    textSize = 12f
                    setTextColor(palette.success)
                    setPadding(dp(10), 0, 0, 0)
                })
            }
        })
        addView(TextView(this@MainActivity).apply {
            text = candidate.candidateId
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTextColor(palette.primaryText)
            setPadding(0, dp(10), 0, dp(4))
        })
        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.revision_format, candidate.revision)
            textSize = 13f
            setTextColor(palette.secondaryText)
        })
        setOnClickListener { showCandidate(candidate) }
    }

    private fun showFinding(finding: ConsoleFinding, snapshot: ConsoleSnapshot) {
        val related = snapshot.candidates.filter { it.findingFingerprint == finding.fingerprint }
        val message = buildString {
            appendLine(finding.message)
            appendLine()
            appendLine(getString(R.string.scanner_value, finding.scanner))
            appendLine(getString(R.string.severity_value, finding.severity))
            appendLine(getString(R.string.location_format, finding.file, finding.startLine))
            appendLine(getString(R.string.fingerprint_value, finding.fingerprint))
            append(getString(R.string.related_candidates_value, related.size))
        }
        AlertDialog.Builder(this)
            .setTitle(finding.ruleId)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showCandidate(candidate: ConsoleCandidate) {
        val message = buildString {
            appendLine(getString(R.string.lifecycle_value, candidate.lifecycle.replace('_', ' ')))
            appendLine(getString(R.string.revision_format, candidate.revision))
            appendLine(getString(R.string.branch_value, candidate.fixBranch))
            appendLine(getString(R.string.approved_value, candidate.humanApproved.toString()))
            append(getString(R.string.finding_value, candidate.findingFingerprint))
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(candidate.candidateId)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
        if (candidate.lifecycle == "proof_passed") {
            builder.setPositiveButton(R.string.copy_approval_command) { _, _ ->
                copyDecisionCommand(candidate, "candidate-approve")
            }
            builder.setNeutralButton(R.string.copy_rejection_command) { _, _ ->
                copyDecisionCommand(candidate, "candidate-reject")
            }
        } else {
            builder.setPositiveButton(android.R.string.ok, null)
        }
        builder.show()
    }

    private fun copyDecisionCommand(candidate: ConsoleCandidate, command: String) {
        val value = "assay $command --store <candidate-store> --candidate ${candidate.candidateId} " +
            "--revision ${candidate.revision} --actor human:<reviewer>"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.assay_decision), value))
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(palette.primaryText)
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun emptyState(title: String, body: String): View = card().apply {
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.primaryText)
        })
        addView(TextView(this@MainActivity).apply {
            text = body
            textSize = 14f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(palette.surface)
        elevation = dp(1).toFloat()
        layoutParams = matchWrap(top = 8)
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun badge(value: String, color: Int): TextView = TextView(this).apply {
        text = value.uppercase()
        textSize = 11f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = rounded(withAlpha(color, 34), radius = 99)
    }

    private fun labelValue(label: String, value: String, monospace: Boolean = false): View = row().apply {
        setPadding(0, dp(4), 0, dp(4))
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 13f
            setTextColor(palette.secondaryText)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.36f))
        addView(TextView(this@MainActivity).apply {
            text = value
            textSize = 13f
            setTextColor(palette.primaryText)
            setTextIsSelectable(true)
            if (monospace) typeface = Typeface.MONOSPACE
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.64f))
    }

    private fun addLimitNotice(total: Int, label: String) {
        if (total <= DISPLAY_LIMIT) return
        content.addView(TextView(this).apply {
            text = getString(R.string.display_limit_format, DISPLAY_LIMIT, total, label.lowercase())
            textSize = 12f
            setTextColor(palette.tertiaryText)
            setPadding(dp(4), dp(8), dp(4), 0)
        })
    }

    private fun severityColor(value: String): Int = when (value) {
        "SEV1" -> palette.danger
        "SEV2" -> palette.warning
        "SEV3" -> palette.accent
        else -> palette.tertiaryText
    }

    private fun lifecycleColor(value: String): Int = when (value) {
        "applied", "approved" -> palette.success
        "proof_passed" -> palette.accent
        "proof_failed", "rejected", "stale" -> palette.danger
        else -> palette.warning
    }

    private fun rounded(color: Int, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Palette(
        val background: Int,
        val surface: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val tertiaryText: Int,
        val accent: Int,
        val success: Int,
        val successSurface: Int,
        val warning: Int,
        val warningSurface: Int,
        val danger: Int,
        val dangerSurface: Int,
    ) {
        companion object {
            // MainActivity is not referenced from AndroidManifest.xml (AssayHomeActivity is the
            // launcher) — this looks like dead code superseded by AssayHomeActivity, left in
            // place. Not deleted here (out of scope for a visual/dependency-only change); its
            // palette is still migrated for consistency since it is still compiled into the app
            // module. See the identical, more detailed comment on AssayHomeActivity's
            // HomePalette.from for why the light branch is untouched (Hyle has no light theme).
            fun from(context: Context): Palette {
                val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
                return if (dark) {
                    Palette(
                        background = HyleTokens.Color.colorBackgroundField.toColorInt(),
                        surface = HyleTokens.Color.colorBackgroundSurface.toColorInt(),
                        primaryText = HyleTokens.Color.colorTextPrimary.toColorInt(),
                        secondaryText = HyleTokens.Color.colorTextSecondary.toColorInt(),
                        tertiaryText = HyleTokens.Color.colorTextFaint.toColorInt(),
                        accent = HyleTokens.Color.colorActionPrimary.toColorInt(),
                        success = HyleTokens.Color.colorFeedbackSuccess.toColorInt(),
                        successSurface = surfaceTint(HyleTokens.Color.colorFeedbackSuccess.toColorInt()),
                        warning = HyleTokens.Color.colorFeedbackWarning.toColorInt(),
                        warningSurface = surfaceTint(HyleTokens.Color.colorFeedbackWarning.toColorInt()),
                        danger = HyleTokens.Color.colorFeedbackDanger.toColorInt(),
                        dangerSurface = surfaceTint(HyleTokens.Color.colorFeedbackDanger.toColorInt()),
                    )
                } else {
                    Palette(
                        background = Color.rgb(245, 247, 250),
                        surface = Color.WHITE,
                        primaryText = Color.rgb(22, 26, 33),
                        secondaryText = Color.rgb(75, 84, 99),
                        tertiaryText = Color.rgb(104, 114, 130),
                        accent = Color.rgb(55, 91, 210),
                        success = Color.rgb(24, 128, 73),
                        successSurface = Color.rgb(225, 245, 233),
                        warning = Color.rgb(157, 102, 0),
                        warningSurface = Color.rgb(255, 244, 214),
                        danger = Color.rgb(188, 37, 47),
                        dangerSurface = Color.rgb(255, 231, 233),
                    )
                }
            }
        }
    }

    private companion object {
        const val DISPLAY_LIMIT = 500
    }
}

/** An ARGB Hyle token (`0xAARRGGBB` as a `Long`, see [dev.aarso.hyle.Argb]) as an Android color Int. */
private fun Long.toColorInt(): Int = toInt()

/**
 * A translucent wash of [color], the same derivation AssayHomeActivity.kt uses — see that
 * file's `surfaceTint` for the full rationale. Duplicated rather than shared because this
 * whole file is already a duplicate of AssayHomeActivity and out of scope to de-duplicate here.
 */
private fun surfaceTint(color: Int): Int =
    Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))
