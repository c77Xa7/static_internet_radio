package com.staticradio.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** No cassette tape glyph in the Material icon set — drawn directly instead of via an ImageVector. */
@Composable
fun CassetteIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = size.minDimension * 0.09f
        val bodyInset = size.minDimension * 0.08f
        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyInset, size.height * 0.18f),
            size = Size(size.width - bodyInset * 2, size.height * 0.64f),
            cornerRadius = CornerRadius(size.minDimension * 0.12f),
            style = Stroke(width = strokeWidth)
        )
        val reelRadius = size.minDimension * 0.11f
        val reelY = size.height * 0.5f
        val leftReelX = size.width * 0.32f
        val rightReelX = size.width * 0.68f
        drawCircle(color = tint, radius = reelRadius, center = Offset(leftReelX, reelY), style = Stroke(width = strokeWidth * 0.75f))
        drawCircle(color = tint, radius = reelRadius, center = Offset(rightReelX, reelY), style = Stroke(width = strokeWidth * 0.75f))
        drawLine(
            color = tint,
            start = Offset(leftReelX + reelRadius, reelY),
            end = Offset(rightReelX - reelRadius, reelY),
            strokeWidth = strokeWidth * 0.6f
        )
    }
}
