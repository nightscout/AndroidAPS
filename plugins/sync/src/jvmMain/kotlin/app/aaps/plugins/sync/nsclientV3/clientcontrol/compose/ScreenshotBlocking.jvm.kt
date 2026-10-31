package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Composable

/**
 * Reports that the screen is NOT protected, because on desktop it cannot be.
 *
 * There is no FLAG_SECURE equivalent: a desktop window cannot stop the operating system, or any
 * screen-capture tool the user has installed, from photographing it. So this returns false rather
 * than pretending - the caller is showing a pairing PIN and has to be able to warn the user.
 */
@Composable
actual fun blockScreenshotsWhileVisible(): Boolean = false
