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
 * Icon for Activity Treatments.
 *
 * Bounding box: x: 3.1-20.9, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcActivityTreatments: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcActivityTreatments",
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
            moveTo(3.147f, 20.616f)
            verticalLineTo(3.384f)
            curveToRelative(0f, -1.206f, 0.979f, -2.184f, 2.184f, -2.184f)
            horizontalLineToRelative(10.851f)
            curveToRelative(1.206f, 0f, 2.184f, 0.979f, 2.184f, 2.184f)
            verticalLineToRelative(17.232f)
            curveToRelative(0f, 1.206f, -0.979f, 2.184f, -2.184f, 2.184f)
            horizontalLineTo(5.331f)
            curveTo(4.126f, 22.8f, 3.147f, 21.821f, 3.147f, 20.616f)
            close()

            moveTo(10.757f, 16.817f)
            horizontalLineToRelative(0.114f)
            curveToRelative(2.933f, 0f, 5.314f, -2.381f, 5.314f, -5.314f)
            curveToRelative(0f, -2.933f, -2.381f, -5.314f, -5.314f, -5.314f)
            curveToRelative(-0.019f, 0f, -0.038f, 0f, -0.057f, 0f)
            curveToRelative(-0.019f, 0f, -0.038f, 0f, -0.057f, 0f)
            curveToRelative(-2.933f, 0f, -5.314f, 2.381f, -5.314f, 5.314f)
            curveTo(5.443f, 14.436f, 7.824f, 16.817f, 10.757f, 16.817f)
            close()

            moveTo(14.865f, 12.706f)
            verticalLineTo(10.3f)
            horizontalLineToRelative(-7.99f)
            verticalLineToRelative(2.406f)
            horizontalLineTo(14.865f)
            close()

            moveTo(9.668f, 15.498f)
            lineToRelative(2.406f, 0f)
            lineToRelative(-0.001f, -7.99f)
            lineToRelative(-2.406f, 0f)
            lineTo(9.668f, 15.498f)
            close()

            moveTo(19.974f, 8.255f)
            verticalLineTo(7.508f)
            horizontalLineTo(18.57f)
            verticalLineToRelative(0.747f)
            horizontalLineTo(19.974f)
            close()

            moveTo(19.974f, 15.497f)
            verticalLineTo(14.75f)
            horizontalLineTo(18.57f)
            verticalLineToRelative(0.747f)
            horizontalLineTo(19.974f)
            close()

            moveTo(20.853f, 8.06f)
            curveToRelative(0f, 0.305f, -0.745f, 0.856f, -0.79f, 0.553f)
            curveToRelative(-0.054f, -0.358f, -0.484f, -0.535f, -0.791f, -0.553f)
            curveToRelative(-0.435f, -0.025f, 0.354f, -0.552f, 0.791f, -0.552f)
            curveTo(20.499f, 7.508f, 20.853f, 7.756f, 20.853f, 8.06f)
            close()

            moveTo(20.853f, 14.968f)
            verticalLineTo(8.036f)
            horizontalLineToRelative(-0.88f)
            verticalLineToRelative(6.932f)
            horizontalLineTo(20.853f)
            close()

            moveTo(20.853f, 14.914f)
            curveToRelative(0f, 0.322f, -0.354f, 0.584f, -0.79f, 0.584f)
            curveToRelative(-0.436f, 0f, -1.206f, -0.486f, -0.791f, -0.584f)
            curveToRelative(0.242f, -0.057f, 0.757f, -0.142f, 0.791f, -0.584f)
            curveTo(20.086f, 14.009f, 20.853f, 14.592f, 20.853f, 14.914f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcActivityTreatmentsIconPreview() {
    Icon(
        imageVector = IcActivityTreatments,
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
<g id="ic_activity_treatments">
	<g display="inline">
		<path fill="#FFFFFF" d="M3.147,20.616V3.384c0-1.206,0.979-2.184,2.184-2.184h10.851c1.206,0,2.184,0.979,2.184,2.184v17.232
			c0,1.206-0.979,2.184-2.184,2.184H5.331C4.126,22.8,3.147,21.821,3.147,20.616z M10.757,16.817h0.114
			c2.933,0,5.314-2.381,5.314-5.314c0-2.933-2.381-5.314-5.314-5.314c-0.019,0-0.038,0-0.057,0c-0.019,0-0.038,0-0.057,0
			c-2.933,0-5.314,2.381-5.314,5.314C5.443,14.436,7.824,16.817,10.757,16.817z"/>
		<path fill="#FFFFFF" d="M14.865,12.706V10.3h-7.99v2.406H14.865z"/>
		<path fill="#FFFFFF" d="M9.668,15.498l2.406,0l-0.001-7.99l-2.406,0L9.668,15.498z"/>
		<path fill="#FFFFFF" d="M19.974,8.255V7.508H18.57v0.747H19.974z"/>
		<path fill="#FFFFFF" d="M19.974,15.497V14.75H18.57v0.747H19.974z"/>
		<path fill="#FFFFFF" d="M20.853,8.06c0,0.305-0.745,0.856-0.79,0.553c-0.054-0.358-0.484-0.535-0.791-0.553
			c-0.435-0.025,0.354-0.552,0.791-0.552C20.499,7.508,20.853,7.756,20.853,8.06z"/>
		<path fill="#FFFFFF" d="M20.853,14.968V8.036h-0.88v6.932H20.853z"/>
		<path fill="#FFFFFF" d="M20.853,14.914c0,0.322-0.354,0.584-0.79,0.584c-0.436,0-1.206-0.486-0.791-0.584
			c0.242-0.057,0.757-0.142,0.791-0.584C20.086,14.009,20.853,14.592,20.853,14.914z"/>
	</g>
</g>
</svg>
 */