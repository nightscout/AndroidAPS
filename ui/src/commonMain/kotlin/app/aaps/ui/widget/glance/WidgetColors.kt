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

/**
 * Background of every widget chip that needs no attention. Only a chip that asks for attention gets
 * a colour of its own, so a colour on the widget always means something (#5340).
 */
const val WidgetChipNeutral: Int = 0x1AFFFFFF // white @ 10%
