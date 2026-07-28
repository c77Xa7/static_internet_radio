package com.staticradio.app.ui.common

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.staticradio.app.R

/**
 * Wraps the platform MediaRouteButton for Compose.
 *
 * A previous attempt at this crashed with IllegalArgumentException:
 * "background can not be translucent". The actual cause: MediaRouteButton's
 * *constructor* (androidx.mediarouter.app.MediaRouterThemeHelper) reads the
 * android:colorBackground theme attribute off the Context it's given to
 * decide the cast icon's light/dark tint, and throws if that resolves to
 * fully transparent — which it does for the Context Compose's AndroidView
 * hands the factory lambda by default. Setting the View's own background
 * *after* construction (the first, wrong fix) can't help, since the crash
 * already happened inside `MediaRouteButton(context)` itself. The real fix
 * is constructing it with a Context wrapped in a theme that defines a
 * concrete android:colorBackground (Theme.Static.MediaRouteButtonFix, see
 * themes.xml) — that only affects the internal tint decision, not what's
 * actually rendered, so the real visible background is still set at
 * runtime below to match wherever this sits, kept in sync across theme
 * changes via the `update` block.
 */
@Composable
fun CastButton(backgroundColor: Color, modifier: Modifier = Modifier) {
    val bgArgb = backgroundColor.toArgb()
    AndroidView(
        modifier = modifier.size(44.dp),
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, R.style.Theme_Static_MediaRouteButtonFix)
            MediaRouteButton(themedContext).apply {
                setBackgroundColor(bgArgb)
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, this)
            }
        },
        update = { button -> button.setBackgroundColor(bgArgb) }
    )
}
