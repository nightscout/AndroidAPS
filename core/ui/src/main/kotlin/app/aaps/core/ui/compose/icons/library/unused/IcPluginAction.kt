package app.aaps.core.ui.compose.icons.library.unused

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
 * Icon for Action Plugin.
 * Represents actions and quick commands.
 *
 * Bounding box: x: 4.0-20.5, y: 1.2-20.5 (viewport: 24x24, ~80% width)
 */
val IcPluginAction: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAction",
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
            moveTo(14.143f, 5.912f)
            curveToRelative(-0.518f, 0.895f, -0.207f, 2.054f, 0.688f, 2.573f)
            curveToRelative(0.895f, 0.518f, 2.054f, 0.207f, 2.573f, -0.688f)
            curveToRelative(0.518f, -0.895f, 0.207f, -2.054f, -0.688f, -2.573f)
            curveTo(15.821f, 4.706f, 14.661f, 5.016f, 14.143f, 5.912f)
            close()

            moveTo(15.622f, 10.068f)
            curveToRelative(0f, 0f, -1.536f, -0.886f, -2.45f, -1.414f)
            curveToRelative(-2.243f, -1.301f, -3.016f, -4.184f, -1.715f, -6.427f)
            lineToRelative(-1.63f, -0.942f)
            curveToRelative(-1.498f, 2.582f, -1.027f, 5.768f, 0.914f, 7.832f)
            lineToRelative(-4.853f, 8.406f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(1.414f, -2.45f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(-2.827f, 4.901f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(5.928f, -10.263f)
            curveToRelative(1.074f, 1.461f, 1.253f, 3.478f, 0.292f, 5.146f)
            lineToRelative(1.63f, 0.942f)
            curveTo(18.723f, 16.033f, 18.421f, 12.424f, 15.622f, 10.068f)
            close()

            moveTo(12.71f, 3.838f)
            curveToRelative(0.679f, 0.386f, 1.536f, 0.16f, 1.932f, -0.518f)
            curveToRelative(0.386f, -0.679f, 0.16f, -1.536f, -0.518f, -1.932f)
            curveToRelative(-0.679f, -0.386f, -1.536f, -0.16f, -1.932f, 0.518f)
            curveTo(11.806f, 2.585f, 12.032f, 3.452f, 12.71f, 3.838f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginActionIconPreview() {
    Icon(
        imageVector = IcPluginAction,
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
<g id="ic_plugin_action">
	<g id="Plugin_Action" display="inline">
		<path fill="#FFFFFF" d="M14.143,5.912c-0.518,0.895-0.207,2.054,0.688,2.573c0.895,0.518,2.054,0.207,2.573-0.688
			s0.207-2.054-0.688-2.573S14.661,5.016,14.143,5.912z"/>
		<path fill="#FFFFFF" d="M15.622,10.068c0,0-1.536-0.886-2.45-1.414c-2.243-1.301-3.016-4.184-1.715-6.427l-1.63-0.942
			c-1.498,2.582-1.027,5.768,0.914,7.832l-4.853,8.406l1.63,0.942l1.414-2.45l1.63,0.942l-2.827,4.901l1.63,0.942l5.928-10.263
			c1.074,1.461,1.253,3.478,0.292,5.146l1.63,0.942C18.723,16.033,18.421,12.424,15.622,10.068z"/>
		<path fill="#FFFFFF" d="M12.71,3.838c0.679,0.386,1.536,0.16,1.932-0.518c0.386-0.679,0.16-1.536-0.518-1.932
			c-0.679-0.386-1.536-0.16-1.932,0.518C11.806,2.585,12.032,3.452,12.71,3.838z"/>
	</g>
</g>
</svg>
 */