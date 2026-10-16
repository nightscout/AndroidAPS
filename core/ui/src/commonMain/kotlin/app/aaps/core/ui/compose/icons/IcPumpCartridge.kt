package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Pump Cartridge.
 * Represents insulin pump cartridge/reservoir.
 *
 * Bounding box: x: 1.2-22.8, y: 5.5-18.8 (viewport: 24x24, ~90% width)
 *
 * @see IcPumpCartridgeIconPreview
 */
val IcPumpCartridge: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPumpCartridge",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFFEAF05)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(22.366f, 7.797f)
            curveToRelative(-0.398f, 0.228f, -0.892f, 0.114f, -1.104f, -0.254f)
            lineToRelative(-1.387f, -2.42f)
            curveToRelative(-0.211f, -0.369f, -0.06f, -0.853f, 0.338f, -1.081f)
            lineToRelative(0f, 0f)
            curveToRelative(0.398f, -0.228f, 0.892f, -0.114f, 1.104f, 0.254f)
            lineToRelative(1.387f, 2.42f)
            curveTo(22.916f, 7.085f, 22.765f, 7.569f, 22.366f, 7.797f)
            lineTo(22.366f, 7.797f)
            close()

            moveTo(7.132f, 18.698f)
            lineToRelative(-0.228f, -0.396f)
            lineToRelative(14.352f, -8.226f)
            curveToRelative(0.132f, -0.076f, 0.219f, -0.209f, 0.235f, -0.358f)
            lineToRelative(0.21f, -3.573f)
            lineToRelative(-0.397f, -0.693f)
            lineToRelative(-3.189f, -1.624f)
            curveToRelative(-0.136f, -0.062f, -0.295f, -0.054f, -0.427f, 0.022f)
            lineTo(3.336f, 12.077f)
            lineTo(3.108f, 11.68f)
            curveToRelative(-0.274f, -0.477f, -0.893f, -0.636f, -1.385f, -0.354f)
            curveToRelative(-0.492f, 0.282f, -0.668f, 0.896f, -0.394f, 1.374f)
            lineToRelative(4.024f, 7.018f)
            curveToRelative(0.274f, 0.477f, 0.893f, 0.636f, 1.385f, 0.354f)
            curveTo(7.406f, 19.176f, 7.406f, 19.176f, 7.132f, 18.698f)
            close()

            moveTo(19.703f, 9.922f)
            lineTo(18.052f, 7.33f)
            curveToRelative(-0.08f, -0.127f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.367f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-1.102f, 0.632f)
            lineTo(16.464f, 8.24f)
            curveToRelative(-0.081f, -0.126f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.367f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-1.102f, 0.632f)
            lineTo(14.876f, 9.15f)
            curveToRelative(-0.081f, -0.126f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.368f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-0.959f, 0.55f)
            lineToRelative(-2.273f, -3.636f)
            curveToRelative(-0.078f, -0.124f, -0.242f, -0.163f, -0.373f, -0.088f)
            lineToRelative(-8.244f, 4.725f)
            lineToRelative(-0.406f, -0.708f)
            lineToRelative(14.141f, -8.105f)
            lineToRelative(2.837f, 1.464f)
            lineToRelative(-0.17f, 3.189f)
            lineTo(19.703f, 9.922f)
            close()
        }
    }.build()
}
