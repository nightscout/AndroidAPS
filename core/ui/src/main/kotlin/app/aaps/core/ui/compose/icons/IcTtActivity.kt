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
 * Icon for Activity Temp Target.
 *
 * replaces ic_target_activity
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.0 (viewport: 24x24, ~90% height)
 */
val IcTtActivity: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtActivity",
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
            moveTo(19.392f, 17.722f)
            curveToRelative(1.246f, 0f, 2.26f, -1.068f, 2.26f, -2.382f)
            curveToRelative(0f, -1.314f, -1.014f, -2.382f, -2.26f, -2.382f)
            curveToRelative(-1.246f, 0f, -2.26f, 1.069f, -2.26f, 2.382f)
            curveTo(17.132f, 16.654f, 18.146f, 17.722f, 19.392f, 17.722f)
            close()

            moveTo(19.392f, 13.69f)
            curveToRelative(0.863f, 0f, 1.567f, 0.74f, 1.567f, 1.65f)
            curveToRelative(0f, 0.91f, -0.703f, 1.65f, -1.567f, 1.65f)
            curveToRelative(-0.863f, 0f, -1.567f, -0.74f, -1.567f, -1.65f)
            curveTo(17.826f, 14.43f, 18.529f, 13.69f, 19.392f, 13.69f)
            close()

            moveTo(22.732f, 20.5f)
            curveToRelative(-0.032f, -0.045f, -0.781f, -1.088f, -1.886f, -1.088f)
            curveToRelative(-0.822f, 0f, -1.356f, 0.539f, -1.828f, 1.015f)
            curveToRelative(-0.418f, 0.421f, -0.779f, 0.786f, -1.284f, 0.786f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-0.411f, 0f, -0.748f, -0.253f, -1.082f, -0.563f)
            lineToRelative(1.744f, -1.388f)
            lineToRelative(-4.768f, -6.657f)
            lineToRelative(-4.601f, 3.661f)
            curveToRelative(-0.271f, 0.253f, -0.656f, 0.911f, -0.172f, 1.587f)
            curveToRelative(0.224f, 0.313f, 0.485f, 0.406f, 0.665f, 0.429f)
            curveToRelative(0.428f, 0.055f, 0.773f, -0.228f, 0.801f, -0.251f)
            lineToRelative(2.889f, -2.298f)
            lineToRelative(0.708f, 0.988f)
            lineToRelative(-4.152f, 3.302f)
            curveToRelative(-0.389f, -0.368f, -0.828f, -0.667f, -1.367f, -0.667f)
            horizontalLineTo(8.395f)
            curveToRelative(-0.825f, 0f, -1.526f, 0.67f, -2.145f, 1.262f)
            curveToRelative(-0.369f, 0.353f, -0.788f, 0.754f, -1.019f, 0.754f)
            curveToRelative(-0.448f, 0f, -1.028f, -0.677f, -1.196f, -0.915f)
            curveToRelative(-0.114f, -0.161f, -0.331f, -0.195f, -0.486f, -0.075f)
            curveToRelative(-0.154f, 0.12f, -0.186f, 0.349f, -0.072f, 0.511f)
            curveToRelative(0.087f, 0.124f, 0.877f, 1.21f, 1.753f, 1.21f)
            curveToRelative(0.499f, 0f, 0.956f, -0.437f, 1.485f, -0.943f)
            curveToRelative(0.526f, -0.502f, 1.121f, -1.073f, 1.679f, -1.073f)
            horizontalLineToRelative(0f)
            curveToRelative(0.491f, 0f, 0.92f, 0.471f, 1.375f, 0.97f)
            curveToRelative(0.505f, 0.554f, 1.028f, 1.127f, 1.737f, 1.127f)
            curveToRelative(0.677f, 0f, 1.25f, -0.494f, 1.803f, -0.973f)
            curveToRelative(0.517f, -0.447f, 1.052f, -0.909f, 1.615f, -0.909f)
            curveToRelative(0.336f, 0.001f, 0.651f, 0.307f, 1.016f, 0.66f)
            curveToRelative(0.474f, 0.46f, 1.011f, 0.98f, 1.791f, 0.98f)
            horizontalLineToRelative(0.001f)
            curveToRelative(0.784f, 0f, 1.304f, -0.525f, 1.764f, -0.988f)
            curveToRelative(0.432f, -0.436f, 0.805f, -0.812f, 1.348f, -0.812f)
            curveToRelative(0.753f, 0f, 1.323f, 0.786f, 1.329f, 0.795f)
            curveToRelative(0.116f, 0.162f, 0.332f, 0.192f, 0.486f, 0.073f)
            curveTo(22.813f, 20.89f, 22.846f, 20.663f, 22.732f, 20.5f)
            close()

            moveTo(12.871f, 20.647f)
            curveToRelative(-0.48f, 0.415f, -0.934f, 0.807f, -1.363f, 0.807f)
            curveToRelative(-0.414f, -0.001f, -0.814f, -0.439f, -1.237f, -0.903f)
            curveToRelative(-0.002f, -0.003f, -0.005f, -0.005f, -0.007f, -0.008f)
            lineToRelative(4.624f, -3.678f)
            lineToRelative(-1.545f, -2.156f)
            lineTo(9.89f, 17.456f)
            curveToRelative(-0.023f, 0.02f, -0.169f, 0.125f, -0.289f, 0.101f)
            curveToRelative(-0.025f, -0.003f, -0.101f, -0.013f, -0.195f, -0.143f)
            curveToRelative(-0.204f, -0.286f, 0.011f, -0.533f, 0.058f, -0.581f)
            lineToRelative(4.026f, -3.202f)
            lineToRelative(3.931f, 5.489f)
            lineToRelative(-1.299f, 1.033f)
            curveToRelative(-0.348f, -0.315f, -0.723f, -0.58f, -1.197f, -0.58f)
            curveTo(14.115f, 19.572f, 13.455f, 20.143f, 12.871f, 20.647f)
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
private fun IcTtActivityIconPreview() {
    Icon(
        imageVector = IcTtActivity,
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
<g id="ic_tt_activity">
	<g display="inline">
		<g>
			<path fill="#67DFE8" d="M19.392,17.722c1.246,0,2.26-1.068,2.26-2.382c0-1.314-1.014-2.382-2.26-2.382
				c-1.246,0-2.26,1.069-2.26,2.382C17.132,16.654,18.146,17.722,19.392,17.722z M19.392,13.69c0.863,0,1.567,0.74,1.567,1.65
				c0,0.91-0.703,1.65-1.567,1.65c-0.863,0-1.567-0.74-1.567-1.65C17.826,14.43,18.529,13.69,19.392,13.69z"/>
			<path fill="#67DFE8" d="M22.732,20.5c-0.032-0.045-0.781-1.088-1.886-1.088c-0.822,0-1.356,0.539-1.828,1.015
				c-0.418,0.421-0.779,0.786-1.284,0.786h-0.001c-0.411,0-0.748-0.253-1.082-0.563l1.744-1.388l-4.768-6.657l-4.601,3.661
				c-0.271,0.253-0.656,0.911-0.172,1.587c0.224,0.313,0.485,0.406,0.665,0.429c0.428,0.055,0.773-0.228,0.801-0.251l2.889-2.298
				l0.708,0.988l-4.152,3.302c-0.389-0.368-0.828-0.667-1.367-0.667H8.395c-0.825,0-1.526,0.67-2.145,1.262
				c-0.369,0.353-0.788,0.754-1.019,0.754c-0.448,0-1.028-0.677-1.196-0.915c-0.114-0.161-0.331-0.195-0.486-0.075
				c-0.154,0.12-0.186,0.349-0.072,0.511c0.087,0.124,0.877,1.21,1.753,1.21c0.499,0,0.956-0.437,1.485-0.943
				c0.526-0.502,1.121-1.073,1.679-1.073h0c0.491,0,0.92,0.471,1.375,0.97c0.505,0.554,1.028,1.127,1.737,1.127
				c0.677,0,1.25-0.494,1.803-0.973c0.517-0.447,1.052-0.909,1.615-0.909c0.336,0.001,0.651,0.307,1.016,0.66
				c0.474,0.46,1.011,0.98,1.791,0.98h0.001c0.784,0,1.304-0.525,1.764-0.988c0.432-0.436,0.805-0.812,1.348-0.812
				c0.753,0,1.323,0.786,1.329,0.795c0.116,0.162,0.332,0.192,0.486,0.073C22.813,20.89,22.846,20.663,22.732,20.5z M12.871,20.647
				c-0.48,0.415-0.934,0.807-1.363,0.807c-0.414-0.001-0.814-0.439-1.237-0.903c-0.002-0.003-0.005-0.005-0.007-0.008l4.624-3.678
				l-1.545-2.156L9.89,17.456c-0.023,0.02-0.169,0.125-0.289,0.101c-0.025-0.003-0.101-0.013-0.195-0.143
				c-0.204-0.286,0.011-0.533,0.058-0.581l4.026-3.202l3.931,5.489l-1.299,1.033c-0.348-0.315-0.723-0.58-1.197-0.58
				C14.115,19.572,13.455,20.143,12.871,20.647z"/>
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
			<path fill="#67E86A" d="M14.255,6.082h-3.139c-0.258,0-0.466-0.214-0.466-0.478V3.7H5.281l0,1.904
				c0,0.264-0.209,0.478-0.466,0.478H1.666C1.408,6.082,1.2,5.868,1.2,5.604c0-0.264,0.208-0.478,0.466-0.478h2.683l0-1.904
				c0-0.264,0.209-0.478,0.466-0.478h6.3c0.258,0,0.466,0.214,0.466,0.478v1.904h2.673c0.258,0,0.466,0.214,0.466,0.478
				C14.721,5.868,14.512,6.082,14.255,6.082z"/>
		</g>
	</g>
</g>
</svg>
 */