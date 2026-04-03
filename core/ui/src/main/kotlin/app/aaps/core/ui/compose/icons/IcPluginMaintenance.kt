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
 * Icon for Maintenance Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginMaintenance: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMaintenance",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(11.195f, 9.574f)
            curveToRelative(0.559f, 1.506f, 1.709f, 2.721f, 3.164f, 3.354f)
            curveToRelative(-0.041f, 0.405f, -0.217f, 0.798f, -0.527f, 1.107f)
            lineToRelative(-8.171f, 8.171f)
            curveToRelative(-0.715f, 0.715f, -1.876f, 0.715f, -2.591f, 0f)
            lineToRelative(-1.335f, -1.335f)
            curveToRelative(-0.715f, -0.715f, -0.715f, -1.876f, 0f, -2.591f)
            lineToRelative(8.171f, -8.171f)
            curveTo(10.263f, 9.754f, 10.729f, 9.576f, 11.195f, 9.574f)
            close()

            moveTo(22.798f, 7.029f)
            curveTo(22.8f, 7.071f, 22.8f, 7.114f, 22.8f, 7.156f)
            curveToRelative(0f, 3.256f, -2.594f, 5.9f, -5.789f, 5.9f)
            reflectiveCurveToRelative(-5.789f, -2.644f, -5.789f, -5.9f)
            curveToRelative(0f, -3.256f, 2.594f, -5.899f, 5.789f, -5.899f)
            curveToRelative(1.386f, 0f, 2.659f, 0.497f, 3.656f, 1.327f)
            lineToRelative(-4.452f, 2.651f)
            verticalLineToRelative(2.91f)
            lineToRelative(2.562f, 1.355f)
            lineToRelative(0.032f, 0.053f)
            lineToRelative(0.031f, -0.02f)
            lineToRelative(0.037f, 0.02f)
            lineToRelative(0.01f, -0.05f)
            lineTo(22.798f, 7.029f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginMaintenanceIconPreview() {
    Icon(
        imageVector = IcPluginMaintenance,
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
<g id="ic_plugin_maintenance">
	<g id="Maintenance" display="inline">
		<path fill="#FFFFFF" d="M11.195,9.574c0.559,1.506,1.709,2.721,3.164,3.354c-0.041,0.405-0.217,0.798-0.527,1.107l-8.171,8.171
			c-0.715,0.715-1.876,0.715-2.591,0l-1.335-1.335c-0.715-0.715-0.715-1.876,0-2.591l8.171-8.171
			C10.263,9.754,10.729,9.576,11.195,9.574z"/>
		<path fill="#FFFFFF" d="M22.798,7.029C22.8,7.071,22.8,7.114,22.8,7.156c0,3.256-2.594,5.9-5.789,5.9s-5.789-2.644-5.789-5.9
			c0-3.256,2.594-5.899,5.789-5.899c1.386,0,2.659,0.497,3.656,1.327l-4.452,2.651v2.91l2.562,1.355l0.032,0.053l0.031-0.02
			l0.037,0.02l0.01-0.05L22.798,7.029z"/>
	</g>
</g>
</svg>
 */