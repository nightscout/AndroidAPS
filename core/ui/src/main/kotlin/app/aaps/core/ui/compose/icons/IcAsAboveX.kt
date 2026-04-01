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
 * Icon for AutoSens Above Disabled.
 * Represents disabled AutoSensitivity above target range.
 *
 * Bounding box: x: 1.2-22.8, y: 5.7-18.3 (viewport: 24x24, ~90% width)
 */
val IcAsAboveX: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsAboveX",
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
            // Top arrow
            moveTo(17.22f, 5.669f)
            lineToRelative(-4.604f, 4.593f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineToRelative(-8.069f)
            horizontalLineToRelative(3.453f)
            lineTo(17.22f, 5.669f)
            close()

            // X mark
            moveTo(10.073f, 14.547f)
            lineToRelative(-2.532f, -2.532f)
            lineToRelative(2.532f, -2.532f)
            curveToRelative(0.354f, -0.354f, 0.354f, -0.93f, 0.001f, -1.283f)
            curveToRelative(-0.172f, -0.172f, -0.4f, -0.267f, -0.642f, -0.267f)
            curveToRelative(-0.242f, 0f, -0.47f, 0.095f, -0.642f, 0.266f)
            lineToRelative(-2.532f, 2.532f)
            lineTo(3.725f, 8.199f)
            curveToRelative(-0.172f, -0.171f, -0.4f, -0.266f, -0.642f, -0.266f)
            curveToRelative(-0.243f, 0f, -0.471f, 0.095f, -0.641f, 0.267f)
            curveTo(2.088f, 8.554f, 2.088f, 9.13f, 2.443f, 9.484f)
            lineToRelative(2.532f, 2.532f)
            lineToRelative(-2.532f, 2.532f)
            curveToRelative(-0.172f, 0.171f, -0.266f, 0.399f, -0.266f, 0.641f)
            curveToRelative(-0.001f, 0.243f, 0.094f, 0.471f, 0.266f, 0.643f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.242f, 0f, 0.47f, -0.094f, 0.642f, -0.266f)
            lineToRelative(2.532f, -2.531f)
            lineToRelative(2.533f, 2.532f)
            curveToRelative(0.172f, 0.171f, 0.399f, 0.265f, 0.641f, 0.265f)
            curveToRelative(0.243f, 0f, 0.471f, -0.094f, 0.642f, -0.266f)
            curveToRelative(0.172f, -0.171f, 0.266f, -0.399f, 0.266f, -0.642f)
            curveTo(10.34f, 14.946f, 10.245f, 14.718f, 10.073f, 14.547f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAsAboveXIconPreview() {
    Icon(
        imageVector = IcAsAboveX,
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
<g id="ic_as_above_x">
	<g>
		<path fill="#008585" d="M17.22,5.669l-4.604,4.593h3.453v8.069h2.302v-8.069h3.453L17.22,5.669z"/>
		<path fill="#008585" d="M10.073,14.547l-2.532-2.532l2.532-2.532c0.354-0.354,0.354-0.93,0.001-1.283
			c-0.172-0.172-0.4-0.267-0.642-0.267c-0.242,0-0.47,0.095-0.642,0.266l-2.532,2.532L3.725,8.199
			c-0.172-0.171-0.4-0.266-0.642-0.266c-0.243,0-0.471,0.095-0.641,0.267C2.088,8.554,2.088,9.13,2.443,9.484l2.532,2.532
			l-2.532,2.532c-0.172,0.171-0.266,0.399-0.266,0.641c-0.001,0.243,0.094,0.471,0.266,0.643c0.172,0.171,0.399,0.265,0.641,0.265
			s0.47-0.094,0.642-0.266l2.532-2.531l2.533,2.532c0.172,0.171,0.399,0.265,0.641,0.265c0.243,0,0.471-0.094,0.642-0.266
			c0.172-0.171,0.266-0.399,0.266-0.642C10.34,14.946,10.245,14.718,10.073,14.547z"/>
	</g>
</g>
</svg>
 */