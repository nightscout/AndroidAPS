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
 * Icon for Medtrum Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 */
val IcPluginInsight: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginInsight",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Path 1 – top left detail
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
            moveTo(13.005f, 9.141f)
            verticalLineTo(8.574f)
            lineToRelative(-0.611f, -0.541f)
            horizontalLineToRelative(-0.002f)
            horizontalLineToRelative(-1.608f)
            curveToRelative(-0.117f, 0f, -0.212f, 0.095f, -0.212f, 0.212f)
            verticalLineToRelative(0.009f)
            horizontalLineTo(9.777f)
            verticalLineToRelative(1.208f)
            horizontalLineToRelative(0.794f)
            curveToRelative(0f, 0.125f, 0.076f, 0.22f, 0.212f, 0.22f)
            lineToRelative(1.608f, 0f)
            verticalLineTo(9.681f)
            horizontalLineToRelative(0.002f)
            verticalLineToRelative(0.002f)
            lineTo(13.005f, 9.141f)
            close()
        }

        // Path 2 – right side battery shape
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
            moveTo(17.07f, 11.157f)
            horizontalLineToRelative(-1.185f)
            curveToRelative(-0.627f, 0f, -1.135f, 0.509f, -1.135f, 1.135f)
            verticalLineToRelative(2.169f)
            curveToRelative(0f, 0.627f, 0.509f, 1.135f, 1.135f, 1.135f)
            horizontalLineToRelative(1.185f)
            curveToRelative(0.626f, 0f, 1.135f, -0.509f, 1.135f, -1.135f)
            verticalLineToRelative(-2.169f)
            curveTo(18.206f, 11.666f, 17.697f, 11.157f, 17.07f, 11.157f)
            close()
            moveTo(16.482f, 11.668f)
            lineToRelative(0.35f, 0.431f)
            horizontalLineToRelative(-0.699f)
            lineTo(16.482f, 11.668f)
            close()
            moveTo(16.513f, 15.212f)
            lineToRelative(-0.35f, -0.431f)
            horizontalLineToRelative(0.699f)
            lineTo(16.513f, 15.212f)
            close()
            moveTo(17.34f, 13.717f)
            curveToRelative(0f, 0.213f, -0.174f, 0.386f, -0.387f, 0.386f)
            horizontalLineToRelative(-0.946f)
            curveToRelative(-0.213f, 0f, -0.387f, -0.173f, -0.387f, -0.386f)
            verticalLineToRelative(-0.521f)
            curveToRelative(0f, -0.213f, 0.174f, -0.387f, 0.387f, -0.387f)
            horizontalLineToRelative(0.946f)
            curveToRelative(0.213f, 0f, 0.387f, 0.174f, 0.387f, 0.387f)
            verticalLineTo(13.717f)
            close()
        }

        // Path 3 – inner battery segment
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
            moveTo(16.007f, 13.061f)
            curveToRelative(-0.074f, 0f, -0.134f, 0.06f, -0.134f, 0.134f)
            verticalLineToRelative(0.521f)
            curveToRelative(0f, 0.074f, 0.06f, 0.134f, 0.134f, 0.134f)
            horizontalLineToRelative(0.946f)
            curveToRelative(0.074f, 0f, 0.134f, -0.06f, 0.134f, -0.134f)
            verticalLineToRelative(-0.521f)
            curveToRelative(0f, -0.074f, -0.06f, -0.134f, -0.134f, -0.134f)
            horizontalLineTo(16.007f)
            close()
            moveTo(16.485f, 13.7f)
            curveToRelative(-0.148f, 0f, -0.267f, -0.12f, -0.267f, -0.267f)
            curveToRelative(0f, -0.148f, 0.12f, -0.267f, 0.267f, -0.267f)
            reflectiveCurveToRelative(0.267f, 0.12f, 0.267f, 0.267f)
            curveToRelative(0f, 0.147f, -0.12f, 0.267f, -0.267f, 0.267f)
            close()
        }

        // Path 4 – main body (large)
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
            moveTo(22.733f, 8.767f)
            curveTo(22.577f, 7.2f, 21.821f, 6.228f, 20.664f, 5.77f)
            lineToRelative(-0.007f, 12.481f)
            lineToRelative(-0.254f, 0.069f)
            verticalLineTo(5.705f)
            lineToRelative(0.229f, 0.054f)
            curveToRelative(-0.376f, -0.145f, -0.793f, -0.238f, -1.247f, -0.278f)
            curveTo(14.527f, 5.27f, 9.7f, 5.082f, 4.646f, 5.604f)
            curveToRelative(-1.898f, 0.089f, -3.338f, 1.26f, -3.338f, 3.153f)
            curveToRelative(-0.006f, 1.047f, -0.095f, 2.119f, -0.097f, 3.077f)
            curveToRelative(-0.03f, 1.131f, -0.002f, 2.286f, 0.107f, 3.474f)
            curveToRelative(-0.041f, 1.908f, 1.374f, 3.162f, 3.327f, 3.213f)
            curveToRelative(4.752f, 0.124f, 9.652f, 0.367f, 14.678f, 0.072f)
            curveToRelative(1.772f, -0.104f, 3.208f, -1.21f, 3.367f, -3.243f)
            curveTo(22.778f, 13.15f, 22.864f, 10.939f, 22.733f, 8.767f)
            close()

            // Several subpaths
            moveTo(8.005f, 15.926f)
            curveToRelative(-0.066f, 0f, -0.121f, -0.039f, -0.148f, -0.095f)
            curveToRelative(-0.027f, 0.053f, -0.081f, 0.09f, -0.145f, 0.09f)
            curveToRelative(-0.058f, 0f, -0.106f, -0.032f, -0.136f, -0.077f)
            curveToRelative(-0.029f, 0.045f, -0.078f, 0.077f, -0.136f, 0.077f)
            curveToRelative(-0.091f, 0f, -0.164f, -0.074f, -0.164f, -0.164f)
            curveToRelative(0f, -0.09f, 0.074f, -0.164f, 0.164f, -0.164f)
            curveToRelative(0.058f, 0f, 0.106f, 0.032f, 0.135f, 0.077f)
            curveToRelative(0.029f, -0.045f, 0.078f, -0.077f, 0.136f, -0.077f)
            curveToRelative(0.066f, 0f, 0.121f, 0.039f, 0.148f, 0.095f)
            curveToRelative(0.027f, -0.053f, 0.081f, -0.091f, 0.145f, -0.091f)
            curveToRelative(0.091f, 0f, 0.164f, 0.074f, 0.164f, 0.164f)
            curveTo(8.169f, 15.852f, 8.095f, 15.926f, 8.005f, 15.926f)
            close()

            moveTo(8.229f, 15.776f)
            curveToRelative(0f, -0.137f, 0.111f, -0.248f, 0.248f, -0.248f)
            reflectiveCurveToRelative(0.248f, 0.111f, 0.248f, 0.248f)
            curveToRelative(0f, 0.137f, -0.111f, 0.248f, -0.248f, 0.248f)
            reflectiveCurveTo(8.229f, 15.913f, 8.229f, 15.776f)
            close()

            moveTo(8.904f, 16.175f)
            lineToRelative(-0.037f, 0.037f)
            curveToRelative(-0.01f, 0.01f, -0.027f, 0.01f, -0.037f, 0f)
            lineToRelative(-0.182f, -0.184f)
            curveToRelative(-0.01f, -0.01f, -0.01f, -0.027f, 0f, -0.037f)
            lineToRelative(0.037f, -0.037f)
            curveToRelative(0.01f, -0.01f, 0.027f, -0.01f, 0.037f, 0f)
            lineToRelative(0.182f, 0.184f)
            curveTo(8.914f, 16.148f, 8.914f, 16.165f, 8.904f, 16.175f)
            close()

            moveTo(9.514f, 15.941f)
            curveToRelative(-0.064f, 0f, -0.118f, -0.038f, -0.145f, -0.092f)
            curveToRelative(-0.028f, 0.051f, -0.08f, 0.088f, -0.143f, 0.088f)
            curveToRelative(-0.062f, 0f, -0.114f, -0.036f, -0.142f, -0.086f)
            curveToRelative(-0.027f, 0.053f, -0.081f, 0.09f, -0.144f, 0.09f)
            curveToRelative(-0.091f, 0f, -0.164f, -0.074f, -0.164f, -0.164f)
            curveToRelative(0f, -0.09f, 0.074f, -0.164f, 0.164f, -0.164f)
            curveToRelative(0.062f, 0f, 0.114f, 0.036f, 0.142f, 0.086f)
            curveToRelative(0.027f, -0.053f, 0.081f, -0.09f, 0.144f, -0.09f)
            curveToRelative(0.064f, 0f, 0.118f, 0.038f, 0.145f, 0.091f)
            curveToRelative(0.028f, -0.051f, 0.08f, -0.088f, 0.143f, -0.088f)
            curveToRelative(0.091f, 0f, 0.164f, 0.074f, 0.164f, 0.164f)
            curveTo(9.679f, 15.867f, 9.605f, 15.941f, 9.514f, 15.941f)
            close()

            moveTo(13.522f, 14.753f)
            curveToRelative(0f, 0.121f, -0.098f, 0.219f, -0.219f, 0.219f)
            horizontalLineTo(3.69f)
            curveToRelative(-0.121f, 0f, -0.218f, -0.098f, -0.218f, -0.219f)
            verticalLineToRelative(-3.399f)
            curveToRelative(0f, -0.121f, 0.098f, -0.219f, 0.218f, -0.219f)
            horizontalLineToRelative(9.613f)
            curveToRelative(0.121f, 0f, 0.219f, 0.098f, 0.219f, 0.219f)
            verticalLineTo(14.753f)
            close()

            moveTo(9.949f, 9.684f)
            curveToRelative(-0.076f, 0f, -0.138f, -0.062f, -0.138f, -0.138f)
            verticalLineTo(8.171f)
            curveToRelative(0f, -0.076f, 0.062f, -0.138f, 0.138f, -0.138f)
            horizontalLineToRelative(7.366f)
            curveToRelative(0.076f, 0f, 0.137f, 0.062f, 0.137f, 0.138f)
            horizontalLineToRelative(0f)
            verticalLineToRelative(1.375f)
            curveToRelative(0f, 0.076f, -0.062f, 0.138f, -0.137f, 0.138f)
            horizontalLineTo(9.949f)
            close()

            moveTo(18.295f, 14.509f)
            curveToRelative(0f, 0.655f, -0.531f, 1.186f, -1.186f, 1.186f)
            horizontalLineToRelative(-1.263f)
            curveToRelative(-0.655f, 0f, -1.186f, -0.531f, -1.186f, -1.186f)
            verticalLineToRelative(-2.25f)
            curveToRelative(0f, -0.655f, 0.531f, -1.186f, 1.186f, -1.186f)
            horizontalLineToRelative(1.263f)
            curveToRelative(0.655f, 0f, 1.186f, 0.531f, 1.186f, 1.186f)
            verticalLineTo(14.509f)
            close()
        }

        // Group with opacity 0.8 – all subpaths inside
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            // First small shape
            moveTo(9.797f, 12.654f)
            curveToRelative(0.018f, -0.018f, 0.027f, -0.044f, 0.027f, -0.077f)
            curveToRelative(0f, -0.024f, -0.005f, -0.044f, -0.016f, -0.061f)
            curveToRelative(-0.011f, -0.017f, -0.025f, -0.028f, -0.042f, -0.034f)
            curveToRelative(-0.011f, -0.003f, -0.032f, -0.005f, -0.063f, -0.005f)
            horizontalLineToRelative(-0.13f)
            verticalLineToRelative(0.203f)
            horizontalLineToRelative(0.132f)
            curveToRelative(0.03f, 0f, 0.061f, -0.008f, 0.079f, -0.027f)
            close()

            // Dot
            moveTo(4.132f, 11.766f)
            curveToRelative(0.029f, 0f, 0.054f, -0.014f, 0.074f, -0.04f)
            curveToRelative(0.02f, -0.027f, 0.03f, -0.08f, 0.03f, -0.16f)
            curveToRelative(0f, -0.081f, -0.01f, -0.134f, -0.03f, -0.161f)
            curveToRelative(-0.02f, -0.026f, -0.045f, -0.04f, -0.074f, -0.04f)
            curveToRelative(-0.029f, 0f, -0.053f, 0.012f, -0.07f, 0.035f)
            curveToRelative(-0.022f, 0.03f, -0.033f, 0.085f, -0.033f, 0.165f)
            curveToRelative(0f, 0.08f, 0.01f, 0.134f, 0.03f, 0.16f)
            reflectiveCurveTo(4.103f, 11.766f, 4.132f, 11.766f)
            close()

            // Another small shape
            moveTo(8.343f, 12.478f)
            verticalLineToRelative(0.172f)
            horizontalLineToRelative(0.11f)
            curveToRelative(0.03f, 0f, 0.051f, -0.002f, 0.064f, -0.006f)
            curveToRelative(0.017f, -0.006f, 0.03f, -0.015f, 0.039f, -0.028f)
            curveToRelative(0.009f, -0.013f, 0.013f, -0.03f, 0.013f, -0.05f)
            curveToRelative(0f, -0.019f, -0.004f, -0.036f, -0.012f, -0.05f)
            curveToRelative(-0.008f, -0.014f, -0.02f, -0.024f, -0.035f, -0.03f)
            curveToRelative(-0.015f, -0.005f, -0.041f, -0.008f, -0.077f, -0.008f)
            horizontalLineTo(8.343f)
            close()

            // Rectangle
            moveTo(7.965f, 11.457f)
            lineToRelative(0.441f, 0f)
            lineToRelative(0f, 0.205f)
            lineToRelative(-0.441f, 0f)
            close()

            // Dot
            moveTo(10.359f, 12.932f)
            curveToRelative(0.031f, 0f, 0.057f, -0.013f, 0.077f, -0.04f)
            curveToRelative(0.021f, -0.026f, 0.031f, -0.067f, 0.031f, -0.121f)
            curveToRelative(0f, -0.051f, -0.01f, -0.09f, -0.031f, -0.116f)
            curveToRelative(-0.021f, -0.026f, -0.047f, -0.04f, -0.077f, -0.04f)
            curveToRelative(-0.031f, 0f, -0.057f, 0.013f, -0.078f, 0.039f)
            curveToRelative(-0.021f, 0.026f, -0.031f, 0.066f, -0.031f, 0.118f)
            curveToRelative(0f, 0.053f, 0.01f, 0.092f, 0.031f, 0.119f)
            curveToRelative(0.02f, 0.027f, 0.046f, 0.04f, 0.077f, 0.04f)
            close()

            // Dot
            moveTo(11.87f, 11.77f)
            curveToRelative(0.027f, 0f, 0.049f, -0.014f, 0.067f, -0.042f)
            curveToRelative(0.018f, -0.028f, 0.027f, -0.084f, 0.027f, -0.168f)
            curveToRelative(0f, -0.084f, -0.009f, -0.14f, -0.027f, -0.168f)
            curveToRelative(-0.018f, -0.028f, -0.041f, -0.042f, -0.068f, -0.042f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.012f, -0.064f, 0.037f)
            curveToRelative(-0.02f, 0.031f, -0.03f, 0.089f, -0.03f, 0.173f)
            reflectiveCurveToRelative(0.009f, 0.14f, 0.027f, 0.168f)
            curveToRelative(0.018f, 0.028f, 0.04f, 0.042f, 0.067f, 0.042f)
            close()

            // Dot
            moveTo(12.256f, 14.185f)
            curveToRelative(0.045f, 0f, 0.082f, -0.021f, 0.113f, -0.063f)
            curveToRelative(0.03f, -0.042f, 0.046f, -0.126f, 0.046f, -0.252f)
            curveToRelative(0f, -0.126f, -0.015f, -0.211f, -0.046f, -0.252f)
            curveToRelative(-0.03f, -0.042f, -0.068f, -0.062f, -0.114f, -0.062f)
            curveToRelative(-0.045f, 0f, -0.08f, 0.018f, -0.107f, 0.055f)
            curveToRelative(-0.033f, 0.047f, -0.05f, 0.133f, -0.05f, 0.259f)
            curveToRelative(0f, 0.126f, 0.015f, 0.21f, 0.046f, 0.252f)
            curveToRelative(0.03f, 0.042f, 0.067f, 0.063f, 0.112f, 0.063f)
            close()

            // Small shape
            moveTo(10.513f, 11.663f)
            curveToRelative(-0.01f, 0.005f, -0.018f, 0.012f, -0.023f, 0.02f)
            curveToRelative(-0.005f, 0.009f, -0.008f, 0.019f, -0.008f, 0.03f)
            curveToRelative(0f, 0.017f, 0.006f, 0.031f, 0.018f, 0.042f)
            curveToRelative(0.012f, 0.011f, 0.029f, 0.017f, 0.052f, 0.017f)
            curveToRelative(0.022f, 0f, 0.042f, -0.005f, 0.06f, -0.016f)
            curveToRelative(0.017f, -0.011f, 0.03f, -0.025f, 0.038f, -0.044f)
            curveToRelative(0.006f, -0.014f, 0.009f, -0.035f, 0.009f, -0.063f)
            verticalLineToRelative(-0.023f)
            curveToRelative(-0.021f, 0.009f, -0.053f, 0.017f, -0.095f, 0.024f)
            curveToRelative(-0.043f, 0.007f, -0.06f, 0.011f, -0.07f, 0.016f)
            close()

            // Another
            moveTo(8.967f, 12.662f)
            curveToRelative(0.031f, 0f, 0.055f, -0.003f, 0.072f, -0.011f)
            curveToRelative(0.017f, -0.007f, 0.031f, -0.019f, 0.04f, -0.034f)
            curveToRelative(0.009f, -0.016f, 0.014f, -0.033f, 0.014f, -0.051f)
            curveToRelative(0f, -0.027f, -0.009f, -0.049f, -0.026f, -0.066f)
            curveToRelative(-0.017f, -0.017f, -0.045f, -0.026f, -0.083f, -0.026f)
            horizontalLineTo(8.822f)
            verticalLineToRelative(0.188f)
            horizontalLineToRelative(0f)
            horizontalLineTo(8.967f)
            close()

            // Start of a long detailed section
            moveTo(13.211f, 11.228f)
            horizontalLineTo(3.78f)
            curveToRelative(-0.115f, 0f, -0.208f, 0.093f, -0.208f, 0.208f)
            verticalLineToRelative(3.239f)
            curveToRelative(0f, 0.115f, 0.093f, 0.208f, 0.208f, 0.208f)
            horizontalLineToRelative(9.431f)
            curveToRelative(0.115f, 0f, 0.208f, -0.093f, 0.208f, -0.208f)
            verticalLineToRelative(-3.239f)
            curveTo(13.419f, 11.321f, 13.326f, 11.228f, 13.211f, 11.228f)
            close()
            moveTo(13.129f, 13.978f)
            verticalLineToRelative(0.087f)
            horizontalLineToRelative(-0.107f)
            verticalLineToRelative(0.184f)
            horizontalLineToRelative(-0.097f)
            verticalLineToRelative(-0.184f)
            horizontalLineToRelative(-0.343f)
            verticalLineToRelative(-0.087f)
            lineToRelative(0.361f, -0.498f)
            horizontalLineToRelative(0.079f)
            verticalLineToRelative(0.498f)
            horizontalLineTo(13.129f)
            close()

            moveTo(12.471f, 11.414f)
            curveToRelative(0.012f, -0.037f, 0.029f, -0.065f, 0.051f, -0.086f)
            curveToRelative(0.023f, -0.02f, 0.052f, -0.03f, 0.086f, -0.03f)
            curveToRelative(0.025f, 0f, 0.048f, 0.006f, 0.067f, 0.017f)
            curveToRelative(0.019f, 0.011f, 0.035f, 0.027f, 0.048f, 0.048f)
            curveToRelative(0.012f, 0.021f, 0.022f, 0.046f, 0.029f, 0.076f)
            curveToRelative(0.007f, 0.03f, 0.011f, 0.07f, 0.011f, 0.121f)
            curveToRelative(0f, 0.06f, -0.006f, 0.109f, -0.017f, 0.145f)
            curveToRelative(-0.011f, 0.037f, -0.029f, 0.066f, -0.051f, 0.086f)
            curveToRelative(-0.023f, 0.02f, -0.052f, 0.03f, -0.086f, 0.03f)
            curveToRelative(-0.046f, 0f, -0.082f, -0.018f, -0.108f, -0.053f)
            curveToRelative(-0.031f, -0.043f, -0.047f, -0.112f, -0.047f, -0.208f)
            curveToRelative(0f, -0.056f, 0.006f, -0.104f, 0.018f, -0.141f)
            close()

            moveTo(12.084f, 11.769f)
            curveToRelative(0.008f, -0.023f, 0.021f, -0.046f, 0.038f, -0.068f)
            curveToRelative(0.018f, -0.022f, 0.043f, -0.048f, 0.076f, -0.077f)
            curveToRelative(0.052f, -0.046f, 0.087f, -0.082f, 0.105f, -0.109f)
            curveToRelative(0.018f, -0.027f, 0.027f, -0.052f, 0.027f, -0.076f)
            curveToRelative(0f, -0.025f, -0.008f, -0.046f, -0.025f, -0.063f)
            curveToRelative(-0.016f, -0.017f, -0.038f, -0.026f, -0.065f, -0.026f)
            curveToRelative(-0.028f, 0f, -0.051f, 0.009f, -0.067f, 0.027f)
            curveToRelative(-0.017f, 0.018f, -0.025f, 0.043f, -0.026f, 0.075f)
            lineToRelative(-0.06f, -0.007f)
            curveToRelative(0.004f, -0.048f, 0.02f, -0.085f, 0.047f, -0.11f)
            curveToRelative(0.027f, -0.025f, 0.063f, -0.038f, 0.108f, -0.038f)
            curveToRelative(0.046f, 0f, 0.082f, 0.014f, 0.108f, 0.041f)
            curveToRelative(0.027f, 0.027f, 0.04f, 0.061f, 0.04f, 0.101f)
            curveToRelative(0f, 0.02f, -0.004f, 0.041f, -0.012f, 0.06f)
            curveToRelative(-0.008f, 0.02f, -0.021f, 0.041f, -0.039f, 0.063f)
            curveToRelative(-0.018f, 0.022f, -0.048f, 0.052f, -0.09f, 0.09f)
            curveToRelative(-0.035f, 0.032f, -0.057f, 0.053f, -0.067f, 0.065f)
            curveToRelative(-0.01f, 0.011f, -0.018f, 0.023f, -0.025f, 0.034f)
            lineToRelative(0.233f, 0f)
            verticalLineToRelative(0.061f)
            horizontalLineToRelative(-0.314f)
            curveToRelative(-0.001f, -0.022f, 0.001f, -0.036f, 0.006f, -0.05f)
            close()

            moveTo(12.112f, 13.522f)
            curveToRelative(0.038f, -0.03f, 0.086f, -0.045f, 0.143f, -0.045f)
            curveToRelative(0.042f, 0f, 0.08f, 0.008f, 0.112f, 0.025f)
            curveToRelative(0.032f, 0.017f, 0.059f, 0.04f, 0.08f, 0.072f)
            curveToRelative(0.021f, 0.031f, 0.037f, 0.069f, 0.049f, 0.114f)
            curveToRelative(0.012f, 0.045f, 0.018f, 0.106f, 0.018f, 0.182f)
            curveToRelative(0f, 0.09f, -0.01f, 0.163f, -0.029f, 0.219f)
            curveToRelative(-0.019f, 0.055f, -0.048f, 0.098f, -0.086f, 0.128f)
            curveToRelative(-0.038f, 0.03f, -0.086f, 0.045f, -0.144f, 0.045f)
            curveToRelative(-0.076f, 0f, -0.136f, -0.027f, -0.18f, -0.08f)
            curveToRelative(-0.052f, -0.064f, -0.078f, -0.168f, -0.078f, -0.313f)
            curveToRelative(0f, -0.091f, 0.01f, -0.164f, 0.029f, -0.219f)
            curveToRelative(0.019f, -0.055f, 0.047f, -0.098f, 0.085f, -0.128f)
            close()

            moveTo(11.732f, 11.414f)
            curveToRelative(0.012f, -0.037f, 0.029f, -0.065f, 0.052f, -0.086f)
            curveToRelative(0.023f, -0.02f, 0.051f, -0.03f, 0.086f, -0.03f)
            curveToRelative(0.026f, 0f, 0.048f, 0.006f, 0.067f, 0.017f)
            curveToRelative(0.019f, 0.011f, 0.035f, 0.027f, 0.048f, 0.048f)
            curveToRelative(0.013f, 0.021f, 0.022f, 0.046f, 0.03f, 0.076f)
            curveToRelative(0.007f, 0.03f, 0.011f, 0.07f, 0.011f, 0.121f)
            curveToRelative(0f, 0.06f, -0.006f, 0.109f, -0.017f, 0.145f)
            curveToRelative(-0.012f, 0.037f, -0.029f, 0.066f, -0.051f, 0.086f)
            curveToRelative(-0.023f, 0.02f, -0.052f, 0.03f, -0.086f, 0.03f)
            curveToRelative(-0.046f, 0f, -0.082f, -0.018f, -0.108f, -0.053f)
            curveToRelative(-0.031f, -0.043f, -0.047f, -0.112f, -0.047f, -0.208f)
            curveToRelative(0f, -0.056f, 0.006f, -0.104f, 0.018f, -0.141f)
            close()

            moveTo(11.744f, 13.8f)
            verticalLineToRelative(-0.108f)
            horizontalLineToRelative(0.111f)
            verticalLineTo(13.8f)
            horizontalLineTo(11.744f)
            close()

            moveTo(11.855f, 14.142f)
            verticalLineToRelative(0.108f)
            horizontalLineToRelative(-0.111f)
            verticalLineToRelative(-0.108f)
            horizontalLineTo(11.855f)
            close()

            moveTo(11.345f, 11.769f)
            curveToRelative(0.008f, -0.023f, 0.021f, -0.046f, 0.038f, -0.068f)
            curveToRelative(0.018f, -0.022f, 0.043f, -0.048f, 0.076f, -0.077f)
            curveToRelative(0.052f, -0.046f, 0.087f, -0.082f, 0.105f, -0.109f)
            curveToRelative(0.018f, -0.027f, 0.027f, -0.052f, 0.027f, -0.076f)
            curveToRelative(0f, -0.025f, -0.008f, -0.046f, -0.025f, -0.063f)
            curveToRelative(-0.016f, -0.017f, -0.038f, -0.026f, -0.065f, -0.026f)
            curveToRelative(-0.028f, 0f, -0.051f, 0.009f, -0.067f, 0.027f)
            curveToRelative(-0.017f, 0.018f, -0.025f, 0.043f, -0.026f, 0.075f)
            lineToRelative(-0.06f, -0.007f)
            curveToRelative(0.004f, -0.048f, 0.019f, -0.085f, 0.046f, -0.11f)
            curveToRelative(0.027f, -0.025f, 0.063f, -0.038f, 0.108f, -0.038f)
            curveToRelative(0.046f, 0f, 0.082f, 0.014f, 0.108f, 0.041f)
            curveToRelative(0.027f, 0.027f, 0.04f, 0.061f, 0.04f, 0.101f)
            curveToRelative(0f, 0.02f, -0.004f, 0.041f, -0.012f, 0.06f)
            curveToRelative(-0.008f, 0.02f, -0.021f, 0.041f, -0.039f, 0.063f)
            curveToRelative(-0.018f, 0.022f, -0.048f, 0.052f, -0.09f, 0.09f)
            curveToRelative(-0.035f, 0.032f, -0.058f, 0.053f, -0.068f, 0.065f)
            curveToRelative(-0.01f, 0.011f, -0.018f, 0.023f, -0.025f, 0.034f)
            lineToRelative(0.233f, 0f)
            verticalLineToRelative(0.061f)
            horizontalLineToRelative(-0.314f)
            curveToRelative(-0.001f, -0.022f, 0.001f, -0.036f, 0.006f, -0.05f)
            close()

            moveTo(11.414f, 12.583f)
            curveToRelative(-0.024f, 0.016f, -0.047f, 0.028f, -0.066f, 0.036f)
            verticalLineToRelative(-0.068f)
            curveToRelative(0.035f, -0.018f, 0.066f, -0.041f, 0.092f, -0.067f)
            curveToRelative(0.026f, -0.026f, 0.045f, -0.052f, 0.056f, -0.076f)
            horizontalLineToRelative(0.04f)
            verticalLineToRelative(0.572f)
            horizontalLineToRelative(0f)
            horizontalLineToRelative(-0.062f)
            verticalLineToRelative(-0.445f)
            curveToRelative(-0.022f, 0.02f, -0.042f, 0.036f, -0.066f, 0.052f)
            close()

            moveTo(11.338f, 13.477f)
            curveToRelative(0.076f, 0f, 0.136f, 0.02f, 0.18f, 0.061f)
            curveToRelative(0.044f, 0.041f, 0.066f, 0.092f, 0.066f, 0.152f)
            curveToRelative(0f, 0.031f, -0.006f, 0.061f, -0.019f, 0.091f)
            curveToRelative(-0.013f, 0.03f, -0.035f, 0.061f, -0.065f, 0.094f)
            curveToRelative(-0.03f, 0.033f, -0.08f, 0.078f, -0.15f, 0.135f)
            curveToRelative(-0.058f, 0.048f, -0.096f, 0.08f, -0.112f, 0.097f)
            curveToRelative(-0.017f, 0.017f, -0.03f, 0.034f, -0.041f, 0.051f)
            lineToRelative(0.388f, 0f)
            verticalLineToRelative(0.091f)
            horizontalLineToRelative(-0.523f)
            curveToRelative(-0.001f, -0.023f, 0.003f, -0.045f, 0.011f, -0.066f)
            curveToRelative(0.013f, -0.035f, 0.035f, -0.069f, 0.064f, -0.102f)
            curveToRelative(0.029f, -0.033f, 0.072f, -0.072f, 0.127f, -0.116f)
            curveToRelative(0.086f, -0.069f, 0.144f, -0.123f, 0.175f, -0.163f)
            curveToRelative(0.03f, -0.04f, 0.045f, -0.078f, 0.045f, -0.113f)
            curveToRelative(0f, -0.037f, -0.014f, -0.069f, -0.041f, -0.095f)
            curveToRelative(-0.027f, -0.026f, -0.063f, -0.038f, -0.108f, -0.038f)
            curveToRelative(-0.047f, 0f, -0.084f, 0.014f, -0.112f, 0.041f)
            curveToRelative(-0.028f, 0.027f, -0.042f, 0.065f, -0.043f, 0.113f)
            lineToRelative(-0.1f, -0.01f)
            curveToRelative(0.007f, -0.072f, 0.033f, -0.128f, 0.077f, -0.165f)
            curveToRelative(0.044f, -0.037f, 0.104f, -0.056f, 0.179f, -0.056f)
            close()

            moveTo(10.808f, 11.442f)
            horizontalLineToRelative(0.052f)
            verticalLineToRelative(0.053f)
            curveToRelative(0.025f, -0.041f, 0.062f, -0.061f, 0.11f, -0.061f)
            curveToRelative(0.021f, 0f, 0.04f, 0.004f, 0.057f, 0.012f)
            curveToRelative(0.017f, 0.008f, 0.03f, 0.019f, 0.039f, 0.032f)
            curveToRelative(0.009f, 0.013f, 0.015f, 0.029f, 0.018f, 0.047f)
            curveToRelative(0.002f, 0.012f, 0.003f, 0.032f, 0.003f, 0.061f)
            verticalLineToRelative(0.228f)
            horizontalLineTo(11.03f)
            verticalLineToRelative(-0.226f)
            curveToRelative(0f, -0.026f, -0.002f, -0.045f, -0.007f, -0.058f)
            curveToRelative(-0.005f, -0.013f, -0.013f, -0.023f, -0.024f, -0.03f)
            curveToRelative(-0.012f, -0.008f, -0.025f, -0.011f, -0.041f, -0.011f)
            curveToRelative(-0.025f, 0f, -0.046f, 0.009f, -0.064f, 0.026f)
            curveToRelative(-0.018f, 0.017f, -0.027f, 0.049f, -0.027f, 0.097f)
            verticalLineToRelative(0.203f)
            horizontalLineToRelative(-0.058f)
            verticalLineTo(11.442f)
            close()

            moveTo(11.022f, 12.411f)
            verticalLineToRelative(0.569f)
            horizontalLineTo(10.96f)
            verticalLineToRelative(-0.569f)
            horizontalLineTo(11.022f)
            close()

            moveTo(10.803f, 12.411f)
            horizontalLineToRelative(0.063f)
            verticalLineToRelative(0.08f)
            horizontalLineToRelative(-0.063f)
            verticalLineTo(12.411f)
            close()

            moveTo(10.803f, 12.568f)
            horizontalLineToRelative(0.063f)
            verticalLineToRelative(0.412f)
            horizontalLineToRelative(-0.063f)
            verticalLineTo(12.568f)
            close()

            moveTo(10.637f, 13.713f)
            curveToRelative(-0.038f, 0.022f, -0.072f, 0.038f, -0.102f, 0.049f)
            verticalLineTo(13.67f)
            curveToRelative(0.054f, -0.025f, 0.102f, -0.055f, 0.143f, -0.09f)
            curveToRelative(0.041f, -0.035f, 0.069f, -0.07f, 0.086f, -0.103f)
            horizontalLineToRelative(0.063f)
            verticalLineToRelative(0.772f)
            horizontalLineToRelative(-0.097f)
            verticalLineToRelative(-0.602f)
            curveToRelative(-0.028f, 0.024f, -0.058f, 0.046f, -0.096f, 0.068f)
            close()

            moveTo(10.774f, 12.469f)
            curveToRelative(-0.012f, -0.002f, -0.023f, -0.003f, -0.034f, -0.003f)
            curveToRelative(-0.022f, 0f, -0.036f, 0.005f, -0.044f, 0.014f)
            curveToRelative(-0.008f, 0.01f, -0.012f, 0.03f, -0.012f, 0.061f)
            verticalLineToRelative(0.027f)
            horizontalLineToRelative(0.072f)
            verticalLineToRelative(0.054f)
            horizontalLineToRelative(-0.072f)
            verticalLineToRelative(0.359f)
            horizontalLineToRelative(-0.062f)
            verticalLineToRelative(-0.359f)
            horizontalLineToRelative(-0.056f)
            verticalLineToRelative(-0.054f)
            horizontalLineToRelative(0.056f)
            verticalLineToRelative(-0.04f)
            curveToRelative(0f, -0.041f, 0.008f, -0.072f, 0.024f, -0.094f)
            curveToRelative(0.016f, -0.022f, 0.041f, -0.033f, 0.075f, -0.033f)
            curveToRelative(0.023f, 0f, 0.044f, 0.003f, 0.064f, 0.009f)
            lineToRelative(-0.02f, 0.063f)
            close()

            moveTo(10.643f, 11.507f)
            curveToRelative(-0.015f, -0.014f, -0.037f, -0.021f, -0.066f, -0.021f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.005f, -0.061f, 0.016f)
            curveToRelative(-0.013f, 0.01f, -0.023f, 0.029f, -0.029f, 0.055f)
            lineToRelative(-0.057f, -0.008f)
            curveToRelative(0.005f, -0.026f, 0.014f, -0.048f, 0.026f, -0.064f)
            curveToRelative(0.012f, -0.016f, 0.029f, -0.029f, 0.052f, -0.037f)
            curveToRelative(0.022f, -0.009f, 0.048f, -0.013f, 0.078f, -0.013f)
            curveToRelative(0.029f, 0f, 0.053f, 0.004f, 0.072f, 0.011f)
            curveToRelative(0.018f, 0.008f, 0.032f, 0.017f, 0.041f, 0.028f)
            curveToRelative(0.009f, 0.011f, 0.015f, 0.026f, 0.018f, 0.043f)
            curveToRelative(0.002f, 0.011f, 0.003f, 0.03f, 0.003f, 0.058f)
            verticalLineToRelative(0.084f)
            curveToRelative(0f, 0.059f, 0.001f, 0.095f, 0.004f, 0.111f)
            curveToRelative(0.002f, 0.016f, 0.007f, 0.03f, 0.015f, 0.045f)
            horizontalLineToRelative(-0.061f)
            curveToRelative(-0.006f, -0.013f, -0.01f, -0.028f, -0.012f, -0.046f)
            curveToRelative(-0.022f, 0.02f, -0.043f, 0.034f, -0.062f, 0.042f)
            curveToRelative(-0.02f, 0.008f, -0.041f, 0.012f, -0.064f, 0.012f)
            curveToRelative(-0.038f, 0f, -0.067f, -0.01f, -0.087f, -0.03f)
            curveToRelative(-0.02f, -0.02f, -0.031f, -0.045f, -0.031f, -0.076f)
            curveToRelative(0f, -0.018f, 0.004f, -0.035f, 0.012f, -0.05f)
            curveToRelative(0.008f, -0.015f, 0.018f, -0.027f, 0.03f, -0.036f)
            curveToRelative(0.012f, -0.009f, 0.026f, -0.016f, 0.042f, -0.021f)
            curveToRelative(0.011f, -0.003f, 0.029f, -0.006f, 0.052f, -0.009f)
            curveToRelative(0.047f, -0.006f, 0.082f, -0.013f, 0.104f, -0.022f)
            curveToRelative(0f, -0.009f, 0f, -0.014f, 0f, -0.016f)
            curveToRelative(0f, -0.022f, -0.005f, -0.04f, -0.016f, -0.05f)
            close()

            moveTo(10.483f, 12.614f)
            curveToRelative(0.032f, 0.037f, 0.048f, 0.088f, 0.048f, 0.154f)
            curveToRelative(0f, 0.053f, -0.007f, 0.095f, -0.022f, 0.125f)
            curveToRelative(-0.014f, 0.03f, -0.035f, 0.054f, -0.062f, 0.071f)
            curveToRelative(-0.027f, 0.017f, -0.057f, 0.025f, -0.089f, 0.025f)
            curveToRelative(-0.052f, 0f, -0.094f, -0.019f, -0.125f, -0.055f)
            curveToRelative(-0.032f, -0.037f, -0.048f, -0.091f, -0.048f, -0.16f)
            curveToRelative(0f, -0.076f, 0.019f, -0.133f, 0.057f, -0.17f)
            curveToRelative(0.032f, -0.03f, 0.07f, -0.046f, 0.116f, -0.046f)
            curveToRelative(0.045f, 0f, 0.086f, 0.019f, 0.118f, 0.056f)
            close()

            moveTo(10.299f, 13.871f)
            curveToRelative(0f, 0.164f, -0.132f, 0.298f, -0.296f, 0.298f)
            curveToRelative(-0.163f, 0f, -0.296f, -0.133f, -0.296f, -0.298f)
            reflectiveCurveToRelative(0.132f, -0.298f, 0.296f, -0.298f)
            curveToRelative(0.164f, 0f, 0.296f, 0.133f, 0.296f, 0.298f)
            close()

            moveTo(10.139f, 11.659f)
            curveToRelative(0.001f, 0.039f, 0.008f, 0.066f, 0.02f, 0.08f)
            curveToRelative(0.012f, 0.015f, 0.029f, 0.022f, 0.05f, 0.022f)
            curveToRelative(0.016f, 0f, 0.029f, -0.004f, 0.041f, -0.012f)
            curveToRelative(0.012f, -0.008f, 0.019f, -0.018f, 0.024f, -0.032f)
            curveToRelative(0.004f, -0.013f, 0.006f, -0.035f, 0.006f, -0.064f)
            verticalLineTo(11.3f)
            horizontalLineToRelative(0.063f)
            verticalLineToRelative(0.349f)
            curveToRelative(0f, 0.043f, -0.005f, 0.076f, -0.014f, 0.099f)
            curveToRelative(-0.01f, 0.024f, -0.025f, 0.041f, -0.046f, 0.054f)
            curveToRelative(-0.021f, 0.012f, -0.045f, 0.019f, -0.073f, 0.019f)
            curveToRelative(-0.042f, 0f, -0.074f, -0.013f, -0.096f, -0.039f)
            curveToRelative(-0.022f, -0.026f, -0.033f, -0.064f, -0.032f, -0.115f)
            lineToRelative(0.065f, -0.006f)
            close()

            moveTo(9.971f, 12.568f)
            horizontalLineToRelative(0.056f)
            verticalLineToRelative(0.062f)
            curveToRelative(0.014f, -0.029f, 0.028f, -0.048f, 0.04f, -0.058f)
            curveToRelative(0.012f, -0.009f, 0.026f, -0.014f, 0.04f, -0.014f)
            curveToRelative(0.021f, 0f, 0.042f, 0.008f, 0.064f, 0.023f)
            lineToRelative(-0.022f, 0.065f)
            curveToRelative(-0.015f, -0.01f, -0.031f, -0.015f, -0.046f, -0.015f)
            curveToRelative(-0.014f, 0f, -0.026f, 0.005f, -0.037f, 0.014f)
            curveToRelative(-0.011f, 0.009f, -0.019f, 0.022f, -0.023f, 0.038f)
            curveToRelative(-0.007f, 0.025f, -0.01f, 0.052f, -0.01f, 0.081f)
            verticalLineToRelative(0.216f)
            lineToRelative(-0.063f, 0f)
            verticalLineTo(12.568f)
            close()

            moveTo(9.761f, 11.591f)
            curveToRelative(-0.018f, -0.019f, -0.04f, -0.028f, -0.067f, -0.028f)
            curveToRelative(-0.011f, 0f, -0.025f, 0.002f, -0.041f, 0.007f)
            lineToRelative(0.006f, -0.055f)
            curveToRelative(0.004f, 0.001f, 0.007f, 0.001f, 0.009f, 0.001f)
            curveToRelative(0.025f, 0f, 0.047f, -0.007f, 0.067f, -0.021f)
            curveToRelative(0.02f, -0.014f, 0.03f, -0.036f, 0.03f, -0.065f)
            curveToRelative(0f, -0.023f, -0.007f, -0.042f, -0.022f, -0.057f)
            curveToRelative(-0.015f, -0.015f, -0.033f, -0.023f, -0.056f, -0.023f)
            curveToRelative(-0.023f, 0f, -0.041f, 0.008f, -0.057f, 0.023f)
            curveToRelative(-0.015f, 0.015f, -0.025f, 0.038f, -0.029f, 0.069f)
            lineToRelative(-0.058f, -0.011f)
            curveToRelative(0.007f, -0.042f, 0.023f, -0.075f, 0.049f, -0.098f)
            curveToRelative(0.025f, -0.023f, 0.057f, -0.035f, 0.094f, -0.035f)
            curveToRelative(0.026f, 0f, 0.05f, 0.006f, 0.072f, 0.018f)
            curveToRelative(0.022f, 0.012f, 0.038f, 0.028f, 0.05f, 0.049f)
            curveToRelative(0.012f, 0.021f, 0.017f, 0.043f, 0.017f, 0.066f)
            curveToRelative(0f, 0.022f, -0.005f, 0.042f, -0.016f, 0.06f)
            curveToRelative(-0.011f, 0.018f, -0.027f, 0.033f, -0.049f, 0.043f)
            curveToRelative(0.028f, 0.007f, 0.05f, 0.022f, 0.065f, 0.044f)
            curveToRelative(0.016f, 0.022f, 0.023f, 0.049f, 0.023f, 0.083f)
            curveToRelative(0f, 0.045f, -0.015f, 0.083f, -0.045f, 0.114f)
            curveToRelative(-0.03f, 0.031f, -0.069f, 0.047f, -0.115f, 0.047f)
            curveToRelative(-0.042f, 0f, -0.076f, -0.013f, -0.104f, -0.04f)
            curveToRelative(-0.028f, -0.027f, -0.043f, -0.062f, -0.047f, -0.104f)
            lineToRelative(0.058f, -0.008f)
            curveToRelative(0.007f, 0.036f, 0.018f, 0.061f, 0.034f, 0.077f)
            reflectiveCurveToRelative(0.036f, 0.024f, 0.059f, 0.024f)
            curveToRelative(0.027f, 0f, 0.051f, -0.01f, 0.069f, -0.031f)
            curveToRelative(0.019f, -0.02f, 0.029f, -0.046f, 0.029f, -0.076f)
            curveToRelative(0f, -0.029f, -0.009f, -0.053f, -0.026f, -0.072f)
            close()

            moveTo(9.505f, 12.411f)
            horizontalLineToRelative(0.192f)
            curveToRelative(0.034f, 0f, 0.06f, 0.002f, 0.077f, 0.005f)
            curveToRelative(0.025f, 0.005f, 0.046f, 0.013f, 0.063f, 0.027f)
            curveToRelative(0.017f, 0.013f, 0.031f, 0.031f, 0.041f, 0.055f)
            curveToRelative(0.01f, 0.024f, 0.015f, 0.049f, 0.015f, 0.078f)
            curveToRelative(0f, 0.048f, -0.014f, 0.089f, -0.041f, 0.123f)
            curveToRelative(-0.028f, 0.034f, -0.077f, 0.05f, -0.15f, 0.05f)
            horizontalLineTo(9.572f)
            verticalLineToRelative(0.232f)
            lineToRelative(-0.067f, 0f)
            verticalLineTo(12.411f)
            close()

            moveTo(9.185f, 11.414f)
            curveToRelative(0.012f, -0.037f, 0.029f, -0.065f, 0.052f, -0.086f)
            curveToRelative(0.023f, -0.02f, 0.051f, -0.03f, 0.086f, -0.03f)
            curveToRelative(0.026f, 0f, 0.048f, 0.006f, 0.067f, 0.017f)
            curveToRelative(0.019f, 0.011f, 0.035f, 0.027f, 0.048f, 0.048f)
            curveToRelative(0.013f, 0.021f, 0.022f, 0.046f, 0.03f, 0.076f)
            curveToRelative(0.007f, 0.03f, 0.011f, 0.07f, 0.011f, 0.121f)
            curveToRelative(0f, 0.06f, -0.006f, 0.109f, -0.017f, 0.145f)
            curveToRelative(-0.012f, 0.037f, -0.029f, 0.066f, -0.051f, 0.086f)
            curveToRelative(-0.023f, 0.02f, -0.052f, 0.03f, -0.086f, 0.03f)
            curveToRelative(-0.046f, 0f, -0.082f, -0.018f, -0.108f, -0.053f)
            curveToRelative(-0.031f, -0.043f, -0.047f, -0.112f, -0.047f, -0.208f)
            curveToRelative(0f, -0.056f, 0.006f, -0.104f, 0.018f, -0.141f)
            close()

            moveTo(9.428f, 12.739f)
            verticalLineToRelative(0.07f)
            horizontalLineTo(9.236f)
            verticalLineToRelative(-0.07f)
            horizontalLineTo(9.428f)
            close()

            moveTo(8.755f, 12.411f)
            horizontalLineToRelative(0.226f)
            curveToRelative(0.045f, 0f, 0.08f, 0.005f, 0.104f, 0.015f)
            curveToRelative(0.024f, 0.01f, 0.042f, 0.028f, 0.057f, 0.054f)
            curveToRelative(0.014f, 0.026f, 0.021f, 0.055f, 0.021f, 0.086f)
            curveToRelative(0f, 0.04f, -0.012f, 0.074f, -0.035f, 0.102f)
            curveToRelative(-0.023f, 0.028f, -0.06f, 0.045f, -0.109f, 0.053f)
            curveToRelative(0.018f, 0.01f, 0.031f, 0.019f, 0.041f, 0.028f)
            curveToRelative(0.02f, 0.02f, 0.038f, 0.045f, 0.056f, 0.076f)
            lineToRelative(0.089f, 0.155f)
            horizontalLineTo(9.119f)
            lineToRelative(-0.067f, -0.118f)
            curveToRelative(-0.02f, -0.034f, -0.036f, -0.06f, -0.049f, -0.079f)
            curveToRelative(-0.013f, -0.018f, -0.024f, -0.031f, -0.034f, -0.038f)
            curveToRelative(-0.01f, -0.007f, -0.02f, -0.012f, -0.031f, -0.015f)
            curveToRelative(-0.008f, -0.002f, -0.02f, -0.003f, -0.038f, -0.003f)
            horizontalLineTo(8.822f)
            verticalLineToRelative(0.253f)
            horizontalLineTo(8.755f)
            verticalLineTo(12.411f)
            close()

            moveTo(8.616f, 12.481f)
            curveToRelative(0.013f, 0.024f, 0.02f, 0.049f, 0.02f, 0.075f)
            curveToRelative(0f, 0.024f, -0.006f, 0.047f, -0.018f, 0.069f)
            curveToRelative(-0.012f, 0.022f, -0.03f, 0.039f, -0.054f, 0.052f)
            curveToRelative(0.031f, 0.01f, 0.055f, 0.027f, 0.071f, 0.052f)
            curveToRelative(0.017f, 0.024f, 0.025f, 0.053f, 0.025f, 0.086f)
            curveToRelative(0f, 0.027f, -0.005f, 0.051f, -0.015f, 0.074f)
            curveToRelative(-0.01f, 0.023f, -0.023f, 0.041f, -0.037f, 0.053f)
            curveToRelative(-0.015f, 0.012f, -0.033f, 0.022f, -0.056f, 0.028f)
            curveToRelative(-0.022f, 0.006f, -0.05f, 0.01f, -0.082f, 0.01f)
            horizontalLineTo(8.276f)
            verticalLineToRelative(0f)
            verticalLineToRelative(-0.569f)
            horizontalLineToRelative(0.191f)
            curveToRelative(0.039f, 0f, 0.07f, 0.006f, 0.094f, 0.017f)
            curveToRelative(0.023f, 0.011f, 0.042f, 0.028f, 0.055f, 0.052f)
            close()

            moveTo(8.498f, 11.477f)
            horizontalLineToRelative(0.055f)
            verticalLineToRelative(0.149f)
            horizontalLineTo(8.498f)
            verticalLineTo(11.477f)
            close()

            moveTo(7.866f, 11.386f)
            horizontalLineToRelative(0.628f)
            verticalLineToRelative(0.356f)
            horizontalLineTo(7.866f)
            verticalLineTo(11.386f)
            close()

            moveTo(7.604f, 12.37f)
            horizontalLineToRelative(0.076f)
            verticalLineToRelative(0.219f)
            curveToRelative(0.035f, -0.04f, 0.08f, -0.061f, 0.134f, -0.061f)
            curveToRelative(0.033f, 0f, 0.062f, 0.006f, 0.086f, 0.019f)
            curveToRelative(0.024f, 0.013f, 0.042f, 0.031f, 0.052f, 0.054f)
            curveToRelative(0.01f, 0.023f, 0.016f, 0.056f, 0.016f, 0.099f)
            verticalLineToRelative(0.28f)
            horizontalLineTo(7.892f)
            verticalLineTo(12.7f)
            curveToRelative(0f, -0.037f, -0.008f, -0.065f, -0.025f, -0.082f)
            curveToRelative(-0.016f, -0.017f, -0.04f, -0.026f, -0.07f, -0.026f)
            curveToRelative(-0.022f, 0f, -0.044f, 0.006f, -0.063f, 0.017f)
            curveToRelative(-0.02f, 0.012f, -0.034f, 0.027f, -0.042f, 0.047f)
            curveToRelative(-0.008f, 0.02f, -0.013f, 0.047f, -0.013f, 0.081f)
            verticalLineToRelative(0.242f)
            horizontalLineTo(7.604f)
            verticalLineTo(12.37f)
            close()

            moveTo(7.486f, 12.359f)
            horizontalLineToRelative(0.061f)
            lineToRelative(-0.179f, 0.631f)
            horizontalLineTo(7.307f)
            lineToRelative(0.179f, -0.631f)
            close()

            moveTo(7.117f, 12.877f)
            curveToRelative(0.026f, -0.027f, 0.038f, -0.079f, 0.038f, -0.155f)
            lineToRelative(0f, -0.352f)
            horizontalLineToRelative(0.082f)
            verticalLineToRelative(0.353f)
            curveToRelative(0f, 0.061f, -0.007f, 0.11f, -0.021f, 0.146f)
            curveToRelative(-0.014f, 0.036f, -0.039f, 0.065f, -0.076f, 0.088f)
            curveToRelative(-0.037f, 0.023f, -0.085f, 0.034f, -0.144f, 0.034f)
            curveToRelative(-0.058f, 0f, -0.105f, -0.01f, -0.142f, -0.03f)
            curveToRelative(-0.037f, -0.02f, -0.063f, -0.048f, -0.079f, -0.086f)
            curveToRelative(-0.016f, -0.037f, -0.024f, -0.088f, -0.024f, -0.153f)
            verticalLineTo(12.37f)
            horizontalLineToRelative(0.082f)
            verticalLineToRelative(0.352f)
            curveToRelative(0f, 0.053f, 0.005f, 0.092f, 0.015f, 0.117f)
            curveToRelative(0.01f, 0.025f, 0.027f, 0.044f, 0.051f, 0.058f)
            curveToRelative(0.024f, 0.014f, 0.054f, 0.02f, 0.089f, 0.02f)
            curveToRelative(0.037f, 0f, 0.08f, -0.014f, 0.105f, -0.041f)
            close()

            moveTo(6.771f, 14.072f)
            curveToRelative(0.012f, 0.032f, 0.032f, 0.056f, 0.062f, 0.073f)
            curveToRelative(0.029f, 0.017f, 0.065f, 0.026f, 0.107f, 0.026f)
            curveToRelative(0.072f, 0f, 0.123f, -0.017f, 0.154f, -0.051f)
            curveToRelative(0.031f, -0.034f, 0.046f, -0.099f, 0.046f, -0.195f)
            verticalLineToRelative(-0.442f)
            horizontalLineToRelative(0.098f)
            verticalLineToRelative(0.443f)
            curveToRelative(0f, 0.077f, -0.008f, 0.138f, -0.025f, 0.183f)
            curveToRelative(-0.017f, 0.045f, -0.047f, 0.082f, -0.091f, 0.111f)
            curveToRelative(-0.044f, 0.029f, -0.101f, 0.043f, -0.173f, 0.043f)
            curveToRelative(-0.069f, 0f, -0.126f, -0.012f, -0.17f, -0.037f)
            curveToRelative(-0.044f, -0.025f, -0.076f, -0.061f, -0.094f, -0.108f)
            curveToRelative(-0.019f, -0.047f, -0.028f, -0.111f, -0.028f, -0.192f)
            verticalLineToRelative(-0.443f)
            horizontalLineToRelative(0.098f)
            verticalLineToRelative(0.442f)
            curveToRelative(0f, 0.054f, 0.006f, 0.104f, 0.018f, 0.135f)
            close()

            moveTo(6.632f, 11.533f)
            verticalLineToRelative(-0.171f)
            verticalLineToRelative(-0.033f)
            curveToRelative(0f, -0.011f, 0.009f, -0.02f, 0.02f, -0.02f)
            horizontalLineToRelative(0.039f)
            curveToRelative(0.011f, 0f, 0.019f, 0.009f, 0.019f, 0.02f)
            verticalLineToRelative(0.033f)
            verticalLineToRelative(0.171f)
            lineToRelative(0.214f, 0.109f)
            lineToRelative(-0.032f, 0.06f)
            lineTo(6.71f, 11.61f)
            verticalLineToRelative(0.097f)
            verticalLineToRelative(0.05f)
            horizontalLineToRelative(0.046f)
            verticalLineToRelative(0.077f)
            horizontalLineTo(6.71f)
            verticalLineToRelative(0f)
            horizontalLineTo(6.632f)
            verticalLineToRelative(0f)
            horizontalLineTo(6.584f)
            verticalLineToRelative(-0.077f)
            horizontalLineToRelative(0.049f)
            verticalLineToRelative(-0.05f)
            verticalLineToRelative(-0.098f)
            lineTo(6.45f, 11.702f)
            lineToRelative(-0.032f, -0.06f)
            lineToRelative(0.214f, -0.109f)
            close()

            moveTo(6.42f, 12.579f)
            curveToRelative(-0.03f, -0.032f, -0.07f, -0.048f, -0.119f, -0.048f)
            curveToRelative(-0.03f, 0f, -0.058f, 0.007f, -0.082f, 0.021f)
            curveToRelative(-0.024f, 0.014f, -0.044f, 0.032f, -0.058f, 0.055f)
            lineToRelative(-0.088f, -0.012f)
            lineToRelative(0.074f, -0.403f)
            horizontalLineToRelative(0.381f)
            verticalLineToRelative(0.092f)
            horizontalLineTo(6.223f)
            lineToRelative(-0.041f, 0.211f)
            curveToRelative(0.046f, -0.033f, 0.094f, -0.049f, 0.145f, -0.049f)
            curveToRelative(0.067f, 0f, 0.123f, 0.024f, 0.169f, 0.071f)
            reflectiveCurveToRelative(0.069f, 0.108f, 0.069f, 0.183f)
            curveToRelative(0f, 0.071f, -0.02f, 0.132f, -0.061f, 0.184f)
            curveToRelative(-0.049f, 0.063f, -0.116f, 0.095f, -0.201f, 0.095f)
            curveToRelative(-0.07f, 0f, -0.127f, -0.02f, -0.171f, -0.06f)
            curveToRelative(-0.044f, -0.04f, -0.069f, -0.093f, -0.075f, -0.159f)
            lineToRelative(0.099f, -0.009f)
            curveToRelative(0.007f, 0.049f, 0.024f, 0.086f, 0.051f, 0.111f)
            curveToRelative(0.027f, 0.025f, 0.06f, 0.037f, 0.097f, 0.037f)
            curveToRelative(0.045f, 0f, 0.084f, -0.017f, 0.115f, -0.052f)
            curveToRelative(0.031f, -0.035f, 0.047f, -0.081f, 0.047f, -0.139f)
            curveToRelative(0f, -0.058f, -0.015f, -0.101f, -0.045f, -0.133f)
            close()

            moveTo(6.138f, 14.04f)
            curveToRelative(0.007f, 0.048f, 0.023f, 0.084f, 0.049f, 0.109f)
            curveToRelative(0.026f, 0.024f, 0.057f, 0.036f, 0.093f, 0.036f)
            curveToRelative(0.044f, 0f, 0.081f, -0.017f, 0.111f, -0.051f)
            curveToRelative(0.03f, -0.034f, 0.045f, -0.079f, 0.045f, -0.136f)
            curveToRelative(0f, -0.054f, -0.015f, -0.096f, -0.044f, -0.127f)
            curveToRelative(-0.029f, -0.031f, -0.067f, -0.047f, -0.114f, -0.047f)
            curveToRelative(-0.029f, 0f, -0.056f, 0.007f, -0.079f, 0.021f)
            curveToRelative(-0.023f, 0.014f, -0.042f, 0.032f, -0.055f, 0.054f)
            lineToRelative(-0.085f, -0.011f)
            lineToRelative(0.072f, -0.394f)
            horizontalLineToRelative(0.368f)
            verticalLineToRelative(0.09f)
            horizontalLineTo(6.203f)
            lineToRelative(-0.04f, 0.206f)
            curveToRelative(0.044f, -0.032f, 0.091f, -0.048f, 0.14f, -0.048f)
            curveToRelative(0.065f, 0f, 0.119f, 0.023f, 0.163f, 0.069f)
            curveToRelative(0.044f, 0.046f, 0.066f, 0.106f, 0.066f, 0.179f)
            curveToRelative(0f, 0.069f, -0.019f, 0.129f, -0.058f, 0.18f)
            curveToRelative(-0.048f, 0.062f, -0.112f, 0.093f, -0.194f, 0.093f)
            curveToRelative(-0.067f, 0f, -0.122f, -0.02f, -0.165f, -0.059f)
            curveToRelative(-0.042f, -0.039f, -0.067f, -0.091f, -0.073f, -0.155f)
            lineToRelative(0.094f, -0.013f)
            close()

            moveTo(5.822f, 12.627f)
            curveToRelative(-0.028f, -0.029f, -0.064f, -0.043f, -0.108f, -0.043f)
            curveToRelative(-0.018f, 0f, -0.04f, 0.004f, -0.066f, 0.011f)
            lineToRelative(0.01f, -0.084f)
            curveToRelative(0.006f, 0.001f, 0.011f, 0.001f, 0.015f, 0.001f)
            curveToRelative(0.04f, 0f, 0.076f, -0.011f, 0.108f, -0.032f)
            curveToRelative(0.032f, -0.021f, 0.048f, -0.054f, 0.048f, -0.099f)
            curveToRelative(0f, -0.035f, -0.012f, -0.065f, -0.035f, -0.088f)
            curveToRelative(-0.023f, -0.023f, -0.054f, -0.035f, -0.091f, -0.035f)
            curveToRelative(-0.037f, 0f, -0.067f, 0.012f, -0.091f, 0.035f)
            curveToRelative(-0.024f, 0.024f, -0.04f, 0.059f, -0.047f, 0.106f)
            lineToRelative(-0.094f, -0.017f)
            curveToRelative(0.011f, -0.065f, 0.038f, -0.115f, 0.078f, -0.15f)
            curveToRelative(0.041f, -0.035f, 0.092f, -0.053f, 0.152f, -0.053f)
            curveToRelative(0.042f, 0f, 0.08f, 0.009f, 0.116f, 0.027f)
            curveToRelative(0.035f, 0.018f, 0.062f, 0.043f, 0.081f, 0.075f)
            curveToRelative(0.019f, 0.032f, 0.028f, 0.065f, 0.028f, 0.101f)
            curveToRelative(0f, 0.034f, -0.009f, 0.065f, -0.027f, 0.092f)
            curveToRelative(-0.018f, 0.028f, -0.044f, 0.05f, -0.079f, 0.066f)
            curveToRelative(0.045f, 0.011f, 0.081f, 0.033f, 0.106f, 0.066f)
            curveToRelative(0.025f, 0.034f, 0.038f, 0.076f, 0.038f, 0.126f)
            curveToRelative(0f, 0.069f, -0.024f, 0.126f, -0.073f, 0.174f)
            curveToRelative(-0.049f, 0.048f, -0.111f, 0.072f, -0.185f, 0.072f)
            curveToRelative(-0.067f, 0f, -0.123f, -0.02f, -0.168f, -0.062f)
            curveToRelative(-0.045f, -0.041f, -0.07f, -0.094f, -0.076f, -0.159f)
            lineToRelative(0.094f, -0.013f)
            curveToRelative(0.011f, 0.055f, 0.029f, 0.094f, 0.055f, 0.118f)
            curveToRelative(0.026f, 0.024f, 0.058f, 0.036f, 0.095f, 0.036f)
            curveToRelative(0.044f, 0f, 0.082f, -0.016f, 0.112f, -0.047f)
            curveToRelative(0.03f, -0.031f, 0.046f, -0.07f, 0.046f, -0.116f)
            curveToRelative(0f, -0.046f, -0.014f, -0.083f, -0.042f, -0.112f)
            close()

            moveTo(5.816f, 13.918f)
            curveToRelative(-0.027f, -0.028f, -0.062f, -0.042f, -0.104f, -0.042f)
            curveToRelative(-0.017f, 0f, -0.038f, 0.003f, -0.064f, 0.01f)
            lineToRelative(0.01f, -0.083f)
            curveToRelative(0.006f, 0.001f, 0.011f, 0.001f, 0.015f, 0.001f)
            curveToRelative(0.039f, 0f, 0.074f, -0.01f, 0.105f, -0.031f)
            curveToRelative(0.031f, -0.021f, 0.046f, -0.053f, 0.046f, -0.097f)
            curveToRelative(0f, -0.034f, -0.011f, -0.063f, -0.034f, -0.086f)
            curveToRelative(-0.023f, -0.023f, -0.052f, -0.034f, -0.087f, -0.034f)
            curveToRelative(-0.035f, 0f, -0.065f, 0.012f, -0.088f, 0.035f)
            curveToRelative(-0.024f, 0.023f, -0.039f, 0.058f, -0.045f, 0.104f)
            lineToRelative(-0.091f, -0.017f)
            curveToRelative(0.011f, -0.063f, 0.036f, -0.112f, 0.076f, -0.147f)
            curveToRelative(0.04f, -0.035f, 0.089f, -0.052f, 0.148f, -0.052f)
            curveToRelative(0.04f, 0f, 0.077f, 0.009f, 0.112f, 0.027f)
            curveToRelative(0.034f, 0.018f, 0.06f, 0.042f, 0.078f, 0.073f)
            curveToRelative(0.018f, 0.031f, 0.027f, 0.064f, 0.027f, 0.099f)
            curveToRelative(0f, 0.033f, -0.009f, 0.063f, -0.026f, 0.09f)
            curveToRelative(-0.017f, 0.027f, -0.043f, 0.049f, -0.076f, 0.065f)
            curveToRelative(0.044f, 0.01f, 0.078f, 0.032f, 0.102f, 0.065f)
            curveToRelative(0.024f, 0.033f, 0.036f, 0.074f, 0.036f, 0.124f)
            curveToRelative(0f, 0.067f, -0.023f, 0.124f, -0.07f, 0.17f)
            curveToRelative(-0.047f, 0.047f, -0.107f, 0.07f, -0.179f, 0.07f)
            curveToRelative(-0.065f, 0f, -0.119f, -0.02f, -0.162f, -0.06f)
            curveToRelative(-0.043f, -0.04f, -0.067f, -0.092f, -0.073f, -0.156f)
            lineToRelative(0.091f, -0.013f)
            curveToRelative(0.01f, 0.053f, 0.028f, 0.092f, 0.053f, 0.115f)
            curveToRelative(0.025f, 0.023f, 0.056f, 0.035f, 0.092f, 0.035f)
            curveToRelative(0.043f, 0f, 0.079f, -0.015f, 0.108f, -0.046f)
            curveToRelative(0.029f, -0.031f, 0.044f, -0.069f, 0.044f, -0.114f)
            curveToRelative(0f, -0.046f, -0.013f, -0.082f, -0.04f, -0.11f)
            close()

            moveTo(5.217f, 12.857f)
            horizontalLineToRelative(0.107f)
            verticalLineToRelative(0.11f)
            horizontalLineTo(5.217f)
            verticalLineTo(12.857f)
            close()

            moveTo(5.335f, 14.142f)
            verticalLineToRelative(0.107f)
            horizontalLineTo(5.232f)
            verticalLineToRelative(-0.107f)
            horizontalLineTo(5.335f)
            close()

            moveTo(5.019f, 11.435f)
            curveToRelative(0.036f, -0.016f, 0.067f, -0.035f, 0.093f, -0.058f)
            curveToRelative(0.027f, -0.023f, 0.046f, -0.045f, 0.057f, -0.066f)
            horizontalLineToRelative(0.041f)
            curveToRelative(0f, 0f, 0f, 0.492f, 0f, 0.492f)
            lineToRelative(-0.064f, 0f)
            verticalLineTo(11.42f)
            curveToRelative(-0.015f, 0.014f, -0.035f, 0.028f, -0.06f, 0.042f)
            curveToRelative(-0.025f, 0.014f, -0.047f, 0.024f, -0.067f, 0.031f)
            verticalLineTo(11.435f)
            close()

            moveTo(4.951f, 13.596f)
            curveToRelative(-0.026f, -0.026f, -0.059f, -0.038f, -0.101f, -0.038f)
            curveToRelative(-0.044f, 0f, -0.079f, 0.014f, -0.105f, 0.041f)
            curveToRelative(-0.026f, 0.027f, -0.04f, 0.065f, -0.04f, 0.113f)
            lineToRelative(-0.093f, -0.01f)
            curveToRelative(0.006f, -0.072f, 0.03f, -0.127f, 0.072f, -0.165f)
            curveToRelative(0.042f, -0.038f, 0.098f, -0.057f, 0.168f, -0.057f)
            curveToRelative(0.071f, 0f, 0.127f, 0.02f, 0.169f, 0.061f)
            curveToRelative(0.041f, 0.041f, 0.062f, 0.091f, 0.062f, 0.152f)
            curveToRelative(0f, 0.031f, -0.006f, 0.061f, -0.018f, 0.09f)
            curveToRelative(-0.012f, 0.03f, -0.032f, 0.061f, -0.06f, 0.094f)
            curveToRelative(-0.028f, 0.033f, -0.075f, 0.078f, -0.14f, 0.135f)
            curveToRelative(-0.054f, 0.047f, -0.089f, 0.08f, -0.105f, 0.096f)
            curveToRelative(-0.015f, 0.017f, -0.028f, 0.034f, -0.038f, 0.051f)
            horizontalLineToRelative(0.363f)
            verticalLineToRelative(0.09f)
            horizontalLineTo(4.595f)
            curveToRelative(-0.001f, -0.023f, 0.003f, -0.044f, 0.01f, -0.065f)
            curveToRelative(0.012f, -0.034f, 0.032f, -0.068f, 0.06f, -0.102f)
            curveToRelative(0.027f, -0.034f, 0.067f, -0.072f, 0.119f, -0.116f)
            curveToRelative(0.08f, -0.068f, 0.135f, -0.122f, 0.163f, -0.162f)
            curveToRelative(0.028f, -0.04f, 0.042f, -0.078f, 0.042f, -0.113f)
            curveToRelative(0f, -0.038f, -0.013f, -0.069f, -0.038f, -0.095f)
            close()

            moveTo(4.923f, 12.966f)
            horizontalLineTo(4.828f)
            verticalLineToRelative(-0.613f)
            curveToRelative(-0.023f, 0.022f, -0.052f, 0.044f, -0.089f, 0.066f)
            curveToRelative(-0.037f, 0.022f, -0.07f, 0.039f, -0.099f, 0.05f)
            verticalLineToRelative(-0.093f)
            curveToRelative(0.053f, -0.025f, 0.099f, -0.056f, 0.138f, -0.092f)
            curveToRelative(0.04f, -0.036f, 0.067f, -0.071f, 0.084f, -0.105f)
            horizontalLineToRelative(0.061f)
            verticalLineTo(12.966f)
            close()

            moveTo(4.567f, 11.764f)
            curveToRelative(0.009f, -0.022f, 0.023f, -0.044f, 0.042f, -0.065f)
            curveToRelative(0.019f, -0.022f, 0.047f, -0.046f, 0.083f, -0.074f)
            curveToRelative(0.056f, -0.044f, 0.094f, -0.079f, 0.114f, -0.104f)
            curveToRelative(0.02f, -0.025f, 0.03f, -0.05f, 0.03f, -0.072f)
            curveToRelative(0f, -0.024f, -0.009f, -0.044f, -0.027f, -0.06f)
            curveToRelative(-0.018f, -0.016f, -0.041f, -0.024f, -0.07f, -0.024f)
            curveToRelative(-0.031f, 0f, -0.055f, 0.009f, -0.074f, 0.026f)
            curveToRelative(-0.018f, 0.017f, -0.028f, 0.041f, -0.028f, 0.072f)
            lineToRelative(-0.065f, -0.006f)
            curveToRelative(0.004f, -0.046f, 0.021f, -0.081f, 0.051f, -0.106f)
            curveToRelative(0.029f, -0.024f, 0.069f, -0.036f, 0.118f, -0.037f)
            curveToRelative(0.05f, 0f, 0.089f, 0.013f, 0.118f, 0.039f)
            curveToRelative(0.029f, 0.026f, 0.044f, 0.058f, 0.044f, 0.097f)
            curveToRelative(0f, 0.02f, -0.004f, 0.039f, -0.013f, 0.058f)
            curveToRelative(-0.009f, 0.019f, -0.023f, 0.039f, -0.042f, 0.06f)
            curveToRelative(-0.02f, 0.021f, -0.052f, 0.05f, -0.098f, 0.087f)
            curveToRelative(-0.038f, 0.03f, -0.063f, 0.051f, -0.074f, 0.062f)
            curveToRelative(-0.011f, 0.011f, -0.02f, 0.022f, -0.027f, 0.033f)
            curveToRelative(0f, 0f, 0.254f, -0.001f, 0.254f, -0.001f)
            verticalLineToRelative(0.058f)
            lineToRelative(-0.252f, 0.001f)
            curveToRelative(-0.001f, -0.014f, 0.001f, -0.028f, 0.007f, -0.041f)
            close()

            moveTo(4.402f, 11.451f)
            lineToRelative(0.072f, 0f)
            verticalLineToRelative(0.069f)
            lineToRelative(-0.072f, 0f)
            verticalLineTo(11.451f)
            close()

            moveTo(4.402f, 11.737f)
            lineToRelative(0.072f, 0f)
            verticalLineToRelative(0.068f)
            lineToRelative(-0.072f, 0f)
            verticalLineTo(11.737f)
            close()

            moveTo(3.982f, 11.426f)
            curveToRelative(0.013f, -0.035f, 0.031f, -0.063f, 0.056f, -0.082f)
            curveToRelative(0.025f, -0.019f, 0.056f, -0.029f, 0.094f, -0.029f)
            curveToRelative(0.028f, 0f, 0.052f, 0.005f, 0.073f, 0.016f)
            curveToRelative(0.021f, 0.01f, 0.038f, 0.026f, 0.052f, 0.045f)
            curveToRelative(0.014f, 0.02f, 0.024f, 0.044f, 0.032f, 0.073f)
            curveToRelative(0.008f, 0.029f, 0.012f, 0.067f, 0.012f, 0.116f)
            curveToRelative(0f, 0.058f, -0.006f, 0.104f, -0.019f, 0.139f)
            curveToRelative(-0.013f, 0.035f, -0.031f, 0.063f, -0.056f, 0.082f)
            curveToRelative(-0.025f, 0.019f, -0.056f, 0.029f, -0.094f, 0.029f)
            curveToRelative(-0.05f, 0f, -0.089f, -0.017f, -0.118f, -0.05f)
            curveToRelative(-0.034f, -0.041f, -0.051f, -0.107f, -0.051f, -0.199f)
            curveToRelative(0f, -0.061f, 0.007f, -0.107f, 0.019f, -0.143f)
            close()

            moveTo(3.827f, 12.369f)
            verticalLineToRelative(-0.054f)
            verticalLineToRelative(-0.054f)
            horizontalLineToRelative(0.267f)
            horizontalLineToRelative(0.059f)
            horizontalLineToRelative(0.063f)
            verticalLineToRelative(0.241f)
            horizontalLineToRelative(0.14f)
            verticalLineToRelative(0.122f)
            horizontalLineToRelative(0f)
            verticalLineToRelative(0.305f)
            horizontalLineTo(4.234f)
            verticalLineToRelative(-0.305f)
            horizontalLineTo(4.092f)
            verticalLineToRelative(-0.122f)
            horizontalLineToRelative(0.002f)
            verticalLineToRelative(-0.133f)
            horizontalLineTo(3.946f)
            verticalLineToRelative(0.32f)
            horizontalLineTo(3.833f)
            verticalLineToRelative(0.234f)
            horizontalLineTo(3.711f)
            verticalLineToRelative(-0.343f)
            horizontalLineToRelative(0.116f)
            verticalLineTo(12.369f)
            close()

            moveTo(3.705f, 13.665f)
            verticalLineToRelative(-0.107f)
            horizontalLineTo(4.22f)
            verticalLineToRelative(0.107f)
            horizontalLineTo(3.705f)
            close()

            moveTo(3.811f, 13.665f)
            verticalLineToRelative(0.544f)
            horizontalLineTo(3.705f)
            verticalLineToRelative(-0.544f)
            horizontalLineTo(3.811f)
            close()

            moveTo(4.11f, 13.665f)
            horizontalLineToRelative(0.11f)
            verticalLineToRelative(0.437f)
            horizontalLineTo(4.33f)
            verticalLineToRelative(0.107f)
            horizontalLineTo(4.22f)
            verticalLineToRelative(0f)
            horizontalLineTo(4.11f)
            verticalLineTo(13.665f)
            close()

            moveTo(3.609f, 11.498f)
            verticalLineTo(11.44f)
            curveToRelative(0.036f, -0.016f, 0.067f, -0.035f, 0.094f, -0.058f)
            curveToRelative(0.027f, -0.023f, 0.045f, -0.044f, 0.056f, -0.066f)
            horizontalLineToRelative(0.041f)
            verticalLineToRelative(0.492f)
            lineToRelative(-0.064f, 0f)
            verticalLineToRelative(-0.383f)
            curveToRelative(-0.015f, 0.014f, -0.036f, 0.028f, -0.06f, 0.042f)
            curveToRelative(-0.025f, 0.014f, -0.047f, 0.024f, -0.067f, 0.031f)
            close()

            moveTo(13.282f, 14.696f)
            curveToRelative(0f, 0.031f, -0.025f, 0.056f, -0.056f, 0.056f)
            horizontalLineTo(3.74f)
            curveToRelative(-0.031f, 0f, -0.056f, -0.025f, -0.056f, -0.056f)
            verticalLineToRelative(-0.271f)
            curveToRelative(0f, -0.031f, 0.025f, -0.056f, 0.056f, -0.056f)
            horizontalLineToRelative(9.486f)
            curveToRelative(0.031f, 0f, 0.056f, 0.025f, 0.056f, 0.056f)
            verticalLineTo(14.696f)
            close()

            moveTo(12.967f, 11.738f)
            verticalLineToRelative(-0.376f)
            lineToRelative(0.331f, 0.188f)
            lineToRelative(-0.331f, 0.188f)
            close()

            // Polygon (points: 12.924,13.978 12.924,13.632 12.676,13.978)
            moveTo(12.924f, 13.978f)
            lineTo(12.924f, 13.632f)
            lineTo(12.676f, 13.978f)
            lineTo(12.924f, 13.978f)
            close()

            // Another dot
            moveTo(9.323f, 11.77f)
            curveToRelative(0.027f, 0f, 0.049f, -0.014f, 0.068f, -0.042f)
            curveToRelative(0.018f, -0.028f, 0.027f, -0.084f, 0.027f, -0.168f)
            curveToRelative(0f, -0.084f, -0.009f, -0.14f, -0.027f, -0.168f)
            curveToRelative(-0.019f, -0.028f, -0.041f, -0.042f, -0.069f, -0.042f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.012f, -0.064f, 0.037f)
            curveToRelative(-0.02f, 0.031f, -0.03f, 0.089f, -0.03f, 0.173f)
            reflectiveCurveToRelative(0.009f, 0.14f, 0.027f, 0.168f)
            curveToRelative(0.018f, 0.028f, 0.04f, 0.042f, 0.067f, 0.042f)
            close()

            // Another dot
            moveTo(12.609f, 11.77f)
            curveToRelative(0.027f, 0f, 0.049f, -0.014f, 0.067f, -0.042f)
            curveToRelative(0.018f, -0.028f, 0.027f, -0.084f, 0.027f, -0.168f)
            curveToRelative(0f, -0.084f, -0.009f, -0.14f, -0.027f, -0.168f)
            curveToRelative(-0.018f, -0.028f, -0.041f, -0.042f, -0.068f, -0.042f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.012f, -0.064f, 0.037f)
            curveToRelative(-0.02f, 0.031f, -0.03f, 0.089f, -0.03f, 0.173f)
            reflectiveCurveToRelative(0.009f, 0.14f, 0.027f, 0.168f)
            curveToRelative(0.018f, 0.028f, 0.041f, 0.042f, 0.068f, 0.042f)
            close()

            // Another shape
            moveTo(8.461f, 12.717f)
            horizontalLineTo(8.343f)
            lineToRelative(0f, 0.196f)
            horizontalLineTo(8.47f)
            curveToRelative(0.022f, 0f, 0.037f, -0.001f, 0.046f, -0.003f)
            curveToRelative(0.016f, -0.003f, 0.029f, -0.008f, 0.039f, -0.016f)
            curveToRelative(0.01f, -0.007f, 0.019f, -0.018f, 0.026f, -0.032f)
            curveToRelative(0.007f, -0.014f, 0.01f, -0.03f, 0.01f, -0.048f)
            curveToRelative(0f, -0.021f, -0.005f, -0.04f, -0.015f, -0.055f)
            curveToRelative(-0.01f, -0.016f, -0.023f, -0.027f, -0.04f, -0.033f)
            curveToRelative(-0.017f, -0.006f, -0.042f, -0.009f, -0.075f, -0.009f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginInsightPreview() {
    Icon(
        imageVector = IcPluginInsight,
        contentDescription = "Insight Plugin Icon",
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
<g id="plugin_insight">
	<g>
		<path d="M13.005,9.141V8.574l-0.611-0.541h-0.002h-1.608c-0.117,0-0.212,0.095-0.212,0.212v0.009H9.777v1.208h0.794
			c0,0.125,0.076,0.22,0.212,0.22l1.608,0V9.681h0.002v0.002L13.005,9.141z"/>
		<path d="M17.07,11.157h-1.185c-0.627,0-1.135,0.509-1.135,1.135v2.169c0,0.627,0.509,1.135,1.135,1.135h1.185
			c0.626,0,1.135-0.509,1.135-1.135v-2.169C18.206,11.666,17.697,11.157,17.07,11.157z M16.482,11.668l0.35,0.431h-0.699
			L16.482,11.668z M16.513,15.212l-0.35-0.431h0.699L16.513,15.212z M17.34,13.717c0,0.213-0.174,0.386-0.387,0.386h-0.946
			c-0.213,0-0.387-0.173-0.387-0.386v-0.521c0-0.213,0.174-0.387,0.387-0.387h0.946c0.213,0,0.387,0.174,0.387,0.387V13.717z"/>
		<path d="M16.007,13.061c-0.074,0-0.134,0.06-0.134,0.134v0.521c0,0.074,0.06,0.134,0.134,0.134h0.946
			c0.074,0,0.134-0.06,0.134-0.134v-0.521c0-0.074-0.06-0.134-0.134-0.134H16.007z M16.485,13.7c-0.148,0-0.267-0.12-0.267-0.267
			c0-0.148,0.12-0.267,0.267-0.267s0.267,0.12,0.267,0.267C16.752,13.58,16.632,13.7,16.485,13.7z"/>
		<path d="M22.733,8.767C22.577,7.2,21.821,6.228,20.664,5.77l-0.007,12.481l-0.254,0.069V5.705l0.229,0.054
			c-0.376-0.145-0.793-0.238-1.247-0.278C14.527,5.27,9.7,5.082,4.646,5.604c-1.898,0.089-3.338,1.26-3.338,3.153
			c-0.006,1.047-0.095,2.119-0.097,3.077c-0.03,1.131-0.002,2.286,0.107,3.474c-0.041,1.908,1.374,3.162,3.327,3.213
			c4.752,0.124,9.652,0.367,14.678,0.072c1.772-0.104,3.208-1.21,3.367-3.243C22.778,13.15,22.864,10.939,22.733,8.767z
			 M8.005,15.926c-0.066,0-0.121-0.039-0.148-0.095c-0.027,0.053-0.081,0.09-0.145,0.09c-0.058,0-0.106-0.032-0.136-0.077
			c-0.029,0.045-0.078,0.077-0.136,0.077c-0.091,0-0.164-0.074-0.164-0.164c0-0.09,0.074-0.164,0.164-0.164
			c0.058,0,0.106,0.032,0.135,0.077c0.029-0.045,0.078-0.077,0.136-0.077c0.066,0,0.121,0.039,0.148,0.095
			c0.027-0.053,0.081-0.091,0.145-0.091c0.091,0,0.164,0.074,0.164,0.164C8.169,15.852,8.095,15.926,8.005,15.926z M8.229,15.776
			c0-0.137,0.111-0.248,0.248-0.248c0.137,0,0.248,0.111,0.248,0.248c0,0.137-0.111,0.248-0.248,0.248
			C8.34,16.024,8.229,15.913,8.229,15.776z M8.904,16.175l-0.037,0.037c-0.01,0.01-0.027,0.01-0.037,0l-0.182-0.184
			c-0.01-0.01-0.01-0.027,0-0.037l0.037-0.037c0.01-0.01,0.027-0.01,0.037,0l0.182,0.184C8.914,16.148,8.914,16.165,8.904,16.175z
			 M9.514,15.941c-0.064,0-0.118-0.038-0.145-0.092c-0.028,0.051-0.08,0.088-0.143,0.088c-0.062,0-0.114-0.036-0.142-0.086
			c-0.027,0.053-0.081,0.09-0.144,0.09c-0.091,0-0.164-0.074-0.164-0.164c0-0.09,0.074-0.164,0.164-0.164
			c0.062,0,0.114,0.036,0.142,0.086c0.027-0.053,0.081-0.09,0.144-0.09c0.064,0,0.118,0.038,0.145,0.091
			c0.028-0.051,0.08-0.088,0.143-0.088c0.091,0,0.164,0.074,0.164,0.164C9.679,15.867,9.605,15.941,9.514,15.941z M13.522,14.753
			c0,0.121-0.098,0.219-0.219,0.219H3.69c-0.121,0-0.218-0.098-0.218-0.219v-3.399c0-0.121,0.098-0.219,0.218-0.219h9.613
			c0.121,0,0.219,0.098,0.219,0.219V14.753z M9.949,9.684c-0.076,0-0.138-0.062-0.138-0.138V8.171c0-0.076,0.062-0.138,0.138-0.138
			h7.366c0.076,0,0.137,0.062,0.137,0.138h0v1.375c0,0.076-0.062,0.138-0.137,0.138H9.949z M18.295,14.509
			c0,0.655-0.531,1.186-1.186,1.186h-1.263c-0.655,0-1.186-0.531-1.186-1.186v-2.25c0-0.655,0.531-1.186,1.186-1.186h1.263
			c0.655,0,1.186,0.531,1.186,1.186V14.509z"/>
		<g>
			<path opacity="0.8" d="M9.797,12.654c0.018-0.018,0.027-0.044,0.027-0.077c0-0.024-0.005-0.044-0.016-0.061
				c-0.011-0.017-0.025-0.028-0.042-0.034c-0.011-0.003-0.032-0.005-0.063-0.005h-0.13v0.203h0.132
				C9.748,12.681,9.779,12.673,9.797,12.654z"/>
			<path opacity="0.8" d="M4.132,11.766c0.029,0,0.054-0.014,0.074-0.04c0.02-0.027,0.03-0.08,0.03-0.16
				c0-0.081-0.01-0.134-0.03-0.161c-0.02-0.026-0.045-0.04-0.074-0.04c-0.029,0-0.053,0.012-0.07,0.035
				c-0.022,0.03-0.033,0.085-0.033,0.165c0,0.08,0.01,0.134,0.03,0.16S4.103,11.766,4.132,11.766z"/>
			<path opacity="0.8" d="M8.343,12.478v0.172h0.11c0.03,0,0.051-0.002,0.064-0.006c0.017-0.006,0.03-0.015,0.039-0.028
				c0.009-0.013,0.013-0.03,0.013-0.05c0-0.019-0.004-0.036-0.012-0.05c-0.008-0.014-0.02-0.024-0.035-0.03
				c-0.015-0.005-0.041-0.008-0.077-0.008H8.343z"/>
			<rect x="7.965" y="11.457" opacity="0.8" width="0.441" height="0.205"/>
			<path opacity="0.8" d="M10.359,12.932c0.031,0,0.057-0.013,0.077-0.04c0.021-0.026,0.031-0.067,0.031-0.121
				c0-0.051-0.01-0.09-0.031-0.116c-0.021-0.026-0.047-0.04-0.077-0.04c-0.031,0-0.057,0.013-0.078,0.039
				c-0.021,0.026-0.031,0.066-0.031,0.118c0,0.053,0.01,0.092,0.031,0.119C10.302,12.919,10.328,12.932,10.359,12.932z"/>
			<path opacity="0.8" d="M11.87,11.77c0.027,0,0.049-0.014,0.067-0.042c0.018-0.028,0.027-0.084,0.027-0.168
				c0-0.084-0.009-0.14-0.027-0.168c-0.018-0.028-0.041-0.042-0.068-0.042c-0.027,0-0.048,0.012-0.064,0.037
				c-0.02,0.031-0.03,0.089-0.03,0.173s0.009,0.14,0.027,0.168C11.821,11.756,11.843,11.77,11.87,11.77z"/>
			<path opacity="0.8" d="M12.256,14.185c0.045,0,0.082-0.021,0.113-0.063c0.03-0.042,0.046-0.126,0.046-0.252
				c0-0.126-0.015-0.211-0.046-0.252c-0.03-0.042-0.068-0.062-0.114-0.062c-0.045,0-0.08,0.018-0.107,0.055
				c-0.033,0.047-0.05,0.133-0.05,0.259c0,0.126,0.015,0.21,0.046,0.252C12.174,14.164,12.211,14.185,12.256,14.185z"/>
			<path opacity="0.8" d="M10.513,11.663c-0.01,0.005-0.018,0.012-0.023,0.02c-0.005,0.009-0.008,0.019-0.008,0.03
				c0,0.017,0.006,0.031,0.018,0.042c0.012,0.011,0.029,0.017,0.052,0.017c0.022,0,0.042-0.005,0.06-0.016
				c0.017-0.011,0.03-0.025,0.038-0.044c0.006-0.014,0.009-0.035,0.009-0.063v-0.023c-0.021,0.009-0.053,0.017-0.095,0.024
				C10.54,11.654,10.523,11.658,10.513,11.663z"/>
			<path opacity="0.8" d="M8.967,12.662c0.031,0,0.055-0.003,0.072-0.011c0.017-0.007,0.031-0.019,0.04-0.034
				c0.009-0.016,0.014-0.033,0.014-0.051c0-0.027-0.009-0.049-0.026-0.066c-0.017-0.017-0.045-0.026-0.083-0.026H8.822v0.188h0
				H8.967z"/>
			<path opacity="0.8" d="M13.211,11.228H3.78c-0.115,0-0.208,0.093-0.208,0.208v3.239c0,0.115,0.093,0.208,0.208,0.208h9.431
				c0.115,0,0.208-0.093,0.208-0.208v-3.239C13.419,11.321,13.326,11.228,13.211,11.228z M13.129,13.978v0.087h-0.107v0.184h-0.097
				v-0.184h-0.343v-0.087l0.361-0.498h0.079v0.498H13.129z M12.471,11.414c0.012-0.037,0.029-0.065,0.051-0.086
				c0.023-0.02,0.052-0.03,0.086-0.03c0.025,0,0.048,0.006,0.067,0.017c0.019,0.011,0.035,0.027,0.048,0.048
				c0.012,0.021,0.022,0.046,0.029,0.076c0.007,0.03,0.011,0.07,0.011,0.121c0,0.06-0.006,0.109-0.017,0.145
				c-0.011,0.037-0.029,0.066-0.051,0.086c-0.023,0.02-0.052,0.03-0.086,0.03c-0.046,0-0.082-0.018-0.108-0.053
				c-0.031-0.043-0.047-0.112-0.047-0.208C12.454,11.499,12.459,11.451,12.471,11.414z M12.084,11.769
				c0.008-0.023,0.021-0.046,0.038-0.068s0.043-0.048,0.076-0.077c0.052-0.046,0.087-0.082,0.105-0.109
				c0.018-0.027,0.027-0.052,0.027-0.076c0-0.025-0.008-0.046-0.025-0.063c-0.016-0.017-0.038-0.026-0.065-0.026
				c-0.028,0-0.051,0.009-0.067,0.027c-0.017,0.018-0.025,0.043-0.026,0.075l-0.06-0.007c0.004-0.048,0.02-0.085,0.047-0.11
				c0.027-0.025,0.063-0.038,0.108-0.038c0.046,0,0.082,0.014,0.108,0.041c0.027,0.027,0.04,0.061,0.04,0.101
				c0,0.02-0.004,0.041-0.012,0.06c-0.008,0.02-0.021,0.041-0.039,0.063c-0.018,0.022-0.048,0.052-0.09,0.09
				c-0.035,0.032-0.057,0.053-0.067,0.065c-0.01,0.011-0.018,0.023-0.025,0.034l0.233,0v0.061h-0.314
				C12.077,11.797,12.079,11.783,12.084,11.769z M12.112,13.522c0.038-0.03,0.086-0.045,0.143-0.045c0.042,0,0.08,0.008,0.112,0.025
				c0.032,0.017,0.059,0.04,0.08,0.072c0.021,0.031,0.037,0.069,0.049,0.114c0.012,0.045,0.018,0.106,0.018,0.182
				c0,0.09-0.01,0.163-0.029,0.219c-0.019,0.055-0.048,0.098-0.086,0.128c-0.038,0.03-0.086,0.045-0.144,0.045
				c-0.076,0-0.136-0.027-0.18-0.08c-0.052-0.064-0.078-0.168-0.078-0.313c0-0.091,0.01-0.164,0.029-0.219
				C12.046,13.595,12.074,13.552,12.112,13.522z M11.732,11.414c0.012-0.037,0.029-0.065,0.052-0.086
				c0.023-0.02,0.051-0.03,0.086-0.03c0.026,0,0.048,0.006,0.067,0.017c0.019,0.011,0.035,0.027,0.048,0.048
				c0.013,0.021,0.022,0.046,0.03,0.076c0.007,0.03,0.011,0.07,0.011,0.121c0,0.06-0.006,0.109-0.017,0.145
				c-0.012,0.037-0.029,0.066-0.051,0.086c-0.023,0.02-0.052,0.03-0.086,0.03c-0.046,0-0.082-0.018-0.108-0.053
				c-0.031-0.043-0.047-0.112-0.047-0.208C11.715,11.499,11.721,11.451,11.732,11.414z M11.744,13.8v-0.108h0.111V13.8H11.744z
				 M11.855,14.142v0.108h-0.111v-0.108H11.855z M11.345,11.769c0.008-0.023,0.021-0.046,0.038-0.068
				c0.018-0.022,0.043-0.048,0.076-0.077c0.052-0.046,0.087-0.082,0.105-0.109c0.018-0.027,0.027-0.052,0.027-0.076
				c0-0.025-0.008-0.046-0.025-0.063c-0.016-0.017-0.038-0.026-0.065-0.026c-0.028,0-0.051,0.009-0.067,0.027
				c-0.017,0.018-0.025,0.043-0.026,0.075l-0.06-0.007c0.004-0.048,0.019-0.085,0.046-0.11c0.027-0.025,0.063-0.038,0.108-0.038
				c0.046,0,0.082,0.014,0.108,0.041c0.027,0.027,0.04,0.061,0.04,0.101c0,0.02-0.004,0.041-0.012,0.06
				c-0.008,0.02-0.021,0.041-0.039,0.063c-0.018,0.022-0.048,0.052-0.09,0.09c-0.035,0.032-0.058,0.053-0.068,0.065
				c-0.01,0.011-0.018,0.023-0.025,0.034l0.233,0v0.061h-0.314C11.338,11.797,11.34,11.783,11.345,11.769z M11.414,12.583
				c-0.024,0.016-0.047,0.028-0.066,0.036v-0.068c0.035-0.018,0.066-0.041,0.092-0.067c0.026-0.026,0.045-0.052,0.056-0.076h0.04
				v0.572h0h-0.062v-0.445C11.458,12.551,11.438,12.567,11.414,12.583z M11.338,13.477c0.076,0,0.136,0.02,0.18,0.061
				c0.044,0.041,0.066,0.092,0.066,0.152c0,0.031-0.006,0.061-0.019,0.091c-0.013,0.03-0.035,0.061-0.065,0.094
				c-0.03,0.033-0.08,0.078-0.15,0.135c-0.058,0.048-0.096,0.08-0.112,0.097c-0.017,0.017-0.03,0.034-0.041,0.051l0.388,0v0.091
				h-0.523c-0.001-0.023,0.003-0.045,0.011-0.066c0.013-0.035,0.035-0.069,0.064-0.102s0.072-0.072,0.127-0.116
				c0.086-0.069,0.144-0.123,0.175-0.163c0.03-0.04,0.045-0.078,0.045-0.113c0-0.037-0.014-0.069-0.041-0.095
				c-0.027-0.026-0.063-0.038-0.108-0.038c-0.047,0-0.084,0.014-0.112,0.041c-0.028,0.027-0.042,0.065-0.043,0.113l-0.1-0.01
				c0.007-0.072,0.033-0.128,0.077-0.165C11.203,13.496,11.263,13.477,11.338,13.477z M10.808,11.442h0.052v0.053
				c0.025-0.041,0.062-0.061,0.11-0.061c0.021,0,0.04,0.004,0.057,0.012c0.017,0.008,0.03,0.019,0.039,0.032
				c0.009,0.013,0.015,0.029,0.018,0.047c0.002,0.012,0.003,0.032,0.003,0.061v0.228H11.03v-0.226c0-0.026-0.002-0.045-0.007-0.058
				c-0.005-0.013-0.013-0.023-0.024-0.03c-0.012-0.008-0.025-0.011-0.041-0.011c-0.025,0-0.046,0.009-0.064,0.026
				c-0.018,0.017-0.027,0.049-0.027,0.097v0.203h-0.058V11.442z M11.022,12.411v0.569H10.96v-0.569H11.022z M10.803,12.411h0.063
				v0.08h-0.063V12.411z M10.803,12.568h0.063v0.412h-0.063V12.568z M10.637,13.713c-0.038,0.022-0.072,0.038-0.102,0.049V13.67
				c0.054-0.025,0.102-0.055,0.143-0.09c0.041-0.035,0.069-0.07,0.086-0.103h0.063v0.772h-0.097v-0.602
				C10.705,13.669,10.675,13.691,10.637,13.713z M10.774,12.469c-0.012-0.002-0.023-0.003-0.034-0.003
				c-0.022,0-0.036,0.005-0.044,0.014c-0.008,0.01-0.012,0.03-0.012,0.061v0.027h0.072v0.054h-0.072v0.359h-0.062v-0.359h-0.056
				v-0.054h0.056v-0.04c0-0.041,0.008-0.072,0.024-0.094c0.016-0.022,0.041-0.033,0.075-0.033c0.023,0,0.044,0.003,0.064,0.009
				L10.774,12.469z M10.643,11.507c-0.015-0.014-0.037-0.021-0.066-0.021c-0.027,0-0.048,0.005-0.061,0.016
				c-0.013,0.01-0.023,0.029-0.029,0.055l-0.057-0.008c0.005-0.026,0.014-0.048,0.026-0.064c0.012-0.016,0.029-0.029,0.052-0.037
				c0.022-0.009,0.048-0.013,0.078-0.013c0.029,0,0.053,0.004,0.072,0.011c0.018,0.008,0.032,0.017,0.041,0.028
				c0.009,0.011,0.015,0.026,0.018,0.043c0.002,0.011,0.003,0.03,0.003,0.058v0.084c0,0.059,0.001,0.095,0.004,0.111
				c0.002,0.016,0.007,0.03,0.015,0.045h-0.061c-0.006-0.013-0.01-0.028-0.012-0.046c-0.022,0.02-0.043,0.034-0.062,0.042
				c-0.02,0.008-0.041,0.012-0.064,0.012c-0.038,0-0.067-0.01-0.087-0.03c-0.02-0.02-0.031-0.045-0.031-0.076
				c0-0.018,0.004-0.035,0.012-0.05c0.008-0.015,0.018-0.027,0.03-0.036c0.012-0.009,0.026-0.016,0.042-0.021
				c0.011-0.003,0.029-0.006,0.052-0.009c0.047-0.006,0.082-0.013,0.104-0.022c0-0.009,0-0.014,0-0.016
				C10.659,11.535,10.654,11.517,10.643,11.507z M10.483,12.614c0.032,0.037,0.048,0.088,0.048,0.154
				c0,0.053-0.007,0.095-0.022,0.125c-0.014,0.03-0.035,0.054-0.062,0.071c-0.027,0.017-0.057,0.025-0.089,0.025
				c-0.052,0-0.094-0.019-0.125-0.055c-0.032-0.037-0.048-0.091-0.048-0.16c0-0.076,0.019-0.133,0.057-0.17
				c0.032-0.03,0.07-0.046,0.116-0.046C10.41,12.558,10.451,12.577,10.483,12.614z M10.299,13.871c0,0.164-0.132,0.298-0.296,0.298
				c-0.163,0-0.296-0.133-0.296-0.298s0.132-0.298,0.296-0.298C10.167,13.574,10.299,13.707,10.299,13.871z M10.139,11.659
				c0.001,0.039,0.008,0.066,0.02,0.08c0.012,0.015,0.029,0.022,0.05,0.022c0.016,0,0.029-0.004,0.041-0.012
				c0.012-0.008,0.019-0.018,0.024-0.032c0.004-0.013,0.006-0.035,0.006-0.064V11.3h0.063v0.349c0,0.043-0.005,0.076-0.014,0.099
				c-0.01,0.024-0.025,0.041-0.046,0.054c-0.021,0.012-0.045,0.019-0.073,0.019c-0.042,0-0.074-0.013-0.096-0.039
				c-0.022-0.026-0.033-0.064-0.032-0.115L10.139,11.659z M9.971,12.568h0.056v0.062c0.014-0.029,0.028-0.048,0.04-0.058
				c0.012-0.009,0.026-0.014,0.04-0.014c0.021,0,0.042,0.008,0.064,0.023l-0.022,0.065c-0.015-0.01-0.031-0.015-0.046-0.015
				c-0.014,0-0.026,0.005-0.037,0.014c-0.011,0.009-0.019,0.022-0.023,0.038c-0.007,0.025-0.01,0.052-0.01,0.081v0.216l-0.063,0
				V12.568z M9.761,11.591c-0.018-0.019-0.04-0.028-0.067-0.028c-0.011,0-0.025,0.002-0.041,0.007l0.006-0.055
				c0.004,0.001,0.007,0.001,0.009,0.001c0.025,0,0.047-0.007,0.067-0.021c0.02-0.014,0.03-0.036,0.03-0.065
				c0-0.023-0.007-0.042-0.022-0.057c-0.015-0.015-0.033-0.023-0.056-0.023c-0.023,0-0.041,0.008-0.057,0.023
				c-0.015,0.015-0.025,0.038-0.029,0.069l-0.058-0.011c0.007-0.042,0.023-0.075,0.049-0.098c0.025-0.023,0.057-0.035,0.094-0.035
				c0.026,0,0.05,0.006,0.072,0.018c0.022,0.012,0.038,0.028,0.05,0.049c0.012,0.021,0.017,0.043,0.017,0.066
				c0,0.022-0.005,0.042-0.016,0.06c-0.011,0.018-0.027,0.033-0.049,0.043c0.028,0.007,0.05,0.022,0.065,0.044
				c0.016,0.022,0.023,0.049,0.023,0.083c0,0.045-0.015,0.083-0.045,0.114c-0.03,0.031-0.069,0.047-0.115,0.047
				c-0.042,0-0.076-0.013-0.104-0.04c-0.028-0.027-0.043-0.062-0.047-0.104l0.058-0.008c0.007,0.036,0.018,0.061,0.034,0.077
				s0.036,0.024,0.059,0.024c0.027,0,0.051-0.01,0.069-0.031c0.019-0.02,0.029-0.046,0.029-0.076
				C9.787,11.634,9.778,11.61,9.761,11.591z M9.505,12.411h0.192c0.034,0,0.06,0.002,0.077,0.005
				c0.025,0.005,0.046,0.013,0.063,0.027c0.017,0.013,0.031,0.031,0.041,0.055c0.01,0.024,0.015,0.049,0.015,0.078
				c0,0.048-0.014,0.089-0.041,0.123c-0.028,0.034-0.077,0.05-0.15,0.05H9.572v0.232l-0.067,0V12.411z M9.185,11.414
				c0.012-0.037,0.029-0.065,0.052-0.086c0.023-0.02,0.051-0.03,0.086-0.03c0.026,0,0.048,0.006,0.067,0.017
				c0.019,0.011,0.035,0.027,0.048,0.048c0.013,0.021,0.022,0.046,0.03,0.076c0.007,0.03,0.011,0.07,0.011,0.121
				c0,0.06-0.006,0.109-0.017,0.145c-0.012,0.037-0.029,0.066-0.051,0.086c-0.023,0.02-0.052,0.03-0.086,0.03
				c-0.046,0-0.082-0.018-0.108-0.053c-0.031-0.043-0.047-0.112-0.047-0.208C9.168,11.499,9.174,11.451,9.185,11.414z M9.428,12.739
				v0.07H9.236v-0.07H9.428z M8.755,12.411h0.226c0.045,0,0.08,0.005,0.104,0.015c0.024,0.01,0.042,0.028,0.057,0.054
				c0.014,0.026,0.021,0.055,0.021,0.086c0,0.04-0.012,0.074-0.035,0.102c-0.023,0.028-0.06,0.045-0.109,0.053
				c0.018,0.01,0.031,0.019,0.041,0.028c0.02,0.02,0.038,0.045,0.056,0.076l0.089,0.155H9.119l-0.067-0.118
				c-0.02-0.034-0.036-0.06-0.049-0.079c-0.013-0.018-0.024-0.031-0.034-0.038c-0.01-0.007-0.02-0.012-0.031-0.015
				c-0.008-0.002-0.02-0.003-0.038-0.003H8.822v0.253H8.755V12.411z M8.616,12.481c0.013,0.024,0.02,0.049,0.02,0.075
				c0,0.024-0.006,0.047-0.018,0.069c-0.012,0.022-0.03,0.039-0.054,0.052c0.031,0.01,0.055,0.027,0.071,0.052
				c0.017,0.024,0.025,0.053,0.025,0.086c0,0.027-0.005,0.051-0.015,0.074c-0.01,0.023-0.023,0.041-0.037,0.053
				c-0.015,0.012-0.033,0.022-0.056,0.028c-0.022,0.006-0.05,0.01-0.082,0.01H8.276v0v-0.569h0.191c0.039,0,0.07,0.006,0.094,0.017
				C8.584,12.44,8.603,12.457,8.616,12.481z M8.498,11.477h0.055v0.149H8.498V11.477z M7.866,11.386h0.628v0.356H7.866V11.386z
				 M7.604,12.37h0.076v0.219c0.035-0.04,0.08-0.061,0.134-0.061c0.033,0,0.062,0.006,0.086,0.019
				c0.024,0.013,0.042,0.031,0.052,0.054c0.01,0.023,0.016,0.056,0.016,0.099v0.28H7.892V12.7c0-0.037-0.008-0.065-0.025-0.082
				c-0.016-0.017-0.04-0.026-0.07-0.026c-0.022,0-0.044,0.006-0.063,0.017c-0.02,0.012-0.034,0.027-0.042,0.047
				c-0.008,0.02-0.013,0.047-0.013,0.081v0.242H7.604V12.37z M7.486,12.359h0.061L7.368,12.99H7.307L7.486,12.359z M7.117,12.877
				c0.026-0.027,0.038-0.079,0.038-0.155l0-0.352h0.082v0.353c0,0.061-0.007,0.11-0.021,0.146c-0.014,0.036-0.039,0.065-0.076,0.088
				c-0.037,0.023-0.085,0.034-0.144,0.034c-0.058,0-0.105-0.01-0.142-0.03c-0.037-0.02-0.063-0.048-0.079-0.086
				c-0.016-0.037-0.024-0.088-0.024-0.153V12.37h0.082v0.352c0,0.053,0.005,0.092,0.015,0.117c0.01,0.025,0.027,0.044,0.051,0.058
				c0.024,0.014,0.054,0.02,0.089,0.02C7.049,12.918,7.092,12.904,7.117,12.877z M6.771,14.072c0.012,0.032,0.032,0.056,0.062,0.073
				c0.029,0.017,0.065,0.026,0.107,0.026c0.072,0,0.123-0.017,0.154-0.051c0.031-0.034,0.046-0.099,0.046-0.195v-0.442h0.098v0.443
				c0,0.077-0.008,0.138-0.025,0.183c-0.017,0.045-0.047,0.082-0.091,0.111c-0.044,0.029-0.101,0.043-0.173,0.043
				c-0.069,0-0.126-0.012-0.17-0.037c-0.044-0.025-0.076-0.061-0.094-0.108c-0.019-0.047-0.028-0.111-0.028-0.192v-0.443h0.098
				v0.442C6.753,13.991,6.759,14.041,6.771,14.072z M6.632,11.533v-0.171v-0.033c0-0.011,0.009-0.02,0.02-0.02h0.039
				c0.011,0,0.019,0.009,0.019,0.02v0.033v0.171l0.214,0.109l-0.032,0.06L6.71,11.61v0.097v0.05h0.046v0.077H6.71v0H6.632v0H6.584
				v-0.077h0.049v-0.05v-0.098L6.45,11.702l-0.032-0.06L6.632,11.533z M6.42,12.579c-0.03-0.032-0.07-0.048-0.119-0.048
				c-0.03,0-0.058,0.007-0.082,0.021c-0.024,0.014-0.044,0.032-0.058,0.055l-0.088-0.012l0.074-0.403h0.381v0.092H6.223
				l-0.041,0.211c0.046-0.033,0.094-0.049,0.145-0.049c0.067,0,0.123,0.024,0.169,0.071s0.069,0.108,0.069,0.183
				c0,0.071-0.02,0.132-0.061,0.184c-0.049,0.063-0.116,0.095-0.201,0.095c-0.07,0-0.127-0.02-0.171-0.06
				c-0.044-0.04-0.069-0.093-0.075-0.159l0.099-0.009c0.007,0.049,0.024,0.086,0.051,0.111C6.233,12.888,6.266,12.9,6.303,12.9
				c0.045,0,0.084-0.017,0.115-0.052c0.031-0.035,0.047-0.081,0.047-0.139C6.465,12.654,6.45,12.611,6.42,12.579z M6.138,14.04
				c0.007,0.048,0.023,0.084,0.049,0.109c0.026,0.024,0.057,0.036,0.093,0.036c0.044,0,0.081-0.017,0.111-0.051
				c0.03-0.034,0.045-0.079,0.045-0.136c0-0.054-0.015-0.096-0.044-0.127c-0.029-0.031-0.067-0.047-0.114-0.047
				c-0.029,0-0.056,0.007-0.079,0.021c-0.023,0.014-0.042,0.032-0.055,0.054l-0.085-0.011l0.072-0.394h0.368v0.09H6.203l-0.04,0.206
				c0.044-0.032,0.091-0.048,0.14-0.048c0.065,0,0.119,0.023,0.163,0.069c0.044,0.046,0.066,0.106,0.066,0.179
				c0,0.069-0.019,0.129-0.058,0.18c-0.048,0.062-0.112,0.093-0.194,0.093c-0.067,0-0.122-0.02-0.165-0.059
				c-0.042-0.039-0.067-0.091-0.073-0.155L6.138,14.04z M5.822,12.627c-0.028-0.029-0.064-0.043-0.108-0.043
				c-0.018,0-0.04,0.004-0.066,0.011l0.01-0.084c0.006,0.001,0.011,0.001,0.015,0.001c0.04,0,0.076-0.011,0.108-0.032
				c0.032-0.021,0.048-0.054,0.048-0.099c0-0.035-0.012-0.065-0.035-0.088c-0.023-0.023-0.054-0.035-0.091-0.035
				c-0.037,0-0.067,0.012-0.091,0.035c-0.024,0.024-0.04,0.059-0.047,0.106l-0.094-0.017c0.011-0.065,0.038-0.115,0.078-0.15
				c0.041-0.035,0.092-0.053,0.152-0.053c0.042,0,0.08,0.009,0.116,0.027c0.035,0.018,0.062,0.043,0.081,0.075
				c0.019,0.032,0.028,0.065,0.028,0.101c0,0.034-0.009,0.065-0.027,0.092c-0.018,0.028-0.044,0.05-0.079,0.066
				c0.045,0.011,0.081,0.033,0.106,0.066c0.025,0.034,0.038,0.076,0.038,0.126c0,0.069-0.024,0.126-0.073,0.174
				C5.842,12.956,5.78,12.98,5.706,12.98c-0.067,0-0.123-0.02-0.168-0.062c-0.045-0.041-0.07-0.094-0.076-0.159l0.094-0.013
				c0.011,0.055,0.029,0.094,0.055,0.118C5.637,12.888,5.669,12.9,5.706,12.9c0.044,0,0.082-0.016,0.112-0.047
				c0.03-0.031,0.046-0.07,0.046-0.116C5.864,12.693,5.85,12.656,5.822,12.627z M5.816,13.918c-0.027-0.028-0.062-0.042-0.104-0.042
				c-0.017,0-0.038,0.003-0.064,0.01l0.01-0.083c0.006,0.001,0.011,0.001,0.015,0.001c0.039,0,0.074-0.01,0.105-0.031
				c0.031-0.021,0.046-0.053,0.046-0.097c0-0.034-0.011-0.063-0.034-0.086c-0.023-0.023-0.052-0.034-0.087-0.034
				c-0.035,0-0.065,0.012-0.088,0.035c-0.024,0.023-0.039,0.058-0.045,0.104l-0.091-0.017c0.011-0.063,0.036-0.112,0.076-0.147
				C5.592,13.497,5.641,13.48,5.7,13.48c0.04,0,0.077,0.009,0.112,0.027c0.034,0.018,0.06,0.042,0.078,0.073
				c0.018,0.031,0.027,0.064,0.027,0.099c0,0.033-0.009,0.063-0.026,0.09c-0.017,0.027-0.043,0.049-0.076,0.065
				c0.044,0.01,0.078,0.032,0.102,0.065c0.024,0.033,0.036,0.074,0.036,0.124c0,0.067-0.023,0.124-0.07,0.17
				c-0.047,0.047-0.107,0.07-0.179,0.07c-0.065,0-0.119-0.02-0.162-0.06c-0.043-0.04-0.067-0.092-0.073-0.156l0.091-0.013
				c0.01,0.053,0.028,0.092,0.053,0.115c0.025,0.023,0.056,0.035,0.092,0.035c0.043,0,0.079-0.015,0.108-0.046
				c0.029-0.031,0.044-0.069,0.044-0.114C5.856,13.982,5.843,13.946,5.816,13.918z M5.217,12.857h0.107v0.11H5.217V12.857z
				 M5.335,14.142v0.107H5.232v-0.107H5.335z M5.019,11.435c0.036-0.016,0.067-0.035,0.093-0.058
				c0.027-0.023,0.046-0.045,0.057-0.066l0.041,0c0,0,0,0.492,0,0.492l-0.064,0V11.42c-0.015,0.014-0.035,0.028-0.06,0.042
				c-0.025,0.014-0.047,0.024-0.067,0.031V11.435z M4.951,13.596c-0.026-0.026-0.059-0.038-0.101-0.038
				c-0.044,0-0.079,0.014-0.105,0.041c-0.026,0.027-0.04,0.065-0.04,0.113l-0.093-0.01c0.006-0.072,0.03-0.127,0.072-0.165
				c0.042-0.038,0.098-0.057,0.168-0.057c0.071,0,0.127,0.02,0.169,0.061c0.041,0.041,0.062,0.091,0.062,0.152
				c0,0.031-0.006,0.061-0.018,0.09c-0.012,0.03-0.032,0.061-0.06,0.094c-0.028,0.033-0.075,0.078-0.14,0.135
				c-0.054,0.047-0.089,0.08-0.105,0.096c-0.015,0.017-0.028,0.034-0.038,0.051h0.363v0.09H4.595
				c-0.001-0.023,0.003-0.044,0.01-0.065c0.012-0.034,0.032-0.068,0.06-0.102c0.027-0.034,0.067-0.072,0.119-0.116
				c0.08-0.068,0.135-0.122,0.163-0.162c0.028-0.04,0.042-0.078,0.042-0.113C4.989,13.653,4.976,13.622,4.951,13.596z M4.923,12.966
				H4.828v-0.613c-0.023,0.022-0.052,0.044-0.089,0.066c-0.037,0.022-0.07,0.039-0.099,0.05v-0.093
				c0.053-0.025,0.099-0.056,0.138-0.092c0.04-0.036,0.067-0.071,0.084-0.105h0.061V12.966z M4.567,11.764
				c0.009-0.022,0.023-0.044,0.042-0.065c0.019-0.022,0.047-0.046,0.083-0.074c0.056-0.044,0.094-0.079,0.114-0.104
				s0.03-0.05,0.03-0.072c0-0.024-0.009-0.044-0.027-0.06c-0.018-0.016-0.041-0.024-0.07-0.024c-0.031,0-0.055,0.009-0.074,0.026
				c-0.018,0.017-0.028,0.041-0.028,0.072l-0.065-0.006c0.004-0.046,0.021-0.081,0.051-0.106c0.029-0.024,0.069-0.036,0.118-0.037
				c0.05,0,0.089,0.013,0.118,0.039c0.029,0.026,0.044,0.058,0.044,0.097c0,0.02-0.004,0.039-0.013,0.058
				c-0.009,0.019-0.023,0.039-0.042,0.06c-0.02,0.021-0.052,0.05-0.098,0.087c-0.038,0.03-0.063,0.051-0.074,0.062
				c-0.011,0.011-0.02,0.022-0.027,0.033c0,0,0.254-0.001,0.254-0.001v0.058L4.56,11.805C4.559,11.791,4.561,11.777,4.567,11.764z
				 M4.402,11.451l0.072,0v0.069l-0.072,0V11.451z M4.402,11.737l0.072,0v0.068l-0.072,0V11.737z M3.982,11.426
				c0.013-0.035,0.031-0.063,0.056-0.082c0.025-0.019,0.056-0.029,0.094-0.029c0.028,0,0.052,0.005,0.073,0.016
				c0.021,0.01,0.038,0.026,0.052,0.045c0.014,0.02,0.024,0.044,0.032,0.073c0.008,0.029,0.012,0.067,0.012,0.116
				c0,0.058-0.006,0.104-0.019,0.139c-0.013,0.035-0.031,0.063-0.056,0.082c-0.025,0.019-0.056,0.029-0.094,0.029
				c-0.05,0-0.089-0.017-0.118-0.05c-0.034-0.041-0.051-0.107-0.051-0.199C3.963,11.508,3.97,11.462,3.982,11.426z M3.827,12.369
				v-0.054v-0.054h0.267h0.059h0.063v0.241h0.14v0.122h0v0.305H4.234v-0.305H4.092v-0.122h0.002v-0.133H3.946v0.32H3.833v0.234
				H3.711v-0.343h0.116V12.369z M3.705,13.665v-0.107H4.22v0.107H3.705z M3.811,13.665v0.544H3.705v-0.544H3.811z M4.11,13.665h0.11
				v0.437H4.33v0.107H4.22v0H4.11V13.665z M3.609,11.498V11.44c0.036-0.016,0.067-0.035,0.094-0.058
				c0.027-0.023,0.045-0.044,0.056-0.066l0.041,0v0.492l-0.064,0v-0.383c-0.015,0.014-0.036,0.028-0.06,0.042
				C3.651,11.481,3.629,11.491,3.609,11.498z M13.282,14.696c0,0.031-0.025,0.056-0.056,0.056H3.74
				c-0.031,0-0.056-0.025-0.056-0.056v-0.271c0-0.031,0.025-0.056,0.056-0.056h9.486c0.031,0,0.056,0.025,0.056,0.056V14.696z
				 M12.967,11.738v-0.376l0.331,0.188L12.967,11.738z"/>
			<polygon opacity="0.8" points="12.924,13.978 12.924,13.632 12.676,13.978 			"/>
			<path opacity="0.8" d="M9.323,11.77c0.027,0,0.049-0.014,0.068-0.042c0.018-0.028,0.027-0.084,0.027-0.168
				c0-0.084-0.009-0.14-0.027-0.168C9.372,11.364,9.35,11.35,9.322,11.35c-0.027,0-0.048,0.012-0.064,0.037
				c-0.02,0.031-0.03,0.089-0.03,0.173s0.009,0.14,0.027,0.168C9.274,11.756,9.296,11.77,9.323,11.77z"/>
			<path opacity="0.8" d="M12.609,11.77c0.027,0,0.049-0.014,0.067-0.042c0.018-0.028,0.027-0.084,0.027-0.168
				c0-0.084-0.009-0.14-0.027-0.168c-0.018-0.028-0.041-0.042-0.068-0.042c-0.027,0-0.048,0.012-0.064,0.037
				c-0.02,0.031-0.03,0.089-0.03,0.173s0.009,0.14,0.027,0.168C12.559,11.756,12.582,11.77,12.609,11.77z"/>
			<path opacity="0.8" d="M8.461,12.717H8.343l0,0.196H8.47c0.022,0,0.037-0.001,0.046-0.003c0.016-0.003,0.029-0.008,0.039-0.016
				c0.01-0.007,0.019-0.018,0.026-0.032c0.007-0.014,0.01-0.03,0.01-0.048c0-0.021-0.005-0.04-0.015-0.055
				c-0.01-0.016-0.023-0.027-0.04-0.033C8.519,12.72,8.494,12.717,8.461,12.717z"/>
		</g>
	</g>
</g>
</svg>
 */