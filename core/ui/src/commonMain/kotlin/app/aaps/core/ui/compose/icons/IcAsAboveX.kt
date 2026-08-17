package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens Above Disabled.
 * Represents disabled AutoSensitivity above target range.
 *
 * Bounding box: x: 1.2-22.8, y: 5.7-18.3 (viewport: 24x24, ~90% width)
 *
 * @see IcAsAboveXIconPreview
 */
val IcAsAboveX: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsAboveX",
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
            // Top arrow
            moveTo(17.22f, 5.669f)
            lineToRelative(-4.604f, 4.593f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineToRelative(-8.069f)
            horizontalLineToRelative(3.453f)
            lineTo(17.22f, 5.669f)
            close()

            // X mark
            moveTo(10.073f, 14.547f)
            lineToRelative(-2.532f, -2.532f)
            lineToRelative(2.532f, -2.532f)
            curveToRelative(0.354f, -0.354f, 0.354f, -0.93f, 0.001f, -1.283f)
            curveToRelative(-0.172f, -0.172f, -0.4f, -0.267f, -0.642f, -0.267f)
            curveToRelative(-0.242f, 0f, -0.47f, 0.095f, -0.642f, 0.266f)
            lineToRelative(-2.532f, 2.532f)
            lineTo(3.725f, 8.199f)
            curveToRelative(-0.172f, -0.171f, -0.4f, -0.266f, -0.642f, -0.266f)
            curveToRelative(-0.243f, 0f, -0.471f, 0.095f, -0.641f, 0.267f)
            curveTo(2.088f, 8.554f, 2.088f, 9.13f, 2.443f, 9.484f)
            lineToRelative(2.532f, 2.532f)
            lineToRelative(-2.532f, 2.532f)
            curveToRelative(-0.172f, 0.171f, -0.266f, 0.399f, -0.266f, 0.641f)
            curveToRelative(-0.001f, 0.243f, 0.094f, 0.471f, 0.266f, 0.643f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.242f, 0f, 0.47f, -0.094f, 0.642f, -0.266f)
            lineToRelative(2.532f, -2.531f)
            lineToRelative(2.533f, 2.532f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.243f, 0f, 0.471f, -0.094f, 0.642f, -0.266f)
            curveToRelative(0.172f, -0.171f, 0.266f, -0.399f, 0.266f, -0.642f)
            curveTo(10.34f, 14.946f, 10.245f, 14.718f, 10.073f, 14.547f)
            close()
        }
    }.build()
}
