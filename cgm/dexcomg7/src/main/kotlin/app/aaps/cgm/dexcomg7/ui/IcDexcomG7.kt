package app.aaps.cgm.dexcomg7.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the Dexcom G7 Direct source: a G7 sensor seen from above. The oval sensor, a little wider
 * than tall like the real one (27 x 24 mm), with the groove round its top. No adhesive patch, so the
 * sensor fills the icon like the pump icons do.
 *
 * Bounding box: x: 1.2-22.8, y: 2.5-21.5 (viewport: 24x24, ~90% width)
 */
val IcDexcomG7: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcDexcomG7",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Sensor body with the groove round its top cut out
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            ellipse(cx = 12f, cy = 12f, rx = 10.8f, ry = 9.5f)
            ellipse(cx = 12f, cy = 12f, rx = 7.6f, ry = 6.6f)
            ellipse(cx = 12f, cy = 12f, rx = 6.1f, ry = 5.1f)
        }
    }.build()
}

private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    moveTo(cx - rx, cy)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * rx, dy1 = 0f)
    arcToRelative(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * rx, dy1 = 0f)
    close()
}
