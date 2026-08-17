package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for History Browser.
 * Represents historical data.
 *
 * replaces ic_pump_history
 *
 * Bounding box: x: 1.2-22.8, y: 2.4-21.5 (viewport: 24x24, ~90% width)
 *
 * @see IcHistoryIconPreview
 */
val IcHistory: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcHistory",
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
            moveTo(13.198f, 2.399f)
            curveToRelative(-5.107f, 0f, -9.283f, 4.011f, -9.573f, 9.047f)
            lineTo(2.529f, 10.35f)
            curveToRelative(-0.305f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
            curveToRelative(-0.304f, 0.305f, -0.304f, 0.797f, 0f, 1.101f)
            lineToRelative(2.397f, 2.396f)
            curveToRelative(0.152f, 0.151f, 0.352f, 0.228f, 0.551f, 0.228f)
            curveToRelative(0.199f, 0f, 0.399f, -0.076f, 0.551f, -0.228f)
            lineToRelative(2.396f, -2.396f)
            curveToRelative(0.304f, -0.304f, 0.304f, -0.797f, 0f, -1.101f)
            curveToRelative(-0.304f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
            lineToRelative(-1.036f, 1.036f)
            curveToRelative(0.316f, -4.149f, 3.785f, -7.431f, 8.013f, -7.431f)
            curveToRelative(4.436f, 0f, 8.045f, 3.609f, 8.045f, 8.045f)
            curveToRelative(0f, 4.436f, -3.609f, 8.045f, -8.045f, 8.045f)
            curveToRelative(-2.19f, 0f, -4.239f, -0.869f, -5.77f, -2.448f)
            curveToRelative(-0.3f, -0.308f, -0.793f, -0.315f, -1.101f, -0.017f)
            curveToRelative(-0.309f, 0.299f, -0.316f, 0.793f, -0.017f, 1.101f)
            curveToRelative(1.827f, 1.883f, 4.273f, 2.92f, 6.888f, 2.92f)
            curveToRelative(5.294f, 0f, 9.602f, -4.307f, 9.602f, -9.602f)
            reflectiveCurveTo(18.493f, 2.399f, 13.198f, 2.399f)
            close()

            moveTo(13.198f, 12.778f)
            horizontalLineToRelative(4.348f)
            curveToRelative(0.43f, 0f, 0.778f, -0.349f, 0.778f, -0.778f)
            curveToRelative(0f, -0.43f, -0.348f, -0.778f, -0.778f, -0.778f)
            horizontalLineToRelative(-3.57f)
            verticalLineTo(6.202f)
            curveToRelative(0f, -0.43f, -0.349f, -0.778f, -0.778f, -0.778f)
            reflectiveCurveToRelative(-0.778f, 0.348f, -0.778f, 0.777f)
            verticalLineTo(12f)
            curveTo(12.42f, 12.429f, 12.769f, 12.778f, 13.198f, 12.778f)
            close()
        }
    }.build()
}
