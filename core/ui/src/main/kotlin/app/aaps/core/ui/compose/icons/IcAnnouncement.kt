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
 * Icon for Announcement treatment type.
 * Represents patient announcements/notes.
 *
 * replaces ic_cp_announcement
 *
 * Bounding box: x: 1.5-22.0, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcAnnouncement: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAnnouncement",
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
            moveTo(11.139f, 17.791f)
            curveToRelative(0.359f, 0.441f, 0.721f, 0.908f, 0.656f, 1.567f)
            curveToRelative(-0.028f, 0.287f, 0.254f, 0.422f, 0.468f, 0.566f)
            curveToRelative(0.379f, 0.255f, 0.776f, 0.486f, 1.134f, 0.768f)
            curveToRelative(0.584f, 0.459f, 0.551f, 0.893f, -0.068f, 1.282f)
            curveToRelative(-0.024f, 0.015f, -0.053f, 0.023f, -0.077f, 0.037f)
            curveToRelative(-1.908f, 1.116f, -1.91f, 1.119f, -3.503f, -0.445f)
            curveToRelative(-0.308f, -0.302f, -0.648f, -0.557f, -0.996f, -0.808f)
            curveToRelative(-0.523f, -0.378f, -1.029f, -0.565f, -1.636f, -0.145f)
            curveToRelative(-0.397f, 0.274f, -0.893f, 0.242f, -1.336f, 0.159f)
            curveToRelative(-1.938f, -0.361f, -3.766f, -2.196f, -3.628f, -4.353f)
            curveToRelative(0.027f, -0.421f, 0.15f, -0.782f, 0.425f, -1.092f)
            curveToRelative(0.473f, -0.534f, 0.94f, -1.075f, 1.433f, -1.591f)
            curveToRelative(1.053f, -1.102f, 1.622f, -2.391f, 1.616f, -3.93f)
            curveTo(5.622f, 8.494f, 5.615f, 7.183f, 6.298f, 5.981f)
            curveToRelative(0.668f, -1.176f, 1.45f, -1.552f, 2.78f, -1.272f)
            curveToRelative(1.491f, 0.314f, 2.75f, 1.096f, 3.937f, 1.997f)
            curveToRelative(1.723f, 1.309f, 3.164f, 2.882f, 4.262f, 4.754f)
            curveToRelative(0.566f, 0.966f, 1.028f, 1.963f, 1.05f, 3.126f)
            curveToRelative(0.016f, 0.838f, -0.361f, 1.439f, -1.01f, 1.864f)
            curveToRelative(-0.722f, 0.473f, -1.512f, 0.762f, -2.415f, 0.753f)
            curveToRelative(-1.109f, -0.01f, -2.218f, 0.085f, -3.312f, 0.298f)
            curveTo(11.422f, 17.534f, 11.25f, 17.566f, 11.139f, 17.791f)
            close()

            moveTo(15.964f, 15.131f)
            curveToRelative(0.814f, 0.019f, 1.146f, -0.311f, 1.054f, -0.994f)
            curveToRelative(-0.076f, -0.565f, -0.261f, -1.102f, -0.549f, -1.609f)
            curveToRelative(-1.297f, -2.287f, -3.143f, -4.054f, -5.262f, -5.554f)
            curveToRelative(-0.672f, -0.476f, -1.398f, -0.873f, -2.246f, -1.009f)
            curveTo(8.118f, 5.828f, 7.792f, 6.08f, 7.812f, 6.941f)
            curveToRelative(0.015f, 0.653f, 0.28f, 1.225f, 0.603f, 1.764f)
            curveToRelative(1.327f, 2.218f, 3.098f, 4.016f, 5.248f, 5.441f)
            curveTo(14.402f, 14.636f, 15.183f, 15.05f, 15.964f, 15.131f)
            close()

            moveTo(14.074f, 15.887f)
            curveToRelative(-2.942f, -1.676f, -5.256f, -3.962f, -7.018f, -6.86f)
            curveTo(6.968f, 9.339f, 6.98f, 9.61f, 6.966f, 9.881f)
            curveToRelative(-0.046f, 0.886f, -0.116f, 1.806f, -0.571f, 2.56f)
            curveToRelative(-0.493f, 0.817f, -0.344f, 1.495f, 0.07f, 2.226f)
            curveToRelative(0.588f, 1.039f, 1.428f, 1.764f, 2.583f, 2.108f)
            curveToRelative(0.309f, 0.092f, 0.636f, 0.154f, 0.919f, -0.004f)
            curveTo(11.235f, 16.063f, 12.615f, 15.945f, 14.074f, 15.887f)
            close()

            moveTo(19.315f, 10.836f)
            curveToRelative(-0.356f, 0f, -0.527f, 0.007f, -0.697f, -0.002f)
            curveToRelative(-0.224f, -0.012f, -0.376f, -0.132f, -0.426f, -0.351f)
            curveToRelative(-0.053f, -0.231f, 0.073f, -0.402f, 0.266f, -0.48f)
            curveToRelative(0.421f, -0.169f, 0.852f, -0.314f, 1.285f, -0.453f)
            curveToRelative(0.351f, -0.113f, 0.709f, -0.202f, 1.064f, -0.304f)
            curveToRelative(0.687f, -0.197f, 0.768f, -0.122f, 0.985f, 0.505f)
            curveToRelative(0.228f, 0.659f, -0.219f, 0.742f, -0.64f, 0.82f)
            curveTo(20.485f, 10.695f, 19.807f, 10.768f, 19.315f, 10.836f)
            close()

            moveTo(11.967f, 3.94f)
            curveToRelative(0.111f, -0.717f, 0.227f, -1.505f, 0.358f, -2.29f)
            curveToRelative(0.09f, -0.541f, 0.518f, -0.471f, 0.873f, -0.405f)
            curveToRelative(0.404f, 0.074f, 0.677f, 0.279f, 0.495f, 0.783f)
            curveToRelative(-0.26f, 0.724f, -0.48f, 1.462f, -0.723f, 2.192f)
            curveToRelative(-0.103f, 0.309f, -0.251f, 0.626f, -0.644f, 0.551f)
            curveTo(11.911f, 4.692f, 11.956f, 4.327f, 11.967f, 3.94f)
            close()

            moveTo(16.128f, 7.225f)
            curveToRelative(-0.44f, -0.003f, -0.67f, -0.372f, -0.418f, -0.696f)
            curveToRelative(0.556f, -0.716f, 1.142f, -1.412f, 1.755f, -2.08f)
            curveToRelative(0.296f, -0.322f, 0.647f, -0.132f, 0.903f, 0.113f)
            curveToRelative(0.23f, 0.22f, 0.43f, 0.521f, 0.125f, 0.814f)
            curveToRelative(-0.675f, 0.648f, -1.386f, 1.258f, -2.158f, 1.79f)
            curveTo(16.269f, 7.212f, 16.174f, 7.213f, 16.128f, 7.225f)
            close()

            moveTo(13.662f, 11.656f)
            curveToRelative(0f, 0.2f, 0.03f, 0.406f, -0.006f, 0.599f)
            curveToRelative(-0.087f, 0.47f, -0.46f, 0.662f, -0.805f, 0.357f)
            curveToRelative(-0.922f, -0.813f, -1.819f, -1.658f, -2.594f, -2.618f)
            curveTo(10f, 9.675f, 10.075f, 9.445f, 10.491f, 9.296f)
            curveTo(12.09f, 8.724f, 13.686f, 9.897f, 13.662f, 11.656f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAnnouncementIconPreview() {
    Icon(
        imageVector = IcAnnouncement,
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
<g id="ic_announcement">
	<g display="inline">
		<path fill="#CF8BFE" d="M11.139,17.791c0.359,0.441,0.721,0.908,0.656,1.567c-0.028,0.287,0.254,0.422,0.468,0.566
			c0.379,0.255,0.776,0.486,1.134,0.768c0.584,0.459,0.551,0.893-0.068,1.282c-0.024,0.015-0.053,0.023-0.077,0.037
			c-1.908,1.116-1.91,1.119-3.503-0.445c-0.308-0.302-0.648-0.557-0.996-0.808c-0.523-0.378-1.029-0.565-1.636-0.145
			c-0.397,0.274-0.893,0.242-1.336,0.159c-1.938-0.361-3.766-2.196-3.628-4.353c0.027-0.421,0.15-0.782,0.425-1.092
			c0.473-0.534,0.94-1.075,1.433-1.591c1.053-1.102,1.622-2.391,1.616-3.93C5.622,8.494,5.615,7.183,6.298,5.981
			c0.668-1.176,1.45-1.552,2.78-1.272c1.491,0.314,2.75,1.096,3.937,1.997c1.723,1.309,3.164,2.882,4.262,4.754
			c0.566,0.966,1.028,1.963,1.05,3.126c0.016,0.838-0.361,1.439-1.01,1.864c-0.722,0.473-1.512,0.762-2.415,0.753
			c-1.109-0.01-2.218,0.085-3.312,0.298C11.422,17.534,11.25,17.566,11.139,17.791z M15.964,15.131
			c0.814,0.019,1.146-0.311,1.054-0.994c-0.076-0.565-0.261-1.102-0.549-1.609c-1.297-2.287-3.143-4.054-5.262-5.554
			c-0.672-0.476-1.398-0.873-2.246-1.009C8.118,5.828,7.792,6.08,7.812,6.941c0.015,0.653,0.28,1.225,0.603,1.764
			c1.327,2.218,3.098,4.016,5.248,5.441C14.402,14.636,15.183,15.05,15.964,15.131z M14.074,15.887
			c-2.942-1.676-5.256-3.962-7.018-6.86C6.968,9.339,6.98,9.61,6.966,9.881c-0.046,0.886-0.116,1.806-0.571,2.56
			c-0.493,0.817-0.344,1.495,0.07,2.226c0.588,1.039,1.428,1.764,2.583,2.108c0.309,0.092,0.636,0.154,0.919-0.004
			C11.235,16.063,12.615,15.945,14.074,15.887z"/>
		<path fill="#CF8BFE" d="M19.315,10.836c-0.356,0-0.527,0.007-0.697-0.002c-0.224-0.012-0.376-0.132-0.426-0.351
			c-0.053-0.231,0.073-0.402,0.266-0.48c0.421-0.169,0.852-0.314,1.285-0.453c0.351-0.113,0.709-0.202,1.064-0.304
			c0.687-0.197,0.768-0.122,0.985,0.505c0.228,0.659-0.219,0.742-0.64,0.82C20.485,10.695,19.807,10.768,19.315,10.836z"/>
		<path fill="#CF8BFE" d="M11.967,3.94c0.111-0.717,0.227-1.505,0.358-2.29c0.09-0.541,0.518-0.471,0.873-0.405
			c0.404,0.074,0.677,0.279,0.495,0.783c-0.26,0.724-0.48,1.462-0.723,2.192c-0.103,0.309-0.251,0.626-0.644,0.551
			C11.911,4.692,11.956,4.327,11.967,3.94z"/>
		<path fill="#CF8BFE" d="M16.128,7.225c-0.44-0.003-0.67-0.372-0.418-0.696c0.556-0.716,1.142-1.412,1.755-2.08
			c0.296-0.322,0.647-0.132,0.903,0.113c0.23,0.22,0.43,0.521,0.125,0.814c-0.675,0.648-1.386,1.258-2.158,1.79
			C16.269,7.212,16.174,7.213,16.128,7.225z"/>
		<path fill="#CF8BFE" d="M13.662,11.656c0,0.2,0.03,0.406-0.006,0.599c-0.087,0.47-0.46,0.662-0.805,0.357
			c-0.922-0.813-1.819-1.658-2.594-2.618C10,9.675,10.075,9.445,10.491,9.296C12.09,8.724,13.686,9.897,13.662,11.656z"/>
	</g>
</g>
</svg>
 */