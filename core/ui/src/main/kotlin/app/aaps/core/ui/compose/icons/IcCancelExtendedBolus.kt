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
 * Icon for Cancel Extended Bolus.
 * Represents cancellation of an extended/multi-wave insulin bolus.
 *
 * replaces ic_actions_cancel_extended_bolus
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-21.7 (viewport: 24x24, ~90% height)
 */
val IcCancelExtendedBolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCancelExtendedBolus",
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
            moveTo(19.538f, 9.528f)
            lineToRelative(0.378f, -0.731f)
            curveToRelative(0.192f, -0.371f, 0.046f, -0.828f, -0.326f, -1.02f)
            curveToRelative(-0.375f, -0.194f, -0.828f, -0.046f, -1.02f, 0.326f)
            lineToRelative(-0.378f, 0.732f)
            curveToRelative(-0.405f, -0.158f, -0.825f, -0.284f, -1.261f, -0.367f)
            verticalLineTo(7.828f)
            curveToRelative(0.48f, -0.065f, 0.857f, -0.46f, 0.857f, -0.959f)
            curveToRelative(0f, -0.544f, -0.44f, -0.984f, -0.984f, -0.984f)
            horizontalLineToRelative(-2.471f)
            curveToRelative(-0.544f, 0f, -0.984f, 0.441f, -0.984f, 0.984f)
            curveToRelative(0f, 0.498f, 0.376f, 0.894f, 0.857f, 0.959f)
            verticalLineToRelative(0.639f)
            curveToRelative(-3.339f, 0.64f, -5.871f, 3.578f, -5.871f, 7.1f)
            curveToRelative(0f, 3.988f, 3.245f, 7.233f, 7.233f, 7.233f)
            curveToRelative(3.988f, 0f, 7.233f, -3.245f, 7.233f, -7.233f)
            curveTo(22.8f, 13.045f, 21.5f, 10.823f, 19.538f, 9.528f)
            close()

            moveTo(15.567f, 21.732f)
            curveToRelative(-3.399f, 0f, -6.165f, -2.765f, -6.165f, -6.164f)
            reflectiveCurveToRelative(2.765f, -6.165f, 6.165f, -6.165f)
            reflectiveCurveToRelative(6.164f, 2.765f, 6.164f, 6.165f)
            reflectiveCurveTo(18.967f, 21.732f, 15.567f, 21.732f)
            close()

            moveTo(15.569f, 10.722f)
            curveToRelative(-0.092f, 0f, -0.181f, 0.037f, -0.246f, 0.102f)
            curveToRelative(-0.065f, 0.065f, -0.102f, 0.154f, -0.102f, 0.246f)
            verticalLineToRelative(4.486f)
            curveToRelative(0f, 0.086f, 0.031f, 0.168f, 0.088f, 0.232f)
            lineToRelative(2.979f, 3.354f)
            curveToRelative(0.062f, 0.069f, 0.148f, 0.111f, 0.241f, 0.116f)
            curveToRelative(0.006f, 0.001f, 0.014f, 0.001f, 0.02f, 0.001f)
            curveToRelative(0.086f, 0f, 0.168f, -0.031f, 0.232f, -0.089f)
            curveToRelative(1.037f, -0.925f, 1.632f, -2.238f, 1.632f, -3.603f)
            curveTo(20.413f, 12.897f, 18.24f, 10.723f, 15.569f, 10.722f)
            close()
        }

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
            moveTo(1.884f, 10.1f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            curveTo(1.133f, 9.633f, 1.133f, 9.201f, 1.401f, 8.934f)
            lineTo(8.934f, 1.4f)
            curveToRelative(0.268f, -0.267f, 0.699f, -0.267f, 0.967f, 0.001f)
            curveTo(10.168f, 1.668f, 10.168f, 2.1f, 9.9f, 2.368f)
            lineTo(2.367f, 9.9f)
            curveTo(2.233f, 10.033f, 2.058f, 10.1f, 1.884f, 10.1f)
            close()

            moveTo(9.418f, 10.1f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            lineTo(1.401f, 2.368f)
            curveTo(1.133f, 2.1f, 1.133f, 1.668f, 1.4f, 1.401f)
            curveTo(1.667f, 1.133f, 2.099f, 1.133f, 2.367f, 1.4f)
            lineTo(9.9f, 8.934f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, 0.001f, 0.966f)
            curveTo(9.768f, 10.033f, 9.592f, 10.1f, 9.418f, 10.1f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCancelExtendedBolusIconPreview() {
    Icon(
        imageVector = IcCancelExtendedBolus,
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
<g id="ic_cancel_extended_bolus">
	<g display="inline">
		<g>
			<path fill="#FEAF05" d="M19.538,9.528l0.378-0.731c0.192-0.371,0.046-0.828-0.326-1.02c-0.375-0.194-0.828-0.046-1.02,0.326
				l-0.378,0.732c-0.405-0.158-0.825-0.284-1.261-0.367V7.828c0.48-0.065,0.857-0.46,0.857-0.959c0-0.544-0.44-0.984-0.984-0.984
				h-2.471c-0.544,0-0.984,0.441-0.984,0.984c0,0.498,0.376,0.894,0.857,0.959v0.639c-3.339,0.64-5.871,3.578-5.871,7.1
				c0,3.988,3.245,7.233,7.233,7.233c3.988,0,7.233-3.245,7.233-7.233C22.8,13.045,21.5,10.823,19.538,9.528z M15.567,21.732
				c-3.399,0-6.165-2.765-6.165-6.164s2.765-6.165,6.165-6.165s6.164,2.765,6.164,6.165S18.967,21.732,15.567,21.732z"/>
			<path fill="#FEAF05" d="M15.569,10.722c-0.092,0-0.181,0.037-0.246,0.102c-0.065,0.065-0.102,0.154-0.102,0.246v4.486
				c0,0.086,0.031,0.168,0.088,0.232l2.979,3.354c0.062,0.069,0.148,0.111,0.241,0.116c0.006,0.001,0.014,0.001,0.02,0.001
				c0.086,0,0.168-0.031,0.232-0.089c1.037-0.925,1.632-2.238,1.632-3.603C20.413,12.897,18.24,10.723,15.569,10.722z"/>
		</g>
		<g>
			<path fill="#FDAE04" d="M1.884,10.1c-0.175,0-0.35-0.067-0.484-0.2C1.133,9.633,1.133,9.201,1.401,8.934L8.934,1.4
				c0.268-0.267,0.699-0.267,0.967,0.001C10.168,1.668,10.168,2.1,9.9,2.368L2.367,9.9C2.233,10.033,2.058,10.1,1.884,10.1z"/>
			<path fill="#FDAE04" d="M9.418,10.1c-0.175,0-0.35-0.067-0.484-0.2L1.401,2.368C1.133,2.1,1.133,1.668,1.4,1.401
				C1.667,1.133,2.099,1.133,2.367,1.4L9.9,8.934c0.267,0.267,0.267,0.699,0.001,0.966C9.768,10.033,9.592,10.1,9.418,10.1z"/>
		</g>
	</g>
</g>
</svg>
 */