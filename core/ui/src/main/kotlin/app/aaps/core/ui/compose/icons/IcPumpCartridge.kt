package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Pump Cartridge.
 * Represents insulin pump cartridge/reservoir.
 *
 * Bounding box: x: 1.2-22.8, y: 5.5-18.8 (viewport: 24x24, ~90% width)
 */
val IcPumpCartridge: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPumpCartridge",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFFEAF05)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(22.366f, 7.797f)
            curveToRelative(-0.398f, 0.228f, -0.892f, 0.114f, -1.104f, -0.254f)
            lineToRelative(-1.387f, -2.42f)
            curveToRelative(-0.211f, -0.369f, -0.06f, -0.853f, 0.338f, -1.081f)
            lineToRelative(0f, 0f)
            curveToRelative(0.398f, -0.228f, 0.892f, -0.114f, 1.104f, 0.254f)
            lineToRelative(1.387f, 2.42f)
            curveTo(22.916f, 7.085f, 22.765f, 7.569f, 22.366f, 7.797f)
            lineTo(22.366f, 7.797f)
            close()

            moveTo(7.132f, 18.698f)
            lineToRelative(-0.228f, -0.396f)
            lineToRelative(14.352f, -8.226f)
            curveToRelative(0.132f, -0.076f, 0.219f, -0.209f, 0.235f, -0.358f)
            lineToRelative(0.21f, -3.573f)
            lineToRelative(-0.397f, -0.693f)
            lineToRelative(-3.189f, -1.624f)
            curveToRelative(-0.136f, -0.062f, -0.295f, -0.054f, -0.427f, 0.022f)
            lineTo(3.336f, 12.077f)
            lineTo(3.108f, 11.68f)
            curveToRelative(-0.274f, -0.477f, -0.893f, -0.636f, -1.385f, -0.354f)
            curveToRelative(-0.492f, 0.282f, -0.668f, 0.896f, -0.394f, 1.374f)
            lineToRelative(4.024f, 7.018f)
            curveToRelative(0.274f, 0.477f, 0.893f, 0.636f, 1.385f, 0.354f)
            curveTo(7.406f, 19.176f, 7.406f, 19.176f, 7.132f, 18.698f)
            close()

            moveTo(19.703f, 9.922f)
            lineTo(18.052f, 7.33f)
            curveToRelative(-0.08f, -0.127f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.367f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-1.102f, 0.632f)
            lineTo(16.464f, 8.24f)
            curveToRelative(-0.081f, -0.126f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.367f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-1.102f, 0.632f)
            lineTo(14.876f, 9.15f)
            curveToRelative(-0.081f, -0.126f, -0.252f, -0.162f, -0.382f, -0.079f)
            curveToRelative(-0.127f, 0.08f, -0.169f, 0.242f, -0.097f, 0.368f)
            curveToRelative(0.002f, 0.003f, 0.004f, 0.008f, 0.006f, 0.011f)
            lineToRelative(1.638f, 2.571f)
            lineToRelative(-0.959f, 0.55f)
            lineToRelative(-2.273f, -3.636f)
            curveToRelative(-0.078f, -0.124f, -0.242f, -0.163f, -0.373f, -0.088f)
            lineToRelative(-8.244f, 4.725f)
            lineToRelative(-0.406f, -0.708f)
            lineToRelative(14.141f, -8.105f)
            lineToRelative(2.837f, 1.464f)
            lineToRelative(-0.17f, 3.189f)
            lineTo(19.703f, 9.922f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPumpCartridgeIconPreview() {
    Icon(
        imageVector = IcPumpCartridge,
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
<g id="ic_pump_cartridge">
	<g display="inline">
		<path fill="#FEAF05" d="M22.366,7.797c-0.398,0.228-0.892,0.114-1.104-0.254l-1.387-2.42c-0.211-0.369-0.06-0.853,0.338-1.081l0,0
			c0.398-0.228,0.892-0.114,1.104,0.254l1.387,2.42C22.916,7.085,22.765,7.569,22.366,7.797L22.366,7.797z"/>
		<path fill="#FEAF05" d="M7.132,18.698l-0.228-0.396l14.352-8.226c0.132-0.076,0.219-0.209,0.235-0.358l0.21-3.573l-0.397-0.693
			l-3.189-1.624c-0.136-0.062-0.295-0.054-0.427,0.022L3.336,12.077L3.108,11.68c-0.274-0.477-0.893-0.636-1.385-0.354
			c-0.492,0.282-0.668,0.896-0.394,1.374l4.024,7.018c0.274,0.477,0.893,0.636,1.385,0.354S7.406,19.176,7.132,18.698z
			 M19.703,9.922L18.052,7.33C17.972,7.203,17.8,7.168,17.67,7.251c-0.127,0.08-0.169,0.242-0.097,0.367
			c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571l-1.102,0.632L16.464,8.24c-0.081-0.126-0.252-0.162-0.382-0.079
			c-0.127,0.08-0.169,0.242-0.097,0.367c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571l-1.102,0.632L14.876,9.15
			c-0.081-0.126-0.252-0.162-0.382-0.079c-0.127,0.08-0.169,0.242-0.097,0.368c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571
			l-0.959,0.55l-2.273-3.636c-0.078-0.124-0.242-0.163-0.373-0.088l-8.244,4.725l-0.406-0.708l14.141-8.105l2.837,1.464l-0.17,3.189
			L19.703,9.922z"/>
	</g>
</g>
</svg>
 */