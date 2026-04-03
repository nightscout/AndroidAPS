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
 * Icon for Automation Plugin.
 *
 * replacing ic_automation
 *
 * Bounding box: x: 2.7-21.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginAutomation: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAutomation",
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
            moveTo(16.114f, 12.514f)
            curveToRelative(-2.839f, 0f, -5.143f, 2.304f, -5.143f, 5.143f)
            reflectiveCurveToRelative(2.304f, 5.143f, 5.143f, 5.143f)
            reflectiveCurveToRelative(5.143f, -2.304f, 5.143f, -5.143f)
            reflectiveCurveTo(18.953f, 12.514f, 16.114f, 12.514f)
            close()

            moveTo(17.811f, 20.074f)
            lineTo(15.6f, 17.863f)
            verticalLineToRelative(-3.291f)
            horizontalLineToRelative(1.029f)
            verticalLineToRelative(2.87f)
            lineToRelative(1.903f, 1.903f)
            lineTo(17.811f, 20.074f)
            close()

            moveTo(17.143f, 3.257f)
            horizontalLineToRelative(-3.271f)
            curveTo(13.44f, 2.064f, 12.309f, 1.2f, 10.971f, 1.2f)
            reflectiveCurveTo(8.503f, 2.064f, 8.071f, 3.257f)
            horizontalLineTo(4.8f)
            curveToRelative(-1.131f, 0f, -2.057f, 0.926f, -2.057f, 2.057f)
            verticalLineToRelative(15.429f)
            curveToRelative(0f, 1.131f, 0.926f, 2.057f, 2.057f, 2.057f)
            horizontalLineToRelative(6.285f)
            curveToRelative(-0.607f, -0.586f, -1.101f, -1.286f, -1.461f, -2.057f)
            horizontalLineTo(4.8f)
            verticalLineTo(5.314f)
            horizontalLineToRelative(2.057f)
            verticalLineTo(8.4f)
            horizontalLineToRelative(8.229f)
            verticalLineTo(5.314f)
            horizontalLineToRelative(2.057f)
            verticalLineToRelative(5.225f)
            curveToRelative(0.73f, 0.103f, 1.419f, 0.319f, 2.057f, 0.617f)
            verticalLineTo(5.314f)
            curveTo(19.2f, 4.183f, 18.274f, 3.257f, 17.143f, 3.257f)
            close()

            moveTo(10.971f, 5.314f)
            curveToRelative(-0.566f, 0f, -1.029f, -0.463f, -1.029f, -1.029f)
            curveToRelative(0f, -0.566f, 0.463f, -1.029f, 1.029f, -1.029f)
            curveToRelative(0.566f, 0f, 1.029f, 0.463f, 1.029f, 1.029f)
            curveTo(12f, 4.851f, 11.537f, 5.314f, 10.971f, 5.314f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginAutomationIconPreview() {
    Icon(
        imageVector = IcPluginAutomation,
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
<g id="ic_plugin_automation">
	<g id="Plguin_Automation" display="inline">
		<path fill="#FFFFFF" d="M16.114,12.514c-2.839,0-5.143,2.304-5.143,5.143s2.304,5.143,5.143,5.143s5.143-2.304,5.143-5.143
			S18.953,12.514,16.114,12.514z M17.811,20.074L15.6,17.863v-3.291h1.029v2.87l1.903,1.903L17.811,20.074z M17.143,3.257h-3.271
			C13.44,2.064,12.309,1.2,10.971,1.2S8.503,2.064,8.071,3.257H4.8c-1.131,0-2.057,0.926-2.057,2.057v15.429
			c0,1.131,0.926,2.057,2.057,2.057h6.285c-0.607-0.586-1.101-1.286-1.461-2.057H4.8V5.314h2.057V8.4h8.229V5.314h2.057v5.225
			c0.73,0.103,1.419,0.319,2.057,0.617V5.314C19.2,4.183,18.274,3.257,17.143,3.257z M10.971,5.314
			c-0.566,0-1.029-0.463-1.029-1.029c0-0.566,0.463-1.029,1.029-1.029C11.537,3.257,12,3.72,12,4.286
			C12,4.851,11.537,5.314,10.971,5.314z"/>
	</g>
</g>
</svg>
 */