package app.aaps.core.ui.compose

/**
 * True where this platform can read a QR code with the camera.
 *
 * Here rather than next to the scanner itself because two modules have to agree on it: the scanner
 * lives in `:plugins:sync`, and the setup wizard in `:plugins:configuration` has to decide whether
 * to offer the step that leads to it. Both already depend on `:core:ui`, so this costs no new
 * dependency between them.
 *
 * It says what the device can do, not which shell is running. `AppPlatform` would be the wrong
 * place: its own documentation says it is "not a device capability", after a desktop build once
 * asked for a Bluetooth permission because the question had been asked that way.
 *
 * A platform that answers true still has to cope with a device that cannot help right now - no
 * camera, or a camera another app holds. That belongs to the scanner, which can explain it, and
 * not to this flag, which would only hide the explanation along with the button.
 */
expect val qrScannerAvailable: Boolean
