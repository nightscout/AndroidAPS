package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for PocTech CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginPocTecPreview
 */
val IcPluginPocTec: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginPocTech",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Path with opacity 0.3 (background shape)
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
            moveTo(13.086f, 2.733f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.003f, 0f)
            horizontalLineToRelative(-2.167f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.003f, 0f)
            curveToRelative(-2.471f, 0f, -4.483f, 1.814f, -4.485f, 4.045f)
            lineTo(6.424f, 18.491f)
            curveToRelative(0.391f, 1.139f, 1.135f, 2.22f, 2.02f, 3.069f)
            horizontalLineToRelative(7.109f)
            curveToRelative(0.885f, -0.849f, 1.614f, -1.93f, 2.004f, -3.069f)
            lineToRelative(0.016f, -11.713f)
            curveTo(17.57f, 4.547f, 15.557f, 2.733f, 13.086f, 2.733f)
            close()
            moveTo(17.012f, 18.354f)
            curveToRelative(-0.351f, 1.001f, -0.918f, 1.893f, -1.686f, 2.65f)
            lineTo(12f, 21.006f)
            lineToRelative(-3.327f, -0.002f)
            curveToRelative(-0.768f, -0.758f, -1.335f, -1.649f, -1.686f, -2.65f)
            lineTo(6.989f, 6.779f)
            curveToRelative(0.002f, -1.923f, 1.762f, -3.486f, 3.925f, -3.486f)
            curveToRelative(0.001f, 0f, 0.002f, 0f, 0.003f, 0f)
            horizontalLineToRelative(2.167f)
            curveToRelative(0.001f, 0f, 0.002f, 0f, 0.003f, 0f)
            curveToRelative(2.162f, 0f, 3.924f, 1.563f, 3.926f, 3.485f)
            verticalLineTo(18.354f)
            close()
        }

        // Second path (opacity 1)
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
            moveTo(19.442f, 8.639f)
            curveTo(19.438f, 4.53f, 16.099f, 1.196f, 11.989f, 1.2f)
            curveTo(7.88f, 1.204f, 4.547f, 4.544f, 4.55f, 8.653f)
            lineToRelative(0.008f, 8.344f)
            curveToRelative(0.003f, 3.205f, 2.608f, 5.805f, 5.813f, 5.802f)
            lineToRelative(3.276f, -0.003f)
            curveToRelative(3.206f, -0.003f, 5.806f, -2.608f, 5.803f, -5.813f)
            lineTo(19.442f, 8.639f)
            close()
            moveTo(17.557f, 18.491f)
            curveToRelative(-0.391f, 1.139f, -1.12f, 2.22f, -2.004f, 3.069f)
            horizontalLineTo(8.444f)
            curveToRelative(-0.885f, -0.849f, -1.629f, -1.93f, -2.02f, -3.069f)
            lineTo(6.428f, 6.778f)
            curveToRelative(0.002f, -2.231f, 2.015f, -4.045f, 4.485f, -4.045f)
            curveToRelative(0.001f, 0f, 0.002f, 0f, 0.003f, 0f)
            horizontalLineToRelative(2.167f)
            curveToRelative(0.001f, 0f, 0.002f, 0f, 0.003f, 0f)
            curveToRelative(2.471f, 0f, 4.483f, 1.813f, 4.486f, 4.044f)
            lineTo(17.557f, 18.491f)
            close()
        }

        // Small detailed paths (all with fill alpha 1)
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
            moveTo(9.478f, 18.637f)
            curveToRelative(-0.033f, -0.019f, -0.07f, -0.027f, -0.114f, -0.027f)
            curveToRelative(-0.053f, 0f, -0.097f, 0.013f, -0.131f, 0.038f)
            curveToRelative(-0.034f, 0.026f, -0.056f, 0.06f, -0.064f, 0.099f)
            curveToRelative(-0.007f, 0.029f, -0.01f, 0.089f, -0.01f, 0.177f)
            verticalLineToRelative(0.118f)
            horizontalLineToRelative(0.414f)
            verticalLineToRelative(-0.134f)
            curveToRelative(0f, -0.097f, -0.008f, -0.161f, -0.023f, -0.194f)
            curveToRelative(-0.015f, -0.032f, -0.039f, -0.058f, -0.071f, -0.076f)
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
            moveTo(9.636f, 17.28f)
            curveToRelative(-0.167f, 0f, -0.292f, 0.03f, -0.374f, 0.091f)
            curveToRelative(-0.083f, 0.062f, -0.124f, 0.143f, -0.124f, 0.243f)
            curveToRelative(0f, 0.102f, 0.042f, 0.183f, 0.125f, 0.245f)
            curveToRelative(0.083f, 0.062f, 0.209f, 0.093f, 0.377f, 0.093f)
            curveToRelative(0.165f, 0f, 0.29f, -0.032f, 0.376f, -0.096f)
            curveToRelative(0.085f, -0.063f, 0.128f, -0.144f, 0.128f, -0.242f)
            curveToRelative(0f, -0.098f, -0.043f, -0.178f, -0.127f, -0.24f)
            curveToRelative(-0.085f, -0.063f, -0.212f, -0.095f, -0.381f, -0.095f)
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
            moveTo(9.751f, 14.525f)
            verticalLineToRelative(-0.348f)
            curveToRelative(-0.081f, 0.002f, -0.143f, 0.019f, -0.185f, 0.052f)
            reflectiveCurveToRelative(-0.063f, 0.074f, -0.063f, 0.121f)
            curveToRelative(0f, 0.051f, 0.022f, 0.093f, 0.066f, 0.126f)
            curveToRelative(0.045f, 0.034f, 0.105f, 0.05f, 0.181f, 0.049f)
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
            moveTo(10.015f, 7.158f)
            curveToRelative(-0.071f, -0.016f, -0.193f, -0.024f, -0.364f, -0.024f)
            reflectiveCurveToRelative(-0.289f, 0.007f, -0.353f, 0.022f)
            curveToRelative(-0.064f, 0.014f, -0.107f, 0.032f, -0.128f, 0.054f)
            curveToRelative(-0.021f, 0.022f, -0.032f, 0.047f, -0.032f, 0.075f)
            curveToRelative(0f, 0.028f, 0.011f, 0.054f, 0.032f, 0.076f)
            curveToRelative(0.021f, 0.022f, 0.064f, 0.04f, 0.128f, 0.054f)
            curveToRelative(0.073f, 0.016f, 0.195f, 0.024f, 0.366f, 0.024f)
            reflectiveCurveToRelative(0.289f, -0.007f, 0.353f, -0.021f)
            curveToRelative(0.064f, -0.014f, 0.107f, -0.032f, 0.128f, -0.054f)
            curveToRelative(0.021f, -0.022f, 0.032f, -0.047f, 0.032f, -0.075f)
            curveToRelative(0f, -0.028f, -0.011f, -0.053f, -0.032f, -0.075f)
            curveToRelative(-0.021f, -0.022f, -0.064f, -0.04f, -0.128f, -0.054f)
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
            moveTo(10.015f, 6.214f)
            curveToRelative(-0.071f, -0.016f, -0.193f, -0.024f, -0.364f, -0.024f)
            reflectiveCurveToRelative(-0.289f, 0.007f, -0.353f, 0.022f)
            curveToRelative(-0.064f, 0.014f, -0.107f, 0.032f, -0.128f, 0.054f)
            curveToRelative(-0.021f, 0.022f, -0.032f, 0.047f, -0.032f, 0.075f)
            curveToRelative(0f, 0.028f, 0.011f, 0.054f, 0.032f, 0.076f)
            curveToRelative(0.021f, 0.022f, 0.064f, 0.04f, 0.128f, 0.054f)
            curveToRelative(0.073f, 0.016f, 0.195f, 0.024f, 0.366f, 0.024f)
            reflectiveCurveToRelative(0.289f, -0.007f, 0.353f, -0.021f)
            curveToRelative(0.064f, -0.014f, 0.107f, -0.032f, 0.128f, -0.054f)
            curveToRelative(0.021f, -0.022f, 0.032f, -0.047f, 0.032f, -0.075f)
            curveToRelative(0f, -0.028f, -0.011f, -0.053f, -0.032f, -0.075f)
            curveToRelative(-0.021f, -0.022f, -0.064f, -0.04f, -0.128f, -0.054f)
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
            moveTo(9.434f, 5.055f)
            curveToRelative(-0.028f, -0.024f, -0.066f, -0.035f, -0.113f, -0.035f)
            curveToRelative(-0.046f, 0f, -0.082f, 0.01f, -0.11f, 0.031f)
            curveToRelative(-0.028f, 0.02f, -0.045f, 0.051f, -0.05f, 0.092f)
            curveToRelative(-0.004f, 0.024f, -0.006f, 0.094f, -0.006f, 0.21f)
            verticalLineToRelative(0.141f)
            horizontalLineToRelative(0.338f)
            verticalLineTo(5.333f)
            curveToRelative(0f, -0.096f, -0.002f, -0.155f, -0.005f, -0.178f)
            curveToRelative(-0.004f, -0.022f, -0.022f, -0.054f, -0.051f, -0.079f)
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
            moveTo(10.057f, 5.009f)
            curveToRelative(-0.03f, -0.023f, -0.071f, -0.034f, -0.123f, -0.034f)
            curveToRelative(-0.044f, 0f, -0.081f, 0.009f, -0.112f, 0.026f)
            curveToRelative(-0.03f, 0.018f, -0.053f, 0.043f, -0.066f, 0.077f)
            curveToRelative(-0.014f, 0.033f, -0.021f, 0.106f, -0.021f, 0.217f)
            verticalLineToRelative(0.198f)
            horizontalLineToRelative(0.39f)
            verticalLineTo(5.266f)
            curveToRelative(0f, -0.088f, -0.003f, -0.145f, -0.009f, -0.168f)
            curveToRelative(-0.006f, -0.023f, -0.026f, -0.053f, -0.057f, -0.076f)
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
            moveTo(13.086f, 3.293f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.003f, 0f)
            horizontalLineToRelative(-2.167f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.003f, 0f)
            curveToRelative(-2.163f, 0f, -3.923f, 1.563f, -3.925f, 3.486f)
            lineTo(6.988f, 18.354f)
            curveToRelative(0.351f, 1.001f, 0.918f, 1.893f, 1.686f, 2.65f)
            lineTo(12f, 21.006f)
            lineToRelative(3.326f, -0.002f)
            curveToRelative(0.768f, -0.758f, 1.335f, -1.649f, 1.686f, -2.65f)
            verticalLineTo(6.778f)
            curveToRelative(-0.002f, -1.922f, -1.764f, -3.485f, -3.926f, -3.485f)
            close()
            moveTo(8.911f, 5.253f)
            curveToRelative(0f, -0.096f, 0.005f, -0.167f, 0.014f, -0.215f)
            curveToRelative(0.01f, -0.047f, 0.03f, -0.089f, 0.061f, -0.127f)
            curveToRelative(0.03f, -0.037f, 0.071f, -0.068f, 0.122f, -0.093f)
            reflectiveCurveToRelative(0.107f, -0.037f, 0.171f, -0.037f)
            curveToRelative(0.068f, 0f, 0.131f, 0.015f, 0.188f, 0.046f)
            curveToRelative(0.058f, 0.031f, 0.101f, 0.072f, 0.129f, 0.125f)
            curveToRelative(0.025f, -0.074f, 0.07f, -0.131f, 0.133f, -0.17f)
            curveToRelative(0.062f, -0.04f, 0.136f, -0.06f, 0.22f, -0.06f)
            curveToRelative(0.066f, 0f, 0.131f, 0.013f, 0.193f, 0.039f)
            curveToRelative(0.063f, 0.025f, 0.113f, 0.061f, 0.15f, 0.105f)
            reflectiveCurveToRelative(0.061f, 0.1f, 0.069f, 0.165f)
            curveToRelative(0.005f, 0.041f, 0.009f, 0.14f, 0.01f, 0.296f)
            verticalLineToRelative(0.413f)
            horizontalLineTo(8.911f)
            verticalLineTo(5.253f)
            close()
            moveTo(8.906f, 8.026f)
            horizontalLineToRelative(1.465f)
            verticalLineToRelative(0.233f)
            horizontalLineTo(9.317f)
            curveToRelative(0.094f, 0.085f, 0.166f, 0.185f, 0.212f, 0.301f)
            horizontalLineTo(9.275f)
            curveToRelative(-0.025f, -0.061f, -0.07f, -0.127f, -0.136f, -0.198f)
            curveToRelative(-0.067f, -0.071f, -0.145f, -0.12f, -0.233f, -0.147f)
            verticalLineTo(8.026f)
            close()
            moveTo(10.37f, 9.56f)
            verticalLineToRelative(0.245f)
            horizontalLineTo(9.159f)
            verticalLineToRelative(0.36f)
            horizontalLineTo(8.911f)
            verticalLineTo(9.201f)
            horizontalLineToRelative(0.247f)
            verticalLineTo(9.56f)
            horizontalLineTo(10.37f)
            close()
            moveTo(9.702f, 9.164f)
            verticalLineTo(8.707f)
            horizontalLineToRelative(0.279f)
            verticalLineToRelative(0.458f)
            horizontalLineTo(9.702f)
            close()
            moveTo(10.37f, 19.287f)
            horizontalLineTo(8.911f)
            verticalLineToRelative(-0.394f)
            curveToRelative(0f, -0.149f, 0.007f, -0.247f, 0.021f, -0.292f)
            curveToRelative(0.022f, -0.069f, 0.069f, -0.128f, 0.143f, -0.175f)
            curveToRelative(0.074f, -0.047f, 0.169f, -0.07f, 0.284f, -0.07f)
            curveToRelative(0.09f, 0f, 0.165f, 0.014f, 0.227f, 0.04f)
            curveToRelative(0.061f, 0.027f, 0.108f, 0.062f, 0.144f, 0.104f)
            reflectiveCurveToRelative(0.058f, 0.084f, 0.069f, 0.127f)
            curveToRelative(0.014f, 0.059f, 0.021f, 0.144f, 0.021f, 0.255f)
            verticalLineToRelative(0.159f)
            horizontalLineToRelative(0.551f)
            verticalLineTo(19.287f)
            close()
            moveTo(10.197f, 18.045f)
            curveToRelative(-0.132f, 0.106f, -0.314f, 0.159f, -0.547f, 0.159f)
            curveToRelative(-0.148f, 0f, -0.273f, -0.018f, -0.374f, -0.055f)
            curveToRelative(-0.074f, -0.028f, -0.142f, -0.065f, -0.2f, -0.113f)
            curveToRelative(-0.06f, -0.048f, -0.103f, -0.101f, -0.132f, -0.157f)
            curveToRelative(-0.038f, -0.075f, -0.058f, -0.163f, -0.058f, -0.262f)
            curveToRelative(0f, -0.179f, 0.067f, -0.322f, 0.2f, -0.43f)
            curveToRelative(0.134f, -0.107f, 0.319f, -0.161f, 0.557f, -0.161f)
            curveToRelative(0.235f, 0f, 0.42f, 0.054f, 0.553f, 0.16f)
            reflectiveCurveToRelative(0.199f, 0.249f, 0.199f, 0.428f)
            curveToRelative(0f, 0.179f, -0.066f, 0.322f, -0.198f, 0.429f)
            close()
            moveTo(10.197f, 16.723f)
            curveToRelative(-0.132f, 0.104f, -0.313f, 0.156f, -0.543f, 0.156f)
            curveToRelative(-0.243f, 0f, -0.432f, -0.053f, -0.565f, -0.157f)
            curveToRelative(-0.135f, -0.104f, -0.202f, -0.241f, -0.202f, -0.411f)
            curveToRelative(0f, -0.148f, 0.053f, -0.27f, 0.158f, -0.362f)
            curveToRelative(0.063f, -0.056f, 0.152f, -0.097f, 0.27f, -0.124f)
            lineToRelative(0.069f, 0.242f)
            curveToRelative(-0.076f, 0.015f, -0.136f, 0.045f, -0.18f, 0.09f)
            curveToRelative(-0.043f, 0.046f, -0.065f, 0.102f, -0.065f, 0.166f)
            curveToRelative(0f, 0.091f, 0.039f, 0.163f, 0.116f, 0.22f)
            curveToRelative(0.078f, 0.056f, 0.204f, 0.084f, 0.378f, 0.084f)
            curveToRelative(0.185f, 0f, 0.315f, -0.027f, 0.394f, -0.083f)
            curveToRelative(0.079f, -0.055f, 0.118f, -0.127f, 0.118f, -0.215f)
            curveToRelative(0f, -0.065f, -0.025f, -0.122f, -0.075f, -0.169f)
            reflectiveCurveToRelative(-0.128f, -0.08f, -0.234f, -0.101f)
            lineToRelative(0.09f, -0.238f)
            curveToRelative(0.159f, 0.037f, 0.278f, 0.098f, 0.355f, 0.183f)
            reflectiveCurveToRelative(0.115f, 0.191f, 0.115f, 0.322f)
            curveToRelative(0f, 0.167f, -0.066f, 0.3f, -0.198f, 0.404f)
            close()
            moveTo(9.289f, 12.312f)
            curveToRelative(0f, -0.054f, 0.012f, -0.102f, 0.036f, -0.145f)
            curveToRelative(0.023f, -0.043f, 0.054f, -0.076f, 0.092f, -0.097f)
            curveToRelative(0.037f, -0.022f, 0.078f, -0.037f, 0.123f, -0.045f)
            reflectiveCurveToRelative(0.115f, -0.012f, 0.21f, -0.012f)
            horizontalLineToRelative(0.62f)
            verticalLineToRelative(0.233f)
            horizontalLineTo(9.812f)
            curveToRelative(-0.11f, 0f, -0.181f, 0.004f, -0.211f, 0.013f)
            curveToRelative(-0.029f, 0.009f, -0.054f, 0.024f, -0.071f, 0.047f)
            reflectiveCurveToRelative(-0.026f, 0.051f, -0.026f, 0.084f)
            curveToRelative(0f, 0.039f, 0.012f, 0.073f, 0.034f, 0.104f)
            curveToRelative(0.022f, 0.03f, 0.057f, 0.052f, 0.102f, 0.066f)
            curveToRelative(0.046f, 0.014f, 0.113f, 0.021f, 0.202f, 0.021f)
            horizontalLineToRelative(0.529f)
            verticalLineToRelative(0.233f)
            horizontalLineTo(8.911f)
            verticalLineToRelative(-0.233f)
            horizontalLineToRelative(0.536f)
            curveToRelative(-0.105f, -0.083f, -0.158f, -0.172f, -0.158f, -0.277f)
            close()
            moveTo(10.117f, 14.474f)
            curveToRelative(0.046f, -0.036f, 0.068f, -0.081f, 0.068f, -0.135f)
            curveToRelative(0f, -0.036f, -0.012f, -0.066f, -0.035f, -0.092f)
            curveToRelative(-0.024f, -0.024f, -0.063f, -0.043f, -0.116f, -0.056f)
            lineToRelative(0.047f, -0.232f)
            curveToRelative(0.103f, 0.029f, 0.181f, 0.077f, 0.233f, 0.142f)
            curveToRelative(0.054f, 0.064f, 0.08f, 0.145f, 0.08f, 0.241f)
            curveToRelative(0f, 0.153f, -0.06f, 0.267f, -0.18f, 0.34f)
            curveToRelative(-0.097f, 0.058f, -0.218f, 0.087f, -0.364f, 0.087f)
            curveToRelative(-0.175f, 0f, -0.313f, -0.038f, -0.412f, -0.114f)
            curveToRelative(-0.099f, -0.076f, -0.148f, -0.173f, -0.148f, -0.289f)
            curveToRelative(0f, -0.131f, 0.052f, -0.234f, 0.156f, -0.311f)
            curveToRelative(0.104f, -0.075f, 0.263f, -0.111f, 0.477f, -0.108f)
            verticalLineToRelative(0.584f)
            curveToRelative(-0.063f, 0.018f, -0.128f, 0.036f, -0.175f, 0.072f)
            close()
            moveTo(10.248f, 13.693f)
            curveToRelative(-0.098f, 0.077f, -0.233f, 0.115f, -0.405f, 0.115f)
            curveToRelative(-0.175f, 0f, -0.311f, -0.038f, -0.408f, -0.115f)
            curveToRelative(-0.097f, -0.077f, -0.146f, -0.181f, -0.146f, -0.312f)
            curveToRelative(0f, -0.107f, 0.027f, -0.192f, 0.083f, -0.256f)
            reflectiveCurveToRelative(0.14f, -0.108f, 0.254f, -0.136f)
            lineToRelative(0.05f, 0.229f)
            curveToRelative(-0.056f, 0.008f, -0.097f, 0.025f, -0.125f, 0.053f)
            curveToRelative(-0.027f, 0.027f, -0.042f, 0.063f, -0.042f, 0.106f)
            curveToRelative(0f, 0.058f, 0.024f, 0.104f, 0.072f, 0.139f)
            curveToRelative(0.048f, 0.034f, 0.129f, 0.052f, 0.241f, 0.052f)
            curveToRelative(0.126f, 0f, 0.215f, -0.018f, 0.267f, -0.053f)
            reflectiveCurveToRelative(0.077f, -0.082f, 0.077f, -0.142f)
            curveToRelative(0f, -0.044f, -0.015f, -0.08f, -0.045f, -0.108f)
            reflectiveCurveToRelative(-0.082f, -0.048f, -0.156f, -0.06f)
            lineToRelative(0.047f, -0.229f)
            curveToRelative(0.126f, 0.023f, 0.222f, 0.069f, 0.286f, 0.137f)
            reflectiveCurveToRelative(0.096f, 0.157f, 0.096f, 0.271f)
            curveToRelative(0f, 0.127f, -0.048f, 0.229f, -0.146f, 0.305f)
            close()
            moveTo(9.159f, 14.731f)
            verticalLineToRelative(0.359f)
            horizontalLineToRelative(1.212f)
            verticalLineToRelative(0.246f)
            horizontalLineTo(9.159f)
            verticalLineToRelative(0.36f)
            horizontalLineTo(8.911f)
            verticalLineToRelative(-0.966f)
            horizontalLineTo(9.159f)
            close()
            moveTo(10.197f, 11.191f)
            curveToRelative(-0.132f, 0.104f, -0.313f, 0.156f, -0.543f, 0.156f)
            curveToRelative(-0.243f, 0f, -0.432f, -0.052f, -0.565f, -0.156f)
            curveToRelative(-0.135f, -0.104f, -0.202f, -0.242f, -0.202f, -0.412f)
            curveToRelative(0f, -0.148f, 0.053f, -0.269f, 0.158f, -0.362f)
            curveToRelative(0.063f, -0.055f, 0.152f, -0.097f, 0.27f, -0.125f)
            lineToRelative(0.069f, 0.243f)
            curveToRelative(-0.076f, 0.014f, -0.136f, 0.044f, -0.18f, 0.09f)
            curveToRelative(-0.043f, 0.045f, -0.065f, 0.101f, -0.065f, 0.166f)
            curveToRelative(0f, 0.09f, 0.039f, 0.163f, 0.116f, 0.219f)
            curveToRelative(0.078f, 0.056f, 0.204f, 0.084f, 0.378f, 0.084f)
            curveToRelative(0.185f, 0f, 0.315f, -0.028f, 0.394f, -0.083f)
            curveToRelative(0.079f, -0.055f, 0.118f, -0.127f, 0.118f, -0.215f)
            curveToRelative(0f, -0.065f, -0.025f, -0.121f, -0.075f, -0.168f)
            curveToRelative(-0.05f, -0.047f, -0.128f, -0.081f, -0.234f, -0.101f)
            lineToRelative(0.09f, -0.238f)
            curveToRelative(0.159f, 0.036f, 0.278f, 0.097f, 0.355f, 0.182f)
            reflectiveCurveToRelative(0.115f, 0.192f, 0.115f, 0.323f)
            curveToRelative(0f, 0.168f, -0.066f, 0.3f, -0.198f, 0.404f)
            close()
            moveTo(10.232f, 7.571f)
            curveToRelative(-0.109f, 0.072f, -0.304f, 0.108f, -0.584f, 0.108f)
            curveToRelative(-0.274f, 0f, -0.473f, -0.04f, -0.594f, -0.119f)
            curveToRelative(-0.1f, -0.066f, -0.149f, -0.158f, -0.149f, -0.275f)
            reflectiveCurveToRelative(0.05f, -0.209f, 0.151f, -0.276f)
            curveToRelative(0.119f, -0.079f, 0.317f, -0.118f, 0.594f, -0.118f)
            reflectiveCurveToRelative(0.475f, 0.04f, 0.596f, 0.119f)
            curveToRelative(0.1f, 0.066f, 0.148f, 0.157f, 0.148f, 0.275f)
            reflectiveCurveToRelative(-0.054f, 0.208f, -0.163f, 0.28f)
            close()
            moveTo(10.232f, 6.627f)
            curveToRelative(-0.109f, 0.072f, -0.304f, 0.108f, -0.584f, 0.108f)
            curveToRelative(-0.274f, 0f, -0.473f, -0.04f, -0.594f, -0.119f)
            curveToRelative(-0.1f, -0.066f, -0.149f, -0.158f, -0.149f, -0.275f)
            reflectiveCurveToRelative(0.05f, -0.209f, 0.151f, -0.276f)
            curveToRelative(0.119f, -0.079f, 0.317f, -0.118f, 0.594f, -0.118f)
            reflectiveCurveToRelative(0.475f, 0.04f, 0.596f, 0.119f)
            curveToRelative(0.1f, 0.066f, 0.148f, 0.157f, 0.148f, 0.275f)
            reflectiveCurveToRelative(-0.054f, 0.208f, -0.163f, 0.28f)
            close()
        }
    }.build()
}
