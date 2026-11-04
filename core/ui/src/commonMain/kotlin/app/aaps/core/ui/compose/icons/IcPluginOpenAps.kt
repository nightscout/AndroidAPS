package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for OpenAPS Plugin.
 *
 * Bounding box: x: 1.8-22.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginOpenAPSIconPreview
 */
val IcPluginOpenAPS: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOpenAPS",
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
            moveTo(13.128f, 1.261f)
            curveToRelative(-0.75f, -0.081f, -1.507f, -0.081f, -2.257f, 0f)
            lineTo(10.571f, 3.39f)
            curveToRelative(-0.684f, 0.116f, -1.352f, 0.316f, -1.99f, 0.595f)
            lineTo(7.197f, 2.359f)
            curveTo(6.523f, 2.703f, 5.887f, 3.119f, 5.298f, 3.6f)
            lineToRelative(0.878f, 1.956f)
            curveToRelative(-0.514f, 0.475f, -0.97f, 1.01f, -1.358f, 1.595f)
            lineTo(2.79f, 6.546f)
            curveTo(2.407f, 7.206f, 2.092f, 7.907f, 1.853f, 8.635f)
            lineToRelative(1.778f, 1.162f)
            curveToRelative(-0.18f, 0.682f, -0.28f, 1.383f, -0.295f, 2.089f)
            lineToRelative(-2.028f, 0.605f)
            curveToRelative(0.028f, 0.767f, 0.136f, 1.529f, 0.321f, 2.273f)
            horizontalLineToRelative(2.113f)
            curveToRelative(0.211f, 0.673f, 0.5f, 1.317f, 0.862f, 1.92f)
            lineTo(3.22f, 18.31f)
            curveToRelative(0.431f, 0.63f, 0.927f, 1.212f, 1.478f, 1.736f)
            lineToRelative(1.778f, -1.163f)
            curveToRelative(0.535f, 0.45f, 1.12f, 0.833f, 1.745f, 1.141f)
            lineTo(7.92f, 22.153f)
            curveToRelative(0.697f, 0.293f, 1.423f, 0.51f, 2.166f, 0.647f)
            lineToRelative(0.878f, -1.956f)
            curveToRelative(0.689f, 0.084f, 1.385f, 0.084f, 2.074f, 0f)
            lineToRelative(0.878f, 1.956f)
            curveToRelative(0.742f, -0.138f, 1.468f, -0.354f, 2.166f, -0.647f)
            lineToRelative(-0.301f, -2.128f)
            curveToRelative(0.625f, -0.308f, 1.21f, -0.691f, 1.745f, -1.141f)
            lineToRelative(1.778f, 1.163f)
            curveToRelative(0.551f, -0.524f, 1.047f, -1.106f, 1.478f, -1.736f)
            lineToRelative(-1.384f, -1.625f)
            curveToRelative(0.362f, -0.602f, 0.651f, -1.247f, 0.862f, -1.92f)
            horizontalLineToRelative(2.113f)
            curveToRelative(0.186f, -0.744f, 0.293f, -1.506f, 0.321f, -2.273f)
            lineToRelative(-2.028f, -0.605f)
            curveToRelative(-0.016f, -0.706f, -0.115f, -1.408f, -0.295f, -2.089f)
            lineToRelative(1.778f, -1.162f)
            curveToRelative(-0.239f, -0.728f, -0.554f, -1.428f, -0.938f, -2.089f)
            lineToRelative(-2.028f, 0.606f)
            curveToRelative(-0.388f, -0.585f, -0.844f, -1.121f, -1.358f, -1.595f)
            lineTo(18.702f, 3.6f)
            curveToRelative(-0.588f, -0.481f, -1.224f, -0.897f, -1.898f, -1.242f)
            lineToRelative(-1.384f, 1.625f)
            curveToRelative(-0.638f, -0.279f, -1.306f, -0.478f, -1.99f, -0.595f)
            lineTo(13.128f, 1.261f)
            close()

            moveTo(12f, 6.971f)
            curveToRelative(2.776f, 0f, 5.029f, 2.293f, 5.029f, 5.117f)
            curveToRelative(0f, 2.824f, -2.253f, 5.117f, -5.029f, 5.117f)
            reflectiveCurveToRelative(-5.029f, -2.293f, -5.029f, -5.117f)
            curveTo(6.971f, 9.264f, 9.224f, 6.971f, 12f, 6.971f)
            close()
        }
    }.build()
}
