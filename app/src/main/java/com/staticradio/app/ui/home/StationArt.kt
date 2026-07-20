package com.staticradio.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

// No local favicon caching yet (see PROJECT_CONTEXT.md "Known gaps") — a
// Radio Browser import's favicon URL is loaded directly over the network via
// Coil; everything else (manual URL entries, load failures) falls back to a
// deterministic colored initial.
private val PLACEHOLDER_PALETTE = listOf(
    Color(0xFF1D1C18), Color(0xFF3D6BFF), Color(0xFFCFEE2E), Color(0xFFB7AE99), Color(0xFFFF4713)
)

@Composable
fun StationArt(
    name: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    size: Dp? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val keyline = MaterialTheme.colorScheme.outline
    val sized = if (size != null) modifier.size(size) else modifier
    val outer = sized.clip(shape).border(1.0.dp, keyline, shape)

    if (!imageUrl.isNullOrBlank()) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = outer,
            loading = { ArtPlaceholder(name, Modifier.fillMaxSize()) },
            error = { ArtPlaceholder(name, Modifier.fillMaxSize()) }
        )
    } else {
        ArtPlaceholder(name, outer)
    }
}

@Composable
private fun ArtPlaceholder(name: String, modifier: Modifier) {
    val bg = PLACEHOLDER_PALETTE[Math.floorMod(name.hashCode(), PLACEHOLDER_PALETTE.size)]
    val textColor = if (bg.luminance() > 0.5f) Color(0xFF14140F) else Color(0xFFF1EDE2)

    Box(modifier = modifier.background(bg), contentAlignment = Alignment.Center) {
        Text(
            text = name.trim().take(1).ifEmpty { "?" }.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
