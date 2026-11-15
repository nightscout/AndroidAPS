package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Calibration treatment type.
 * Represents sensor calibration entries.
 *
 * replaces ic_calibration
 *
 * Bounding box: x: 1.2-22.8, y: 2.3-21.3 (viewport: 24x24, ~90% width)
 *
 * @see IcCalibrationIconPreview
 */
val IcCalibration: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCalibration",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(6.327f, 2.286f)
            curveToRelative(2.218f, 3.627f, 5.391f, 6.697f, 4.873f, 11.263f)
            curveToRelative(-0.318f, 2.805f, -3.148f, 4.523f, -5.952f, 3.981f)
            curveToRelative(-2.669f, -0.516f, -4.511f, -3.168f, -3.946f, -5.884f)
            curveTo(2.037f, 8.118f, 4.127f, 5.288f, 6.327f, 2.286f)
            close()
            moveTo(3.558f, 9.23f)
            curveToRelative(-0.264f, 0.793f, -0.609f, 1.57f, -0.773f, 2.384f)
            curveToRelative(-0.255f, 1.265f, -0.081f, 2.481f, 0.951f, 3.399f)
            curveToRelative(0.369f, 0.328f, 0.846f, 0.44f, 1.292f, 0.095f)
            curveToRelative(0.301f, -0.233f, 0.335f, -0.573f, 0.119f, -0.861f)
            curveTo(4.041f, 12.766f, 3.499f, 11.131f, 3.558f, 9.23f)
            close()

            moveTo(19.586f, 2.392f)
            curveToRelative(1.335f, 1.809f, 2.58f, 3.53f, 3.098f, 5.644f)
            curveToRelative(0.348f, 1.422f, -0.085f, 2.614f, -1.291f, 3.438f)
            curveToRelative(-1.173f, 0.802f, -2.44f, 0.815f, -3.614f, 0.011f)
            curveToRelative(-1.204f, -0.824f, -1.64f, -2.035f, -1.295f, -3.447f)
            curveTo(16.999f, 5.928f, 18.237f, 4.2f, 19.586f, 2.392f)
            close()

            moveTo(15.679f, 14.66f)
            curveToRelative(0.992f, 1.362f, 1.91f, 2.618f, 2.264f, 4.175f)
            curveToRelative(0.234f, 1.028f, -0.12f, 1.865f, -0.976f, 2.446f)
            curveToRelative(-0.833f, 0.565f, -1.734f, 0.581f, -2.573f, 0.018f)
            curveToRelative(-0.857f, -0.575f, -1.226f, -1.407f, -0.996f, -2.438f)
            curveTo(13.753f, 17.282f, 14.663f, 16.002f, 15.679f, 14.66f)
            close()
        }
    }.build()
}
