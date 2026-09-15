package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Exercise treatment type.
 * Represents physical activity entries.
 *
 * replaces ic_cp_exercise
 *
 * Bounding box: x: 1.2-22.8, y: 4.0-21.7 (viewport: 24x24, ~90% width)
 *
 * @see IcActivityIconPreview
 */
val IcActivity: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcActivity",
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
            moveTo(19.004f, 12.345f)
            curveToRelative(1.388f, 0f, 2.518f, -1.129f, 2.518f, -2.518f)
            curveToRelative(0f, -1.389f, -1.13f, -2.518f, -2.518f, -2.518f)
            reflectiveCurveToRelative(-2.518f, 1.13f, -2.518f, 2.518f)
            curveTo(16.486f, 11.216f, 17.616f, 12.345f, 19.004f, 12.345f)
            close()
            moveTo(19.004f, 8.083f)
            curveToRelative(0.962f, 0f, 1.745f, 0.782f, 1.745f, 1.745f)
            curveToRelative(0f, 0.962f, -0.783f, 1.745f, -1.745f, 1.745f)
            curveToRelative(-0.962f, 0f, -1.745f, -0.782f, -1.745f, -1.745f)
            curveTo(17.258f, 8.865f, 18.042f, 8.083f, 19.004f, 8.083f)
            close()

            moveTo(22.724f, 15.283f)
            curveToRelative(-0.036f, -0.047f, -0.869f, -1.15f, -2.101f, -1.15f)
            curveToRelative(-0.916f, 0f, -1.511f, 0.569f, -2.037f, 1.073f)
            curveToRelative(-0.465f, 0.445f, -0.868f, 0.83f, -1.43f, 0.83f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-0.458f, 0f, -0.833f, -0.268f, -1.206f, -0.595f)
            lineToRelative(1.943f, -1.467f)
            lineToRelative(-5.312f, -7.037f)
            lineToRelative(-5.126f, 3.871f)
            curveToRelative(-0.302f, 0.268f, -0.731f, 0.963f, -0.192f, 1.678f)
            curveToRelative(0.25f, 0.33f, 0.54f, 0.429f, 0.74f, 0.454f)
            curveToRelative(0.477f, 0.059f, 0.861f, -0.241f, 0.892f, -0.266f)
            lineToRelative(3.219f, -2.43f)
            lineToRelative(0.788f, 1.044f)
            lineToRelative(-4.625f, 3.49f)
            curveToRelative(-0.433f, -0.389f, -0.923f, -0.705f, -1.523f, -0.705f)
            horizontalLineTo(6.753f)
            curveToRelative(-0.919f, 0f, -1.7f, 0.709f, -2.389f, 1.334f)
            curveToRelative(-0.411f, 0.373f, -0.877f, 0.797f, -1.135f, 0.797f)
            curveToRelative(-0.499f, 0f, -1.145f, -0.716f, -1.332f, -0.967f)
            curveToRelative(-0.127f, -0.171f, -0.369f, -0.206f, -0.541f, -0.079f)
            curveToRelative(-0.171f, 0.127f, -0.207f, 0.369f, -0.08f, 0.54f)
            curveToRelative(0.097f, 0.131f, 0.977f, 1.279f, 1.953f, 1.279f)
            curveToRelative(0.556f, 0f, 1.065f, -0.462f, 1.654f, -0.997f)
            curveToRelative(0.586f, -0.531f, 1.249f, -1.134f, 1.87f, -1.134f)
            horizontalLineToRelative(0f)
            curveToRelative(0.547f, 0f, 1.025f, 0.498f, 1.532f, 1.026f)
            curveToRelative(0.563f, 0.586f, 1.145f, 1.192f, 1.935f, 1.192f)
            curveToRelative(0.754f, 0f, 1.392f, -0.522f, 2.009f, -1.028f)
            curveToRelative(0.576f, -0.472f, 1.172f, -0.961f, 1.799f, -0.961f)
            curveToRelative(0.374f, 0.001f, 0.726f, 0.324f, 1.131f, 0.698f)
            curveToRelative(0.528f, 0.486f, 1.126f, 1.036f, 1.995f, 1.036f)
            horizontalLineToRelative(0.001f)
            curveToRelative(0.873f, 0f, 1.453f, -0.555f, 1.965f, -1.044f)
            curveToRelative(0.481f, -0.461f, 0.897f, -0.859f, 1.502f, -0.859f)
            curveToRelative(0.839f, 0f, 1.474f, 0.831f, 1.481f, 0.84f)
            curveToRelative(0.129f, 0.171f, 0.37f, 0.203f, 0.541f, 0.077f)
            curveTo(22.815f, 15.695f, 22.851f, 15.454f, 22.724f, 15.283f)
            close()

            moveTo(11.74f, 15.437f)
            curveToRelative(-0.535f, 0.439f, -1.04f, 0.854f, -1.518f, 0.854f)
            curveToRelative(-0.461f, -0.001f, -0.907f, -0.465f, -1.378f, -0.955f)
            curveToRelative(-0.003f, -0.003f, -0.006f, -0.006f, -0.008f, -0.009f)
            lineToRelative(5.151f, -3.888f)
            lineTo(12.265f, 9.16f)
            lineToRelative(-3.847f, 2.904f)
            curveToRelative(-0.025f, 0.021f, -0.188f, 0.132f, -0.322f, 0.107f)
            curveToRelative(-0.028f, -0.004f, -0.113f, -0.013f, -0.217f, -0.151f)
            curveToRelative(-0.227f, -0.302f, 0.012f, -0.563f, 0.064f, -0.614f)
            lineTo(12.43f, 8.02f)
            lineToRelative(4.379f, 5.802f)
            lineToRelative(-1.447f, 1.092f)
            curveToRelative(-0.387f, -0.333f, -0.805f, -0.613f, -1.333f, -0.613f)
            curveTo(13.125f, 14.301f, 12.389f, 14.904f, 11.74f, 15.437f)
            close()
        }
    }.build()
}
