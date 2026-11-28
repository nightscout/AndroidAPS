package app.aaps.ui.compose.foodManagement.openfoodfacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** No camera on the desktop: the barcode is typed. */
internal actual val barcodeScannerAvailable: Boolean = false

@Composable
internal actual fun BarcodeScanner(onScanned: (String) -> Unit, modifier: Modifier) = Unit
