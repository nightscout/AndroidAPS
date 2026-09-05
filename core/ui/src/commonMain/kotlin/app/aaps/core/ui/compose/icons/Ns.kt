package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for NSClient background service.
 * Represents the Nightscout client connection status.
 *
 * Bounding box: x: 4.0-19.9 y: 1.1-22.5 (viewport: 24x24, ~89% height)
 *
 * @see nsIconPreview
 */
val Ns: ImageVector by lazy {
    ImageVector.Builder(
        name = "NsClient",
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
            moveTo(19.937f, 8.998f)
            curveToRelative(0f, 0.712f, -0.092f, 1.6f, -0.132f, 1.889f)
            curveToRelative(-0.115f, 0.816f, -0.32f, 1.61f, -0.578f, 2.391f)
            curveToRelative(-0.335f, 1.015f, -0.758f, 1.992f, -1.27f, 2.931f)
            curveToRelative(-0.314f, 0.576f, -0.643f, 1.144f, -1.002f, 1.692f)
            curveToRelative(-0.388f, 0.592f, -0.799f, 1.166f, -1.236f, 1.723f)
            curveToRelative(-0.415f, 0.529f, -0.837f, 1.05f, -1.282f, 1.555f)
            curveToRelative(-0.145f, 0.164f, -0.298f, 0.322f, -0.444f, 0.485f)
            curveToRelative(-0.283f, 0.316f, -0.531f, 0.575f, -0.858f, 0.849f)
            curveToRelative(-0.712f, 0.67f, -1.759f, 0.616f, -2.378f, -0.081f)
            curveToRelative(-0.469f, -0.458f, -0.911f, -0.94f, -1.341f, -1.436f)
            curveToRelative(-0.531f, -0.613f, -1.037f, -1.245f, -1.521f, -1.896f)
            curveToRelative(-0.718f, -0.966f, -1.362f, -1.978f, -1.933f, -3.036f)
            curveToRelative(-0.41f, -0.758f, -0.755f, -1.549f, -1.041f, -2.362f)
            curveToRelative(-0.386f, -1.097f, -0.69f, -2.215f, -0.799f, -3.381f)
            curveToRelative(-0.013f, -0.143f, -0.059f, -0.71f, -0.059f, -1.319f)
            curveToRelative(0f, -0.647f, 0.067f, -1.158f, 0.096f, -1.358f)
            curveTo(4.27f, 6.89f, 4.52f, 6.182f, 4.846f, 5.499f)
            curveTo(5.113f, 4.94f, 5.45f, 4.426f, 5.845f, 3.944f)
            curveTo(6.226f, 3.479f, 6.65f, 3.061f, 7.12f, 2.693f)
            curveTo(7.636f, 2.288f, 8.198f, 1.95f, 8.805f, 1.69f)
            curveToRelative(0.667f, -0.285f, 1.353f, -0.494f, 2.075f, -0.586f)
            curveToRelative(0.127f, -0.016f, 0.598f, -0.062f, 1.166f, -0.062f)
            curveToRelative(0.728f, 0f, 1.44f, 0.108f, 1.764f, 0.184f)
            curveToRelative(0.732f, 0.172f, 1.428f, 0.44f, 2.086f, 0.812f)
            curveToRelative(0.93f, 0.526f, 1.725f, 1.21f, 2.383f, 2.046f)
            curveToRelative(0.473f, 0.601f, 0.853f, 1.26f, 1.128f, 1.976f)
            curveToRelative(0.193f, 0.503f, 0.347f, 1.016f, 0.434f, 1.551f)
            curveTo(19.871f, 7.8f, 19.937f, 8.371f, 19.937f, 8.998f)
            close()

            moveTo(12.052f, 2.302f)
            curveToRelative(-0.311f, -0.005f, -0.618f, 0.035f, -0.926f, 0.068f)
            curveTo(10.484f, 2.439f, 9.876f, 2.628f, 9.29f, 2.888f)
            curveToRelative(-1.007f, 0.447f, -1.839f, 1.12f, -2.525f, 1.983f)
            curveToRelative(-0.477f, 0.6f, -0.832f, 1.261f, -1.083f, 1.98f)
            curveTo(5.545f, 7.245f, 5.455f, 7.654f, 5.399f, 8.066f)
            curveTo(5.354f, 8.405f, 5.324f, 8.752f, 5.336f, 9.091f)
            curveToRelative(0.014f, 0.41f, 0.027f, 0.796f, 0.086f, 1.192f)
            curveToRelative(0.058f, 0.386f, 0.103f, 0.775f, 0.19f, 1.154f)
            curveToRelative(0.247f, 1.084f, 0.608f, 2.131f, 1.076f, 3.14f)
            curveToRelative(0.409f, 0.88f, 0.883f, 1.723f, 1.413f, 2.535f)
            curveToRelative(0.534f, 0.817f, 1.113f, 1.601f, 1.734f, 2.355f)
            curveToRelative(0.563f, 0.683f, 1.143f, 1.349f, 1.772f, 1.972f)
            curveToRelative(0.234f, 0.232f, 0.548f, 0.228f, 0.784f, 0f)
            curveToRelative(0.628f, -0.608f, 1.529f, -1.669f, 1.956f, -2.191f)
            curveToRelative(0.395f, -0.484f, 0.769f, -0.983f, 1.125f, -1.497f)
            curveToRelative(0.456f, -0.659f, 0.881f, -1.336f, 1.263f, -2.041f)
            curveToRelative(0.43f, -0.793f, 0.818f, -1.604f, 1.123f, -2.454f)
            curveToRelative(0.27f, -0.754f, 0.479f, -1.521f, 0.62f, -2.311f)
            curveToRelative(0.105f, -0.586f, 0.16f, -1.175f, 0.194f, -1.767f)
            curveToRelative(0.02f, -0.349f, -0.023f, -0.696f, -0.063f, -1.044f)
            curveToRelative(-0.074f, -0.652f, -0.247f, -1.277f, -0.519f, -1.87f)
            curveToRelative(-0.386f, -0.841f, -0.91f, -1.583f, -1.602f, -2.208f)
            curveToRelative(-0.514f, -0.464f, -1.077f, -0.844f, -1.703f, -1.136f)
            curveTo(13.919f, 2.516f, 13.006f, 2.318f, 12.052f, 2.302f)
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
            moveTo(11.403f, 10.462f)
            curveToRelative(0f, 0.495f, 0.002f, 0.99f, -0.001f, 1.485f)
            curveToRelative(-0.001f, 0.242f, 0.102f, 0.353f, 0.33f, 0.33f)
            curveToRelative(0.175f, -0.017f, 0.352f, -0.003f, 0.529f, -0.003f)
            curveToRelative(0.287f, 0f, 0.337f, -0.049f, 0.337f, -0.329f)
            curveToRelative(0f, -0.967f, -0.024f, -1.935f, 0.007f, -2.901f)
            curveToRelative(0.027f, -0.853f, 0.404f, -1.541f, 1.159f, -1.975f)
            curveToRelative(0.782f, -0.449f, 1.606f, -0.458f, 2.409f, -0.08f)
            curveToRelative(0.674f, 0.317f, 1.073f, 0.88f, 1.242f, 1.612f)
            curveToRelative(0.145f, 0.629f, 0.02f, 1.233f, -0.129f, 1.837f)
            curveToRelative(-0.184f, 0.747f, -0.541f, 1.413f, -1.022f, 2.005f)
            curveToRelative(-0.729f, 0.896f, -1.653f, 1.5f, -2.772f, 1.819f)
            curveToRelative(-0.644f, 0.184f, -1.296f, 0.239f, -1.952f, 0.191f)
            curveToRelative(-1.02f, -0.075f, -1.944f, -0.434f, -2.775f, -1.04f)
            curveToRelative(-0.64f, -0.467f, -1.149f, -1.049f, -1.544f, -1.727f)
            curveToRelative(-0.295f, -0.506f, -0.493f, -1.056f, -0.597f, -1.636f)
            curveToRelative(-0.056f, -0.315f, -0.111f, -0.63f, -0.102f, -0.951f)
            curveToRelative(0.028f, -1.103f, 0.777f, -2.126f, 1.932f, -2.325f)
            curveToRelative(0.66f, -0.114f, 1.28f, -0.031f, 1.85f, 0.314f)
            curveToRelative(0.551f, 0.334f, 0.885f, 0.834f, 1.031f, 1.461f)
            curveToRelative(0.048f, 0.208f, 0.073f, 0.418f, 0.07f, 0.633f)
            curveTo(11.398f, 9.608f, 11.403f, 10.035f, 11.403f, 10.462f)
            close()

            moveTo(15.047f, 7.016f)
            curveToRelative(-1.149f, -0.005f, -2.089f, 0.928f, -2.103f, 2.061f)
            curveToRelative(-0.015f, 1.185f, 0.931f, 2.132f, 2.085f, 2.129f)
            curveToRelative(1.187f, -0.004f, 2.088f, -0.909f, 2.101f, -2.077f)
            curveTo(17.145f, 7.951f, 16.2f, 7.034f, 15.047f, 7.016f)
            close()

            moveTo(11.061f, 9.097f)
            curveToRelative(-0.006f, -1.146f, -0.964f, -2.049f, -2.002f, -2.079f)
            curveTo(7.781f, 6.982f, 6.872f, 7.985f, 6.87f, 9.095f)
            curveToRelative(-0.002f, 1.185f, 0.922f, 2.103f, 2.085f, 2.11f)
            curveTo(10.098f, 11.211f, 11.045f, 10.32f, 11.061f, 9.097f)
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
            moveTo(16.472f, 5.843f)
            curveToRelative(-0.192f, -0.021f, -0.365f, -0.097f, -0.549f, -0.135f)
            curveToRelative(-1.148f, -0.239f, -2.208f, -0.059f, -3.16f, 0.649f)
            curveToRelative(-0.259f, 0.193f, -0.482f, 0.425f, -0.656f, 0.695f)
            curveToRelative(-0.099f, 0.154f, -0.155f, 0.074f, -0.213f, -0.009f)
            curveToRelative(-0.116f, -0.167f, -0.248f, -0.32f, -0.396f, -0.458f)
            curveToRelative(-0.558f, -0.521f, -1.215f, -0.832f, -1.972f, -0.934f)
            curveTo(8.936f, 5.572f, 8.353f, 5.602f, 7.782f, 5.786f)
            curveTo(7.714f, 5.808f, 7.647f, 5.834f, 7.579f, 5.855f)
            curveTo(7.564f, 5.859f, 7.545f, 5.852f, 7.504f, 5.847f)
            curveToRelative(1.186f, -1.6f, 2.768f, -2.406f, 4.734f, -2.334f)
            curveTo(14.013f, 3.579f, 15.429f, 4.395f, 16.472f, 5.843f)
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
            moveTo(12.002f, 20.169f)
            curveToRelative(-0.383f, -0.408f, -0.749f, -0.812f, -1.096f, -1.233f)
            curveToRelative(-0.146f, -0.177f, -0.292f, -0.353f, -0.434f, -0.533f)
            curveToRelative(-0.05f, -0.063f, -0.058f, -0.094f, -0.006f, -0.167f)
            curveToRelative(0.466f, -0.65f, 0.907f, -1.317f, 1.304f, -2.013f)
            curveToRelative(0.088f, -0.154f, 0.179f, -0.307f, 0.259f, -0.466f)
            curveToRelative(0.038f, -0.076f, 0.083f, -0.102f, 0.168f, -0.106f)
            curveToRelative(1.054f, -0.05f, 2.056f, -0.298f, 2.982f, -0.816f)
            curveToRelative(0.329f, -0.184f, 0.647f, -0.385f, 0.959f, -0.607f)
            curveTo(15.124f, 16.459f, 13.679f, 18.388f, 12.002f, 20.169f)
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
            moveTo(7.861f, 14.231f)
            curveToRelative(0.421f, 0.291f, 0.845f, 0.562f, 1.303f, 0.779f)
            curveToRelative(0.483f, 0.229f, 0.985f, 0.403f, 1.51f, 0.5f)
            curveToRelative(0.144f, 0.027f, 0.126f, 0.072f, 0.075f, 0.155f)
            curveToRelative(-0.277f, 0.449f, -0.546f, 0.903f, -0.848f, 1.336f)
            curveToRelative(-0.076f, 0.109f, -0.152f, 0.217f, -0.238f, 0.34f)
            curveTo(8.965f, 16.352f, 8.354f, 15.329f, 7.861f, 14.231f)
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
            moveTo(15.032f, 8.211f)
            curveToRelative(0.475f, -0.065f, 0.935f, 0.407f, 0.92f, 0.924f)
            curveToRelative(-0.013f, 0.432f, -0.413f, 0.897f, -0.925f, 0.891f)
            curveToRelative(-0.474f, -0.005f, -0.915f, -0.445f, -0.903f, -0.948f)
            curveTo(14.134f, 8.626f, 14.579f, 8.147f, 15.032f, 8.211f)
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
            moveTo(9.866f, 9.095f)
            curveToRelative(0.053f, 0.502f, -0.439f, 0.935f, -0.908f, 0.931f)
            curveToRelative(-0.483f, -0.005f, -0.915f, -0.417f, -0.908f, -0.94f)
            curveToRelative(0.006f, -0.404f, 0.373f, -0.92f, 0.985f, -0.884f)
            curveTo(9.424f, 8.224f, 9.917f, 8.615f, 9.866f, 9.095f)
            close()
        }
    }.build()
}
