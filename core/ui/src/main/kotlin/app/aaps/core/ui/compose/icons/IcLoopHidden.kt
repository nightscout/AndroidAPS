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
 * Icon for Hide Loop Information.
 * Mainly used to hide Loop information in UserEntry.
 *
 * Bounding box: x: 1.2-22.8, y: 2.1-21.8 (viewport: 24x24, ~90% width)
 */
val IcLoopHidden: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopHidden",
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
            moveTo(18.133f, 18.598f)
            lineToRelative(-1.934f, -1.934f)
            lineToRelative(0f, 0f)
            lineTo(5.882f, 6.347f)
            lineTo(4.325f, 4.79f)
            lineToRelative(0f, 0f)
            lineToRelative(-1.85f, -1.85f)
            lineTo(1.2f, 4.216f)
            lineToRelative(1.939f, 1.939f)
            curveTo(1.975f, 7.761f, 1.282f, 9.73f, 1.282f, 11.865f)
            curveToRelative(0f, 5.399f, 4.376f, 9.775f, 9.775f, 9.775f)
            curveToRelative(2.136f, 0f, 4.104f, -0.693f, 5.711f, -1.856f)
            lineToRelative(1.879f, 1.879f)
            lineToRelative(1.275f, -1.275f)
            lineTo(18.133f, 18.598f)
            lineTo(18.133f, 18.598f)
            close()

            moveTo(11.058f, 18.905f)
            curveToRelative(-3.888f, 0f, -7.039f, -3.152f, -7.039f, -7.039f)
            curveToRelative(0f, -1.378f, 0.405f, -2.656f, 1.091f, -3.74f)
            lineToRelative(9.688f, 9.688f)
            curveTo(13.714f, 18.499f, 12.436f, 18.905f, 11.058f, 18.905f)
            close()

            moveTo(11.058f, 4.826f)
            curveToRelative(1.054f, 0f, 2.051f, 0.238f, 2.949f, 0.654f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineToRelative(1.845f, -0.529f)
            curveToRelative(-1.542f, -1.017f, -3.386f, -1.612f, -5.371f, -1.612f)
            curveToRelative(-1.959f, 0f, -3.779f, 0.582f, -5.308f, 1.574f)
            lineToRelative(1.992f, 1.992f)
            curveTo(8.73f, 5.128f, 9.858f, 4.826f, 11.058f, 4.826f)
            close()

            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 1.199f, -0.302f, 2.328f, -0.831f, 3.316f)
            lineToRelative(1.992f, 1.992f)
            curveToRelative(0.992f, -1.529f, 1.574f, -3.35f, 1.574f, -5.308f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcLoopHiddenIconPreview() {
    Icon(
        imageVector = IcLoopHidden,
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
<g id="ic_loop_hidden">
	<g display="inline">
		<path fill="#FFFFFF" d="M18.133,18.598l-1.934-1.934l0,0L5.882,6.347L4.325,4.79l0,0l-1.85-1.85L1.2,4.216l1.939,1.939
			C1.975,7.761,1.282,9.73,1.282,11.865c0,5.399,4.376,9.775,9.775,9.775c2.136,0,4.104-0.693,5.711-1.856l1.879,1.879l1.275-1.275
			L18.133,18.598L18.133,18.598z M11.058,18.905c-3.888,0-7.039-3.152-7.039-7.039c0-1.378,0.405-2.656,1.091-3.74l9.688,9.688
			C13.714,18.499,12.436,18.905,11.058,18.905z"/>
		<path fill="#FFFFFF" d="M11.058,4.826c1.054,0,2.051,0.238,2.949,0.654c0.32,0.148,0.629,0.316,0.921,0.508l0.002-0.002
			l-0.346-1.755l1.845-0.529c-1.542-1.017-3.386-1.612-5.371-1.612c-1.959,0-3.779,0.582-5.308,1.574l1.992,1.992
			C8.73,5.128,9.858,4.826,11.058,4.826z"/>
		<path fill="#FFFFFF" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,1.199-0.302,2.328-0.831,3.316l1.992,1.992c0.992-1.529,1.574-3.35,1.574-5.308
			c0-0.747-0.091-1.471-0.25-2.17c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
	</g>
</g>
</svg>
 */