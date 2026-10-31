package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import androidx.compose.runtime.Composable

/**
 * Reports that the screen is NOT protected, because on Apple platforms it cannot be.
 *
 * There is no FLAG_SECURE equivalent. UIKit can hide content from the app-switcher snapshot (by
 * covering the window on `willResignActive`) and can *detect* a screenshot after the fact via
 * `userDidTakeScreenshotNotification`, but neither prevents one. So this returns false rather than
 * pretending: the caller is showing a pairing PIN and has to be able to warn the user.
 *
 * Covering the app-switcher snapshot is still worth doing and needs UIKit access from the iOS app
 * target - tracked in `_docs/ios_blockers.md`.
 */
@Composable
actual fun blockScreenshotsWhileVisible(): Boolean = false
