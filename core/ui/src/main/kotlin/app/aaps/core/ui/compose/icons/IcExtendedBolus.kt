package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Extended Bolus.
 * Represents extended or multi-wave insulin bolus.
 *
 * replaces ic_actions_start_extended_bolus
 *
 * Bounding box: x: 3.0-21.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcExtendedBolusIconPreview
 */
val IcExtendedBolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcExtendedBolus",
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
            moveTo(17.07f, 5.852f)
            lineToRelative(0.482f, -0.934f)
            curveToRelative(0.245f, -0.474f, 0.059f, -1.058f, -0.416f, -1.303f)
            curveToRelative(-0.478f, -0.247f, -1.058f, -0.059f, -1.303f, 0.416f)
            lineToRelative(-0.483f, 0.935f)
            curveToRelative(-0.517f, -0.202f, -1.054f, -0.362f, -1.61f, -0.469f)
            verticalLineTo(3.681f)
            curveToRelative(0.614f, -0.083f, 1.094f, -0.588f, 1.094f, -1.224f)
            curveToRelative(0f, -0.694f, -0.562f, -1.257f, -1.256f, -1.257f)
            horizontalLineToRelative(-3.155f)
            curveToRelative(-0.694f, 0f, -1.257f, 0.563f, -1.257f, 1.257f)
            curveToRelative(0f, 0.637f, 0.481f, 1.141f, 1.095f, 1.224f)
            verticalLineToRelative(0.816f)
            curveToRelative(-4.263f, 0.817f, -7.496f, 4.569f, -7.496f, 9.067f)
            curveToRelative(0f, 5.092f, 4.144f, 9.236f, 9.236f, 9.236f)
            curveToRelative(5.092f, 0f, 9.236f, -4.144f, 9.236f, -9.236f)
            curveTo(21.236f, 10.343f, 19.576f, 7.506f, 17.07f, 5.852f)
            close()

            moveTo(12f, 21.436f)
            curveToRelative(-4.341f, 0f, -7.872f, -3.531f, -7.872f, -7.872f)
            reflectiveCurveTo(7.66f, 5.692f, 12f, 5.692f)
            reflectiveCurveToRelative(7.872f, 3.531f, 7.872f, 7.872f)
            reflectiveCurveTo(16.341f, 21.436f, 12f, 21.436f)
            close()

            moveTo(12.003f, 7.377f)
            curveToRelative(-0.118f, 0f, -0.231f, 0.047f, -0.314f, 0.131f)
            curveToRelative(-0.083f, 0.083f, -0.131f, 0.197f, -0.131f, 0.314f)
            verticalLineToRelative(5.728f)
            curveToRelative(0f, 0.109f, 0.04f, 0.215f, 0.113f, 0.296f)
            lineToRelative(3.805f, 4.283f)
            curveToRelative(0.079f, 0.088f, 0.189f, 0.141f, 0.308f, 0.148f)
            curveToRelative(0.008f, 0.001f, 0.017f, 0.001f, 0.025f, 0.001f)
            curveToRelative(0.109f, 0f, 0.215f, -0.04f, 0.296f, -0.113f)
            curveToRelative(1.324f, -1.182f, 2.084f, -2.858f, 2.084f, -4.601f)
            curveTo(18.188f, 10.154f, 15.414f, 7.379f, 12.003f, 7.377f)
            close()
        }
    }.build()
}
