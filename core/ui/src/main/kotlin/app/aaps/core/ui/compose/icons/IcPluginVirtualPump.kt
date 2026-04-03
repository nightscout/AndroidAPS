package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for Virtual Pump Plugin.
 * Represents virtual insulin pump for simulations.
 *
 * replacing ic_virtual_pump
 *
 * Bounding box: x: 1.8-22.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
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

@Preview(showBackground = true)
@Composable
private fun IcPluginVirtualPumpIconPreview() {
    Icon(
        imageVector = IcPluginVirtualPump,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" width="24px"
	 height="24px" viewBox="0 0 24 24" enable-background="new 0 0 24 24" xml:space="preserve">
<g id="ic_plugin_VirtualPump">
	<g id="Treatments_copy_2" display="inline">
	</g>
	<g display="inline">
		<path fill="#FFFFFF" d="M15.277,19.68c0,1.668-2.88,3.055-6.471,3.118c-3.591,0.064-6.689-1.219-6.96-2.883
			c-0.271-1.662,2.376-3.147,5.945-3.335c3.571-0.189,6.868,0.982,7.409,2.631L15.277,19.68z M12.582,12.572
			c0,1.978-1.638,3.624-3.679,3.698C6.86,16.345,5.1,14.823,4.945,12.852c-0.155-1.972,1.351-3.733,3.38-3.956
			c2.031-0.224,3.906,1.165,4.213,3.12L12.582,12.572z"/>
		<g>
			<path fill="#FFFFFF" d="M17.75,5.842c-0.156,0-0.283,0.14-0.283,0.312v0.013c0,0.165,0.127,0.3,0.283,0.3
				c0.156,0,0.283-0.134,0.283-0.3c0-0.002,0-0.004,0-0.006c0-0.002,0-0.005,0-0.007C18.033,5.982,17.906,5.842,17.75,5.842z"/>
			<polygon fill="#FFFFFF" points="16.808,6.441 16.808,5.868 16.278,6.154 			"/>
			<polygon fill="#FFFFFF" points="18.848,6.442 19.378,6.155 18.848,5.868 			"/>
			<path fill="#FFFFFF" d="M17.476,1.2c-2.593,0-4.697,1.732-4.697,3.864c0,1.056,0.525,2.065,1.452,2.794l-1.452,3.002l2.81-2.258
				c0.595,0.215,1.238,0.326,1.887,0.326c2.593,0,4.697-1.732,4.697-3.864C22.173,2.932,20.068,1.2,17.476,1.2z M20.35,6.158
				c0,0.383-0.288,0.695-0.642,0.695l-3.76-0.001c-0.024,0-0.048-0.001-0.072-0.004c-0.018,0.003-0.037,0.004-0.056,0.004l-0.876,0
				c-0.012,0-0.023-0.001-0.034-0.002v0.002c0,0.059-0.048,0.106-0.107,0.106l-0.114,0c-0.059,0-0.106-0.048-0.106-0.106l0-1.436
				c0-0.059,0.048-0.106,0.106-0.106h0.114c0.056,0,0.102,0.043,0.106,0.098c0.011-0.001,0.023-0.002,0.035-0.002l0.362,0l0-1.861
				c0-0.383,0.288-0.695,0.642-0.695l3.76,0.001c0.354,0,0.642,0.311,0.641,0.695L20.35,6.158z"/>
			<rect x="16.278" y="3.687" fill="#FFFFFF" width="3.1" height="1.839"/>
		</g>
	</g>
</g>
</svg>
 */