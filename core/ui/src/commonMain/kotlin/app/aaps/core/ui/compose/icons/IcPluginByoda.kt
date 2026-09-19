package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for BYODA Plugin.
 * Represents Bring Your Own Device AAPS integration.
 *
 * replacing ic_dexcom_g6
 *
 * Bounding box: x: 5.2-18.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginByodaIconPreview
 */
val IcPluginByoda: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginByoda",
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
            moveTo(16.941f, 10.734f)
            curveToRelative(-0.014f, 3.375f, -0.228f, 6.749f, -0.636f, 10.124f)
            curveToRelative(-0.042f, 0.82f, -0.522f, 1.643f, -1.842f, 1.88f)
            curveToRelative(-1.628f, 0.082f, -3.278f, 0.082f, -4.949f, 0f)
            curveToRelative(-1.29f, -0.189f, -1.344f, -0.931f, -1.556f, -1.833f)
            curveToRelative(-0.779f, -3.312f, -0.843f, -6.732f, -0.898f, -10.137f)
            curveToRelative(0.945f, 1.64f, 2.8f, 2.394f, 4.931f, 2.394f)
            curveTo(14.137f, 13.162f, 16.002f, 12.392f, 16.941f, 10.734f)
            close()

            moveTo(12f, 1.2f)
            curveToRelative(2.94f, 0f, 5.326f, 2.386f, 5.326f, 5.325f)
            curveToRelative(0f, 2.939f, -2.387f, 5.326f, -5.326f, 5.326f)
            reflectiveCurveToRelative(-5.326f, -2.387f, -5.326f, -5.326f)
            reflectiveCurveTo(9.06f, 1.2f, 12f, 1.2f)
            close()
        }
    }.build()
}
