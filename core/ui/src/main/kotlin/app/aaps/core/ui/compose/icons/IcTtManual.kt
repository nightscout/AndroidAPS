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
 * Icon for Manual Temp Target.
 * Represents a manual TT.
 *
 * replaces ic_crosstarget (different)
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.0 (viewport: 24x24, ~90% height)
 */
val IcTtManual: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtManual",
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
            moveTo(17.655f, 8.401f)
            lineToRelative(-7.911f, 7.911f)
            curveToRelative(-0.063f, 0.063f, -0.106f, 0.144f, -0.122f, 0.232f)
            lineToRelative(-0.944f, 5.121f)
            curveToRelative(-0.026f, 0.143f, 0.019f, 0.289f, 0.122f, 0.391f)
            curveToRelative(0.103f, 0.103f, 0.249f, 0.148f, 0.391f, 0.122f)
            lineToRelative(5.121f, -0.944f)
            curveToRelative(0.088f, -0.016f, 0.169f, -0.059f, 0.232f, -0.122f)
            lineToRelative(7.911f, -7.911f)
            curveToRelative(0.461f, -0.461f, 0.46f, -1.213f, -0.003f, -1.676f)
            lineTo(19.33f, 8.403f)
            curveTo(18.868f, 7.941f, 18.116f, 7.939f, 17.655f, 8.401f)
            close()

            moveTo(13.491f, 20.02f)
            curveToRelative(0.101f, 0.101f, 0.137f, 0.249f, 0.094f, 0.385f)
            curveToRelative(-0.019f, 0.06f, -0.052f, 0.113f, -0.094f, 0.155f)
            curveToRelative(-0.054f, 0.054f, -0.125f, 0.092f, -0.204f, 0.106f)
            lineToRelative(-2.552f, 0.451f)
            curveToRelative(-0.253f, -0.008f, -0.504f, -0.107f, -0.698f, -0.3f)
            curveToRelative(-0.193f, -0.193f, -0.292f, -0.444f, -0.3f, -0.697f)
            lineToRelative(0.451f, -2.552f)
            curveToRelative(0.025f, -0.14f, 0.126f, -0.255f, 0.261f, -0.298f)
            curveToRelative(0.136f, -0.043f, 0.284f, -0.006f, 0.385f, 0.094f)
            lineTo(13.491f, 20.02f)
            close()

            moveTo(21.794f, 12.084f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(21.794f, 12.084f)
            close()

            moveTo(20.431f, 10.699f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(20.431f, 10.699f)
            close()

            moveTo(19.068f, 9.314f)
            curveToRelative(0.146f, 0.146f, 0.146f, 0.382f, 0f, 0.528f)
            lineToRelative(-6.991f, 6.991f)
            curveToRelative(-0.146f, 0.146f, -0.382f, 0.146f, -0.528f, 0f)
            lineToRelative(-0.275f, -0.275f)
            curveToRelative(-0.146f, -0.146f, -0.146f, -0.382f, 0f, -0.528f)
            lineToRelative(6.991f, -6.991f)
            curveToRelative(0.146f, -0.146f, 0.382f, -0.146f, 0.528f, 0f)
            lineTo(19.068f, 9.314f)
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
            moveTo(4.835f, 9.393f)
            curveToRelative(-0.004f, 0f, -0.007f, 0f, -0.011f, 0f)
            curveTo(4.367f, 9.385f, 3.957f, 8.841f, 3.7f, 7.901f)
            curveToRelative(-0.187f, -0.686f, -0.357f, -1.4f, -0.521f, -2.092f)
            curveTo(3.076f, 5.376f, 2.974f, 4.943f, 2.866f, 4.514f)
            lineTo(2.795f, 4.225f)
            curveToRelative(-0.243f, -0.99f, -0.494f, -2.014f, -1.148f, -2.129f)
            curveTo(1.57f, 2.083f, 1.52f, 2.009f, 1.533f, 1.932f)
            curveToRelative(0.013f, -0.077f, 0.085f, -0.126f, 0.162f, -0.115f)
            curveToRelative(0.834f, 0.147f, 1.107f, 1.262f, 1.371f, 2.34f)
            lineToRelative(0.071f, 0.287f)
            curveToRelative(0.108f, 0.43f, 0.211f, 0.865f, 0.314f, 1.299f)
            curveTo(3.615f, 6.433f, 3.784f, 7.145f, 3.97f, 7.826f)
            curveTo(4.181f, 8.601f, 4.519f, 9.105f, 4.83f, 9.11f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.291f, 0f, 0.595f, -0.437f, 0.834f, -1.201f)
            curveToRelative(0.123f, -0.394f, 0.229f, -0.819f, 0.332f, -1.23f)
            lineToRelative(0.082f, -0.325f)
            curveToRelative(0.091f, -0.357f, 0.176f, -0.719f, 0.262f, -1.081f)
            curveTo(6.52f, 4.532f, 6.701f, 3.765f, 6.922f, 3.055f)
            curveToRelative(0.245f, -0.787f, 0.611f, -1.223f, 1.029f, -1.227f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.42f, 0f, 0.795f, 0.433f, 1.054f, 1.22f)
            curveTo(9.194f, 3.602f, 9.349f, 4.2f, 9.498f, 4.778f)
            lineToRelative(0.116f, 0.447f)
            curveToRelative(0.097f, 0.368f, 0.189f, 0.741f, 0.283f, 1.113f)
            curveToRelative(0.158f, 0.637f, 0.322f, 1.296f, 0.506f, 1.917f)
            curveToRelative(0.151f, 0.513f, 0.407f, 0.839f, 0.669f, 0.853f)
            curveToRelative(0.234f, 0.029f, 0.479f, -0.241f, 0.665f, -0.693f)
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
            curveTo(9.949f, 7.709f, 9.785f, 7.047f, 9.625f, 6.407f)
            curveToRelative(-0.092f, -0.371f, -0.184f, -0.742f, -0.281f, -1.11f)
            lineTo(9.228f, 4.849f)
            curveTo(9.08f, 4.276f, 8.926f, 3.683f, 8.747f, 3.138f)
            curveToRelative(-0.213f, -0.643f, -0.508f, -1.026f, -0.79f, -1.026f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.002f, 0f)
            curveTo(7.675f, 2.114f, 7.389f, 2.498f, 7.189f, 3.14f)
            curveToRelative(-0.218f, 0.701f, -0.398f, 1.463f, -0.572f, 2.2f)
            curveTo(6.531f, 5.703f, 6.444f, 6.067f, 6.354f, 6.425f)
            lineTo(6.273f, 6.749f)
            curveTo(6.169f, 7.164f, 6.062f, 7.593f, 5.936f, 7.995f)
            curveTo(5.575f, 9.148f, 5.14f, 9.393f, 4.835f, 9.393f)
            close()

            moveTo(14.255f, 6.082f)
            horizontalLineToRelative(-3.139f)
            curveToRelative(-0.258f, 0f, -0.466f, -0.214f, -0.466f, -0.478f)
            verticalLineTo(3.7f)
            horizontalLineTo(5.281f)
            lineToRelative(0f, 1.904f)
            curveToRelative(0f, 0.264f, -0.209f, 0.478f, -0.466f, 0.478f)
            horizontalLineTo(1.666f)
            curveTo(1.408f, 6.082f, 1.2f, 5.868f, 1.2f, 5.604f)
            curveToRelative(0f, -0.264f, 0.208f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(2.683f)
            lineToRelative(0f, -1.904f)
            curveToRelative(0f, -0.264f, 0.209f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(6.3f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            verticalLineToRelative(1.904f)
            horizontalLineToRelative(2.673f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            curveTo(14.721f, 5.868f, 14.512f, 6.082f, 14.255f, 6.082f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTtManualIconPreview() {
    Icon(
        imageVector = IcTtManual,
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
<g id="ic_tt_manual">
	<path display="inline" fill="#67E86A" d="M17.655,8.401l-7.911,7.911c-0.063,0.063-0.106,0.144-0.122,0.232l-0.944,5.121
		c-0.026,0.143,0.019,0.289,0.122,0.391c0.103,0.103,0.249,0.148,0.391,0.122l5.121-0.944c0.088-0.016,0.169-0.059,0.232-0.122
		l7.911-7.911c0.461-0.461,0.46-1.213-0.003-1.676L19.33,8.403C18.868,7.941,18.116,7.939,17.655,8.401z M13.491,20.02
		c0.101,0.101,0.137,0.249,0.094,0.385c-0.019,0.06-0.052,0.113-0.094,0.155c-0.054,0.054-0.125,0.092-0.204,0.106l-2.552,0.451
		c-0.253-0.008-0.504-0.107-0.698-0.3c-0.193-0.193-0.292-0.444-0.3-0.697l0.451-2.552c0.025-0.14,0.126-0.255,0.261-0.298
		c0.136-0.043,0.284-0.006,0.385,0.094L13.491,20.02z M21.794,12.084c0.146,0.146,0.146,0.382,0,0.528l-6.991,6.991
		c-0.146,0.146-0.382,0.146-0.528,0l-0.275-0.275c-0.146-0.146-0.146-0.382,0-0.528l6.991-6.991c0.146-0.146,0.382-0.146,0.528,0
		L21.794,12.084z M20.431,10.699c0.146,0.146,0.146,0.382,0,0.528l-6.991,6.991c-0.146,0.146-0.382,0.146-0.528,0l-0.275-0.275
		c-0.146-0.146-0.146-0.382,0-0.528l6.991-6.991c0.146-0.146,0.382-0.146,0.528,0L20.431,10.699z M19.068,9.314
		c0.146,0.146,0.146,0.382,0,0.528l-6.991,6.991c-0.146,0.146-0.382,0.146-0.528,0l-0.275-0.275c-0.146-0.146-0.146-0.382,0-0.528
		l6.991-6.991c0.146-0.146,0.382-0.146,0.528,0L19.068,9.314z"/>
	<g display="inline">
		<path fill="#67E86A" d="M4.835,9.393c-0.004,0-0.007,0-0.011,0C4.367,9.385,3.957,8.841,3.7,7.901
			c-0.187-0.686-0.357-1.4-0.521-2.092C3.076,5.376,2.974,4.943,2.866,4.514L2.795,4.225c-0.243-0.99-0.494-2.014-1.148-2.129
			C1.57,2.083,1.52,2.009,1.533,1.932c0.013-0.077,0.085-0.126,0.162-0.115c0.834,0.147,1.107,1.262,1.371,2.34l0.071,0.287
			c0.108,0.43,0.211,0.865,0.314,1.299C3.615,6.433,3.784,7.145,3.97,7.826C4.181,8.6,4.519,9.104,4.83,9.11
			c0.002,0,0.004,0,0.005,0c0.291,0,0.595-0.437,0.834-1.201c0.123-0.394,0.229-0.819,0.332-1.23l0.082-0.325
			c0.091-0.357,0.176-0.719,0.262-1.081C6.52,4.532,6.701,3.765,6.922,3.055c0.245-0.787,0.611-1.223,1.029-1.227
			c0.002,0,0.004,0,0.005,0c0.42,0,0.795,0.433,1.054,1.22C9.194,3.602,9.349,4.2,9.498,4.778l0.116,0.447
			c0.097,0.368,0.189,0.741,0.283,1.113c0.158,0.637,0.322,1.296,0.506,1.917c0.151,0.513,0.407,0.839,0.669,0.853
			c0.234,0.029,0.479-0.241,0.665-0.693c0.179-0.434,0.342-0.939,0.5-1.544c0.219-0.842,0.427-1.696,0.634-2.55
			c0.096-0.394,0.191-0.788,0.288-1.181c0.185-0.75,0.534-1.181,1.066-1.321c0.073-0.019,0.15,0.026,0.17,0.102
			c0.019,0.076-0.026,0.153-0.101,0.173C13.865,2.206,13.59,2.56,13.43,3.208c-0.097,0.393-0.193,0.786-0.288,1.18
			c-0.208,0.855-0.416,1.711-0.635,2.555c-0.161,0.618-0.329,1.135-0.513,1.581c-0.321,0.779-0.721,0.879-0.938,0.867
			c-0.388-0.02-0.733-0.414-0.922-1.055C9.949,7.709,9.785,7.047,9.625,6.407c-0.092-0.371-0.184-0.742-0.281-1.11L9.228,4.849
			C9.08,4.276,8.926,3.683,8.747,3.137c-0.213-0.643-0.508-1.026-0.79-1.026c-0.001,0-0.002,0-0.002,0
			C7.675,2.113,7.389,2.498,7.189,3.14c-0.218,0.701-0.398,1.463-0.572,2.2C6.53,5.703,6.444,6.066,6.354,6.425L6.273,6.749
			C6.169,7.164,6.062,7.593,5.936,7.995C5.575,9.147,5.14,9.393,4.835,9.393z"/>
		<path fill="#67E86A" d="M14.255,6.082h-3.139c-0.258,0-0.466-0.214-0.466-0.478V3.7H5.281l0,1.904
			c0,0.264-0.209,0.478-0.466,0.478H1.666C1.408,6.082,1.2,5.868,1.2,5.604c0-0.264,0.208-0.478,0.466-0.478h2.683l0-1.904
			c0-0.264,0.209-0.478,0.466-0.478h6.3c0.258,0,0.466,0.214,0.466,0.478v1.904h2.673c0.258,0,0.466,0.214,0.466,0.478
			C14.721,5.868,14.512,6.082,14.255,6.082z"/>
	</g>
</g>
</svg>
 */