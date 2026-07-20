package com.staticradio.app.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much bottom space scrollable screens need to reserve so their last
 * item isn't hidden behind the floating persistent player panel, which
 * overlays content rather than pushing it up (see StaticApp.kt). Zero when
 * no station is loaded and the panel isn't showing.
 */
val LocalPlayerBarBottomInset = compositionLocalOf { 0.dp }

/** Panel height (~64dp bar) + its own vertical margins (10dp top + 10dp bottom), plus breathing room. */
val PlayerBarReservedHeight = 96.dp
