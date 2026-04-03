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
 * Icon for Cannula change treatment type.
 * Represents infusion set change entries.
 *
 * replacing ic_cp_pump_cannula
 *
 * Bounding box: x: 1.2-22.8, y: 6.3-19.5 (viewport: 24x24, ~90% width)
 */
val IcCannulaChange: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCannulaChange",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF67DFE8)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(22.768f, 7.141f)
            curveToRelative(-0.137f, -0.5f, -0.672f, -0.788f, -1.194f, -0.645f)
            lineToRelative(-2.346f, 0.645f)
            curveToRelative(-0.3f, -0.843f, -1.201f, -1.327f, -2.082f, -1.085f)
            lineToRelative(-2.776f, 0.763f)
            curveToRelative(-0.881f, 0.242f, -1.407f, 1.118f, -1.235f, 1.996f)
            lineTo(10.788f, 9.46f)
            curveToRelative(-0.46f, 0.127f, -0.738f, 0.548f, -0.708f, 0.988f)
            curveToRelative(-1.565f, 0.406f, -2.803f, 0.062f, -3.785f, -0.229f)
            curveToRelative(-0.966f, -0.286f, -1.801f, -0.533f, -2.504f, 0.14f)
            curveToRelative(-1.207f, 1.157f, -0.341f, 2.649f, 0.355f, 3.849f)
            curveToRelative(0.462f, 0.796f, 0.94f, 1.621f, 0.707f, 2.123f)
            curveToRelative(-0.479f, 1.038f, -2.519f, 1.041f, -3.265f, 0.954f)
            curveToRelative(-0.187f, -0.02f, -0.362f, 0.113f, -0.385f, 0.303f)
            curveToRelative(-0.022f, 0.19f, 0.113f, 0.361f, 0.303f, 0.383f)
            curveToRelative(0.079f, 0.009f, 1.27f, 0.14f, 2.357f, -0.158f)
            curveToRelative(0.677f, -0.186f, 1.314f, -0.54f, 1.618f, -1.193f)
            curveToRelative(0.384f, -0.827f, -0.16f, -1.764f, -0.736f, -2.757f)
            curveToRelative(-0.779f, -1.344f, -1.233f, -2.279f, -0.475f, -3.006f)
            curveToRelative(0.393f, -0.377f, 0.897f, -0.251f, 1.825f, 0.024f)
            curveToRelative(1.042f, 0.308f, 2.445f, 0.746f, 4.314f, 0.198f)
            curveToRelative(0.236f, 0.19f, 0.552f, 0.28f, 0.876f, 0.191f)
            lineToRelative(5.196f, -1.428f)
            lineToRelative(1.861f, 6.773f)
            lineToRelative(0.118f, -1.11f)
            lineToRelative(-1.586f, -5.771f)
            lineToRelative(5.196f, -1.428f)
            curveTo(22.593f, 8.162f, 22.905f, 7.64f, 22.768f, 7.141f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCannulaChangeIconPreview() {
    Icon(
        imageVector = IcCannulaChange,
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
<g id="ic_canula_change">
	<path display="inline" fill="#67DFE8" d="M22.768,7.141c-0.137-0.5-0.672-0.788-1.194-0.645l-2.346,0.645
		c-0.3-0.843-1.201-1.327-2.082-1.085l-2.776,0.763c-0.881,0.242-1.407,1.118-1.235,1.996L10.788,9.46
		c-0.46,0.127-0.738,0.548-0.708,0.988c-1.565,0.406-2.803,0.062-3.785-0.229c-0.966-0.286-1.801-0.533-2.504,0.14
		c-1.207,1.157-0.341,2.649,0.355,3.849c0.462,0.796,0.94,1.621,0.707,2.123c-0.479,1.038-2.519,1.041-3.265,0.954
		c-0.187-0.02-0.362,0.113-0.385,0.303c-0.022,0.19,0.113,0.361,0.303,0.383c0.079,0.009,1.27,0.14,2.357-0.158
		c0.677-0.186,1.314-0.54,1.618-1.193c0.384-0.827-0.16-1.764-0.736-2.757c-0.779-1.344-1.233-2.279-0.475-3.006
		c0.393-0.377,0.897-0.251,1.825,0.024c1.042,0.308,2.445,0.746,4.314,0.198c0.236,0.19,0.552,0.28,0.876,0.191l5.196-1.428
		l1.861,6.773l0.118-1.11l-1.586-5.771l5.196-1.428C22.593,8.162,22.905,7.64,22.768,7.141z"/>
</g>
</svg>
 */