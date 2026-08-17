package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Maintenance Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginMaintenanceIconPreview
 */
val IcPluginMaintenance: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMaintenance",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(11.195f, 9.574f)
            curveToRelative(0.559f, 1.506f, 1.709f, 2.721f, 3.164f, 3.354f)
            curveToRelative(-0.041f, 0.405f, -0.217f, 0.798f, -0.527f, 1.107f)
            lineToRelative(-8.171f, 8.171f)
            curveToRelative(-0.715f, 0.715f, -1.876f, 0.715f, -2.591f, 0f)
            lineToRelative(-1.335f, -1.335f)
            curveToRelative(-0.715f, -0.715f, -0.715f, -1.876f, 0f, -2.591f)
            lineToRelative(8.171f, -8.171f)
            curveTo(10.263f, 9.754f, 10.729f, 9.576f, 11.195f, 9.574f)
            close()

            moveTo(22.798f, 7.029f)
            curveTo(22.8f, 7.071f, 22.8f, 7.114f, 22.8f, 7.156f)
            curveToRelative(0f, 3.256f, -2.594f, 5.9f, -5.789f, 5.9f)
            reflectiveCurveToRelative(-5.789f, -2.644f, -5.789f, -5.9f)
            curveToRelative(0f, -3.256f, 2.594f, -5.899f, 5.789f, -5.899f)
            curveToRelative(1.386f, 0f, 2.659f, 0.497f, 3.656f, 1.327f)
            lineToRelative(-4.452f, 2.651f)
            verticalLineToRelative(2.91f)
            lineToRelative(2.562f, 1.355f)
            lineToRelative(0.032f, 0.053f)
            lineToRelative(0.031f, -0.02f)
            lineToRelative(0.037f, 0.02f)
            lineToRelative(0.01f, -0.05f)
            lineTo(22.798f, 7.029f)
            close()
        }
    }.build()
}
