package com.staticradio.app.ui.mixes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.staticradio.app.R

/** Provided brand art, cropped to a circle — square source images, so ContentScale.Crop fills the circle with no letterboxing. */
@Composable
fun SoundCloudLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.soundcloud_logo),
        contentDescription = "SoundCloud",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape)
    )
}

@Composable
fun MixcloudLogo(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Image(
        painter = painterResource(R.drawable.mixcloud_logo),
        contentDescription = "Mixcloud",
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape)
    )
}
