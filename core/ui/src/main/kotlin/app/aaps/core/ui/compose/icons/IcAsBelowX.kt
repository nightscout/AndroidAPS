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
 * Icon for AutoSens Below Disabled.
 * Represents disabled AutoSensitivity below target range.
 *
 * Bounding box: x: 2.1-22.0, y: 5.7-18.3 (viewport: 24x24, ~90% width)
 */
val IcAsBelowX: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsBelowX",
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
            // Bottom arrow
            moveTo(21.824f, 13.738f)
            horizontalLineToRelative(-3.453f)
            verticalLineTo(5.669f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(-3.453f)
            lineToRelative(4.604f, 4.593f)
            lineTo(21.824f, 13.738f)
            close()

            // X mark
            moveTo(10.073f, 9.453f)
            lineToRelative(-2.532f, 2.532f)
            lineToRelative(2.532f, 2.532f)
            curveToRelative(0.354f, 0.354f, 0.354f, 0.93f, 0.001f, 1.283f)
            curveToRelative(-0.172f, 0.172f, -0.4f, 0.267f, -0.642f, 0.267f)
            curveToRelative(-0.242f, 0f, -0.47f, -0.095f, -0.642f, -0.266f)
            lineToRelative(-2.532f, -2.532f)
            lineToRelative(-2.533f, 2.533f)
            curveToRelative(-0.172f, 0.171f, -0.4f, 0.266f, -0.642f, 0.266f)
            curveToRelative(-0.243f, 0f, -0.471f, -0.095f, -0.641f, -0.267f)
            curveToRelative(-0.354f, -0.353f, -0.354f, -0.929f, 0.001f, -1.283f)
            lineToRelative(2.532f, -2.532f)
            lineTo(2.443f, 9.453f)
            curveTo(2.271f, 9.282f, 2.176f, 9.054f, 2.176f, 8.812f)
            curveToRelative(0f, -0.244f, 0.094f, -0.472f, 0.267f, -0.643f)
            curveToRelative(0.172f, -0.171f, 0.399f, -0.265f, 0.641f, -0.265f)
            curveToRelative(0.242f, 0f, 0.47f, 0.094f, 0.642f, 0.266f)
            lineToRelative(2.532f, 2.531f)
            lineToRelative(2.533f, -2.532f)
            curveTo(8.962f, 7.998f, 9.19f, 7.904f, 9.432f, 7.904f)
            curveToRelative(0.243f, 0f, 0.471f, 0.094f, 0.642f, 0.266f)
            curveToRelative(0.172f, 0.171f, 0.266f, 0.399f, 0.266f, 0.642f)
            curveTo(10.34f, 9.054f, 10.245f, 9.282f, 10.073f, 9.453f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAsBelowXIconPreview() {
    Icon(
        imageVector = IcAsBelowX,
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
<g id="ic_as_below_x">
	<g>
		<path fill="#008585" d="M21.824,13.738h-3.453V5.669h-2.302v8.069h-3.453l4.604,4.593L21.824,13.738z"/>
		<path fill="#008585" d="M10.073,9.453l-2.532,2.532l2.532,2.532c0.354,0.354,0.354,0.93,0.001,1.283
			c-0.172,0.172-0.4,0.267-0.642,0.267c-0.242,0-0.47-0.095-0.642-0.266l-2.532-2.532l-2.533,2.533
			c-0.172,0.171-0.4,0.266-0.642,0.266c-0.243,0-0.471-0.095-0.641-0.267c-0.354-0.353-0.354-0.929,0.001-1.283l2.532-2.532
			L2.443,9.453C2.271,9.282,2.176,9.054,2.176,8.812C2.176,8.568,2.27,8.34,2.443,8.169c0.172-0.171,0.399-0.265,0.641-0.265
			s0.47,0.094,0.642,0.266l2.532,2.531l2.533-2.532C8.962,7.998,9.19,7.904,9.432,7.904c0.243,0,0.471,0.094,0.642,0.266
			c0.172,0.171,0.266,0.399,0.266,0.642C10.34,9.054,10.245,9.282,10.073,9.453z"/>
	</g>
</g>
</svg>
 */