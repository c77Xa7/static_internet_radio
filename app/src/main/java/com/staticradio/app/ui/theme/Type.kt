package com.staticradio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.staticradio.app.R

// Single-typeface system now — IBM Plex Mono (the "now playing" text's font)
// used everywhere, not just metadata. Weight/size/letterSpacing still vary
// per role to keep the hierarchy readable.
val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
)

// Any role NOT listed here silently falls back to Material3's default
// (Roboto), which is how "Tracklist" on the mix form, the Home empty-state
// heading and a Settings section header all ended up in a different typeface
// from the rest of the app. Defining them here fixes every call site at once
// rather than patching individual Text composables — add the role here, not a
// per-call-site override, if another one turns up.
val StaticTypography = Typography(
    headlineMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 9.sp, letterSpacing = 1.sp)
)
