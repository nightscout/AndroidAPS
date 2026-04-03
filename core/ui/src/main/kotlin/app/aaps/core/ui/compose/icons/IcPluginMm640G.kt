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
 * Icon for MM640G CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginMM640G: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMM640G",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // First path (opacity 0.3)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.3f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.216f, 6.125f)
            curveToRelative(-0.11f, -0.305f, -0.203f, -0.563f, -0.203f, -0.6f)
            lineToRelative(-0.765f, -2.428f)
            curveToRelative(0f, -0.098f, -0.167f, -0.177f, -0.265f, -0.177f)
            horizontalLineTo(12f)
            horizontalLineToRelative(-0.983f)
            curveToRelative(-0.098f, 0f, -0.265f, 0.079f, -0.265f, 0.177f)
            lineTo(9.987f, 5.525f)
            curveToRelative(0f, 0.037f, -0.093f, 0.295f, -0.203f, 0.6f)
            curveToRelative(-0.039f, 0.11f, -0.35f, 0.035f, -0.35f, 0.156f)
            curveToRelative(0f, 0.545f, 0f, 0.863f, 0f, 1.01f)
            horizontalLineTo(12f)
            horizontalLineToRelative(2.565f)
            curveToRelative(0f, -0.147f, 0f, -0.465f, 0f, -1.01f)
            curveToRelative(0f, -0.121f, -0.31f, -0.046f, -0.349f, -0.156f)
            close()
        }

        // Main path inside group (no explicit opacity, so inherits group opacity 0.5)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(17.194f, 3.731f)
            curveTo(17.194f, 2.219f, 14.869f, 1.2f, 12f, 1.2f)
            reflectiveCurveTo(6.806f, 2.219f, 6.806f, 3.731f)
            curveToRelative(0f, 0.397f, 0.331f, 0.375f, 0.331f, 0.589f)
            curveToRelative(0f, 0.313f, -0.228f, 0.397f, -0.228f, 0.647f)
            curveToRelative(0f, 0.25f, 0.324f, 0.249f, 0.324f, 0.471f)
            curveToRelative(0f, 0.186f, -0.184f, 0.287f, -0.184f, 0.53f)
            curveToRelative(0f, 0.309f, 0.272f, 0.383f, 0.272f, 0.633f)
            curveToRelative(0f, 0.221f, -0.177f, 0.206f, -0.177f, 0.424f)
            curveToRelative(0f, 0.216f, 0.045f, 0.268f, 0.191f, 0.268f)
            horizontalLineToRelative(1.739f)
            verticalLineTo(6.078f)
            curveToRelative(0f, -0.09f, 0.068f, -0.161f, 0.154f, -0.172f)
            verticalLineTo(4.22f)
            lineTo(8.147f, 3.475f)
            curveToRelative(-0.025f, -0.094f, 0.047f, -0.196f, 0.162f, -0.228f)
            lineToRelative(0.848f, -0.231f)
            curveToRelative(0.115f, -0.031f, 0.229f, 0.02f, 0.255f, 0.114f)
            lineToRelative(0.105f, 0.383f)
            curveToRelative(0.011f, 0.041f, 0.065f, 0.253f, 0.112f, 0.48f)
            horizontalLineToRelative(0.543f)
            curveToRelative(0.077f, 0f, 0.194f, 0.084f, 0.229f, 0.22f)
            lineToRelative(0.352f, -1.116f)
            curveToRelative(0f, -0.098f, 0.167f, -0.177f, 0.265f, -0.177f)
            horizontalLineTo(12f)
            horizontalLineToRelative(0.983f)
            curveToRelative(0.098f, 0f, 0.265f, 0.079f, 0.265f, 0.177f)
            lineTo(13.6f, 4.213f)
            curveToRelative(0.035f, -0.136f, 0.152f, -0.22f, 0.229f, -0.22f)
            horizontalLineToRelative(0.543f)
            curveToRelative(0.047f, -0.227f, 0.1f, -0.439f, 0.112f, -0.48f)
            lineToRelative(0.105f, -0.383f)
            curveToRelative(0.026f, -0.094f, 0.14f, -0.145f, 0.255f, -0.113f)
            lineToRelative(0.848f, 0.231f)
            curveToRelative(0.115f, 0.031f, 0.188f, 0.133f, 0.162f, 0.227f)
            lineTo(14.771f, 4.22f)
            verticalLineToRelative(1.685f)
            curveToRelative(0.087f, 0.011f, 0.154f, 0.082f, 0.154f, 0.172f)
            verticalLineToRelative(1.214f)
            horizontalLineToRelative(1.739f)
            curveToRelative(0.146f, 0f, 0.191f, -0.052f, 0.191f, -0.268f)
            curveToRelative(0f, -0.218f, -0.177f, -0.203f, -0.177f, -0.424f)
            curveToRelative(0f, -0.25f, 0.272f, -0.324f, 0.272f, -0.633f)
            curveToRelative(0f, -0.243f, -0.184f, -0.343f, -0.184f, -0.53f)
            curveToRelative(0f, -0.222f, 0.324f, -0.221f, 0.324f, -0.471f)
            curveToRelative(0f, -0.25f, -0.228f, -0.334f, -0.228f, -0.647f)
            curveToRelative(0f, -0.214f, 0.331f, -0.192f, 0.331f, -0.589f)
            close()
        }

        // Last inner path inside group (opacity 0.8 -> final 0.4)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.29f, 6.18f)
            curveToRelative(-0.002f, -0.001f, -0.004f, -0.002f, -0.007f, -0.002f)
            curveToRelative(0.002f, 0f, 0.004f, 0.001f, 0.007f, 0.002f)
            close()
        }

        // Final main path (full opacity)
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
            moveTo(16.664f, 7.292f)
            curveToRelative(-1.677f, 0f, -2.999f, 0f, -4.708f, 0f)
            curveToRelative(-1.704f, 0f, -3.325f, 0f, -4.65f, 0f)
            curveToRelative(0f, 3.502f, -4.914f, 2.178f, -4.914f, 6.71f)
            curveToRelative(0f, 4.708f, 4.623f, 8.799f, 9.564f, 8.799f)
            reflectiveCurveToRelative(9.652f, -4.414f, 9.652f, -8.799f)
            curveToRelative(0f, -3.332f, -4.944f, -2.008f, -4.944f, -5.509f)
            close()
            moveTo(17.67f, 14.315f)
            curveToRelative(-0.131f, 0.127f, -0.321f, 0.238f, -0.57f, 0.335f)
            curveToRelative(-0.249f, 0.096f, -0.501f, 0.145f, -0.756f, 0.145f)
            curveToRelative(-0.324f, 0f, -0.607f, -0.068f, -0.848f, -0.204f)
            curveToRelative(-0.241f, -0.136f, -0.422f, -0.33f, -0.543f, -0.583f)
            curveToRelative(-0.121f, -0.253f, -0.182f, -0.528f, -0.182f, -0.825f)
            curveToRelative(0f, -0.323f, 0.068f, -0.61f, 0.203f, -0.86f)
            curveToRelative(0.135f, -0.251f, 0.333f, -0.443f, 0.594f, -0.577f)
            curveToRelative(0.199f, -0.103f, 0.446f, -0.154f, 0.742f, -0.154f)
            curveToRelative(0.385f, 0f, 0.685f, 0.081f, 0.902f, 0.242f)
            curveToRelative(0.216f, 0.161f, 0.355f, 0.384f, 0.417f, 0.669f)
            lineToRelative(-0.621f, 0.116f)
            curveToRelative(-0.044f, -0.152f, -0.126f, -0.272f, -0.246f, -0.36f)
            curveToRelative(-0.12f, -0.088f, -0.271f, -0.132f, -0.451f, -0.132f)
            curveToRelative(-0.273f, 0f, -0.491f, 0.087f, -0.652f, 0.26f)
            curveToRelative(-0.161f, 0.173f, -0.242f, 0.431f, -0.242f, 0.772f)
            curveToRelative(0f, 0.368f, 0.082f, 0.644f, 0.245f, 0.827f)
            curveToRelative(0.163f, 0.184f, 0.378f, 0.276f, 0.643f, 0.276f)
            curveToRelative(0.131f, 0f, 0.262f, -0.026f, 0.394f, -0.077f)
            curveToRelative(0.132f, -0.052f, 0.245f, -0.114f, 0.339f, -0.187f)
            verticalLineToRelative(-0.393f)
            horizontalLineToRelative(-0.717f)
            verticalLineTo(13.08f)
            horizontalLineToRelative(1.349f)
            verticalLineTo(14.315f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginMM640GPreview() {
    Icon(
        imageVector = IcPluginMM640G,
        contentDescription = "MM640G Plugin Icon",
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
<g id="MM640G">
	<g>
		<path opacity="0.3" fill="#FFFFFF" d="M14.216,6.125c-0.11-0.305-0.203-0.563-0.203-0.6l-0.765-2.428
			c0-0.098-0.167-0.177-0.265-0.177H12h-0.983c-0.098,0-0.265,0.079-0.265,0.177L9.987,5.525c0,0.037-0.093,0.295-0.203,0.6
			c-0.039,0.11-0.35,0.035-0.35,0.156c0,0.545,0,0.863,0,1.01H12h2.565c0-0.147,0-0.465,0-1.01
			C14.565,6.16,14.255,6.235,14.216,6.125z"/>
		<g opacity="0.5">
			<path opacity="0.8" fill="#FFFFFF" d="M17.194,3.731C17.194,2.219,14.869,1.2,12,1.2S6.806,2.219,6.806,3.731
				c0,0.397,0.331,0.375,0.331,0.589c0,0.313-0.228,0.397-0.228,0.647c0,0.25,0.324,0.249,0.324,0.471
				c0,0.186-0.184,0.287-0.184,0.53c0,0.309,0.272,0.383,0.272,0.633c0,0.221-0.177,0.206-0.177,0.424
				c0,0.216,0.045,0.268,0.191,0.268h1.739V6.078c0-0.09,0.068-0.161,0.154-0.172V4.22L8.147,3.475
				C8.122,3.381,8.194,3.279,8.309,3.247l0.848-0.231C9.272,2.985,9.386,3.036,9.412,3.13l0.105,0.383
				c0.011,0.041,0.065,0.253,0.112,0.48h0.543c0.077,0,0.194,0.084,0.229,0.22l0.352-1.116c0-0.098,0.167-0.177,0.265-0.177H12
				h0.983c0.098,0,0.265,0.079,0.265,0.177L13.6,4.213c0.035-0.136,0.152-0.22,0.229-0.22h0.543c0.047-0.227,0.1-0.439,0.112-0.48
				l0.105-0.383c0.026-0.094,0.14-0.145,0.255-0.113l0.848,0.231c0.115,0.031,0.188,0.133,0.162,0.227L14.771,4.22v1.685
				c0.087,0.011,0.154,0.082,0.154,0.172v1.214h1.739c0.146,0,0.191-0.052,0.191-0.268c0-0.218-0.177-0.203-0.177-0.424
				c0-0.25,0.272-0.324,0.272-0.633c0-0.243-0.184-0.343-0.184-0.53c0-0.222,0.324-0.221,0.324-0.471
				c0-0.25-0.228-0.334-0.228-0.647C16.863,4.106,17.194,4.128,17.194,3.731z"/>
			<path opacity="0.8" fill="#FFFFFF" d="M14.29,6.18c-0.002-0.001-0.004-0.002-0.007-0.002C14.285,6.178,14.287,6.179,14.29,6.18z"
				/>
		</g>
		<path fill="#FFFFFF" d="M16.664,7.292c-1.677,0-2.999,0-4.708,0c-1.704,0-3.325,0-4.65,0c0,3.502-4.914,2.178-4.914,6.71
			c0,4.708,4.623,8.799,9.564,8.799s9.652-4.414,9.652-8.799C21.608,9.469,16.664,10.793,16.664,7.292z M17.67,14.315
			c-0.131,0.127-0.321,0.238-0.57,0.335c-0.249,0.096-0.501,0.145-0.756,0.145c-0.324,0-0.607-0.068-0.848-0.204
			c-0.241-0.136-0.422-0.33-0.543-0.583c-0.121-0.253-0.182-0.528-0.182-0.825c0-0.323,0.068-0.61,0.203-0.86
			c0.135-0.251,0.333-0.443,0.594-0.577c0.199-0.103,0.446-0.154,0.742-0.154c0.385,0,0.685,0.081,0.902,0.242
			c0.216,0.161,0.355,0.384,0.417,0.669l-0.621,0.116c-0.044-0.152-0.126-0.272-0.246-0.36c-0.12-0.088-0.271-0.132-0.451-0.132
			c-0.273,0-0.491,0.087-0.652,0.26c-0.161,0.173-0.242,0.431-0.242,0.772c0,0.368,0.082,0.644,0.245,0.827
			c0.163,0.184,0.378,0.276,0.643,0.276c0.131,0,0.262-0.026,0.394-0.077c0.132-0.052,0.245-0.114,0.339-0.187v-0.393h-0.717V13.08
			h1.349V14.315z"/>
	</g>
</g>
</svg>
 */