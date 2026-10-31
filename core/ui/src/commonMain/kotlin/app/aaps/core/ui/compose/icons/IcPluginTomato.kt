package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Tomato Plugin.
 *
 * replacing ic_sensor
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginTomatoIconPreview
 */
val IcPluginTomato: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginTomato",
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
            moveTo(12f, 1.2f)
            curveToRelative(5.961f, 0f, 10.8f, 4.839f, 10.8f, 10.8f)
            curveToRelative(0f, 5.961f, -4.839f, 10.8f, -10.8f, 10.8f)
            curveTo(6.039f, 22.8f, 1.2f, 17.961f, 1.2f, 12f)
            curveTo(1.2f, 6.039f, 6.039f, 1.2f, 12f, 1.2f)
            close()

            moveTo(21.058f, 11.86f)
            curveToRelative(0f, -0.024f, 0f, -0.049f, 0f, -0.072f)
            curveToRelative(0f, -5.055f, -4.112f, -9.158f, -9.176f, -9.158f)
            curveToRelative(-5.064f, 0f, -9.175f, 4.104f, -9.175f, 9.158f)
            curveToRelative(0f, 5.054f, 4.111f, 9.158f, 9.175f, 9.158f)
            lineToRelative(0.059f, 0f)
            curveToRelative(0.02f, 0f, 0.04f, 0f, 0.059f, 0f)
            curveToRelative(4.999f, 0f, 9.058f, -4.039f, 9.058f, -9.014f)
            curveTo(21.059f, 11.908f, 21.059f, 11.884f, 21.058f, 11.86f)
            close()

            moveTo(12f, 1.821f)
            curveToRelative(4.817f, 0f, 8.728f, 3.957f, 8.728f, 8.831f)
            curveToRelative(0f, 4.874f, -3.911f, 8.831f, -8.728f, 8.831f)
            curveToRelative(-4.817f, 0f, -8.728f, -3.957f, -8.728f, -8.831f)
            curveTo(3.272f, 5.778f, 7.183f, 1.821f, 12f, 1.821f)
            close()

            moveTo(11.028f, 11.086f)
            curveToRelative(0f, 0.006f, 0f, 0.011f, 0f, 0.017f)
            curveTo(11.028f, 11.598f, 11.464f, 12f, 12f, 12f)
            curveToRelative(0.536f, 0f, 0.972f, -0.402f, 0.972f, -0.897f)
            verticalLineToRelative(-0.018f)
            curveToRelative(0f, -0.005f, 0f, -0.011f, 0f, -0.017f)
            curveToRelative(0f, -0.476f, -0.435f, -0.863f, -0.972f, -0.863f)
            curveToRelative(-0.536f, 0f, -0.972f, 0.387f, -0.972f, 0.863f)
            verticalLineTo(11.086f)
            close()
        }
    }.build()
}
