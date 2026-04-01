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
 * Icon for Eating Soon Temp Target.
 *
 * replaces ic_target_eatingsoon
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.2 (viewport: 24x24, ~90% height)
 */
val IcTtEatingSoon: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtEatingSoon",
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
            moveTo(22.353f, 21.291f)
            horizontalLineToRelative(-0.575f)
            curveToRelative(-0.257f, -3.61f, -2.891f, -6.589f, -6.324f, -7.38f)
            curveToRelative(-0.1f, -0.916f, -0.868f, -1.633f, -1.81f, -1.633f)
            curveToRelative(-0.942f, 0f, -1.711f, 0.718f, -1.81f, 1.634f)
            curveToRelative(-3.433f, 0.791f, -6.066f, 3.77f, -6.324f, 7.38f)
            horizontalLineTo(4.935f)
            curveToRelative(-0.247f, 0f, -0.447f, 0.2f, -0.447f, 0.447f)
            curveToRelative(0f, 0.246f, 0.2f, 0.447f, 0.447f, 0.447f)
            horizontalLineToRelative(17.418f)
            curveToRelative(0.246f, 0f, 0.447f, -0.2f, 0.447f, -0.447f)
            curveTo(22.8f, 21.492f, 22.6f, 21.291f, 22.353f, 21.291f)
            close()

            moveTo(13.644f, 12.964f)
            curveToRelative(0.515f, 0f, 0.934f, 0.347f, 1.077f, 0.816f)
            curveToRelative(-0.354f, -0.048f, -0.712f, -0.081f, -1.077f, -0.081f)
            curveToRelative(-0.366f, 0f, -0.724f, 0.033f, -1.078f, 0.081f)
            curveTo(12.71f, 13.312f, 13.13f, 12.964f, 13.644f, 12.964f)
            close()

            moveTo(13.644f, 14.594f)
            curveToRelative(3.789f, 0f, 6.94f, 2.966f, 7.239f, 6.698f)
            horizontalLineTo(6.406f)
            curveTo(6.704f, 17.559f, 9.856f, 14.594f, 13.644f, 14.594f)
            close()

            moveTo(16.008f, 15.809f)
            curveToRelative(-0.657f, -0.303f, -1.581f, -0.51f, -2.534f, -0.569f)
            curveToRelative(-0.247f, -0.015f, -0.459f, 0.173f, -0.474f, 0.419f)
            curveToRelative(-0.015f, 0.247f, 0.173f, 0.459f, 0.419f, 0.474f)
            curveToRelative(0.833f, 0.051f, 1.661f, 0.233f, 2.213f, 0.488f)
            curveToRelative(0.052f, 0.024f, 0.106f, 0.037f, 0.16f, 0.04f)
            curveToRelative(0.178f, 0.011f, 0.354f, -0.087f, 0.434f, -0.259f)
            curveTo(16.33f, 16.179f, 16.232f, 15.913f, 16.008f, 15.809f)
            close()

            moveTo(7.765f, 20.627f)
            curveToRelative(-0.033f, 0.001f, -0.068f, -0.003f, -0.106f, -0.013f)
            curveToRelative(-0.21f, -0.053f, -0.34f, -0.267f, -0.287f, -0.477f)
            curveToRelative(0.48f, -1.929f, 1.86f, -3.571f, 3.692f, -4.394f)
            curveToRelative(0.048f, -0.021f, 0.099f, -0.033f, 0.151f, -0.034f)
            curveToRelative(0.16f, -0.004f, 0.305f, 0.087f, 0.37f, 0.232f)
            curveToRelative(0.044f, 0.096f, 0.047f, 0.204f, 0.009f, 0.302f)
            curveToRelative(-0.038f, 0.098f, -0.111f, 0.177f, -0.207f, 0.219f)
            curveToRelative(-1.613f, 0.725f, -2.828f, 2.17f, -3.25f, 3.864f)
            curveTo(8.095f, 20.499f, 7.942f, 20.622f, 7.765f, 20.627f)
            close()
        }

        path(
            fill = SolidColor(Color(0xFF67E86A)),
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

            moveTo(11.12f, 8.463f)
            horizontalLineTo(4.815f)
            curveToRelative(-0.257f, 0f, -0.466f, -0.214f, -0.466f, -0.478f)
            lineToRelative(0f, -1.903f)
            horizontalLineTo(1.666f)
            curveTo(1.408f, 6.082f, 1.2f, 5.868f, 1.2f, 5.604f)
            curveToRelative(0f, -0.264f, 0.208f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(3.149f)
            curveToRelative(0.257f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            lineToRelative(0f, 1.903f)
            horizontalLineToRelative(5.372f)
            verticalLineTo(5.604f)
            curveToRelative(0f, -0.264f, 0.209f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(3.135f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            curveToRelative(0f, 0.264f, -0.209f, 0.478f, -0.466f, 0.478f)
            horizontalLineToRelative(-2.668f)
            verticalLineToRelative(1.903f)
            curveTo(11.586f, 8.249f, 11.378f, 8.463f, 11.12f, 8.463f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTtEatingSoonIconPreview() {
    Icon(
        imageVector = IcTtEatingSoon,
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
<g id="ic_tt_eating_soon">
	<g display="inline">
		<g>
			<path fill="#FEAF05" d="M22.353,21.291h-0.575c-0.257-3.61-2.891-6.589-6.324-7.38c-0.1-0.916-0.868-1.633-1.81-1.633
				c-0.942,0-1.711,0.718-1.81,1.634c-3.433,0.791-6.066,3.77-6.324,7.38H4.935c-0.247,0-0.447,0.2-0.447,0.447
				c0,0.246,0.2,0.447,0.447,0.447h17.418c0.246,0,0.447-0.2,0.447-0.447C22.8,21.492,22.6,21.291,22.353,21.291z M13.644,12.964
				c0.515,0,0.934,0.347,1.077,0.816c-0.354-0.048-0.712-0.081-1.077-0.081c-0.366,0-0.724,0.033-1.078,0.081
				C12.71,13.312,13.13,12.964,13.644,12.964z M13.644,14.594c3.789,0,6.94,2.966,7.239,6.698H6.406
				C6.704,17.559,9.856,14.594,13.644,14.594z"/>
			<path fill="#FEAF05" d="M16.008,15.809c-0.657-0.303-1.581-0.51-2.534-0.569c-0.247-0.015-0.459,0.173-0.474,0.419
				c-0.015,0.247,0.173,0.459,0.419,0.474c0.833,0.051,1.661,0.233,2.213,0.488c0.052,0.024,0.106,0.037,0.16,0.04
				c0.178,0.011,0.354-0.087,0.434-0.259C16.33,16.179,16.232,15.913,16.008,15.809z"/>
			<path fill="#FEAF05" d="M7.765,20.627c-0.033,0.001-0.068-0.003-0.106-0.013c-0.21-0.053-0.34-0.267-0.287-0.477
				c0.48-1.929,1.86-3.571,3.692-4.394c0.048-0.021,0.099-0.033,0.151-0.034c0.16-0.004,0.305,0.087,0.37,0.232
				c0.044,0.096,0.047,0.204,0.009,0.302s-0.111,0.177-0.207,0.219c-1.613,0.725-2.828,2.17-3.25,3.864
				C8.095,20.499,7.942,20.622,7.765,20.627z"/>
		</g>
		<g>
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
			<path fill="#67E86A" d="M11.12,8.463H4.815c-0.257,0-0.466-0.214-0.466-0.478l0-1.903H1.666C1.408,6.082,1.2,5.868,1.2,5.604
				c0-0.264,0.208-0.478,0.466-0.478h3.149c0.257,0,0.466,0.214,0.466,0.478l0,1.903h5.372V5.604c0-0.264,0.209-0.478,0.466-0.478
				h3.135c0.258,0,0.466,0.214,0.466,0.478c0,0.264-0.209,0.478-0.466,0.478h-2.668v1.903C11.586,8.249,11.378,8.463,11.12,8.463z"
				/>
		</g>
	</g>
</g>
</svg>
 */