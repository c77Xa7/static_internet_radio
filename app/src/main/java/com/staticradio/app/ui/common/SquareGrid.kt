package com.staticradio.app.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Inset the first line off the very left/top edge — flush-against-the-edge
// read as a stray line rather than a grid, and isn't reachable via the
// spacing setting alone.
private val GRID_EDGE_INSET = 10.dp

/** Faint background grid from mockup.html's `.device` — 28px squares, lost in the rebuild. */
fun Modifier.squareGridBackground(
    lineColor: Color,
    cellSize: Dp = 28.dp,
    lineWidth: Dp = 1.dp,
    opacity: Float = 1f
): Modifier = drawBehind {
    val cellPx = cellSize.toPx()
    val strokePx = lineWidth.toPx()
    val insetPx = GRID_EDGE_INSET.toPx()
    val color = lineColor.copy(alpha = lineColor.alpha * opacity)
    var x = insetPx
    while (x <= size.width) {
        drawLine(color, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = strokePx)
        x += cellPx
    }
    var y = insetPx
    while (y <= size.height) {
        drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = strokePx)
        y += cellPx
    }
}

/** Same faint grid, vertical lines only — used where a full grid reads too busy (e.g. Mixes list). */
fun Modifier.verticalLinesBackground(
    lineColor: Color,
    cellSize: Dp = 28.dp,
    lineWidth: Dp = 1.dp,
    opacity: Float = 1f
): Modifier = drawBehind {
    val cellPx = cellSize.toPx()
    val strokePx = lineWidth.toPx()
    val insetPx = GRID_EDGE_INSET.toPx()
    val color = lineColor.copy(alpha = lineColor.alpha * opacity)
    var x = insetPx
    while (x <= size.width) {
        drawLine(color, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = strokePx)
        x += cellPx
    }
}
