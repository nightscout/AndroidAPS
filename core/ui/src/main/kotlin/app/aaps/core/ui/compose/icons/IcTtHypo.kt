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
 * Icon for Hypo Temp Target.
 *
 * replaces ic_target_hypo
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcTtHypo: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtHypo",
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
            moveTo(8.57f, 18.055f)
            curveToRelative(-0.018f, -0.311f, 0.025f, -0.602f, 0.403f, -0.646f)
            curveToRelative(0.347f, -0.041f, 0.472f, 0.21f, 0.512f, 0.497f)
            curveToRelative(0.15f, 1.09f, 0.743f, 1.733f, 1.849f, 1.876f)
            curveToRelative(0.324f, 0.042f, 0.57f, 0.185f, 0.509f, 0.552f)
            curveToRelative(-0.056f, 0.338f, -0.359f, 0.374f, -0.63f, 0.373f)
            curveTo(9.909f, 20.704f, 8.585f, 19.368f, 8.57f, 18.055f)
            close()

            moveTo(15.653f, 16.915f)
            curveToRelative(-0.699f, -2.754f, -3.75f, -7.008f, -4.183f, -7.008f)
            curveToRelative(-0.433f, 0f, -3.494f, 4.263f, -4.19f, 7.011f)
            curveToRelative(-0.466f, 1.838f, 0.124f, 3.416f, 1.749f, 4.489f)
            curveToRelative(1.586f, 1.047f, 3.296f, 1.03f, 4.881f, -0.014f)
            curveTo(15.537f, 20.32f, 16.123f, 18.767f, 15.653f, 16.915f)
            close()

            moveTo(13.485f, 20.75f)
            curveToRelative(-0.669f, 0.44f, -1.353f, 0.664f, -2.034f, 0.664f)
            curveToRelative(-0.673f, 0f, -1.345f, -0.218f, -1.998f, -0.65f)
            curveToRelative(-1.338f, -0.882f, -1.817f, -2.113f, -1.427f, -3.657f)
            curveToRelative(0.542f, -2.139f, 3.21f, -5.916f, 3.443f, -5.916f)
            curveToRelative(0.233f, 0f, 2.893f, 3.773f, 3.437f, 5.913f)
            curveTo(15.297f, 18.644f, 14.819f, 19.871f, 13.485f, 20.75f)
            close()

            moveTo(20.433f, 17.649f)
            verticalLineToRelative(-7.442f)
            horizontalLineToRelative(-1.578f)
            verticalLineToRelative(7.442f)
            horizontalLineToRelative(-2.367f)
            lineToRelative(3.156f, 4.236f)
            lineToRelative(3.156f, -4.236f)
            horizontalLineTo(20.433f)
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

            moveTo(14.255f, 6.082f)
            horizontalLineToRelative(-3.139f)
            curveToRelative(-0.258f, 0f, -0.466f, -0.214f, -0.466f, -0.478f)
            verticalLineTo(3.701f)
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
private fun IcTtHypoIconPreview() {
    Icon(
        imageVector = IcTtHypo,
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
<g id="ic_tt_hypo">
	<g display="inline">
		<g>
			<path fill="#FF1313" d="M8.57,18.055c-0.018-0.311,0.025-0.602,0.403-0.646c0.347-0.041,0.472,0.21,0.512,0.497
				c0.15,1.09,0.743,1.733,1.849,1.876c0.324,0.042,0.57,0.185,0.509,0.552c-0.056,0.338-0.359,0.374-0.63,0.373
				C9.909,20.704,8.585,19.368,8.57,18.055z"/>
			<g>
				<g>
					<path fill="#FF1313" d="M15.653,16.915c-0.699-2.754-3.75-7.008-4.183-7.008s-3.494,4.263-4.19,7.011
						c-0.466,1.838,0.124,3.416,1.749,4.489c1.586,1.047,3.296,1.03,4.881-0.014C15.537,20.32,16.123,18.767,15.653,16.915z
						 M13.485,20.75c-0.669,0.44-1.353,0.664-2.034,0.664c-0.673,0-1.345-0.218-1.998-0.65c-1.338-0.882-1.817-2.113-1.427-3.657
						c0.542-2.139,3.21-5.916,3.443-5.916c0.233,0,2.893,3.773,3.437,5.913C15.297,18.644,14.819,19.871,13.485,20.75z"/>
				</g>
			</g>
		</g>
		<path fill="#FF1313" d="M20.433,17.649v-7.442h-1.578v7.442h-2.367l3.156,4.236l3.156-4.236H20.433z"/>
	</g>
	<g display="inline">
		<path fill="#67E86A" d="M4.835,9.393c-0.004,0-0.007,0-0.011,0C4.367,9.385,3.957,8.841,3.7,7.901
			c-0.187-0.686-0.357-1.4-0.521-2.092C3.076,5.376,2.974,4.943,2.866,4.514L2.795,4.225c-0.243-0.99-0.494-2.014-1.148-2.129
			C1.57,2.083,1.52,2.009,1.533,1.932c0.013-0.077,0.085-0.126,0.162-0.115c0.834,0.147,1.107,1.262,1.371,2.34l0.071,0.287
			c0.108,0.43,0.211,0.865,0.314,1.299C3.615,6.433,3.784,7.145,3.97,7.826C4.181,8.601,4.519,9.105,4.83,9.11
			c0.002,0,0.004,0,0.005,0c0.291,0,0.595-0.437,0.834-1.201c0.123-0.394,0.229-0.819,0.332-1.23l0.082-0.325
			c0.091-0.357,0.176-0.719,0.262-1.081C6.52,4.532,6.701,3.765,6.922,3.055c0.245-0.787,0.611-1.223,1.029-1.227
			c0.002,0,0.004,0,0.005,0c0.42,0,0.795,0.433,1.054,1.22C9.194,3.602,9.349,4.2,9.498,4.778l0.116,0.447
			c0.097,0.368,0.189,0.741,0.283,1.113c0.158,0.637,0.322,1.296,0.506,1.917c0.151,0.513,0.407,0.839,0.669,0.853
			c0.234,0.029,0.479-0.241,0.665-0.693c0.179-0.434,0.342-0.939,0.5-1.544c0.219-0.842,0.427-1.696,0.634-2.55
			c0.096-0.394,0.191-0.788,0.288-1.181c0.185-0.75,0.534-1.181,1.066-1.321c0.073-0.019,0.15,0.026,0.17,0.102
			c0.019,0.076-0.026,0.153-0.101,0.173c-0.429,0.112-0.703,0.466-0.864,1.115c-0.097,0.393-0.193,0.786-0.288,1.18
			c-0.208,0.855-0.416,1.711-0.635,2.555c-0.161,0.618-0.329,1.135-0.513,1.581c-0.321,0.779-0.721,0.879-0.938,0.867
			c-0.388-0.02-0.733-0.414-0.922-1.055C9.949,7.709,9.785,7.047,9.625,6.407c-0.092-0.371-0.184-0.742-0.281-1.11L9.228,4.849
			C9.08,4.276,8.926,3.683,8.747,3.138c-0.213-0.643-0.508-1.026-0.79-1.026c-0.001,0-0.002,0-0.002,0
			C7.675,2.114,7.389,2.498,7.189,3.14c-0.218,0.701-0.398,1.463-0.572,2.2C6.531,5.703,6.444,6.067,6.354,6.425L6.273,6.749
			C6.169,7.164,6.062,7.593,5.936,7.995C5.575,9.148,5.14,9.393,4.835,9.393z"/>
		<path fill="#67E86A" d="M14.255,6.082h-3.139c-0.258,0-0.466-0.214-0.466-0.478V3.701H5.281l0,1.904
			c0,0.264-0.209,0.478-0.466,0.478H1.666C1.408,6.082,1.2,5.868,1.2,5.604c0-0.264,0.208-0.478,0.466-0.478h2.683l0-1.904
			c0-0.264,0.209-0.478,0.466-0.478h6.3c0.258,0,0.466,0.214,0.466,0.478v1.904h2.673c0.258,0,0.466,0.214,0.466,0.478
			C14.721,5.868,14.512,6.082,14.255,6.082z"/>
	</g>
</g>
</svg>
 */