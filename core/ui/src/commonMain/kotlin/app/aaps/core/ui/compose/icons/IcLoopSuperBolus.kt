package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Superbolus Loop.
 * Represents superbolus insulin delivery mode.
 *
 * Bounding box: x: 2.0-22.8, y: 3.2-21.8 (viewport: 24x24, ~90% width)
 *
 * @see IcLoopSuperbolusIconPreview
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
