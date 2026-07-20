package com.staticradio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.staticradio.app.data.settings.AccentColor
import com.staticradio.app.data.settings.ImageShape
import com.staticradio.app.data.settings.ThemeMode

fun AccentColor.toComposeColor() = when (this) {
    AccentColor.REBAR -> AccentRebar
    AccentColor.SIGNAL_BLUE -> AccentBlue
    AccentColor.HAZARD_LIME -> AccentLime
}

fun ImageShape.toComposeShape(): Shape = when (this) {
    ImageShape.CIRCLE -> CircleShape
    ImageShape.SQUARE -> RoundedCornerShape(4.dp)
    ImageShape.ROUNDED -> RoundedCornerShape(16.dp)
}

fun resolveDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StaticTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.REBAR,
    content: @Composable () -> Unit
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val accent = accentColor.toComposeColor()

    // Material3's darkColorScheme()/lightColorScheme() factories leave every
    // role you don't pass at their own baked-in purple baseline — not derived
    // from the custom primary. onPrimary and the surfaceVariant family are
    // exactly what Switch and filled Button read for their thumb/text colors,
    // so leaving them unset is what caused the purple bleed-through.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accent,
            onPrimary = OnAccentInk,
            background = ConcreteDark,
            surface = SurfaceDark,
            surfaceVariant = SurfaceDark,
            onSurfaceVariant = KeylineDark.copy(alpha = 0.75f),
            outline = KeylineDark,
            onBackground = KeylineDark,
            onSurface = KeylineDark
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = OnAccentInk,
            background = ConcreteLight,
            surface = SurfaceLight,
            surfaceVariant = SurfaceLight,
            onSurfaceVariant = KeylineLight.copy(alpha = 0.75f),
            outline = KeylineLight,
            onBackground = KeylineLight,
            onSurface = KeylineLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StaticTypography,
        content = content
    )
}
