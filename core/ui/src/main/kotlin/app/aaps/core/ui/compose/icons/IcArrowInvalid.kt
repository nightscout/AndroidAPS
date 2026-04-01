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
 * Icon for Invalid BG Value.
 *
 * Bounding box: x: 1.2-22.8, y: 3.0-21.0 (viewport: 24x24, ~90% width)
 */
val IcArrowInvalid: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcInvalid",
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
            // Premier cercle
            moveTo(17.633f, 19.781f)
            curveToRelative(0.667f, 0f, 1.208f, -0.541f, 1.208f, -1.208f)
            curveToRelative(0f, -0.667f, -0.541f, -1.208f, -1.208f, -1.208f)
            curveToRelative(-0.667f, 0f, -1.208f, 0.541f, -1.208f, 1.208f)
            curveTo(16.425f, 19.24f, 16.966f, 19.781f, 17.633f, 19.781f)
            close()

            // Premier chemin
            moveTo(17.582f, 16.878f)
            curveToRelative(-0.483f, 0f, -0.874f, -0.392f, -0.874f, -0.875f)
            curveToRelative(0f, -3.143f, 1.465f, -4.24f, 2.643f, -5.122f)
            curveToRelative(0.987f, -0.739f, 1.7f, -1.273f, 1.7f, -3.001f)
            curveToRelative(0f, -2.382f, -2.288f, -3.123f, -3.122f, -3.123f)
            curveToRelative(-1.796f, 0f, -3.246f, 1.076f, -3.979f, 2.952f)
            curveToRelative(-0.176f, 0.45f, -0.685f, 0.673f, -1.133f, 0.496f)
            curveToRelative(-0.45f, -0.176f, -0.672f, -0.683f, -0.496f, -1.133f)
            curveToRelative(0.994f, -2.545f, 3.09f, -4.064f, 5.608f, -4.064f)
            curveToRelative(1.964f, 0f, 4.871f, 1.548f, 4.871f, 4.871f)
            curveToRelative(0f, 2.603f, -1.331f, 3.599f, -2.401f, 4.4f)
            curveToRelative(-1.086f, 0.813f, -1.942f, 1.454f, -1.942f, 3.722f)
            curveTo(18.457f, 16.487f, 18.065f, 16.878f, 17.582f, 16.878f)
            close()

            // Deuxième cercle
            moveTo(6.572f, 19.781f)
            curveToRelative(0.667f, 0f, 1.208f, -0.541f, 1.208f, -1.208f)
            curveToRelative(0f, -0.667f, -0.541f, -1.208f, -1.208f, -1.208f)
            curveToRelative(-0.667f, 0f, -1.208f, 0.541f, -1.208f, 1.208f)
            curveTo(5.364f, 19.24f, 5.905f, 19.781f, 6.572f, 19.781f)
            close()

            // Deuxième chemin
            moveTo(6.521f, 16.878f)
            curveToRelative(-0.483f, 0f, -0.874f, -0.392f, -0.874f, -0.875f)
            curveToRelative(0f, -3.143f, 1.465f, -4.24f, 2.643f, -5.122f)
            curveToRelative(0.987f, -0.739f, 1.7f, -1.273f, 1.7f, -3.001f)
            curveToRelative(0f, -2.382f, -2.288f, -3.123f, -3.122f, -3.123f)
            curveToRelative(-1.796f, 0f, -3.246f, 1.076f, -3.979f, 2.952f)
            curveToRelative(-0.176f, 0.45f, -0.685f, 0.673f, -1.133f, 0.496f)
            curveTo(1.306f, 8.031f, 1.085f, 7.525f, 1.26f, 7.075f)
            curveTo(2.254f, 4.53f, 4.35f, 3.01f, 6.868f, 3.01f)
            curveToRelative(1.964f, 0f, 4.871f, 1.548f, 4.871f, 4.871f)
            curveToRelative(0f, 2.603f, -1.331f, 3.599f, -2.401f, 4.4f)
            curveToRelative(-1.086f, 0.813f, -1.942f, 1.454f, -1.942f, 3.722f)
            curveTo(7.395f, 16.487f, 7.004f, 16.878f, 6.521f, 16.878f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowInvalidIconPreview() {
    Icon(
        imageVector = IcArrowInvalid,
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
<g id="ic_arrow_invalid">
	<g display="inline">
		<g>
			<circle fill="#36FF00" cx="17.633" cy="19.781" r="1.208"/>
			<path fill="#36FF00" d="M17.582,16.878c-0.483,0-0.874-0.392-0.874-0.875c0-3.143,1.465-4.24,2.643-5.122
				c0.987-0.739,1.7-1.273,1.7-3.001c0-2.382-2.288-3.123-3.122-3.123c-1.796,0-3.246,1.076-3.979,2.952
				c-0.176,0.45-0.685,0.673-1.133,0.496c-0.45-0.176-0.672-0.683-0.496-1.133c0.994-2.545,3.09-4.064,5.608-4.064
				c1.964,0,4.871,1.548,4.871,4.871c0,2.603-1.331,3.599-2.401,4.4c-1.086,0.813-1.942,1.454-1.942,3.722
				C18.457,16.487,18.065,16.878,17.582,16.878z"/>
		</g>
		<g>
			<circle fill="#36FF00" cx="6.572" cy="19.781" r="1.208"/>
			<path fill="#36FF00" d="M6.521,16.878c-0.483,0-0.874-0.392-0.874-0.875c0-3.143,1.465-4.24,2.643-5.122
				c0.987-0.739,1.7-1.273,1.7-3.001c0-2.382-2.288-3.123-3.122-3.123c-1.796,0-3.246,1.076-3.979,2.952
				c-0.176,0.45-0.685,0.673-1.133,0.496C1.306,8.031,1.085,7.525,1.26,7.075C2.254,4.53,4.35,3.01,6.868,3.01
				c1.964,0,4.871,1.548,4.871,4.871c0,2.603-1.331,3.599-2.401,4.4c-1.086,0.813-1.942,1.454-1.942,3.722
				C7.395,16.487,7.004,16.878,6.521,16.878z"/>
		</g>
	</g>
</g>
</svg>
 */