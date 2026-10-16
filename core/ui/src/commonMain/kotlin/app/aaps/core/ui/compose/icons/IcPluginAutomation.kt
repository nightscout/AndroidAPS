package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Automation Plugin.
 *
 * replacing ic_automation
 *
 * Bounding box: x: 2.7-21.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginAutomationIconPreview
 */
val IcPluginAutomation: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAutomation",
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
            moveTo(16.114f, 12.514f)
            curveToRelative(-2.839f, 0f, -5.143f, 2.304f, -5.143f, 5.143f)
            reflectiveCurveToRelative(2.304f, 5.143f, 5.143f, 5.143f)
            reflectiveCurveToRelative(5.143f, -2.304f, 5.143f, -5.143f)
            reflectiveCurveTo(18.953f, 12.514f, 16.114f, 12.514f)
            close()

            moveTo(17.811f, 20.074f)
            lineTo(15.6f, 17.863f)
            verticalLineToRelative(-3.291f)
            horizontalLineToRelative(1.029f)
            verticalLineToRelative(2.87f)
            lineToRelative(1.903f, 1.903f)
            lineTo(17.811f, 20.074f)
            close()

            moveTo(17.143f, 3.257f)
            horizontalLineToRelative(-3.271f)
            curveTo(13.44f, 2.064f, 12.309f, 1.2f, 10.971f, 1.2f)
            reflectiveCurveTo(8.503f, 2.064f, 8.071f, 3.257f)
            horizontalLineTo(4.8f)
            curveToRelative(-1.131f, 0f, -2.057f, 0.926f, -2.057f, 2.057f)
            verticalLineToRelative(15.429f)
            curveToRelative(0f, 1.131f, 0.926f, 2.057f, 2.057f, 2.057f)
            horizontalLineToRelative(6.285f)
            curveToRelative(-0.607f, -0.586f, -1.101f, -1.286f, -1.461f, -2.057f)
            horizontalLineTo(4.8f)
            verticalLineTo(5.314f)
            horizontalLineToRelative(2.057f)
            verticalLineTo(8.4f)
            horizontalLineToRelative(8.229f)
            verticalLineTo(5.314f)
            horizontalLineToRelative(2.057f)
            verticalLineToRelative(5.225f)
            curveToRelative(0.73f, 0.103f, 1.419f, 0.319f, 2.057f, 0.617f)
            verticalLineTo(5.314f)
            curveTo(19.2f, 4.183f, 18.274f, 3.257f, 17.143f, 3.257f)
            close()

            moveTo(10.971f, 5.314f)
            curveToRelative(-0.566f, 0f, -1.029f, -0.463f, -1.029f, -1.029f)
            curveToRelative(0f, -0.566f, 0.463f, -1.029f, 1.029f, -1.029f)
            curveToRelative(0.566f, 0f, 1.029f, 0.463f, 1.029f, 1.029f)
            curveTo(12f, 4.851f, 11.537f, 5.314f, 10.971f, 5.314f)
            close()
        }
    }.build()
}
