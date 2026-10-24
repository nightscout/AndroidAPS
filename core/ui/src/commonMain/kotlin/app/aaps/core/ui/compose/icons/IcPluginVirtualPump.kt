package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Virtual Pump Plugin.
 * Represents virtual insulin pump for simulations.
 *
 * replacing ic_virtual_pump
 *
 * Bounding box: x: 1.8-22.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginVirtualPumpIconPreview
 */
val IcPluginVirtualPump: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginVirtualPump",
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
            moveTo(15.277f, 19.68f)
            curveToRelative(0f, 1.668f, -2.88f, 3.055f, -6.471f, 3.118f)
            curveToRelative(-3.591f, 0.064f, -6.689f, -1.219f, -6.96f, -2.883f)
            curveToRelative(-0.271f, -1.662f, 2.376f, -3.147f, 5.945f, -3.335f)
            curveToRelative(3.571f, -0.189f, 6.868f, 0.982f, 7.409f, 2.631f)
            lineTo(15.277f, 19.68f)
            close()

            moveTo(12.582f, 12.572f)
            curveToRelative(0f, 1.978f, -1.638f, 3.624f, -3.679f, 3.698f)
            curveTo(6.86f, 16.345f, 5.1f, 14.823f, 4.945f, 12.852f)
            curveToRelative(-0.155f, -1.972f, 1.351f, -3.733f, 3.38f, -3.956f)
            curveToRelative(2.031f, -0.224f, 3.906f, 1.165f, 4.213f, 3.12f)
            lineTo(12.582f, 12.572f)
            close()

            moveTo(17.75f, 5.842f)
            curveToRelative(-0.156f, 0f, -0.283f, 0.14f, -0.283f, 0.312f)
            verticalLineToRelative(0.013f)
            curveToRelative(0f, 0.165f, 0.127f, 0.3f, 0.283f, 0.3f)
            curveToRelative(0.156f, 0f, 0.283f, -0.134f, 0.283f, -0.3f)
            curveToRelative(0f, -0.002f, 0f, -0.004f, 0f, -0.006f)
            curveToRelative(0f, -0.002f, 0f, -0.005f, 0f, -0.007f)
            curveTo(18.033f, 5.982f, 17.906f, 5.842f, 17.75f, 5.842f)
            close()

            moveTo(16.808f, 6.441f)
            verticalLineTo(5.868f)
            lineTo(16.278f, 6.154f)
            close()

            moveTo(18.848f, 6.442f)
            lineTo(19.378f, 6.155f)
            lineTo(18.848f, 5.868f)
            close()

            moveTo(17.476f, 1.2f)
            curveToRelative(-2.593f, 0f, -4.697f, 1.732f, -4.697f, 3.864f)
            curveToRelative(0f, 1.056f, 0.525f, 2.065f, 1.452f, 2.794f)
            lineToRelative(-1.452f, 3.002f)
            lineToRelative(2.81f, -2.258f)
            curveToRelative(0.595f, 0.215f, 1.238f, 0.326f, 1.887f, 0.326f)
            curveToRelative(2.593f, 0f, 4.697f, -1.732f, 4.697f, -3.864f)
            curveTo(22.173f, 2.932f, 20.068f, 1.2f, 17.476f, 1.2f)
            close()

            moveTo(20.35f, 6.158f)
            curveToRelative(0f, 0.383f, -0.288f, 0.695f, -0.642f, 0.695f)
            lineToRelative(-3.76f, -0.001f)
            curveToRelative(-0.024f, 0f, -0.048f, -0.001f, -0.072f, -0.004f)
            curveToRelative(-0.018f, 0.003f, -0.037f, 0.004f, -0.056f, 0.004f)
            horizontalLineToRelative(-0.876f)
            curveToRelative(-0.012f, 0f, -0.023f, -0.001f, -0.034f, -0.002f)
            verticalLineToRelative(0.002f)
            curveToRelative(0f, 0.059f, -0.048f, 0.106f, -0.107f, 0.106f)
            horizontalLineToRelative(-0.114f)
            curveToRelative(-0.059f, 0f, -0.106f, -0.048f, -0.106f, -0.106f)
            lineToRelative(0f, -1.436f)
            curveToRelative(0f, -0.059f, 0.048f, -0.106f, 0.106f, -0.106f)
            horizontalLineToRelative(0.114f)
            curveToRelative(0.056f, 0f, 0.102f, 0.043f, 0.106f, 0.098f)
            curveToRelative(0.011f, -0.001f, 0.023f, -0.002f, 0.035f, -0.002f)
            horizontalLineToRelative(0.362f)
            lineToRelative(0f, -1.861f)
            curveToRelative(0f, -0.383f, 0.288f, -0.695f, 0.642f, -0.695f)
            lineToRelative(3.76f, 0.001f)
            curveToRelative(0.354f, 0f, 0.642f, 0.311f, 0.641f, 0.695f)
            lineTo(20.35f, 6.158f)
            close()

            moveTo(16.278f, 3.687f)
            horizontalLineToRelative(3.1f)
            verticalLineToRelative(1.839f)
            horizontalLineToRelative(-3.1f)
            verticalLineTo(3.687f)
            close()
        }
    }.build()
}
