package app.aaps.ui.compose.foodManagement.openfoodfacts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * True where [BarcodeScanner] can read a barcode. Where it is false, the scan button is not shown
 * at all: the user types the barcode or the name instead.
 */
internal expect val barcodeScannerAvailable: Boolean

/**
 * Shows the camera and calls [onScanned] with the digits of each product barcode it reads
 * (EAN-13, EAN-8, UPC-A, UPC-E). It can call [onScanned] several times for the same code.
 */
@Composable
internal expect fun BarcodeScanner(onScanned: (String) -> Unit, modifier: Modifier)
