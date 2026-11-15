package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * ZXing, the same on a phone and on a desktop. Size 0 and no margin make one matrix cell per square; the border and
 * the scaling are drawn by `QrCodeImage`.
 */
internal actual fun qrModules(text: String): QrModules? {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    val size = matrix.width
    return QrModules(size, BooleanArray(size * size) { matrix.get(it % size, it / size) })
}
