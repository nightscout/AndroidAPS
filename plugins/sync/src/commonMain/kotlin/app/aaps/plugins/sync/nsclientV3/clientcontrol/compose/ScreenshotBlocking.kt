package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Composable

/**
 * Blocks screenshots and the recents / app-switcher preview while this composable is in composition.
 *
 * Used by the pairing screen, which puts the PIN on display. That PIN wraps the shared secret a
 * paired client signs commands with, so a screenshot sitting in a gallery or a cloud backup is a
 * real exposure, not a cosmetic one.
 *
 * **Returns whether the platform actually did it.** Not Unit: Android can block screenshots, Apple
 * has no equivalent, and a screen showing a secret must be able to tell the difference. Returning
 * Unit would let the iOS build imply a protection it does not have - the exact silent-no-op failure
 * this codebase avoids elsewhere.
 */
@Composable
expect fun blockScreenshotsWhileVisible(): Boolean
