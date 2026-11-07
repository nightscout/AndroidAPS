package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Manual Temp Target.
 * Represents a manual TT.
 *
 * replaces ic_crosstarget (different)
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.0 (viewport: 24x24, ~90% height)
 *
 * @see IcTtManualIconPreview
 */
val IcTtManual: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtManual",
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
            moveTo(17.655f, 8.401f)
            lineToRelative(-7.911f, 7.911f)
            curveToRelative(-0.063f, 0.063f, -0.106f, 0.144f, -0.122f, 0.232f)
            lineToRelative(-0.944f, 5.121f)
            curveToRelative(-0.026f, 0.143f, 0.019f, 0.289f, 0.122f, 0.391f)
            curveToRelative(0.103f, 0.103f, 0.249f, 0.148f, 0.391f, 0.122f)
            lineToRelative(5.121f, -0.944f)
            curveToRelative(0.088f, -0.016f, 0.169f, -0.059f, 0.232f, -0.122f)
            lineToRelative(7.911f, -7.911f)
            curveToRelative(0.461f, -0.461f, 0.46f, -1.213f, -0.003f, -1.676f)
            lineTo(19.33f, 8.403f)
            curveTo(18.868f, 7.941f, 18.116f, 7.939f, 17.655f, 8.401f)
            close()

            moveTo(13.491f, 20.02f)
            curveToRelative(0.101f, 0.101f, 0.137f, 0.249f, 0.094f, 0.385f)
            curveToRelative(-0.019f, 0.06f, -0.052f, 0.113f, -0.094f, 0.155f)
            curveToRelative(-0.054f, 0.054f, -0.125f, 0.092f, -0.204f, 0.106f)
            lineToRelative(-2.552f, 0.451f)
            curveToRelative(-0.253f, -0.008f, -0.504f, -0.107f, -0.698f, -0.3f)
            curveToRelative(-0.193f, -0.193f, -0.292f, -0.444f, -0.3f, -0.697f)
            lineToRelative(0.451f, -2.552f)
            curveToRelative(0.025f, -0.14f, 0.126f, -0.255f, 0.261f, -0.298f)
            curveToRelative(0.136f, -0.043f, 0.284f, -0.006f, 0.385f, 0.094f)
            lineTo(13.491f, 20.02f)
            close()

            moveTo(21.794f, 12.084f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(21.794f, 12.084f)
            close()

            moveTo(20.431f, 10.699f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(20.431f, 10.699f)
            close()

            moveTo(19.068f, 9.314f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(19.068f, 9.314f)
            close()
        }

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
            targetShape()
        }
    }.build()
}
