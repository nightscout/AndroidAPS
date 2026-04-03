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
 * Icon for Superbolus Loop.
 * Represents superbolus insulin delivery mode.
 *
 * Bounding box: x: 2.0-22.8, y: 3.2-21.8 (viewport: 24x24, ~90% width)
 */
val IcLoopSuperbolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopSuperbolus",
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
            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 3.888f, -3.152f, 7.039f, -7.039f, 7.039f)
            curveToRelative(-3.888f, 0f, -7.039f, -3.152f, -7.039f, -7.039f)
            curveToRelative(0f, -3.888f, 3.152f, -7.039f, 7.039f, -7.039f)
            curveToRelative(1.054f, 0f, 2.051f, 0.238f, 2.949f, 0.654f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineToRelative(1.845f, -0.529f)
            curveToRelative(-1.542f, -1.017f, -3.386f, -1.612f, -5.371f, -1.612f)
            curveToRelative(-5.399f, 0f, -9.775f, 4.376f, -9.775f, 9.775f)
            curveToRelative(0f, 5.399f, 4.376f, 9.775f, 9.775f, 9.775f)
            curveToRelative(5.399f, 0f, 9.775f, -4.376f, 9.775f, -9.775f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()

            moveTo(13.972f, 13.491f)
            curveToRelative(-0.262f, 0.329f, -0.546f, 0.571f, -0.861f, 0.785f)
            curveToRelative(-0.299f, 0.203f, -0.667f, 0.308f, -0.951f, 0.37f)
            curveToRelative(-0.543f, 0.099f, -0.198f, 0.148f, 0.076f, 0.519f)
            curveToRelative(-0.985f, 1.407f, -2.646f, 1.501f, -4.031f, 0.783f)
            curveToRelative(-0.111f, -0.058f, -0.215f, -0.13f, -0.319f, -0.201f)
            curveToRelative(-0.218f, -0.15f, -0.315f, -0.186f, -0.498f, 0.003f)
            curveToRelative(-0.148f, 0.153f, -0.496f, 0.488f, -0.673f, 0.662f)
            lineToRelative(-0.388f, -0.42f)
            curveToRelative(0.19f, -0.173f, 0.41f, -0.388f, 0.567f, -0.536f)
            curveToRelative(0.344f, -0.323f, 0.266f, -0.362f, -0.009f, -0.754f)
            curveToRelative(-0.953f, -1.363f, -0.661f, -3.142f, 0.679f, -4.138f)
            curveToRelative(0.571f, 0.365f, 0.447f, 0.279f, 0.633f, -0.258f)
            curveToRelative(0.198f, -0.573f, 0.535f, -1.063f, 1.047f, -1.404f)
            curveToRelative(0.521f, 0.46f, 0.434f, 0.25f, 0.643f, -0.277f)
            curveToRelative(0.226f, -0.57f, 0.58f, -1.035f, 1.069f, -1.427f)
            curveToRelative(0.55f, 0.362f, 0.801f, 0.764f, 0.989f, 1.215f)
            curveToRelative(0.216f, 0.518f, 0.299f, 0.344f, 0.678f, -0.023f)
            curveToRelative(0.808f, -0.784f, 1.796f, -1.095f, 2.759f, -0.859f)
            curveToRelative(0.226f, 1.253f, -0.308f, 2.375f, -1.469f, 3.107f)
            curveToRelative(0.609f, 0.165f, 1.185f, 0.494f, 1.751f, 1.16f)
            curveToRelative(-0.347f, 0.508f, -0.958f, 0.995f, -1.575f, 1.098f)
            curveTo(13.207f, 13.044f, 13.704f, 13.077f, 13.972f, 13.491f)
            close()

            moveTo(14.85f, 8.422f)
            curveToRelative(0.01f, -0.349f, -0.101f, -0.442f, -0.403f, -0.401f)
            curveToRelative(-0.983f, 0.132f, -1.907f, 1.014f, -2.071f, 1.978f)
            curveToRelative(-0.057f, 0.333f, 0.119f, 0.512f, 0.451f, 0.435f)
            curveToRelative(0.211f, -0.049f, 0.425f, -0.114f, 0.618f, -0.21f)
            curveTo(14.222f, 9.84f, 14.704f, 9.225f, 14.85f, 8.422f)
            close()

            moveTo(11.748f, 9.799f)
            curveToRelative(0.009f, -0.552f, -0.182f, -0.994f, -0.447f, -1.41f)
            curveToRelative(-0.254f, -0.4f, -0.431f, -0.399f, -0.686f, -0.009f)
            curveToRelative(-0.588f, 0.899f, -0.349f, 1.945f, 0.12f, 2.713f)
            curveToRelative(0.15f, 0.246f, 0.352f, 0.248f, 0.528f, 0.009f)
            curveTo(11.557f, 10.704f, 11.759f, 10.265f, 11.748f, 9.799f)
            close()

            moveTo(9.904f, 15.843f)
            curveToRelative(0.408f, 0f, 0.984f, -0.184f, 1.278f, -0.407f)
            curveToRelative(0.251f, -0.19f, 0.271f, -0.345f, 0.024f, -0.515f)
            curveToRelative(-0.92f, -0.634f, -1.86f, -0.772f, -2.833f, -0.094f)
            curveToRelative(-0.327f, 0.228f, -0.309f, 0.414f, 0.05f, 0.624f)
            curveTo(8.879f, 15.72f, 9.378f, 15.833f, 9.904f, 15.843f)
            close()

            moveTo(10.049f, 11.448f)
            curveToRelative(0.015f, -0.526f, -0.183f, -0.982f, -0.45f, -1.412f)
            curveToRelative(-0.249f, -0.401f, -0.42f, -0.391f, -0.675f, 0.02f)
            curveToRelative(-0.492f, 0.793f, -0.45f, 1.903f, 0.099f, 2.663f)
            curveToRelative(0.191f, 0.264f, 0.355f, 0.28f, 0.558f, 0.024f)
            curveTo(9.881f, 12.365f, 10.06f, 11.937f, 10.049f, 11.448f)
            close()

            moveTo(13.177f, 12.508f)
            curveToRelative(0.523f, -0.002f, 1.091f, -0.171f, 1.388f, -0.397f)
            curveToRelative(0.253f, -0.192f, 0.26f, -0.318f, 0.028f, -0.521f)
            curveToRelative(-0.74f, -0.648f, -2.082f, -0.69f, -2.849f, -0.09f)
            curveToRelative(-0.278f, 0.217f, -0.283f, 0.416f, 0.015f, 0.582f)
            curveTo(12.215f, 12.338f, 12.697f, 12.523f, 13.177f, 12.508f)
            close()

            moveTo(6.9f, 12.997f)
            curveToRelative(-0.019f, 0.433f, 0.098f, 0.882f, 0.353f, 1.29f)
            curveToRelative(0.241f, 0.386f, 0.449f, 0.391f, 0.706f, 0.029f)
            curveToRelative(0.557f, -0.786f, 0.51f, -1.981f, -0.107f, -2.717f)
            curveToRelative(-0.201f, -0.24f, -0.372f, -0.239f, -0.564f, 0.024f)
            curveTo(7.002f, 12.017f, 6.894f, 12.467f, 6.9f, 12.997f)
            close()

            moveTo(11.41f, 12.736f)
            curveToRelative(-0.527f, -0.025f, -0.988f, 0.152f, -1.398f, 0.465f)
            curveToRelative(-0.206f, 0.157f, -0.214f, 0.329f, -0.004f, 0.502f)
            curveToRelative(0.674f, 0.556f, 2.127f, 0.604f, 2.844f, 0.09f)
            curveToRelative(0.29f, -0.208f, 0.297f, -0.381f, 0.004f, -0.574f)
            curveTo(12.417f, 12.932f, 11.948f, 12.723f, 11.41f, 12.736f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcLoopSuperbolusIconPreview() {
    Icon(
        imageVector = IcLoopSuperbolus,
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
<g id="ic_loop_superbolus">
	<g display="inline">
		<path fill="#FEAF05" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,3.888-3.152,7.039-7.039,7.039c-3.888,0-7.039-3.152-7.039-7.039
			c0-3.888,3.152-7.039,7.039-7.039c1.054,0,2.051,0.238,2.949,0.654c0.32,0.148,0.629,0.316,0.921,0.508l0.002-0.002l-0.346-1.755
			l1.845-0.529c-1.542-1.017-3.386-1.612-5.371-1.612c-5.399,0-9.775,4.376-9.775,9.775c0,5.399,4.376,9.775,9.775,9.775
			c5.399,0,9.775-4.376,9.775-9.775c0-0.747-0.091-1.471-0.25-2.17c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
		<path fill="#FEAF05" d="M13.972,13.491c-0.262,0.329-0.546,0.571-0.861,0.785c-0.299,0.203-0.667,0.308-0.951,0.37
			c-0.543,0.099-0.198,0.148,0.076,0.519c-0.985,1.407-2.646,1.501-4.031,0.783c-0.111-0.058-0.215-0.13-0.319-0.201
			c-0.218-0.15-0.315-0.186-0.498,0.003c-0.148,0.153-0.496,0.488-0.673,0.662l-0.388-0.42c0.19-0.173,0.41-0.388,0.567-0.536
			c0.344-0.323,0.266-0.362-0.009-0.754c-0.953-1.363-0.661-3.142,0.679-4.138c0.571,0.365,0.447,0.279,0.633-0.258
			c0.198-0.573,0.535-1.063,1.047-1.404c0.521,0.46,0.434,0.25,0.643-0.277c0.226-0.57,0.58-1.035,1.069-1.427
			c0.55,0.362,0.801,0.764,0.989,1.215c0.216,0.518,0.299,0.344,0.678-0.023c0.808-0.784,1.796-1.095,2.759-0.859
			c0.226,1.253-0.308,2.375-1.469,3.107c0.609,0.165,1.185,0.494,1.751,1.16c-0.347,0.508-0.958,0.995-1.575,1.098
			C13.207,13.044,13.704,13.077,13.972,13.491z M14.85,8.422c0.01-0.349-0.101-0.442-0.403-0.401
			c-0.983,0.132-1.907,1.014-2.071,1.978c-0.057,0.333,0.119,0.512,0.451,0.435c0.211-0.049,0.425-0.114,0.618-0.21
			C14.222,9.84,14.704,9.225,14.85,8.422z M11.748,9.799c0.009-0.552-0.182-0.994-0.447-1.41c-0.254-0.4-0.431-0.399-0.686-0.009
			c-0.588,0.899-0.349,1.945,0.12,2.713c0.15,0.246,0.352,0.248,0.528,0.009C11.557,10.704,11.759,10.265,11.748,9.799z
			 M9.904,15.843c0.408,0,0.984-0.184,1.278-0.407c0.251-0.19,0.271-0.345,0.024-0.515c-0.92-0.634-1.86-0.772-2.833-0.094
			c-0.327,0.228-0.309,0.414,0.05,0.624C8.879,15.72,9.378,15.833,9.904,15.843z M10.049,11.448c0.015-0.526-0.183-0.982-0.45-1.412
			c-0.249-0.401-0.42-0.391-0.675,0.02c-0.492,0.793-0.45,1.903,0.099,2.663c0.191,0.264,0.355,0.28,0.558,0.024
			C9.881,12.365,10.06,11.937,10.049,11.448z M13.177,12.508c0.523-0.002,1.091-0.171,1.388-0.397
			c0.253-0.192,0.26-0.318,0.028-0.521c-0.74-0.648-2.082-0.69-2.849-0.09c-0.278,0.217-0.283,0.416,0.015,0.582
			C12.215,12.338,12.697,12.523,13.177,12.508z M6.9,12.997c-0.019,0.433,0.098,0.882,0.353,1.29
			c0.241,0.386,0.449,0.391,0.706,0.029c0.557-0.786,0.51-1.981-0.107-2.717c-0.201-0.24-0.372-0.239-0.564,0.024
			C7.002,12.017,6.894,12.467,6.9,12.997z M11.41,12.736c-0.527-0.025-0.988,0.152-1.398,0.465
			c-0.206,0.157-0.214,0.329-0.004,0.502c0.674,0.556,2.127,0.604,2.844,0.09c0.29-0.208,0.297-0.381,0.004-0.574
			C12.417,12.932,11.948,12.723,11.41,12.736z"/>
	</g>
</g>
</svg>
 */