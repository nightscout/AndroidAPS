package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Profile.
 * Represents user profile or settings.
 *
 * replaces ic_ribbon_profile
 *
 * Bounding box: x: 1.2-22.8, y: 2.5-21.5 (viewport: 24x24, ~90% width)
 *
 * @see IcProfileIconPreview
 */
val IcProfile: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcProfile",
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
            moveTo(22.721f, 9.405f)
            curveToRelative(-0.186f, -0.577f, -0.684f, -0.997f, -1.285f, -1.083f)
            lineToRelative(-5.534f, -0.805f)
            lineToRelative(-2.476f, -5.015f)
            curveToRelative(-0.535f, -1.088f, -2.318f, -1.088f, -2.853f, 0f)
            lineTo(8.097f, 7.516f)
            lineTo(2.562f, 8.321f)
            curveTo(1.964f, 8.408f, 1.465f, 8.828f, 1.278f, 9.405f)
            curveToRelative(-0.188f, 0.576f, -0.032f, 1.208f, 0.402f, 1.631f)
            lineToRelative(4.006f, 3.903f)
            lineToRelative(-0.945f, 5.514f)
            curveToRelative(-0.103f, 0.599f, 0.143f, 1.202f, 0.633f, 1.557f)
            curveToRelative(0.277f, 0.202f, 0.605f, 0.305f, 0.935f, 0.305f)
            curveToRelative(0.253f, 0f, 0.508f, -0.061f, 0.74f, -0.184f)
            lineTo(12f, 19.529f)
            lineToRelative(4.951f, 2.601f)
            curveToRelative(0.54f, 0.281f, 1.184f, 0.239f, 1.678f, -0.121f)
            curveToRelative(0.489f, -0.355f, 0.735f, -0.961f, 0.632f, -1.557f)
            lineToRelative(-0.945f, -5.514f)
            lineToRelative(4.005f, -3.903f)
            curveTo(22.752f, 10.613f, 22.91f, 9.981f, 22.721f, 9.405f)
            close()

            moveTo(21.261f, 10.181f)
            lineToRelative(-4.376f, 4.266f)
            lineToRelative(1.033f, 6.023f)
            curveToRelative(0.02f, 0.121f, -0.029f, 0.241f, -0.127f, 0.311f)
            curveToRelative(-0.055f, 0.042f, -0.121f, 0.061f, -0.186f, 0.061f)
            curveToRelative(-0.05f, 0f, -0.101f, -0.011f, -0.149f, -0.037f)
            lineToRelative(-5.409f, -2.842f)
            lineToRelative(-5.41f, 2.842f)
            curveToRelative(-0.104f, 0.061f, -0.235f, 0.05f, -0.336f, -0.024f)
            curveToRelative(-0.098f, -0.07f, -0.147f, -0.191f, -0.126f, -0.311f)
            lineToRelative(1.033f, -6.023f)
            lineToRelative(-4.378f, -4.266f)
            curveToRelative(-0.087f, -0.084f, -0.117f, -0.212f, -0.08f, -0.327f)
            curveToRelative(0.037f, -0.115f, 0.137f, -0.198f, 0.257f, -0.216f)
            lineTo(9.057f, 8.76f)
            lineToRelative(2.705f, -5.481f)
            curveToRelative(0.107f, -0.219f, 0.463f, -0.219f, 0.57f, 0f)
            lineToRelative(2.704f, 5.481f)
            lineToRelative(6.049f, 0.878f)
            curveToRelative(0.121f, 0.018f, 0.219f, 0.101f, 0.257f, 0.216f)
            curveTo(21.379f, 9.97f, 21.349f, 10.097f, 21.261f, 10.181f)
            close()
        }
    }.build()
}
