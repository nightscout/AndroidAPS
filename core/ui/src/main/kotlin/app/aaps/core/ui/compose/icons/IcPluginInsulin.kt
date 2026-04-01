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
 * Icon for Insulin Plugin.
 *
 * replacing ic_insulin
 *
 * Bounding box: x: 6.0-18.9, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginInsulin: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginInsulin",
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
            moveTo(10.685f, 18.578f)
            verticalLineTo(9.348f)
            horizontalLineToRelative(7.152f)
            verticalLineTo(8.085f)
            curveToRelative(0f, -1.215f, -1.219f, -1.945f, -2.713f, -2.2f)
            lineToRelative(-1.061f, -1.061f)
            verticalLineTo(3.385f)
            horizontalLineToRelative(0.784f)
            curveToRelative(0.276f, 0f, 0.5f, -0.224f, 0.5f, -0.5f)
            verticalLineTo(1.7f)
            curveToRelative(0f, -0.276f, -0.224f, -0.5f, -0.5f, -0.5f)
            horizontalLineTo(9.153f)
            curveToRelative(-0.276f, 0f, -0.5f, 0.224f, -0.5f, 0.5f)
            verticalLineToRelative(1.185f)
            curveToRelative(0f, 0.276f, 0.224f, 0.5f, 0.5f, 0.5f)
            horizontalLineToRelative(0.784f)
            verticalLineToRelative(1.439f)
            lineTo(8.876f, 5.885f)
            curveToRelative(-1.493f, 0.255f, -2.713f, 0.985f, -2.713f, 2.2f)
            verticalLineTo(20.6f)
            curveToRelative(0f, 1.215f, 0.985f, 2.2f, 2.2f, 2.2f)
            horizontalLineToRelative(7.273f)
            curveToRelative(1.215f, 0f, 2.2f, -0.985f, 2.2f, -2.2f)
            verticalLineToRelative(-2.022f)
            horizontalLineTo(10.685f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginInsulinIconPreview() {
    Icon(
        imageVector = IcPluginInsulin,
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
<g id="ic_plugin_insulin">
	<path display="inline" fill="#FFFFFF" d="M10.685,18.578V9.348h7.152V8.085c0-1.215-1.219-1.945-2.713-2.2l-1.061-1.061V3.385
		h0.784c0.276,0,0.5-0.224,0.5-0.5V1.7c0-0.276-0.224-0.5-0.5-0.5H9.153c-0.276,0-0.5,0.224-0.5,0.5v1.185
		c0,0.276,0.224,0.5,0.5,0.5h0.784v1.439L8.876,5.885c-1.493,0.255-2.713,0.985-2.713,2.2V20.6c0,1.215,0.985,2.2,2.2,2.2h7.273
		c1.215,0,2.2-0.985,2.2-2.2v-2.022H10.685z"/>
</g>
</svg>
 */