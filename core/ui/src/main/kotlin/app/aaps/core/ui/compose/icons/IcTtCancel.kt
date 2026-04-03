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
 * Icon for Cancel Temp Target.
 *
 * Bounding box: x: 1.2-22.8, y: 2.5-20.5 (viewport: 24x24, ~90% width)
 */
val IcTtCancel: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtCancel",
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
            moveTo(18.469f, 15.891f)
            lineToRelative(4.081f, -4.081f)
            curveToRelative(0.333f, -0.333f, 0.333f, -0.87f, 0.001f, -1.201f)
            curveToRelative(-0.333f, -0.333f, -0.869f, -0.333f, -1.202f, -0.001f)
            lineToRelative(-4.081f, 4.082f)
            lineToRelative(-4.081f, -4.082f)
            curveToRelative(-0.333f, -0.332f, -0.871f, -0.333f, -1.202f, 0.001f)
            curveToRelative(-0.332f, 0.332f, -0.332f, 0.869f, 0.001f, 1.201f)
            lineToRelative(4.081f, 4.081f)
            lineToRelative(-4.081f, 4.082f)
            curveToRelative(-0.333f, 0.332f, -0.333f, 0.869f, -0.001f, 1.201f)
            curveToRelative(0.167f, 0.166f, 0.384f, 0.249f, 0.601f, 0.249f)
            curveToRelative(0.217f, 0f, 0.435f, -0.083f, 0.601f, -0.249f)
            lineToRelative(4.081f, -4.081f)
            lineToRelative(4.081f, 4.081f)
            curveToRelative(0.167f, 0.166f, 0.384f, 0.249f, 0.601f, 0.249f)
            curveToRelative(0.217f, 0f, 0.435f, -0.083f, 0.601f, -0.249f)
            curveToRelative(0.332f, -0.332f, 0.332f, -0.869f, -0.001f, -1.201f)
            lineTo(18.469f, 15.891f)
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
            moveTo(4.835f, 10.157f)
            curveToRelative(-0.004f, 0f, -0.007f, 0f, -0.011f, 0f)
            curveTo(4.367f, 10.148f, 3.957f, 9.604f, 3.7f, 8.665f)
            curveToRelative(-0.187f, -0.686f, -0.357f, -1.4f, -0.521f, -2.092f)
            curveTo(3.076f, 6.139f, 2.974f, 5.706f, 2.866f, 5.277f)
            lineTo(2.795f, 4.988f)
            curveToRelative(-0.243f, -0.99f, -0.494f, -2.014f, -1.148f, -2.129f)
            curveTo(1.57f, 2.846f, 1.52f, 2.772f, 1.533f, 2.695f)
            curveToRelative(0.013f, -0.077f, 0.085f, -0.125f, 0.162f, -0.115f)
            curveToRelative(0.834f, 0.147f, 1.107f, 1.262f, 1.371f, 2.34f)
            lineToRelative(0.071f, 0.287f)
            curveToRelative(0.108f, 0.43f, 0.211f, 0.865f, 0.314f, 1.299f)
            curveTo(3.615f, 7.196f, 3.784f, 7.908f, 3.97f, 8.589f)
            curveToRelative(0.212f, 0.775f, 0.549f, 1.279f, 0.86f, 1.285f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.291f, 0f, 0.595f, -0.437f, 0.834f, -1.201f)
            curveToRelative(0.123f, -0.394f, 0.229f, -0.819f, 0.332f, -1.23f)
            lineToRelative(0.082f, -0.325f)
            curveToRelative(0.091f, -0.357f, 0.176f, -0.719f, 0.262f, -1.081f)
            curveTo(6.52f, 5.295f, 6.701f, 4.528f, 6.922f, 3.818f)
            curveToRelative(0.245f, -0.787f, 0.611f, -1.223f, 1.029f, -1.227f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.42f, 0f, 0.795f, 0.433f, 1.054f, 1.22f)
            curveToRelative(0.183f, 0.554f, 0.338f, 1.152f, 0.487f, 1.73f)
            lineToRelative(0.116f, 0.447f)
            curveToRelative(0.097f, 0.368f, 0.189f, 0.741f, 0.283f, 1.113f)
            curveToRelative(0.158f, 0.637f, 0.322f, 1.296f, 0.506f, 1.917f)
            curveToRelative(0.151f, 0.513f, 0.407f, 0.839f, 0.669f, 0.853f)
            curveTo(11.306f, 9.9f, 11.55f, 9.63f, 11.736f, 9.178f)
            curveToRelative(0.179f, -0.434f, 0.342f, -0.939f, 0.5f, -1.544f)
            curveToRelative(0.219f, -0.842f, 0.427f, -1.696f, 0.634f, -2.55f)
            curveToRelative(0.096f, -0.394f, 0.191f, -0.788f, 0.288f, -1.181f)
            curveToRelative(0.185f, -0.75f, 0.534f, -1.181f, 1.066f, -1.321f)
            curveToRelative(0.073f, -0.019f, 0.15f, 0.026f, 0.17f, 0.102f)
            curveToRelative(0.019f, 0.076f, -0.026f, 0.153f, -0.101f, 0.173f)
            curveToRelative(-0.429f, 0.112f, -0.703f, 0.466f, -0.864f, 1.115f)
            curveToRelative(-0.097f, 0.393f, -0.193f, 0.786f, -0.288f, 1.18f)
            curveToRelative(-0.208f, 0.855f, -0.416f, 1.711f, -0.635f, 2.555f)
            curveToRelative(-0.161f, 0.618f, -0.329f, 1.135f, -0.513f, 1.581f)
            curveToRelative(-0.321f, 0.779f, -0.721f, 0.879f, -0.938f, 0.867f)
            curveToRelative(-0.388f, -0.02f, -0.733f, -0.414f, -0.922f, -1.055f)
            curveTo(9.949f, 8.473f, 9.785f, 7.811f, 9.625f, 7.171f)
            curveToRelative(-0.092f, -0.371f, -0.184f, -0.742f, -0.281f, -1.11f)
            lineTo(9.228f, 5.613f)
            curveTo(9.08f, 5.039f, 8.926f, 4.446f, 8.747f, 3.901f)
            curveToRelative(-0.213f, -0.643f, -0.508f, -1.026f, -0.79f, -1.026f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.002f, 0f)
            curveToRelative(-0.28f, 0.002f, -0.567f, 0.387f, -0.766f, 1.029f)
            curveToRelative(-0.218f, 0.701f, -0.398f, 1.463f, -0.572f, 2.2f)
            curveTo(6.53f, 6.466f, 6.444f, 6.83f, 6.354f, 7.188f)
            lineTo(6.273f, 7.512f)
            curveTo(6.169f, 7.927f, 6.062f, 8.356f, 5.936f, 8.758f)
            curveTo(5.575f, 9.911f, 5.14f, 10.157f, 4.835f, 10.157f)
            close()

            moveTo(14.255f, 6.845f)
            horizontalLineTo(1.666f)
            curveTo(1.408f, 6.845f, 1.2f, 6.631f, 1.2f, 6.367f)
            curveToRelative(0f, -0.264f, 0.208f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(12.589f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            curveTo(14.721f, 6.631f, 14.512f, 6.845f, 14.255f, 6.845f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTtCancelIconPreview() {
    Icon(
        imageVector = IcTtCancel,
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
<g id="ic_tt_cancel">
	<path display="inline" fill="#67E86A" d="M18.469,15.891l4.081-4.081c0.333-0.333,0.333-0.87,0.001-1.201
		c-0.333-0.333-0.869-0.333-1.202-0.001l-4.081,4.082l-4.081-4.082c-0.333-0.332-0.871-0.333-1.202,0.001
		c-0.332,0.332-0.332,0.869,0.001,1.201l4.081,4.081l-4.081,4.082c-0.333,0.332-0.333,0.869-0.001,1.201
		c0.167,0.166,0.384,0.249,0.601,0.249c0.217,0,0.435-0.083,0.601-0.249l4.081-4.081l4.081,4.081
		c0.167,0.166,0.384,0.249,0.601,0.249c0.217,0,0.435-0.083,0.601-0.249c0.332-0.332,0.332-0.869-0.001-1.201L18.469,15.891z"/>
	<g display="inline">
		<path fill="#67E86A" d="M4.835,10.157c-0.004,0-0.007,0-0.011,0C4.367,10.148,3.957,9.604,3.7,8.665
			c-0.187-0.686-0.357-1.4-0.521-2.092C3.076,6.139,2.974,5.706,2.866,5.277L2.795,4.988c-0.243-0.99-0.494-2.014-1.148-2.129
			C1.57,2.846,1.52,2.772,1.533,2.695C1.546,2.618,1.618,2.57,1.695,2.58c0.834,0.147,1.107,1.262,1.371,2.34l0.071,0.287
			c0.108,0.43,0.211,0.865,0.314,1.299C3.615,7.196,3.784,7.908,3.97,8.589c0.212,0.775,0.549,1.279,0.86,1.285
			c0.002,0,0.004,0,0.005,0c0.291,0,0.595-0.437,0.834-1.201c0.123-0.394,0.229-0.819,0.332-1.23l0.082-0.325
			c0.091-0.357,0.176-0.719,0.262-1.081C6.52,5.295,6.701,4.528,6.922,3.818c0.245-0.787,0.611-1.223,1.029-1.227
			c0.002,0,0.004,0,0.005,0c0.42,0,0.795,0.433,1.054,1.22c0.183,0.554,0.338,1.152,0.487,1.73l0.116,0.447
			c0.097,0.368,0.189,0.741,0.283,1.113c0.158,0.637,0.322,1.296,0.506,1.917c0.151,0.513,0.407,0.839,0.669,0.853
			C11.306,9.9,11.55,9.63,11.736,9.178c0.179-0.434,0.342-0.939,0.5-1.544c0.219-0.842,0.427-1.696,0.634-2.55
			c0.096-0.394,0.191-0.788,0.288-1.181c0.185-0.75,0.534-1.181,1.066-1.321c0.073-0.019,0.15,0.026,0.17,0.102
			c0.019,0.076-0.026,0.153-0.101,0.173c-0.429,0.112-0.703,0.466-0.864,1.115c-0.097,0.393-0.193,0.786-0.288,1.18
			c-0.208,0.855-0.416,1.711-0.635,2.555c-0.161,0.618-0.329,1.135-0.513,1.581c-0.321,0.779-0.721,0.879-0.938,0.867
			c-0.388-0.02-0.733-0.414-0.922-1.055C9.949,8.473,9.785,7.811,9.625,7.171c-0.092-0.371-0.184-0.742-0.281-1.11L9.228,5.613
			C9.08,5.039,8.926,4.446,8.747,3.901c-0.213-0.643-0.508-1.026-0.79-1.026c-0.001,0-0.002,0-0.002,0
			c-0.28,0.002-0.567,0.387-0.766,1.029c-0.218,0.701-0.398,1.463-0.572,2.2C6.53,6.466,6.444,6.83,6.354,7.188L6.273,7.512
			C6.169,7.927,6.062,8.356,5.936,8.758C5.575,9.911,5.14,10.157,4.835,10.157z"/>
		<path fill="#67E86A" d="M14.255,6.845H1.666C1.408,6.845,1.2,6.631,1.2,6.367c0-0.264,0.208-0.478,0.466-0.478h12.589
			c0.258,0,0.466,0.214,0.466,0.478C14.721,6.631,14.512,6.845,14.255,6.845z"/>
	</g>
</g>
</svg>
 */