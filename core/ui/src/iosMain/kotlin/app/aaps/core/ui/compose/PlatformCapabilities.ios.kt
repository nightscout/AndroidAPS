package app.aaps.core.ui.compose

/** AVFoundation reads QR codes on every iPhone and iPad. A device that cannot give a picture right
 * now - the simulator, or a camera another app holds - is answered by the scanner itself. */
actual val qrScannerAvailable: Boolean = true
