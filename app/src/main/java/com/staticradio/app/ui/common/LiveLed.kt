package com.staticradio.app.ui.common

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.staticradio.app.ui.theme.AccentLime
import com.staticradio.app.ui.theme.AccentRebar

/**
 * Pulsing "on air" indicator — mockup.html's .onair-led, "signal" lime.
 * Falls back to rebar orange when the current accent IS lime, so the LED
 * doesn't blend into an orange bar/button that's already using the accent.
 */
@Composable
fun LiveLed(modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live-led")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live-led-alpha"
    )
    val accent = MaterialTheme.colorScheme.primary
    val ledColor = if (accent == AccentLime) AccentRebar else AccentLime
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ledColor.copy(alpha = alpha))
    )
}
