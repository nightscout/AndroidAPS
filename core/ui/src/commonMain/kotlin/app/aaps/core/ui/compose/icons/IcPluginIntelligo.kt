package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Intelligo CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginIntelligoPreview
 */
val IcPluginIntelligo: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginIntelligo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Main detailed path (white, full opacity)
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
            // First subpath
            moveTo(17.784f, 7.309f)
            horizontalLineToRelative(-1.538f)
            verticalLineTo(5.874f)
            horizontalLineToRelative(1.178f)
            curveToRelative(-0.647f, -2.123f, -1.832f, -4.078f, -4f, -4.078f)
            horizontalLineToRelative(-3.339f)
            curveToRelative(-1.945f, 0f, -3.008f, 1.955f, -3.589f, 4.078f)
            horizontalLineToRelative(1.258f)
            verticalLineToRelative(1.435f)
            horizontalLineTo(6.173f)
            curveToRelative(-0.366f, 2.011f, -0.376f, 3.81f, -0.376f, 3.851f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.094f, 0.058f, 9.364f, 4.78f, 9.364f)
            horizontalLineToRelative(2.847f)
            curveToRelative(4.726f, 0f, 4.78f, -9.271f, 4.78f, -9.364f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.041f, -0.011f, -1.84f, -0.42f, -3.851f)
            close()

            // Subpath: 9.634,20.198
            moveTo(9.634f, 20.198f)
            horizontalLineToRelative(-0.51f)
            verticalLineToRelative(-0.552f)
            horizontalLineToRelative(0.51f)
            verticalLineTo(20.198f)
            close()

            // Subpath: 12,11.388
            moveTo(12f, 11.388f)
            verticalLineToRelative(0.551f)
            horizontalLineTo(9.125f)
            verticalLineToRelative(-0.551f)
            horizontalLineTo(12f)
            close()

            // Subpath: 9.125,10.823
            moveTo(9.125f, 10.823f)
            verticalLineToRelative(-0.551f)
            horizontalLineToRelative(0.51f)
            verticalLineToRelative(0.551f)
            horizontalLineTo(9.125f)
            close()

            // Subpath: 9.917,10.823
            moveTo(9.917f, 10.823f)
            verticalLineToRelative(-0.551f)
            horizontalLineTo(12f)
            verticalLineToRelative(0.551f)
            horizontalLineTo(9.917f)
            close()

            // Subpath: 12,12.505
            moveTo(12f, 12.505f)
            verticalLineToRelative(0.552f)
            horizontalLineTo(9.125f)
            verticalLineToRelative(-0.552f)
            horizontalLineTo(12f)
            close()

            // Subpath: 12,20.198
            moveTo(12f, 20.198f)
            horizontalLineTo(9.917f)
            verticalLineToRelative(-0.552f)
            horizontalLineTo(12f)
            verticalLineTo(20.198f)
            close()

            // Subpath: 12,17.738
            moveTo(12f, 17.738f)
            horizontalLineToRelative(-1.063f)
            curveToRelative(-0.225f, 0f, -0.371f, 0.012f, -0.437f, 0.035f)
            curveToRelative(-0.066f, 0.023f, -0.118f, 0.062f, -0.154f, 0.114f)
            curveToRelative(-0.037f, 0.054f, -0.055f, 0.117f, -0.055f, 0.191f)
            curveToRelative(0f, 0.096f, 0.026f, 0.182f, 0.079f, 0.257f)
            curveToRelative(0.052f, 0.076f, 0.122f, 0.128f, 0.208f, 0.156f)
            curveToRelative(0.086f, 0.028f, 0.246f, 0.042f, 0.479f, 0.042f)
            horizontalLineTo(12f)
            verticalLineToRelative(0.552f)
            horizontalLineTo(9.917f)
            verticalLineToRelative(-0.513f)
            horizontalLineToRelative(0.306f)
            curveToRelative(-0.235f, -0.182f, -0.353f, -0.41f, -0.353f, -0.687f)
            curveToRelative(0f, -0.121f, 0.022f, -0.232f, 0.066f, -0.333f)
            curveToRelative(0.044f, -0.101f, 0.1f, -0.177f, 0.167f, -0.229f)
            curveToRelative(0.068f, -0.052f, 0.146f, -0.088f, 0.231f, -0.107f)
            curveToRelative(0.086f, -0.021f, 0.21f, -0.031f, 0.371f, -0.031f)
            horizontalLineTo(12f)
            verticalLineTo(17.738f)
            close()

            // Subpath: 11.997,16.331
            moveTo(11.997f, 16.331f)
            curveToRelative(-0.033f, 0.08f, -0.077f, 0.139f, -0.129f, 0.176f)
            curveToRelative(-0.053f, 0.037f, -0.125f, 0.063f, -0.215f, 0.078f)
            curveToRelative(-0.064f, 0.012f, -0.193f, 0.018f, -0.388f, 0.018f)
            horizontalLineToRelative(-0.908f)
            verticalLineToRelative(0.253f)
            horizontalLineTo(9.917f)
            verticalLineToRelative(-0.253f)
            horizontalLineTo(9.503f)
            lineToRelative(-0.322f, -0.554f)
            horizontalLineToRelative(0.736f)
            verticalLineToRelative(-0.376f)
            horizontalLineToRelative(0.439f)
            verticalLineToRelative(0.376f)
            horizontalLineToRelative(0.839f)
            curveToRelative(0.17f, 0f, 0.269f, -0.003f, 0.297f, -0.011f)
            curveToRelative(0.028f, -0.007f, 0.051f, -0.023f, 0.069f, -0.049f)
            curveToRelative(0.019f, -0.025f, 0.027f, -0.057f, 0.027f, -0.093f)
            curveToRelative(0f, -0.052f, -0.018f, -0.125f, -0.053f, -0.222f)
            lineToRelative(0.428f, -0.048f)
            curveToRelative(0.055f, 0.129f, 0.082f, 0.273f, 0.082f, 0.436f)
            curveToRelative(0f, 0.132f, -0.017f, 0.222f, -0.05f, 0.301f)
            close()

            // Subpath: 11.692,15.245
            moveTo(11.692f, 15.245f)
            curveToRelative(-0.189f, 0.137f, -0.429f, 0.205f, -0.718f, 0.205f)
            curveToRelative(-0.345f, 0f, -0.616f, -0.09f, -0.811f, -0.271f)
            curveToRelative(-0.196f, -0.181f, -0.293f, -0.408f, -0.293f, -0.685f)
            curveToRelative(0f, -0.31f, 0.103f, -0.555f, 0.307f, -0.733f)
            curveToRelative(0.204f, -0.178f, 0.518f, -0.265f, 0.94f, -0.257f)
            verticalLineToRelative(1.381f)
            curveToRelative(0.164f, -0.004f, 0.291f, -0.049f, 0.382f, -0.134f)
            curveToRelative(0.091f, -0.085f, 0.136f, -0.19f, 0.136f, -0.317f)
            curveToRelative(0f, -0.086f, -0.023f, -0.159f, -0.071f, -0.218f)
            curveToRelative(-0.047f, -0.059f, -0.123f, -0.104f, -0.228f, -0.134f)
            lineToRelative(0.092f, -0.549f)
            curveToRelative(0.201f, 0.07f, 0.354f, 0.182f, 0.46f, 0.334f)
            curveToRelative(0.106f, 0.152f, 0.158f, 0.344f, 0.158f, 0.572f)
            curveToRelative(0f, 0.236f, -0.118f, 0.503f, -0.355f, 0.678f)
            close()

            // Subpath: 11.86,9.13
            moveTo(11.86f, 9.13f)
            curveToRelative(-0.126f, 0.224f, -0.307f, 0.392f, -0.542f, 0.504f)
            curveToRelative(-0.235f, 0.112f, -0.49f, 0.168f, -0.766f, 0.168f)
            curveToRelative(-0.299f, 0f, -0.565f, -0.063f, -0.798f, -0.188f)
            curveToRelative(-0.233f, -0.124f, -0.411f, -0.308f, -0.535f, -0.55f)
            curveToRelative(-0.096f, -0.185f, -0.144f, -0.414f, -0.144f, -0.689f)
            curveToRelative(0f, -0.357f, 0.075f, -0.636f, 0.225f, -0.837f)
            curveToRelative(0.15f, -0.201f, 0.357f, -0.33f, 0.621f, -0.387f)
            lineToRelative(0.108f, 0.577f)
            curveToRelative(-0.146f, 0.028f, -0.257f, 0.104f, -0.339f, 0.216f)
            curveToRelative(-0.082f, 0.111f, -0.122f, 0.251f, -0.122f, 0.418f)
            curveToRelative(0f, 0.253f, 0.08f, 0.455f, 0.241f, 0.605f)
            curveToRelative(0.161f, 0.15f, 0.399f, 0.225f, 0.716f, 0.225f)
            curveToRelative(0.341f, 0f, 0.597f, -0.076f, 0.768f, -0.228f)
            curveToRelative(0.17f, -0.152f, 0.256f, -0.351f, 0.256f, -0.596f)
            curveToRelative(0f, -0.122f, -0.024f, -0.244f, -0.072f, -0.366f)
            curveToRelative(-0.047f, -0.123f, -0.105f, -0.228f, -0.173f, -0.315f)
            horizontalLineToRelative(-0.365f)
            verticalLineToRelative(0.665f)
            horizontalLineToRelative(-0.485f)
            verticalLineTo(7.114f)
            horizontalLineToRelative(1.146f)
            curveToRelative(0.118f, 0.122f, 0.221f, 0.298f, 0.311f, 0.528f)
            curveToRelative(0.09f, 0.231f, 0.134f, 0.465f, 0.134f, 0.701f)
            curveToRelative(0f, 0.302f, -0.063f, 0.564f, -0.189f, 0.787f)
            close()

            // Subpath: 11.658,6.317
            moveTo(11.658f, 6.317f)
            curveToRelative(-0.261f, 0.252f, -0.62f, 0.378f, -1.078f, 0.378f)
            curveToRelative(-0.292f, 0f, -0.539f, -0.043f, -0.737f, -0.131f)
            curveToRelative(-0.198f, -0.088f, -0.33f, -0.177f, -0.446f, -0.29f)
            curveToRelative(-0.116f, -0.113f, -0.203f, -0.237f, -0.259f, -0.372f)
            curveToRelative(-0.076f, -0.179f, -0.114f, -0.385f, -0.114f, -0.62f)
            curveToRelative(0f, -0.424f, 0.131f, -0.763f, 0.395f, -1.017f)
            curveToRelative(0.263f, -0.254f, 0.628f, -0.381f, 1.096f, -0.381f)
            curveToRelative(0.464f, 0f, 0.828f, 0.126f, 1.09f, 0.378f)
            curveToRelative(0.262f, 0.252f, 0.393f, 0.59f, 0.393f, 1.012f)
            curveToRelative(0f, 0.424f, -0.131f, 0.764f, -0.391f, 1.016f)
            close()
        }

        // Small path (top part, full opacity)
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
            moveTo(10.292f, 14.462f)
            curveToRelative(0f, 0.12f, 0.044f, 0.22f, 0.131f, 0.298f)
            curveToRelative(0.088f, 0.079f, 0.207f, 0.117f, 0.357f, 0.116f)
            verticalLineToRelative(-0.824f)
            curveToRelative(-0.159f, 0.004f, -0.281f, 0.045f, -0.364f, 0.124f)
            curveToRelative(-0.083f, 0.078f, -0.124f, 0.174f, -0.124f, 0.286f)
            close()
        }

        // Another small path (full opacity)
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
            moveTo(10.552f, 4.507f)
            curveToRelative(-0.33f, 0f, -0.575f, 0.072f, -0.737f, 0.217f)
            reflectiveCurveToRelative(-0.243f, 0.336f, -0.243f, 0.575f)
            reflectiveCurveToRelative(0.082f, 0.432f, 0.246f, 0.579f)
            reflectiveCurveToRelative(0.412f, 0.22f, 0.742f, 0.22f)
            curveToRelative(0.326f, 0f, 0.573f, -0.075f, 0.741f, -0.226f)
            reflectiveCurveToRelative(0.252f, -0.341f, 0.252f, -0.573f)
            reflectiveCurveToRelative(-0.083f, -0.421f, -0.25f, -0.57f)
            curveToRelative(-0.167f, -0.149f, -0.417f, -0.223f, -0.751f, -0.223f)
            close()
        }

        // Background path with opacity 0.1
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            // First subpath
            moveTo(5.201f, 11.16f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.407f, 0.061f, 9.96f, 5.376f, 9.96f)
            horizontalLineToRelative(2.847f)
            curveToRelative(5.315f, 0f, 5.376f, -9.553f, 5.376f, -9.96f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.407f, -0.061f, -9.96f, -5.376f, -9.96f)
            horizontalLineToRelative(-3.339f)
            curveTo(5.257f, 1.2f, 5.201f, 10.753f, 5.201f, 11.16f)
            close()

            // Second subpath
            moveTo(13.423f, 1.795f)
            curveToRelative(2.168f, 0f, 3.353f, 1.955f, 4f, 4.078f)
            horizontalLineToRelative(-1.178f)
            verticalLineToRelative(1.435f)
            horizontalLineToRelative(1.538f)
            curveToRelative(0.409f, 2.011f, 0.42f, 3.81f, 0.42f, 3.851f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.094f, -0.055f, 9.364f, -4.78f, 9.364f)
            horizontalLineToRelative(-2.847f)
            curveToRelative(-4.722f, 0f, -4.78f, -9.271f, -4.78f, -9.364f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.041f, 0.01f, -1.84f, 0.376f, -3.851f)
            horizontalLineToRelative(1.581f)
            verticalLineTo(5.874f)
            horizontalLineTo(6.496f)
            curveToRelative(0.581f, -2.123f, 1.643f, -4.078f, 3.589f, -4.078f)
            horizontalLineToRelative(3.338f)
            close()
        }
    }.build()
}
