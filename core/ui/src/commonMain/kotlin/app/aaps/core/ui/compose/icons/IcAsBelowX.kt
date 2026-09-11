package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens Below Disabled.
 * Represents disabled AutoSensitivity below target range.
 *
 * Bounding box: x: 2.1-22.0, y: 5.7-18.3 (viewport: 24x24, ~90% width)
 *
 * @see IcAsBelowXIconPreview
 */
val IcAsBelowX: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsBelowX",
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
            // Bottom arrow
            moveTo(21.824f, 13.738f)
            horizontalLineToRelative(-3.453f)
            verticalLineTo(5.669f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(-3.453f)
            lineToRelative(4.604f, 4.593f)
            lineTo(21.824f, 13.738f)
            close()

            // X mark
            moveTo(10.073f, 9.453f)
            lineToRelative(-2.532f, 2.532f)
            lineToRelative(2.532f, 2.532f)
            curveToRelative(0.354f, 0.354f, 0.354f, 0.93f, 0.001f, 1.283f)
            curveToRelative(-0.172f, 0.172f, -0.4f, 0.267f, -0.642f, 0.267f)
            curveToRelative(-0.242f, 0f, -0.47f, -0.095f, -0.642f, -0.266f)
            lineToRelative(-2.532f, -2.532f)
            lineToRelative(-2.533f, 2.533f)
            curveToRelative(-0.172f, 0.171f, -0.4f, 0.266f, -0.642f, 0.266f)
            curveToRelative(-0.243f, 0f, -0.471f, -0.095f, -0.641f, -0.267f)
            curveToRelative(-0.354f, -0.353f, -0.354f, -0.929f, 0.001f, -1.283f)
            lineToRelative(2.532f, -2.532f)
            lineTo(2.443f, 9.453f)
            curveTo(2.271f, 9.282f, 2.176f, 9.054f, 2.176f, 8.812f)
            curveToRelative(0f, -0.244f, 0.094f, -0.472f, 0.267f, -0.643f)
            curveToRelative(0.172f, -0.171f, 0.399f, -0.265f, 0.641f, -0.265f)
            curveToRelative(0.242f, 0f, 0.47f, 0.094f, 0.642f, 0.266f)
            lineToRelative(2.532f, 2.531f)
            lineToRelative(2.533f, -2.532f)
            curveTo(8.962f, 7.998f, 9.19f, 7.904f, 9.432f, 7.904f)
            curveToRelative(0.243f, 0f, 0.471f, 0.094f, 0.642f, 0.266f)
            curveToRelative(0.172f, 0.171f, 0.266f, 0.399f, 0.266f, 0.642f)
            curveTo(10.34f, 9.054f, 10.245f, 9.282f, 10.073f, 9.453f)
            close()
        }
    }.build()
}
