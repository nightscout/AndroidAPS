package app.aaps.implementation.alerts

/**
 * Which alert the alarm screen should be showing, on a platform where the screen is drawn by the app's own window
 * (iOS, the desktop) and not opened as a screen of its own (Android).
 */
data class AlertId(val source: String, val key: String)
