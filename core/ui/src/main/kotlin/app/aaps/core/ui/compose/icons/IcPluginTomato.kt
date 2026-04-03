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
 * Icon for Tomato Plugin.
 *
 * replacing ic_sensor
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginTomato: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginTomato",
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
            moveTo(12f, 1.2f)
            curveToRelative(5.961f, 0f, 10.8f, 4.839f, 10.8f, 10.8f)
            curveToRelative(0f, 5.961f, -4.839f, 10.8f, -10.8f, 10.8f)
            curveTo(6.039f, 22.8f, 1.2f, 17.961f, 1.2f, 12f)
            curveTo(1.2f, 6.039f, 6.039f, 1.2f, 12f, 1.2f)
            close()

            moveTo(21.058f, 11.86f)
            curveToRelative(0f, -0.024f, 0f, -0.049f, 0f, -0.072f)
            curveToRelative(0f, -5.055f, -4.112f, -9.158f, -9.176f, -9.158f)
            curveToRelative(-5.064f, 0f, -9.175f, 4.104f, -9.175f, 9.158f)
            curveToRelative(0f, 5.054f, 4.111f, 9.158f, 9.175f, 9.158f)
            lineToRelative(0.059f, 0f)
            curveToRelative(0.02f, 0f, 0.04f, 0f, 0.059f, 0f)
            curveToRelative(4.999f, 0f, 9.058f, -4.039f, 9.058f, -9.014f)
            curveTo(21.059f, 11.908f, 21.059f, 11.884f, 21.058f, 11.86f)
            close()

            moveTo(12f, 1.821f)
            curveToRelative(4.817f, 0f, 8.728f, 3.957f, 8.728f, 8.831f)
            curveToRelative(0f, 4.874f, -3.911f, 8.831f, -8.728f, 8.831f)
            curveToRelative(-4.817f, 0f, -8.728f, -3.957f, -8.728f, -8.831f)
            curveTo(3.272f, 5.778f, 7.183f, 1.821f, 12f, 1.821f)
            close()

            moveTo(11.028f, 11.086f)
            curveToRelative(0f, 0.006f, 0f, 0.011f, 0f, 0.017f)
            curveTo(11.028f, 11.598f, 11.464f, 12f, 12f, 12f)
            curveToRelative(0.536f, 0f, 0.972f, -0.402f, 0.972f, -0.897f)
            verticalLineToRelative(-0.018f)
            curveToRelative(0f, -0.005f, 0f, -0.011f, 0f, -0.017f)
            curveToRelative(0f, -0.476f, -0.435f, -0.863f, -0.972f, -0.863f)
            curveToRelative(-0.536f, 0f, -0.972f, 0.387f, -0.972f, 0.863f)
            verticalLineTo(11.086f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginTomatoIconPreview() {
    Icon(
        imageVector = IcPluginTomato,
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
<g id="ic_plugin_tomato">
	<g display="inline">
		<path fill="#FFFFFF" d="M12,1.2c5.961,0,10.8,4.839,10.8,10.8c0,5.961-4.839,10.8-10.8,10.8C6.039,22.8,1.2,17.961,1.2,12
			C1.2,6.039,6.039,1.2,12,1.2z M21.058,11.86c0-0.024,0-0.049,0-0.072c0-5.055-4.112-9.158-9.176-9.158
			c-5.064,0-9.175,4.104-9.175,9.158c0,5.054,4.111,9.158,9.175,9.158l0.059,0c0.02,0,0.04,0,0.059,0
			c4.999,0,9.058-4.039,9.058-9.014C21.059,11.908,21.059,11.884,21.058,11.86z"/>
		<path fill="#FFFFFF" d="M12,1.821c4.817,0,8.728,3.957,8.728,8.831c0,4.874-3.911,8.831-8.728,8.831
			c-4.817,0-8.728-3.957-8.728-8.831C3.272,5.778,7.183,1.821,12,1.821z M11.028,11.086c0,0.006,0,0.011,0,0.017
			C11.028,11.598,11.464,12,12,12c0.536,0,0.972-0.402,0.972-0.897v-0.018c0-0.005,0-0.011,0-0.017c0-0.476-0.435-0.863-0.972-0.863
			c-0.536,0-0.972,0.387-0.972,0.863V11.086z"/>
	</g>
</g>
</svg>
 */