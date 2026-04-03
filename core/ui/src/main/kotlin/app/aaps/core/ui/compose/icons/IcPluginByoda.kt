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
 * Icon for BYODA Plugin.
 * Represents Bring Your Own Device AAPS integration.
 *
 * replacing ic_dexcom_g6
 *
 * Bounding box: x: 5.2-18.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginByoda: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginByoda",
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
            moveTo(16.941f, 10.734f)
            curveToRelative(-0.014f, 3.375f, -0.228f, 6.749f, -0.636f, 10.124f)
            curveToRelative(-0.042f, 0.82f, -0.522f, 1.643f, -1.842f, 1.88f)
            curveToRelative(-1.628f, 0.082f, -3.278f, 0.082f, -4.949f, 0f)
            curveToRelative(-1.29f, -0.189f, -1.344f, -0.931f, -1.556f, -1.833f)
            curveToRelative(-0.779f, -3.312f, -0.843f, -6.732f, -0.898f, -10.137f)
            curveToRelative(0.945f, 1.64f, 2.8f, 2.394f, 4.931f, 2.394f)
            curveTo(14.137f, 13.162f, 16.002f, 12.392f, 16.941f, 10.734f)
            close()

            moveTo(12f, 1.2f)
            curveToRelative(2.94f, 0f, 5.326f, 2.386f, 5.326f, 5.325f)
            curveToRelative(0f, 2.939f, -2.387f, 5.326f, -5.326f, 5.326f)
            reflectiveCurveToRelative(-5.326f, -2.387f, -5.326f, -5.326f)
            reflectiveCurveTo(9.06f, 1.2f, 12f, 1.2f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginByodaIconPreview() {
    Icon(
        imageVector = IcPluginByoda,
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
<g id="ic_plugin_byoda">
	<g id="G6_2_" display="inline">
		<path fill="#FFFFFF" d="M16.941,10.734c-0.014,3.375-0.228,6.749-0.636,10.124c-0.042,0.82-0.522,1.643-1.842,1.88
			c-1.628,0.082-3.278,0.082-4.949,0c-1.29-0.189-1.344-0.931-1.556-1.833c-0.779-3.312-0.843-6.732-0.898-10.137
			c0.945,1.64,2.8,2.394,4.931,2.394C14.137,13.162,16.002,12.392,16.941,10.734z"/>
		<path fill="#FFFFFF" d="M12,1.2c2.94,0,5.326,2.386,5.326,5.325c0,2.939-2.387,5.326-5.326,5.326S6.674,9.465,6.674,6.526
			S9.06,1.2,12,1.2z"/>
	</g>
</g>
</svg>
 */