package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Site Rotation.
 * For Cannula or CGM site rotation.
 *
 * replaces ic_site_rotation
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcSiteRotation: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcSiteRotation",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(0f, 0f)
            horizontalLineTo(24f)
            verticalLineTo(24f)
            horizontalLineTo(0f)
            close()
        }

        path(
            fill = SolidColor(Color(0xFF67DFE8)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(8.128f, 5.493f)
            lineTo(7.22f, 3.968f)
            curveTo(4.489f, 5.6f, 2.649f, 8.576f, 2.642f, 11.981f)
            horizontalLineTo(1.2f)
            lineToRelative(2.33f, 4.035f)
            lineToRelative(2.33f, -4.035f)
            horizontalLineToRelative(-1.44f)
            curveTo(4.426f, 9.224f, 5.916f, 6.815f, 8.128f, 5.493f)
            close()

            moveTo(20.47f, 7.978f)
            lineToRelative(-2.33f, 4.035f)
            horizontalLineToRelative(1.44f)
            curveToRelative(-0.005f, 2.759f, -1.496f, 5.171f, -3.709f, 6.493f)
            lineToRelative(0.908f, 1.525f)
            curveToRelative(2.733f, -1.633f, 4.573f, -4.611f, 4.578f, -8.018f)
            horizontalLineTo(22.8f)
            lineTo(20.47f, 7.978f)
            close()

            moveTo(11.981f, 19.581f)
            curveToRelative(-2.757f, -0.007f, -5.166f, -1.497f, -6.488f, -3.709f)
            lineTo(3.968f, 16.78f)
            curveToRelative(1.632f, 2.731f, 4.608f, 4.571f, 8.013f, 4.578f)
            verticalLineTo(22.8f)
            lineToRelative(4.035f, -2.33f)
            lineToRelative(-4.035f, -2.33f)
            verticalLineTo(19.581f)
            close()
        }

        path(
            fill = SolidColor(Color(0xFF66DEE7)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.013f, 2.642f)
            verticalLineTo(1.2f)
            lineTo(7.978f, 3.53f)
            lineToRelative(4.035f, 2.33f)
            verticalLineToRelative(-1.44f)
            curveToRelative(2.76f, 0.005f, 5.171f, 1.496f, 6.493f, 3.709f)
            lineToRelative(1.525f, -0.908f)
            curveTo(18.398f, 4.487f, 15.421f, 2.647f, 12.013f, 2.642f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcSiteRotationIconPreview() {
    Icon(
        imageVector = IcSiteRotation,
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
<g id="ic_site_rotation">
	<rect x="0" width="24" height="24"/>
	<g display="inline">
		<path fill="#67DFE8" d="M8.128,5.493L7.22,3.968C4.489,5.6,2.649,8.576,2.642,11.981H1.2l2.33,4.035l2.33-4.035h-1.44
			C4.426,9.224,5.916,6.815,8.128,5.493z"/>
		<path fill="#67DFE8" d="M20.47,7.978l-2.33,4.035h1.44c-0.005,2.759-1.496,5.171-3.709,6.493l0.908,1.525
			c2.733-1.633,4.573-4.611,4.578-8.018H22.8L20.47,7.978z"/>
		<path fill="#67DFE8" d="M11.981,19.581c-2.757-0.007-5.166-1.497-6.488-3.709L3.968,16.78c1.632,2.731,4.608,4.571,8.013,4.578
			V22.8l4.035-2.33l-4.035-2.33V19.581z"/>
		<path fill="#66DEE7" d="M12.013,2.642V1.2L7.978,3.53l4.035,2.33v-1.44c2.76,0.005,5.171,1.496,6.493,3.709l1.525-0.908
			C18.398,4.487,15.421,2.647,12.013,2.642z"/>
	</g>
</g>
</svg>
 */