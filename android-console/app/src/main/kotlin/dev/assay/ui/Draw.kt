package dev.assay.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal fun Modifier.drawDashedOutline(color: Color): Modifier =
    this.drawBehind {
        val width = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(width / 2, width / 2),
            size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(size.height / 2),
            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
    }
