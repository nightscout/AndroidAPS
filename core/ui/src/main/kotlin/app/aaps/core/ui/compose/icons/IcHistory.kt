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
 * Icon for History Browser.
 * Represents historical data.
 *
 * replaces ic_pump_history
 *
 * Bounding box: x: 1.2-22.8, y: 2.4-21.5 (viewport: 24x24, ~90% width)
 */
val IcHistory: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcHistory",
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
            moveTo(13.198f, 2.399f)
            curveToRelative(-5.107f, 0f, -9.283f, 4.011f, -9.573f, 9.047f)
            lineTo(2.529f, 10.35f)
            curveToRelative(-0.305f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
            curveToRelative(-0.304f, 0.305f, -0.304f, 0.797f, 0f, 1.101f)
            lineToRelative(2.397f, 2.396f)
            curveToRelative(0.152f, 0.151f, 0.352f, 0.228f, 0.551f, 0.228f)
            curveToRelative(0.199f, 0f, 0.399f, -0.076f, 0.551f, -0.228f)
            lineToRelative(2.396f, -2.396f)
            curveToRelative(0.304f, -0.304f, 0.304f, -0.797f, 0f, -1.101f)
            curveToRelative(-0.304f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
            lineToRelative(-1.036f, 1.036f)
            curveToRelative(0.316f, -4.149f, 3.785f, -7.431f, 8.013f, -7.431f)
            curveToRelative(4.436f, 0f, 8.045f, 3.609f, 8.045f, 8.045f)
            curveToRelative(0f, 4.436f, -3.609f, 8.045f, -8.045f, 8.045f)
            curveToRelative(-2.19f, 0f, -4.239f, -0.869f, -5.77f, -2.448f)
            curveToRelative(-0.3f, -0.308f, -0.793f, -0.315f, -1.101f, -0.017f)
            curveToRelative(-0.309f, 0.299f, -0.316f, 0.793f, -0.017f, 1.101f)
            curveToRelative(1.827f, 1.883f, 4.273f, 2.92f, 6.888f, 2.92f)
            curveToRelative(5.294f, 0f, 9.602f, -4.307f, 9.602f, -9.602f)
            reflectiveCurveTo(18.493f, 2.399f, 13.198f, 2.399f)
            close()

            moveTo(13.198f, 12.778f)
            horizontalLineToRelative(4.348f)
            curveToRelative(0.43f, 0f, 0.778f, -0.349f, 0.778f, -0.778f)
            curveToRelative(0f, -0.43f, -0.348f, -0.778f, -0.778f, -0.778f)
            horizontalLineToRelative(-3.57f)
            verticalLineTo(6.202f)
            curveToRelative(0f, -0.43f, -0.349f, -0.778f, -0.778f, -0.778f)
            reflectiveCurveToRelative(-0.778f, 0.348f, -0.778f, 0.777f)
            verticalLineTo(12f)
            curveTo(12.42f, 12.429f, 12.769f, 12.778f, 13.198f, 12.778f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcHistoryIconPreview() {
    Icon(
        imageVector = IcHistory,
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
<g id="ic_history">
	<g display="inline">
		<path fill="#67DFE8" d="M13.198,2.399c-5.107,0-9.283,4.011-9.573,9.047L2.529,10.35c-0.305-0.304-0.797-0.304-1.101,0
			c-0.304,0.305-0.304,0.797,0,1.101l2.397,2.396c0.152,0.151,0.352,0.228,0.551,0.228c0.199,0,0.399-0.076,0.551-0.228l2.396-2.396
			c0.304-0.304,0.304-0.797,0-1.101s-0.797-0.304-1.101,0l-1.036,1.036c0.316-4.149,3.785-7.431,8.013-7.431
			c4.436,0,8.045,3.609,8.045,8.045c0,4.436-3.609,8.045-8.045,8.045c-2.19,0-4.239-0.869-5.77-2.448
			c-0.3-0.308-0.793-0.315-1.101-0.017c-0.309,0.299-0.316,0.793-0.017,1.101c1.827,1.883,4.273,2.92,6.888,2.92
			c5.294,0,9.602-4.307,9.602-9.602S18.493,2.399,13.198,2.399z"/>
		<path fill="#67DFE8" d="M13.198,12.778h4.348c0.43,0,0.778-0.349,0.778-0.778c0-0.43-0.348-0.778-0.778-0.778h-3.57V6.202
			c0-0.43-0.349-0.778-0.778-0.778S12.42,5.773,12.42,6.202V12C12.42,12.429,12.769,12.778,13.198,12.778z"/>
	</g>
</g>
</svg>
 */