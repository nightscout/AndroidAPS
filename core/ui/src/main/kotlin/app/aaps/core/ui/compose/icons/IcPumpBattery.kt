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
 * Icon for Pump Battery.
 * Represents insulin pump battery status.
 *
 * replaces ic_cp_pump_battery
 *
 * Bounding box: x: 1.2-22.8, y: 6.6-17.4 (viewport: 24x24, ~90% width)
 */
val IcPumpBattery: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPumpBattery",
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
            moveTo(22.025f, 10.135f)
            curveToRelative(-0.428f, 0f, -0.775f, 0.347f, -0.775f, 0.775f)
            verticalLineToRelative(0.351f)
            horizontalLineToRelative(-0.554f)
            verticalLineTo(8.492f)
            curveToRelative(0f, -1.028f, -0.837f, -1.865f, -1.865f, -1.865f)
            horizontalLineTo(3.065f)
            curveTo(2.037f, 6.628f, 1.2f, 7.464f, 1.2f, 8.492f)
            verticalLineToRelative(7.015f)
            curveToRelative(0f, 1.028f, 0.837f, 1.865f, 1.865f, 1.865f)
            horizontalLineToRelative(15.766f)
            curveToRelative(0.851f, 0f, 1.563f, -0.577f, 1.786f, -1.357f)
            curveToRelative(0.012f, 0.001f, 0.021f, 0.007f, 0.033f, 0.007f)
            curveToRelative(0.393f, 0f, 0.711f, -0.351f, 0.711f, -0.785f)
            curveToRelative(0f, -0.415f, -0.295f, -0.747f, -0.665f, -0.774f)
            verticalLineToRelative(-1.725f)
            horizontalLineToRelative(0.554f)
            verticalLineToRelative(0.351f)
            curveToRelative(0f, 0.428f, 0.347f, 0.775f, 0.775f, 0.775f)
            curveToRelative(0.428f, 0f, 0.775f, -0.347f, 0.775f, -0.775f)
            verticalLineToRelative(-2.178f)
            curveTo(22.8f, 10.483f, 22.453f, 10.135f, 22.025f, 10.135f)
            close()

            moveTo(19.514f, 15.508f)
            curveToRelative(0f, 0.376f, -0.307f, 0.683f, -0.683f, 0.683f)
            horizontalLineTo(3.065f)
            curveToRelative(-0.377f, 0f, -0.683f, -0.307f, -0.683f, -0.683f)
            verticalLineTo(8.492f)
            curveToRelative(0f, -0.377f, 0.306f, -0.683f, 0.683f, -0.683f)
            horizontalLineToRelative(15.766f)
            curveToRelative(0.376f, 0f, 0.683f, 0.306f, 0.683f, 0.683f)
            verticalLineTo(15.508f)
            close()

            moveTo(9.582f, 9.96f)
            lineTo(4.929f, 13.412f)
            lineTo(9.009f, 11.972f)
            lineTo(11.114f, 14.058f)
            lineTo(16.357f, 9.942f)
            lineTo(11.28f, 11.935f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPumpBatteryIconPreview() {
    Icon(
        imageVector = IcPumpBattery,
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
<g id="ic_pump_battery">
	<g display="inline">
		<path fill="#36FF00" d="M22.025,10.135c-0.428,0-0.775,0.347-0.775,0.775v0.351h-0.554V8.492c0-1.028-0.837-1.865-1.865-1.865
			H3.065C2.037,6.628,1.2,7.464,1.2,8.492v7.015c0,1.028,0.837,1.865,1.865,1.865h15.766c0.851,0,1.563-0.577,1.786-1.357
			c0.012,0.001,0.021,0.007,0.033,0.007c0.393,0,0.711-0.351,0.711-0.785c0-0.415-0.295-0.747-0.665-0.774v-1.725h0.554v0.351
			c0,0.428,0.347,0.775,0.775,0.775c0.428,0,0.775-0.347,0.775-0.775v-2.178C22.8,10.483,22.453,10.135,22.025,10.135z
			 M19.514,15.508c0,0.376-0.307,0.683-0.683,0.683H3.065c-0.377,0-0.683-0.307-0.683-0.683V8.492c0-0.377,0.306-0.683,0.683-0.683
			h15.766c0.376,0,0.683,0.306,0.683,0.683V15.508z"/>
		<polygon fill="#36FF00" points="9.582,9.96 4.929,13.412 9.009,11.972 11.114,14.058 16.357,9.942 11.28,11.935 		"/>
	</g>
</g>
</svg>
 */