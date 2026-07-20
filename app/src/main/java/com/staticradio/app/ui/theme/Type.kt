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

val StaticTypography = Typography(
    headlineMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Normal, fontSize = 9.sp, letterSpacing = 1.sp)
)
