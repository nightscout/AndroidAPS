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
 * Icon for LGS (Low Glucose Suspend) Loop.
 * Represents low glucose suspend loop mode.
 *
 * Bounding box: x: 1.3-22.8, y: 2.1-21.8 (viewport: 24x24, ~90% width)
 */
val IcLoopLgs: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopLgs",
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
            moveTo(5.437f, 7.639f)
            curveToRelative(0.401f, -0.533f, 0.875f, -1.006f, 1.409f, -1.405f)
            lineTo(5.468f, 3.852f)
            curveToRelative(-0.94f, 0.657f, -1.758f, 1.475f, -2.416f, 2.414f)
            lineTo(5.437f, 7.639f)
            close()

            moveTo(8.299f, 5.388f)
            curveToRelative(0.603f, -0.257f, 1.251f, -0.429f, 1.927f, -0.509f)
            lineToRelative(-0.003f, -2.747f)
            curveTo(9.052f, 2.231f, 7.943f, 2.54f, 6.926f, 3.016f)
            lineTo(8.299f, 5.388f)
            close()

            moveTo(11.903f, 2.153f)
            lineToRelative(0.003f, 2.728f)
            curveToRelative(0.741f, 0.091f, 1.448f, 0.296f, 2.1f, 0.598f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineToRelative(1.845f, -0.529f)
            curveTo(15.11f, 2.833f, 13.563f, 2.298f, 11.903f, 2.153f)
            close()

            moveTo(4.074f, 11.015f)
            curveTo(4.156f, 10.34f, 4.329f, 9.693f, 4.588f, 9.09f)
            lineTo(2.214f, 7.722f)
            curveToRelative(-0.477f, 1.017f, -0.788f, 2.125f, -0.888f, 3.296f)
            lineTo(4.074f, 11.015f)
            close()

            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 3.888f, -3.152f, 7.039f, -7.039f, 7.039f)
            curveToRelative(-2.307f, 0f, -4.348f, -1.114f, -5.632f, -2.829f)
            lineToRelative(-2.382f, 1.378f)
            curveToRelative(1.767f, 2.529f, 4.695f, 4.186f, 8.013f, 4.186f)
            curveToRelative(5.399f, 0f, 9.775f, -4.376f, 9.775f, -9.775f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()

            moveTo(4.58f, 14.624f)
            curveToRelative(-0.257f, -0.604f, -0.429f, -1.252f, -0.509f, -1.928f)
            lineToRelative(-2.747f, 0.003f)
            curveToRelative(0.099f, 1.172f, 0.408f, 2.281f, 0.883f, 3.298f)
            lineTo(4.58f, 14.624f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcLoopLgsIconPreview() {
    Icon(
        imageVector = IcLoopLgs,
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
<g id="ic_loop_lgs">
	<g display="inline">
		<path fill="#00C03E" d="M5.437,7.639c0.401-0.533,0.875-1.006,1.409-1.405L5.468,3.852C4.528,4.509,3.71,5.327,3.052,6.266
			L5.437,7.639z"/>
		<path fill="#00C03E" d="M8.299,5.388c0.603-0.257,1.251-0.429,1.927-0.509l-0.003-2.747C9.052,2.231,7.943,2.54,6.926,3.016
			L8.299,5.388z"/>
		<path fill="#00C03E" d="M11.903,2.153l0.003,2.728c0.741,0.091,1.448,0.296,2.1,0.598c0.32,0.148,0.629,0.316,0.921,0.508
			l0.002-0.002l-0.346-1.755l1.845-0.529C15.11,2.833,13.563,2.298,11.903,2.153z"/>
		<path fill="#00C03E" d="M4.074,11.015C4.156,10.34,4.329,9.693,4.588,9.09L2.214,7.722c-0.477,1.017-0.788,2.125-0.888,3.296
			L4.074,11.015z"/>
		<path fill="#00C03E" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,3.888-3.152,7.039-7.039,7.039c-2.307,0-4.348-1.114-5.632-2.829l-2.382,1.378
			c1.767,2.529,4.695,4.186,8.013,4.186c5.399,0,9.775-4.376,9.775-9.775c0-0.747-0.091-1.471-0.25-2.17
			c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
		<path fill="#00C03E" d="M4.58,14.624c-0.257-0.604-0.429-1.252-0.509-1.928l-2.747,0.003c0.099,1.172,0.408,2.281,0.883,3.298
			L4.58,14.624z"/>
	</g>
</g>
</svg>
 */