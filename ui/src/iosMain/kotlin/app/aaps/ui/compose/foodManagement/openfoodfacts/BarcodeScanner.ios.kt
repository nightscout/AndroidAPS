package app.aaps.ui.compose.foodManagement.openfoodfacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Not done yet on iOS (proof of concept): the barcode is typed. */
internal actual val barcodeScannerAvailable: Boolean = false

@Composable
internal actual fun BarcodeScanner(onScanned: (String) -> Unit, modifier: Modifier) = Unit
