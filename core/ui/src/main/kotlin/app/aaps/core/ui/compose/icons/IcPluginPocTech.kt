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
 * Icon for PocTech CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
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

@Preview(showBackground = true)
@Composable
private fun IcPluginPocTecPreview() {
    Icon(
        imageVector = IcPluginPocTec,
        contentDescription = "PocTech Plugin Icon",
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
<g id="Plugin_PocTech">
	<g>
		<path opacity="0.3" fill="#FFFFFF" d="M13.086,2.733c-0.001,0-0.002,0-0.003,0h-2.167c-0.001,0-0.002,0-0.003,0
			c-2.471,0-4.483,1.814-4.485,4.045L6.424,18.491c0.391,1.139,1.135,2.22,2.02,3.069h7.109c0.885-0.849,1.614-1.93,2.004-3.069
			l0.016-11.713C17.57,4.547,15.557,2.733,13.086,2.733z M17.012,18.354c-0.351,1.001-0.918,1.893-1.686,2.65L12,21.006
			l-3.327-0.002c-0.768-0.758-1.335-1.649-1.686-2.65L6.989,6.779c0.002-1.923,1.762-3.486,3.925-3.486c0.001,0,0.002,0,0.003,0
			h2.167c0.001,0,0.002,0,0.003,0c2.162,0,3.924,1.563,3.926,3.485V18.354z"/>
		<g>
			<path fill="#FFFFFF" d="M19.442,8.639C19.438,4.53,16.099,1.196,11.989,1.2C7.88,1.204,4.547,4.544,4.55,8.653l0.008,8.344
				c0.003,3.205,2.608,5.805,5.813,5.802l3.276-0.003c3.206-0.003,5.806-2.608,5.803-5.813L19.442,8.639z M17.557,18.491
				c-0.391,1.139-1.12,2.22-2.004,3.069H8.444c-0.885-0.849-1.629-1.93-2.02-3.069L6.428,6.778c0.002-2.231,2.015-4.045,4.485-4.045
				c0.001,0,0.002,0,0.003,0h2.167c0.001,0,0.002,0,0.003,0c2.471,0,4.483,1.813,4.486,4.044L17.557,18.491z"/>
			<path fill="#FFFFFF" d="M9.478,18.637c-0.033-0.019-0.07-0.027-0.114-0.027c-0.053,0-0.097,0.013-0.131,0.038
				c-0.034,0.026-0.056,0.06-0.064,0.099c-0.007,0.029-0.01,0.089-0.01,0.177v0.118h0.414v-0.134c0-0.097-0.008-0.161-0.023-0.194
				C9.534,18.681,9.51,18.655,9.478,18.637z"/>
			<path fill="#FFFFFF" d="M9.636,17.28c-0.167,0-0.292,0.03-0.374,0.091c-0.083,0.062-0.124,0.143-0.124,0.243
				c0,0.102,0.042,0.183,0.125,0.245c0.083,0.062,0.209,0.093,0.377,0.093c0.165,0,0.29-0.032,0.376-0.096
				c0.085-0.063,0.128-0.144,0.128-0.242c0-0.098-0.043-0.178-0.127-0.24C9.932,17.312,9.805,17.28,9.636,17.28z"/>
			<path fill="#FFFFFF" d="M9.751,14.525v-0.348c-0.081,0.002-0.143,0.019-0.185,0.052s-0.063,0.074-0.063,0.121
				c0,0.051,0.022,0.093,0.066,0.126C9.615,14.51,9.675,14.526,9.751,14.525z"/>
			<path fill="#FFFFFF" d="M10.015,7.158C9.944,7.142,9.822,7.134,9.651,7.134S9.362,7.141,9.298,7.156
				C9.234,7.17,9.191,7.188,9.169,7.21S9.137,7.257,9.137,7.285c0,0.028,0.011,0.054,0.032,0.076S9.23,7.399,9.285,7.412
				C9.358,7.428,9.48,7.436,9.651,7.436s0.289-0.007,0.353-0.021c0.064-0.014,0.107-0.032,0.128-0.054
				c0.021-0.022,0.032-0.047,0.032-0.075c0-0.028-0.011-0.053-0.032-0.075S10.071,7.17,10.015,7.158z"/>
			<path fill="#FFFFFF" d="M10.015,6.214C9.944,6.198,9.822,6.19,9.651,6.19S9.362,6.197,9.298,6.212
				C9.234,6.226,9.191,6.244,9.169,6.266S9.137,6.313,9.137,6.341c0,0.028,0.011,0.054,0.032,0.076S9.23,6.456,9.285,6.468
				C9.358,6.484,9.48,6.492,9.651,6.492S9.94,6.485,10.003,6.47c0.064-0.014,0.107-0.032,0.128-0.054
				c0.021-0.022,0.032-0.047,0.032-0.075c0-0.028-0.011-0.053-0.032-0.075S10.071,6.227,10.015,6.214z"/>
			<path fill="#FFFFFF" d="M9.434,5.055C9.406,5.031,9.368,5.02,9.321,5.02c-0.046,0-0.082,0.01-0.11,0.031
				C9.182,5.071,9.165,5.102,9.16,5.143c-0.004,0.024-0.006,0.094-0.006,0.21v0.141h0.338V5.333c0-0.096-0.002-0.155-0.005-0.178
				C9.481,5.112,9.463,5.08,9.434,5.055z"/>
			<path fill="#FFFFFF" d="M10.057,5.009c-0.03-0.023-0.071-0.034-0.123-0.034c-0.044,0-0.081,0.009-0.112,0.026
				c-0.03,0.018-0.053,0.043-0.066,0.077C9.742,5.111,9.735,5.184,9.735,5.295v0.198h0.39V5.266c0-0.088-0.003-0.145-0.009-0.168
				C10.108,5.062,10.088,5.032,10.057,5.009z"/>
			<path fill="#FFFFFF" d="M13.086,3.293c-0.001,0-0.002,0-0.003,0h-2.167c-0.001,0-0.002,0-0.003,0
				c-2.163,0-3.923,1.563-3.925,3.486L6.988,18.354c0.351,1.001,0.918,1.893,1.686,2.65L12,21.006l3.326-0.002
				c0.768-0.758,1.335-1.649,1.686-2.65V6.778C17.01,4.856,15.248,3.293,13.086,3.293z M8.911,5.253
				c0-0.096,0.005-0.167,0.014-0.215c0.01-0.047,0.03-0.089,0.061-0.127c0.03-0.037,0.071-0.068,0.122-0.093
				s0.107-0.037,0.171-0.037c0.068,0,0.131,0.015,0.188,0.046c0.058,0.031,0.101,0.072,0.129,0.125
				c0.025-0.074,0.07-0.131,0.133-0.17c0.062-0.04,0.136-0.06,0.22-0.06c0.066,0,0.131,0.013,0.193,0.039
				c0.063,0.025,0.113,0.061,0.15,0.105s0.061,0.1,0.069,0.165c0.005,0.041,0.009,0.14,0.01,0.296v0.413H8.911V5.253z M8.906,8.026
				h1.465v0.233H9.317C9.411,8.344,9.483,8.444,9.529,8.56H9.275C9.25,8.499,9.205,8.433,9.139,8.362
				c-0.067-0.071-0.145-0.12-0.233-0.147V8.026z M10.37,9.56v0.245H9.159v0.36H8.911V9.201h0.247V9.56H10.37z M9.702,9.164V8.707
				h0.279v0.458H9.702z M10.37,19.287H8.911v-0.394c0-0.149,0.007-0.247,0.021-0.292c0.022-0.069,0.069-0.128,0.143-0.175
				c0.074-0.047,0.169-0.07,0.284-0.07c0.09,0,0.165,0.014,0.227,0.04c0.061,0.027,0.108,0.062,0.144,0.104s0.058,0.084,0.069,0.127
				c0.014,0.059,0.021,0.144,0.021,0.255v0.159h0.551V19.287z M10.197,18.045c-0.132,0.106-0.314,0.159-0.547,0.159
				c-0.148,0-0.273-0.018-0.374-0.055c-0.074-0.028-0.142-0.065-0.2-0.113c-0.06-0.048-0.103-0.101-0.132-0.157
				c-0.038-0.075-0.058-0.163-0.058-0.262c0-0.179,0.067-0.322,0.2-0.43c0.134-0.107,0.319-0.161,0.557-0.161
				c0.235,0,0.42,0.054,0.553,0.16s0.199,0.249,0.199,0.428C10.395,17.795,10.329,17.938,10.197,18.045z M10.197,16.723
				c-0.132,0.104-0.313,0.156-0.543,0.156c-0.243,0-0.432-0.053-0.565-0.157c-0.135-0.104-0.202-0.241-0.202-0.411
				c0-0.148,0.053-0.27,0.158-0.362c0.063-0.056,0.152-0.097,0.27-0.124l0.069,0.242c-0.076,0.015-0.136,0.045-0.18,0.09
				c-0.043,0.046-0.065,0.102-0.065,0.166c0,0.091,0.039,0.163,0.116,0.22c0.078,0.056,0.204,0.084,0.378,0.084
				c0.185,0,0.315-0.027,0.394-0.083c0.079-0.055,0.118-0.127,0.118-0.215c0-0.065-0.025-0.122-0.075-0.169s-0.128-0.08-0.234-0.101
				l0.09-0.238c0.159,0.037,0.278,0.098,0.355,0.183c0.077,0.084,0.115,0.191,0.115,0.322
				C10.395,16.486,10.329,16.619,10.197,16.723z M9.289,12.312c0-0.054,0.012-0.102,0.036-0.145
				c0.023-0.043,0.054-0.076,0.092-0.097c0.037-0.022,0.078-0.037,0.123-0.045s0.115-0.012,0.21-0.012h0.62v0.233H9.812
				c-0.11,0-0.181,0.004-0.211,0.013c-0.029,0.009-0.054,0.024-0.071,0.047s-0.026,0.051-0.026,0.084
				c0,0.039,0.012,0.073,0.034,0.104c0.022,0.03,0.057,0.052,0.102,0.066c0.046,0.014,0.113,0.021,0.202,0.021h0.529v0.233H8.911
				v-0.233h0.536C9.342,12.506,9.289,12.417,9.289,12.312z M10.117,14.474c0.046-0.036,0.068-0.081,0.068-0.135
				c0-0.036-0.012-0.066-0.035-0.092c-0.024-0.024-0.063-0.043-0.116-0.056l0.047-0.232c0.103,0.029,0.181,0.077,0.233,0.142
				c0.054,0.064,0.08,0.145,0.08,0.241c0,0.153-0.06,0.267-0.18,0.34c-0.097,0.058-0.218,0.087-0.364,0.087
				c-0.175,0-0.313-0.038-0.412-0.114c-0.099-0.076-0.148-0.173-0.148-0.289c0-0.131,0.052-0.234,0.156-0.311
				c0.104-0.075,0.263-0.111,0.477-0.108v0.584C10.005,14.528,10.07,14.51,10.117,14.474z M10.248,13.693
				c-0.098,0.077-0.233,0.115-0.405,0.115c-0.175,0-0.311-0.038-0.408-0.115c-0.097-0.077-0.146-0.181-0.146-0.312
				c0-0.107,0.027-0.192,0.083-0.256s0.14-0.108,0.254-0.136l0.05,0.229c-0.056,0.008-0.097,0.025-0.125,0.053
				c-0.027,0.027-0.042,0.063-0.042,0.106c0,0.058,0.024,0.104,0.072,0.139c0.048,0.034,0.129,0.052,0.241,0.052
				c0.126,0,0.215-0.018,0.267-0.053s0.077-0.082,0.077-0.142c0-0.044-0.015-0.08-0.045-0.108s-0.082-0.048-0.156-0.06l0.047-0.229
				c0.126,0.023,0.222,0.069,0.286,0.137s0.096,0.157,0.096,0.271C10.394,13.515,10.346,13.617,10.248,13.693z M9.159,14.731v0.359
				h1.212v0.246H9.159v0.36H8.911v-0.966H9.159z M10.197,11.191c-0.132,0.104-0.313,0.156-0.543,0.156
				c-0.243,0-0.432-0.052-0.565-0.156c-0.135-0.104-0.202-0.242-0.202-0.412c0-0.148,0.053-0.269,0.158-0.362
				c0.063-0.055,0.152-0.097,0.27-0.125l0.069,0.243c-0.076,0.014-0.136,0.044-0.18,0.09c-0.043,0.045-0.065,0.101-0.065,0.166
				c0,0.09,0.039,0.163,0.116,0.219c0.078,0.056,0.204,0.084,0.378,0.084c0.185,0,0.315-0.028,0.394-0.083
				c0.079-0.055,0.118-0.127,0.118-0.215c0-0.065-0.025-0.121-0.075-0.168c-0.05-0.047-0.128-0.081-0.234-0.101l0.09-0.238
				c0.159,0.036,0.278,0.097,0.355,0.182s0.115,0.192,0.115,0.323C10.395,10.955,10.329,11.087,10.197,11.191z M10.232,7.571
				c-0.109,0.072-0.304,0.108-0.584,0.108c-0.274,0-0.473-0.04-0.594-0.119C8.955,7.494,8.906,7.402,8.906,7.285
				s0.05-0.209,0.151-0.276C9.176,6.93,9.374,6.891,9.651,6.891s0.475,0.04,0.596,0.119c0.1,0.066,0.148,0.157,0.148,0.275
				C10.395,7.403,10.341,7.499,10.232,7.571z M10.232,6.627c-0.109,0.072-0.304,0.108-0.584,0.108c-0.274,0-0.473-0.04-0.594-0.119
				C8.955,6.55,8.906,6.458,8.906,6.341s0.05-0.209,0.151-0.276c0.119-0.079,0.317-0.118,0.594-0.118s0.475,0.04,0.596,0.119
				c0.1,0.066,0.148,0.157,0.148,0.275C10.395,6.459,10.341,6.555,10.232,6.627z"/>
		</g>
	</g>
</g>
</svg>
 */