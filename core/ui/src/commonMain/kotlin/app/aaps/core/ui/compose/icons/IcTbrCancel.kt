package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Cancel TBR (Temporary Basal Rate).
 * Represents cancellation of temporary basal rate.
 *
 * replacing ic_cancel_basal
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 *
 * @see IcTbrCancelIconPreview
 */
val IcTbrCancel: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrCancel",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFCF8BFE)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(1.948f, 12.668f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            curveToRelative(-0.267f, -0.267f, -0.267f, -0.699f, 0.001f, -0.966f)
            lineToRelative(7.533f, -7.534f)
            curveToRelative(0.268f, -0.267f, 0.699f, -0.267f, 0.967f, 0.001f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, -0.001f, 0.967f)
            lineToRelative(-7.533f, 7.533f)
            curveTo(2.297f, 12.602f, 2.123f, 12.668f, 1.948f, 12.668f)
            close()

            moveTo(9.482f, 12.668f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            lineTo(1.465f, 4.936f)
            curveToRelative(-0.267f, -0.267f, -0.267f, -0.7f, -0.001f, -0.967f)
            curveToRelative(0.267f, -0.268f, 0.699f, -0.267f, 0.967f, -0.001f)
            lineToRelative(7.533f, 7.534f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, 0.001f, 0.966f)
            curveTo(9.832f, 12.602f, 9.657f, 12.668f, 9.482f, 12.668f)
            close()

            moveTo(19.151f, 20.281f)
            lineTo(19.151f, 5.105f)
            lineTo(14.068f, 5.105f)
            lineTo(14.068f, 20.281f)
            lineTo(1.2f, 20.281f)
            lineTo(1.2f, 18.893f)
            lineTo(12.681f, 18.893f)
            lineTo(12.681f, 3.719f)
            lineTo(20.539f, 3.719f)
            lineTo(20.539f, 18.893f)
            lineTo(22.8f, 18.893f)
            lineTo(22.8f, 20.281f)
            close()
        }
    }.build()
}
