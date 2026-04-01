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
 * Icon for Dash or Eros Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 */
val IcPluginOmnipod: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOmnipod",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Pod
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
            moveTo(21.59f, 13.399f)
            curveToRelative(0.09f, -1.244f, 0.625f, -2.221f, 1.208f, -2.218f)
            lineToRelative(0.002f, -0.853f)
            curveToRelative(0.009f, -3.52f, -2.896f, -6.391f, -6.476f, -6.4f)
            lineToRelative(-13.031f, 0f)
            curveToRelative(-1.134f, 0f, -2.058f, 0.903f, -2.061f, 2.012f)
            lineTo(1.2f, 17.994f)
            curveToRelative(-0.003f, 1.143f, 0.944f, 2.075f, 2.112f, 2.078f)
            horizontalLineToRelative(12.977f)
            curveToRelative(2.816f, 0f, 5.284f, -1.73f, 6.166f, -4.311f)
            curveTo(21.886f, 15.665f, 21.503f, 14.629f, 21.59f, 13.399f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginOmnipodPreview() {
    Icon(
        imageVector = IcPluginOmnipod,
        contentDescription = "Omnipod Plugin Icon",
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
<g id="Plugin_Omnipod">
	<path id="Omnipod" fill="#FFFFFF" d="M21.59,13.399c0.09-1.244,0.625-2.221,1.208-2.218l0.002-0.853
		c0.009-3.52-2.896-6.391-6.476-6.4l-13.031,0c-1.134,0-2.058,0.903-2.061,2.012L1.2,17.994c-0.003,1.143,0.944,2.075,2.112,2.078
		h12.977c2.816,0,5.284-1.73,6.166-4.311C21.886,15.665,21.503,14.629,21.59,13.399z"/>
</g>
</svg>
 */