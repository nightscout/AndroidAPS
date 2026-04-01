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
 * Icon for High Temp Target.
 *
 * replaces ic_temptarget_high
 *
 * Bounding box: x: 1.2-22.8, y: 5.9-18.0 (viewport: 24x24, ~90% width)
 */
val IcTtHigh: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtHigh",
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
            moveTo(7f, 17.978f)
            curveToRelative(-0.006f, 0f, -0.011f, 0f, -0.017f, 0f)
            curveToRelative(-0.731f, -0.013f, -1.385f, -0.871f, -1.796f, -2.354f)
            curveToRelative(-0.299f, -1.082f, -0.57f, -2.209f, -0.832f, -3.301f)
            curveToRelative(-0.164f, -0.683f, -0.328f, -1.367f, -0.5f, -2.044f)
            lineTo(3.74f, 9.824f)
            curveTo(3.352f, 8.262f, 2.952f, 6.647f, 1.906f, 6.465f)
            curveToRelative(-0.122f, -0.021f, -0.203f, -0.137f, -0.182f, -0.259f)
            curveToRelative(0.021f, -0.121f, 0.136f, -0.198f, 0.259f, -0.181f)
            curveToRelative(1.332f, 0.232f, 1.769f, 1.991f, 2.191f, 3.692f)
            lineToRelative(0.113f, 0.454f)
            curveToRelative(0.172f, 0.678f, 0.337f, 1.364f, 0.502f, 2.05f)
            curveToRelative(0.261f, 1.088f, 0.532f, 2.211f, 0.829f, 3.286f)
            curveToRelative(0.338f, 1.222f, 0.877f, 2.017f, 1.374f, 2.027f)
            curveToRelative(0.003f, 0f, 0.006f, 0f, 0.009f, 0f)
            curveToRelative(0.465f, 0f, 0.951f, -0.69f, 1.333f, -1.895f)
            curveToRelative(0.197f, -0.622f, 0.366f, -1.292f, 0.53f, -1.94f)
            lineToRelative(0.13f, -0.512f)
            curveToRelative(0.145f, -0.563f, 0.281f, -1.135f, 0.418f, -1.706f)
            curveToRelative(0.28f, -1.17f, 0.57f, -2.38f, 0.923f, -3.501f)
            curveToRelative(0.391f, -1.242f, 0.975f, -1.93f, 1.644f, -1.935f)
            curveToRelative(0.003f, 0f, 0.006f, 0f, 0.009f, 0f)
            curveToRelative(0.671f, 0f, 1.269f, 0.683f, 1.684f, 1.925f)
            curveToRelative(0.292f, 0.875f, 0.54f, 1.817f, 0.778f, 2.73f)
            lineToRelative(0.186f, 0.705f)
            curveToRelative(0.155f, 0.581f, 0.303f, 1.169f, 0.451f, 1.757f)
            curveToRelative(0.252f, 1.005f, 0.514f, 2.044f, 0.808f, 3.025f)
            curveToRelative(0.241f, 0.809f, 0.651f, 1.324f, 1.068f, 1.345f)
            curveToRelative(0.375f, 0.045f, 0.766f, -0.381f, 1.062f, -1.094f)
            curveToRelative(0.285f, -0.684f, 0.547f, -1.482f, 0.799f, -2.436f)
            curveToRelative(0.35f, -1.329f, 0.681f, -2.676f, 1.013f, -4.024f)
            curveToRelative(0.153f, -0.622f, 0.306f, -1.244f, 0.461f, -1.863f)
            curveTo(20.593f, 6.929f, 21.15f, 6.248f, 22f, 6.028f)
            curveToRelative(0.117f, -0.029f, 0.24f, 0.042f, 0.272f, 0.161f)
            curveToRelative(0.031f, 0.12f, -0.041f, 0.241f, -0.161f, 0.272f)
            curveToRelative(-0.685f, 0.177f, -1.124f, 0.736f, -1.38f, 1.759f)
            curveToRelative(-0.155f, 0.619f, -0.308f, 1.241f, -0.46f, 1.862f)
            curveToRelative(-0.332f, 1.349f, -0.665f, 2.699f, -1.015f, 4.031f)
            curveToRelative(-0.258f, 0.975f, -0.525f, 1.791f, -0.819f, 2.495f)
            curveToRelative(-0.512f, 1.229f, -1.152f, 1.387f, -1.498f, 1.368f)
            curveToRelative(-0.62f, -0.032f, -1.171f, -0.654f, -1.474f, -1.664f)
            curveToRelative(-0.296f, -0.989f, -0.558f, -2.034f, -0.813f, -3.044f)
            curveToRelative(-0.147f, -0.586f, -0.295f, -1.171f, -0.448f, -1.751f)
            lineToRelative(-0.187f, -0.708f)
            curveToRelative(-0.236f, -0.905f, -0.481f, -1.84f, -0.769f, -2.701f)
            curveToRelative(-0.34f, -1.015f, -0.811f, -1.619f, -1.262f, -1.619f)
            curveToRelative(-0.002f, 0f, -0.003f, 0f, -0.004f, 0f)
            curveTo(11.536f, 6.493f, 11.079f, 7.1f, 10.76f, 8.112f)
            curveToRelative(-0.348f, 1.106f, -0.636f, 2.308f, -0.914f, 3.47f)
            curveToRelative(-0.138f, 0.574f, -0.275f, 1.147f, -0.42f, 1.713f)
            lineToRelative(-0.13f, 0.511f)
            curveToRelative(-0.166f, 0.655f, -0.337f, 1.332f, -0.538f, 1.966f)
            curveTo(8.182f, 17.59f, 7.487f, 17.978f, 7f, 17.978f)
            close()

            moveTo(22.055f, 12.745f)
            horizontalLineTo(17.04f)
            curveToRelative(-0.411f, 0f, -0.745f, -0.333f, -0.745f, -0.745f)
            verticalLineTo(9.034f)
            horizontalLineTo(7.72f)
            lineTo(7.72f, 12f)
            curveToRelative(0f, 0.411f, -0.333f, 0.745f, -0.744f, 0.745f)
            horizontalLineTo(1.944f)
            curveTo(1.533f, 12.745f, 1.2f, 12.411f, 1.2f, 12f)
            curveToRelative(0f, -0.411f, 0.333f, -0.744f, 0.744f, -0.744f)
            horizontalLineToRelative(4.286f)
            lineToRelative(0.001f, -2.966f)
            curveToRelative(0f, -0.411f, 0.333f, -0.744f, 0.744f, -0.744f)
            horizontalLineTo(17.04f)
            curveToRelative(0.411f, 0f, 0.745f, 0.333f, 0.745f, 0.744f)
            verticalLineToRelative(2.966f)
            horizontalLineToRelative(4.27f)
            curveToRelative(0.411f, 0f, 0.745f, 0.333f, 0.745f, 0.744f)
            curveTo(22.8f, 12.411f, 22.467f, 12.745f, 22.055f, 12.745f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTtHighIconPreview() {
    Icon(
        imageVector = IcTtHigh,
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
<g id="ic_tt_high">
	<g display="inline">
		<path fill="#67E86A" d="M7,17.978c-0.006,0-0.011,0-0.017,0c-0.731-0.013-1.385-0.871-1.796-2.354
			c-0.299-1.082-0.57-2.209-0.832-3.301c-0.164-0.683-0.328-1.367-0.5-2.044L3.74,9.824C3.352,8.262,2.952,6.647,1.906,6.465
			C1.784,6.444,1.703,6.328,1.724,6.206C1.745,6.085,1.86,6.008,1.983,6.025c1.332,0.232,1.769,1.991,2.191,3.692l0.113,0.454
			c0.172,0.678,0.337,1.364,0.502,2.05c0.261,1.088,0.532,2.211,0.829,3.286c0.338,1.222,0.877,2.017,1.374,2.027
			c0.003,0,0.006,0,0.009,0c0.465,0,0.951-0.69,1.333-1.895c0.197-0.622,0.366-1.292,0.53-1.94l0.13-0.512
			c0.145-0.563,0.281-1.135,0.418-1.706c0.28-1.17,0.57-2.38,0.923-3.501c0.391-1.242,0.975-1.93,1.644-1.935
			c0.003,0,0.006,0,0.009,0c0.671,0,1.269,0.683,1.684,1.925c0.292,0.875,0.54,1.817,0.778,2.73l0.186,0.705
			c0.155,0.581,0.303,1.169,0.451,1.757c0.252,1.005,0.514,2.044,0.808,3.025c0.241,0.809,0.651,1.324,1.068,1.345
			c0.375,0.045,0.766-0.381,1.062-1.094c0.285-0.684,0.547-1.482,0.799-2.436c0.35-1.329,0.681-2.676,1.013-4.024
			c0.153-0.622,0.306-1.244,0.461-1.863C20.593,6.929,21.15,6.248,22,6.028c0.117-0.029,0.24,0.042,0.272,0.161
			c0.031,0.12-0.041,0.241-0.161,0.272c-0.685,0.177-1.124,0.736-1.38,1.759c-0.155,0.619-0.308,1.241-0.46,1.862
			c-0.332,1.349-0.665,2.699-1.015,4.031c-0.258,0.975-0.525,1.791-0.819,2.495c-0.512,1.229-1.152,1.387-1.498,1.368
			c-0.62-0.032-1.171-0.654-1.474-1.664c-0.296-0.989-0.558-2.034-0.813-3.044c-0.147-0.586-0.295-1.171-0.448-1.751l-0.187-0.708
			c-0.236-0.905-0.481-1.84-0.769-2.701c-0.34-1.015-0.811-1.619-1.262-1.619c-0.002,0-0.003,0-0.004,0
			C11.536,6.493,11.079,7.1,10.76,8.112c-0.348,1.106-0.636,2.308-0.914,3.47c-0.138,0.574-0.275,1.147-0.42,1.713l-0.13,0.511
			c-0.166,0.655-0.337,1.332-0.538,1.966C8.182,17.59,7.487,17.978,7,17.978z"/>
		<path fill="#67E86A" d="M22.055,12.745H17.04c-0.411,0-0.745-0.333-0.745-0.745V9.034H7.72L7.72,12
			c0,0.411-0.333,0.745-0.744,0.745H1.944C1.533,12.745,1.2,12.411,1.2,12c0-0.411,0.333-0.744,0.744-0.744h4.286l0.001-2.966
			c0-0.411,0.333-0.744,0.744-0.744H17.04c0.411,0,0.745,0.333,0.745,0.744v2.966h4.27c0.411,0,0.745,0.333,0.745,0.744
			C22.8,12.411,22.467,12.745,22.055,12.745z"/>
	</g>
</g>
</svg>
 */