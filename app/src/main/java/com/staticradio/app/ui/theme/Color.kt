package com.staticradio.app.ui.theme

import androidx.compose.ui.graphics.Color

// Post-brutalist palette — see PROJECT_CONTEXT.md "Design direction".
// Deliberately NOT Material You dynamic pastel color.
val ConcreteLight = Color(0xFFE7E2D6)
val SurfaceLight = Color(0xFFF1EDE2)
val KeylineLight = Color(0xFF141412)

val ConcreteDark = Color(0xFF17171A)
// Plain neutral lift off ConcreteDark — no warm/brown tint (previous
// #1D1C18 read as an odd off-color against the cooler background).
val SurfaceDark = Color(0xFF232326)
val KeylineDark = Color(0xFFE7E2D6)

// Content color for anything sitting on an accent-colored surface (buttons,
// switches, badges) — dark ink reads cleanly against all three accents in
// both themes, so it's fixed rather than swapped per light/dark mode.
val OnAccentInk = Color(0xFF17171A)

// Default accent — "rebar" hazard orange. User can swap to AccentBlue / AccentLime
// in Settings (structural Material You pattern: swappable accent, not dynamic pastel).
val AccentRebar = Color(0xFFFF4713)
val AccentBlue = Color(0xFF3D6BFF)
val AccentLime = Color(0xFFCFEE2E)
