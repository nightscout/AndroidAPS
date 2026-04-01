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
 * Icon for Random BG Plugin.
 *
 * replacing ic_dice
 *
 * Bounding box: x: 1.2-22.8, y: 3.0-21.0 (viewport: 24x24, ~90% width)
 */
val IcPluginRandomBg: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginRandomBg",
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
            moveTo(21.18f, 9.84f)
            horizontalLineToRelative(-4.007f)
            curveToRelative(0.428f, 0.999f, 0.24f, 2.201f, -0.574f, 3.015f)
            lineTo(12f, 17.453f)
            verticalLineToRelative(1.567f)
            curveToRelative(0f, 0.895f, 0.725f, 1.62f, 1.62f, 1.62f)
            horizontalLineToRelative(7.56f)
            curveToRelative(0.895f, 0f, 1.62f, -0.725f, 1.62f, -1.62f)
            verticalLineToRelative(-7.56f)
            curveTo(22.8f, 10.565f, 22.075f, 9.84f, 21.18f, 9.84f)
            close()

            moveTo(17.4f, 16.05f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(18.21f, 15.687f, 17.847f, 16.05f, 17.4f, 16.05f)
            close()

            moveTo(15.835f, 9.749f)
            lineTo(9.931f, 3.845f)
            curveToRelative(-0.647f, -0.647f, -1.695f, -0.647f, -2.342f, 0f)
            lineTo(1.685f, 9.749f)
            curveToRelative(-0.647f, 0.647f, -0.647f, 1.695f, 0f, 2.342f)
            lineToRelative(5.904f, 5.904f)
            curveToRelative(0.647f, 0.647f, 1.695f, 0.647f, 2.342f, 0f)
            lineToRelative(5.904f, -5.904f)
            curveTo(16.482f, 11.444f, 16.482f, 10.396f, 15.835f, 9.749f)
            lineTo(15.835f, 9.749f)
            close()

            moveTo(4.44f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(5.25f, 11.367f, 4.887f, 11.73f, 4.44f, 11.73f)
            close()

            moveTo(8.76f, 16.05f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 15.687f, 9.207f, 16.05f, 8.76f, 16.05f)
            close()

            moveTo(8.76f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 11.367f, 9.207f, 11.73f, 8.76f, 11.73f)
            close()

            moveTo(8.76f, 7.41f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 7.047f, 9.207f, 7.41f, 8.76f, 7.41f)
            close()

            moveTo(13.08f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(13.89f, 11.367f, 13.527f, 11.73f, 13.08f, 11.73f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginRandomBgIconPreview() {
    Icon(
        imageVector = IcPluginRandomBg,
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
<g id="ic_plugin_random_bg">
	<g id="Dices" display="inline">
		<path fill="#FFFFFF" d="M21.18,9.84h-4.007c0.428,0.999,0.24,2.201-0.574,3.015L12,17.453v1.567c0,0.895,0.725,1.62,1.62,1.62
			h7.56c0.895,0,1.62-0.725,1.62-1.62v-7.56C22.8,10.565,22.075,9.84,21.18,9.84z M17.4,16.05c-0.447,0-0.81-0.363-0.81-0.81
			c0-0.448,0.363-0.81,0.81-0.81s0.81,0.362,0.81,0.81C18.21,15.687,17.847,16.05,17.4,16.05z M15.835,9.749L9.931,3.845
			c-0.647-0.647-1.695-0.647-2.342,0L1.685,9.749c-0.647,0.647-0.647,1.695,0,2.342l5.904,5.904c0.647,0.647,1.695,0.647,2.342,0
			l5.904-5.904C16.482,11.444,16.482,10.396,15.835,9.749L15.835,9.749z M4.44,11.73c-0.447,0-0.81-0.363-0.81-0.81
			c0-0.448,0.363-0.81,0.81-0.81s0.81,0.362,0.81,0.81C5.25,11.367,4.887,11.73,4.44,11.73z M8.76,16.05
			c-0.447,0-0.81-0.363-0.81-0.81c0-0.448,0.363-0.81,0.81-0.81s0.81,0.362,0.81,0.81C9.57,15.687,9.207,16.05,8.76,16.05z
			 M8.76,11.73c-0.447,0-0.81-0.363-0.81-0.81c0-0.448,0.363-0.81,0.81-0.81s0.81,0.362,0.81,0.81
			C9.57,11.367,9.207,11.73,8.76,11.73z M8.76,7.41c-0.447,0-0.81-0.363-0.81-0.81c0-0.448,0.363-0.81,0.81-0.81
			S9.57,6.152,9.57,6.6C9.57,7.047,9.207,7.41,8.76,7.41z M13.08,11.73c-0.447,0-0.81-0.363-0.81-0.81c0-0.448,0.363-0.81,0.81-0.81
			s0.81,0.362,0.81,0.81C13.89,11.367,13.527,11.73,13.08,11.73z"/>
	</g>
</g>
</svg>
 */