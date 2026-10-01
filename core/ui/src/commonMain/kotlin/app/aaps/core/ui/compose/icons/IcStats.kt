package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for TDD (Total Daily Dose).
 * Represents total daily insulin dose.
 *
 * replaces ic_stats
 *
 * Bounding box: x: 2.7-21.3, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcStatsIconPreview
 */
val IcStats: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcStats",
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
            moveTo(4.145f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(11.96f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(9.382f)
            curveTo(5.602f, 22.147f, 4.949f, 22.8f, 4.145f, 22.8f)
            close()

            moveTo(9.381f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(7.051f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(14.291f)
            curveTo(10.839f, 22.147f, 10.186f, 22.8f, 9.381f, 22.8f)
            close()

            moveTo(14.618f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(8.979f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(12.363f)
            curveTo(16.076f, 22.147f, 15.423f, 22.8f, 14.618f, 22.8f)
            close()

            moveTo(19.855f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(2.658f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            curveToRelative(0.805f, 0f, 1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(18.684f)
            curveTo(21.313f, 22.147f, 20.66f, 22.8f, 19.855f, 22.8f)
            close()
        }
    }.build()
}
