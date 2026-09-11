package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens Disabled.
 * Represents disabled AutoSensitivity feature.
 *
 * Bounding box: x: 1.2-22.8, y: 1.6-22.4 (viewport: 24x24, ~90% height)
 *
 * @see IcAsXIconPreview
 */
val IcAsX: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsX",
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
            // Top arrow (down)
            moveTo(19.347f, 17.767f)
            verticalLineTo(9.698f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(-3.453f)
            lineToRelative(4.604f, 4.593f)
            lineToRelative(4.604f, -4.593f)
            horizontalLineToRelative(-3.453f)
            close()

            // Bottom arrow (up)
            moveTo(12.327f, 1.64f)
            lineTo(7.722f, 6.233f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineTo(6.233f)
            horizontalLineToRelative(3.453f)
            lineTo(12.327f, 1.64f)
            close()

            // X mark
            moveTo(9.097f, 14.547f)
            lineToRelative(-2.532f, -2.532f)
            lineToRelative(2.532f, -2.532f)
            curveTo(9.452f, 9.13f, 9.452f, 8.554f, 9.098f, 8.2f)
            curveToRelative(-0.172f, -0.172f, -0.4f, -0.267f, -0.642f, -0.267f)
            curveToRelative(-0.242f, 0f, -0.47f, 0.095f, -0.642f, 0.266f)
            lineToRelative(-2.532f, 2.532f)
            lineTo(2.749f, 8.199f)
            curveToRelative(-0.172f, -0.171f, -0.4f, -0.266f, -0.642f, -0.266f)
            curveToRelative(-0.243f, 0f, -0.471f, 0.095f, -0.641f, 0.267f)
            curveTo(1.112f, 8.554f, 1.112f, 9.13f, 1.466f, 9.484f)
            lineToRelative(2.532f, 2.532f)
            lineToRelative(-2.532f, 2.532f)
            curveTo(1.295f, 14.718f, 1.2f, 14.946f, 1.2f, 15.188f)
            curveToRelative(-0.001f, 0.243f, 0.094f, 0.471f, 0.266f, 0.643f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.242f, 0f, 0.47f, -0.094f, 0.642f, -0.266f)
            lineToRelative(2.532f, -2.531f)
            lineToRelative(2.533f, 2.532f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.243f, 0f, 0.471f, -0.094f, 0.642f, -0.266f)
            curveToRelative(0.172f, -0.171f, 0.266f, -0.399f, 0.266f, -0.642f)
            curveTo(9.364f, 14.946f, 9.269f, 14.718f, 9.097f, 14.547f)
            close()
        }
    }.build()
}
