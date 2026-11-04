package dev.assay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.assay.RunDisplay

/** Shapes that carry state. Drawn on a canvas so they never depend on font coverage. */
enum class Glyph {
    DIAMOND_FILLED, DIAMOND_OUTLINE, TRIANGLE, CIRCLE_OUTLINE, CIRCLE_DASHED, PLAY, CHECK_CIRCLE,
    HALF_CIRCLE, CROSS, QUESTION, CLOCK, DOT,
}

@Composable
fun GlyphMark(glyph: Glyph, color: Color, modifier: Modifier = Modifier) {
    // 14sp so the mark scales with the user's font size, like the label beside it.
    val side = with(LocalDensity.current) { 14.sp.toDp() }
    Canvas(modifier.size(side)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.14f, cap = StrokeCap.Round)
    val c = Offset(size.width / 2, size.height / 2)
    val r = s * 0.42f
    fun diamond(): Path = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
    }
    when (glyph) {
        Glyph.DIAMOND_FILLED -> drawPath(diamond(), color, style = Fill)
        Glyph.DIAMOND_OUTLINE -> drawPath(diamond(), color, style = stroke)
        Glyph.TRIANGLE -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r * 0.8f); lineTo(c.x - r, c.y + r * 0.8f); close()
            },
            color,
            style = Fill,
        )
        Glyph.CIRCLE_OUTLINE -> drawCircle(color, r, c, style = stroke)
        Glyph.CIRCLE_DASHED -> drawCircle(
            color, r, c,
            style = Stroke(width = s * 0.14f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.18f, s * 0.14f))),
        )
        Glyph.PLAY -> drawPath(
            Path().apply { moveTo(c.x - r * 0.6f, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x - r * 0.6f, c.y + r); close() },
            color,
            style = Fill,
        )
        Glyph.CHECK_CIRCLE -> {
            drawCircle(color, r, c, style = stroke)
            drawPath(
                Path().apply { moveTo(c.x - r * 0.45f, c.y); lineTo(c.x - r * 0.1f, c.y + r * 0.4f); lineTo(c.x + r * 0.5f, c.y - r * 0.35f) },
                color,
                style = stroke,
            )
        }
        Glyph.HALF_CIRCLE -> {
            drawCircle(color, r, c, style = stroke)
            drawArc(color, 90f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Fill)
        }
        Glyph.CROSS -> {
            drawLine(color, Offset(c.x - r * 0.7f, c.y - r * 0.7f), Offset(c.x + r * 0.7f, c.y + r * 0.7f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(c.x + r * 0.7f, c.y - r * 0.7f), Offset(c.x - r * 0.7f, c.y + r * 0.7f), stroke.width, StrokeCap.Round)
        }
        Glyph.QUESTION -> {
            drawCircle(color, r, c, style = stroke)
            drawCircle(color, s * 0.06f, Offset(c.x, c.y + r * 0.35f), style = Fill)
            drawLine(color, Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.05f), stroke.width, StrokeCap.Round)
        }
        Glyph.CLOCK -> {
            drawCircle(color, r, c, style = stroke)
            drawLine(color, c, Offset(c.x, c.y - r * 0.6f), stroke.width, StrokeCap.Round)
            drawLine(color, c, Offset(c.x + r * 0.45f, c.y), stroke.width, StrokeCap.Round)
        }
        Glyph.DOT -> drawCircle(color, r * 0.5f, c, style = Fill)
    }
}

/** Label + shape + luminance: the only way state is shown. */
@Composable
private fun StateChip(
    glyph: Glyph,
    label: String,
    describedAs: String,
    modifier: Modifier = Modifier,
    luminance: Float = 1f,
    dashed: Boolean = false,
    filled: Boolean = false,
    strike: Boolean = false,
) {
    val on = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(50)
    val borderModifier = if (dashed) {
        Modifier.drawDashedOutline(on)
    } else {
        Modifier.border(1.5.dp, on, shape)
    }
    Row(
        modifier
            .clearAndSetSemantics { contentDescription = describedAs }
            .then(borderModifier)
            .let { if (filled) it.background(on.copy(alpha = 0.14f), shape) else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.alpha(luminance)) { GlyphMark(glyph, on) }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = on,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
fun StatusChip(display: RunDisplay, modifier: Modifier = Modifier) {
    val glyph = when (display) {
        RunDisplay.QUEUED -> Glyph.CIRCLE_DASHED
        RunDisplay.RUNNING -> Glyph.PLAY
        RunDisplay.COMPLETE -> Glyph.CHECK_CIRCLE
        RunDisplay.PARTIAL -> Glyph.HALF_CIRCLE
        RunDisplay.FAILED -> Glyph.CROSS
        RunDisplay.UNVERIFIED, RunDisplay.UNKNOWN -> Glyph.QUESTION
    }
    StateChip(
        glyph = glyph,
        label = display.label,
        describedAs = "Run status: ${display.label}",
        dashed = display == RunDisplay.QUEUED,
        filled = display == RunDisplay.COMPLETE,
        modifier = modifier,
    )
}

@Composable
fun SeverityChip(severity: String, modifier: Modifier = Modifier) {
    val (glyph, step) = when (severity) {
        "SEV1" -> Glyph.DIAMOND_FILLED to 0
        "SEV2" -> Glyph.DIAMOND_OUTLINE to 1
        "SEV3" -> Glyph.TRIANGLE to 2
        else -> Glyph.CIRCLE_OUTLINE to 3
    }
    StateChip(glyph, severity, "Severity $severity", luminance = HyleTokens.severityLuminance[step], modifier = modifier)
}

/** Candidate lifecycle: proposed dashed, proof_passed solid + check, approved filled, rejected struck + ✕, stale dimmed + clock. */
@Composable
fun LifecycleChip(lifecycle: String, modifier: Modifier = Modifier) {
    val label = lifecycle.replace('_', ' ')
    when (lifecycle) {
        "proposed" -> StateChip(Glyph.CIRCLE_DASHED, label, "Candidate $label", dashed = true, modifier = modifier)
        "proof_passed" -> StateChip(Glyph.CHECK_CIRCLE, label, "Candidate $label", modifier = modifier)
        "approved", "applied" -> StateChip(Glyph.DOT, label, "Candidate $label", filled = true, modifier = modifier)
        "rejected" -> StateChip(Glyph.CROSS, label, "Candidate $label", strike = true, modifier = modifier)
        "stale" -> StateChip(Glyph.CLOCK, label, "Candidate $label", luminance = 0.6f, modifier = modifier)
        else -> StateChip(Glyph.CIRCLE_OUTLINE, label, "Candidate $label", modifier = modifier)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .padding(top = 28.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

@Composable
fun Hairline() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A short line with an info toggle; the explanation lives behind it instead of cluttering the page. */
@Composable
fun InfoLine(headline: String, info: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = if (open) "Hide details" else "Show details") { open = !open }
                    .semantics { contentDescription = if (open) "Hide details about $headline" else "Show details about $headline" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(24.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("i", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            }
        }
        if (open) Muted(info, Modifier.padding(bottom = 8.dp))
    }
}

@Composable
fun VerticalGap(height: Int = 12) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun MessageLine(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(top = 3.dp)) { GlyphMark(if (isError) Glyph.CROSS else Glyph.CHECK_CIRCLE, MaterialTheme.colorScheme.onSurface) }
        Text(
            (if (isError) "Error: " else "") + text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { contentDescription = (if (isError) "Error: " else "") + text },
        )
    }
}
