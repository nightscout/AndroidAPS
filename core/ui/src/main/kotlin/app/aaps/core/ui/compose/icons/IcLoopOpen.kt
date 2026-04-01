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
 * Icon for Open Loop.
 * Represents open loop insulin delivery mode.
 *
 * Bounding box: x: 1.3-22.7, y: 2.1-21.9 (viewport: 24x24, ~90% width)
 */
val IcLoopOpen: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopOpen",
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

            moveTo(2.214f, 7.722f)
            curveToRelative(-0.477f, 1.017f, -0.788f, 2.125f, -0.888f, 3.296f)
            lineToRelative(2.749f, -0.003f)
            curveTo(4.156f, 10.34f, 4.329f, 9.693f, 4.588f, 9.09f)
            lineTo(2.214f, 7.722f)
            close()

            moveTo(19.907f, 7.733f)
            lineToRelative(-2.372f, 1.373f)
            curveToRelative(0.258f, 0.604f, 0.429f, 1.252f, 0.509f, 1.928f)
            lineToRelative(2.747f, -0.003f)
            curveTo(20.691f, 9.86f, 20.383f, 8.75f, 19.907f, 7.733f)
            close()

            moveTo(16.689f, 7.654f)
            lineToRelative(2.382f, -1.378f)
            curveToRelative(-0.657f, -0.94f, -1.475f, -1.758f, -2.414f, -2.416f)
            lineToRelative(-1.374f, 2.385f)
            curveTo(15.816f, 6.646f, 16.289f, 7.12f, 16.689f, 7.654f)
            close()

            moveTo(18.041f, 12.714f)
            curveToRelative(-0.42f, 3.486f, -3.384f, 6.19f, -6.983f, 6.19f)
            curveToRelative(-3.606f, 0f, -6.574f, -2.713f, -6.986f, -6.209f)
            lineToRelative(-2.747f, 0.003f)
            curveToRelative(0.424f, 5.008f, 4.616f, 8.942f, 9.733f, 8.942f)
            curveToRelative(5.113f, 0f, 9.303f, -3.927f, 9.732f, -8.929f)
            lineTo(18.041f, 12.714f)
            close()

            moveTo(8.299f, 5.388f)
            curveToRelative(0.603f, -0.257f, 1.251f, -0.429f, 1.927f, -0.509f)
            lineToRelative(-0.003f, -2.747f)
            curveTo(9.052f, 2.231f, 7.943f, 2.54f, 6.926f, 3.016f)
            lineTo(8.299f, 5.388f)
            close()

            moveTo(11.906f, 4.882f)
            curveToRelative(0.676f, 0.081f, 1.323f, 0.255f, 1.926f, 0.514f)
            lineTo(15.2f, 3.021f)
            curveToRelative(-1.017f, -0.477f, -2.125f, -0.788f, -3.297f, -0.888f)
            lineTo(11.906f, 4.882f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcLoopOpenIconPreview() {
    Icon(
        imageVector = IcLoopOpen,
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
<g id="ic_loop_open">
	<g display="inline">
		<path fill="#4983D7" d="M5.437,7.639c0.401-0.533,0.875-1.006,1.409-1.405L5.468,3.852C4.528,4.509,3.71,5.327,3.052,6.266
			L5.437,7.639z"/>
		<path fill="#4983D7" d="M2.214,7.722c-0.477,1.017-0.788,2.125-0.888,3.296l2.749-0.003C4.156,10.34,4.329,9.693,4.588,9.09
			L2.214,7.722z"/>
		<path fill="#4983D7" d="M19.907,7.733l-2.372,1.373c0.258,0.604,0.429,1.252,0.509,1.928l2.747-0.003
			C20.691,9.86,20.383,8.75,19.907,7.733z"/>
		<path fill="#4983D7" d="M16.689,7.654l2.382-1.378c-0.657-0.94-1.475-1.758-2.414-2.416l-1.374,2.385
			C15.816,6.646,16.289,7.12,16.689,7.654z"/>
		<path fill="#4983D7" d="M18.041,12.714c-0.42,3.486-3.384,6.19-6.983,6.19c-3.606,0-6.574-2.713-6.986-6.209l-2.747,0.003
			c0.424,5.008,4.616,8.942,9.733,8.942c5.113,0,9.303-3.927,9.732-8.929L18.041,12.714z"/>
		<path fill="#4983D7" d="M8.299,5.388c0.603-0.257,1.251-0.429,1.927-0.509l-0.003-2.747C9.052,2.231,7.943,2.54,6.926,3.016
			L8.299,5.388z"/>
		<path fill="#4983D7" d="M11.906,4.882c0.676,0.081,1.323,0.255,1.926,0.514L15.2,3.021c-1.017-0.477-2.125-0.788-3.297-0.888
			L11.906,4.882z"/>
	</g>
</g>
</svg>
 */