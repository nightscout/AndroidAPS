package app.aaps.ui.widget.glance

/**
 * Widget-only color constants that cannot come from the shared theme palette
 * ([app.aaps.core.ui.compose.DarkGeneralColors] / [app.aaps.core.ui.compose.navigation.DarkElementColors]).
 *
 * All state-based chip colors (loop mode, temp target reason, IOB/COB/Sensitivity, inProgress)
 * are read from the theme data classes directly via [WidgetStateLoader] / [AapsGlanceWidget].
 */

/** Approximation of Material3 `onSurfaceVariant` in dark mode — widgets can't read MaterialTheme. */
const val WidgetTextMuted: Int = 0xFFFFFFFF.toInt() // white @ 100%
