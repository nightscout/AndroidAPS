package app.aaps.core.ui.compose.icons.library.unused

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Center Arrow.
 * Represents centered or neutral position.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcArrowCenterIconPreview
 */
val IcArrowCenter: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowCenter",
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
            curveTo(6.035f, 1.2f, 1.2f, 6.035f, 1.2f, 12f)
            reflectiveCurveTo(6.035f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(22.8f, 17.965f, 22.8f, 12f)
            reflectiveCurveTo(17.965f, 1.2f, 12f, 1.2f)
            close()

            moveTo(12f, 19.312f)
            curveToRelative(-4.038f, 0f, -7.312f, -3.274f, -7.312f, -7.312f)
            reflectiveCurveTo(7.962f, 4.688f, 12f, 4.688f)
            reflectiveCurveTo(19.312f, 7.962f, 19.312f, 12f)
            reflectiveCurveTo(16.038f, 19.312f, 12f, 19.312f)
            close()

            moveTo(12f, 13.744f)
            curveToRelative(-0.963f, 0f, -1.744f, -0.781f, -1.744f, -1.744f)
            reflectiveCurveToRelative(0.781f, -1.744f, 1.744f, -1.744f)
            reflectiveCurveToRelative(1.744f, 0.781f, 1.744f, 1.744f)
            reflectiveCurveToRelative(-0.781f, 1.744f, -1.744f, 1.744f)
            close()
        }
    }.build()
}
