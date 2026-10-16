package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Medtrum Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 *
 * @see IcPluginEopatchIconPreview
 */
val IcPluginEopatch: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginEopatch",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Path with opacity 0.4
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(9.13f, 14.58f)
            curveToRelative(0f, -0.451f, -0.366f, -0.817f, -0.817f, -0.817f)
            curveToRelative(-0.09f, 0f, -0.175f, 0.015f, -0.256f, 0.042f)
            verticalLineToRelative(0.321f)
            curveToRelative(0.076f, -0.043f, 0.163f, -0.068f, 0.256f, -0.068f)
            curveToRelative(0.288f, 0f, 0.521f, 0.233f, 0.521f, 0.521f)
            curveToRelative(0f, 0.288f, -0.233f, 0.521f, -0.521f, 0.521f)
            curveToRelative(-0.288f, 0f, -0.521f, -0.233f, -0.521f, -0.521f)
            curveToRelative(0f, -0.143f, 0.058f, -0.272f, 0.151f, -0.366f)
            verticalLineToRelative(-0.06f)
            verticalLineToRelative(-0.198f)
            verticalLineToRelative(-0.102f)
            verticalLineToRelative(-0.206f)
            verticalLineToRelative(-0.052f)
            curveToRelative(-0.093f, -0.094f, -0.151f, -0.224f, -0.151f, -0.366f)
            curveToRelative(0f, -0.288f, 0.233f, -0.521f, 0.521f, -0.521f)
            curveToRelative(0.288f, 0f, 0.521f, 0.233f, 0.521f, 0.521f)
            curveToRelative(0f, 0.142f, -0.082f, 0.295f, -0.143f, 0.289f)
            reflectiveCurveToRelative(-0.259f, -0.334f, -0.362f, -0.378f)
            curveToRelative(-0.086f, -0.037f, -0.212f, 0.017f, -0.212f, 0.017f)
            reflectiveCurveToRelative(0.271f, 0.514f, 0.326f, 0.521f)
            curveToRelative(0.127f, 0.016f, 0.289f, 0.079f, 0.406f, 0.165f)
            curveToRelative(0.171f, -0.149f, 0.28f, -0.369f, 0.28f, -0.614f)
            curveToRelative(0f, -0.451f, -0.366f, -0.817f, -0.817f, -0.817f)
            curveToRelative(-0.451f, 0f, -0.817f, 0.366f, -0.817f, 0.817f)
            curveToRelative(0f, 0.187f, 0.063f, 0.358f, 0.169f, 0.496f)
            verticalLineToRelative(0.36f)
            curveToRelative(-0.106f, 0.137f, -0.169f, 0.309f, -0.169f, 0.496f)
            curveToRelative(0f, 0.451f, 0.366f, 0.817f, 0.817f, 0.817f)
            curveToRelative(0.451f, 0f, 0.817f, -0.366f, 0.817f, -0.817f)
            close()
        }

        // Path 1 (plein)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(16.641f, 8.827f)
            curveToRelative(-0.857f, 0f, -1.554f, 0.697f, -1.554f, 1.554f)
            reflectiveCurveToRelative(0.697f, 1.554f, 1.554f, 1.554f)
            curveToRelative(0.857f, 0f, 1.554f, -0.697f, 1.554f, -1.554f)
            reflectiveCurveTo(17.498f, 8.827f, 16.641f, 8.827f)
            close()
            moveTo(16.904f, 11.359f)
            lineToRelative(-0.213f, 0.034f)
            curveToRelative(-0.079f, 0.012f, -0.153f, -0.041f, -0.165f, -0.12f)
            lineTo(16.258f, 9.57f)
            curveToRelative(-0.012f, -0.079f, 0.042f, -0.153f, 0.12f, -0.165f)
            lineToRelative(0.213f, -0.034f)
            curveToRelative(0.079f, -0.012f, 0.153f, 0.042f, 0.165f, 0.12f)
            lineToRelative(0.268f, 1.703f)
            curveToRelative(0.012f, 0.079f, -0.042f, 0.153f, -0.12f, 0.165f)
            close()
        }

        // Path with opacity 0.4 (gray)
        path(
            fill = SolidColor(Color(0xFF231F20)),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(16.691f, 11.393f)
            curveToRelative(-0.079f, 0.012f, -0.153f, -0.041f, -0.165f, -0.12f)
            lineTo(16.258f, 9.57f)
            curveToRelative(-0.012f, -0.079f, 0.042f, -0.153f, 0.12f, -0.165f)
            lineToRelative(0.213f, -0.034f)
            curveToRelative(0.079f, -0.012f, 0.153f, 0.042f, 0.165f, 0.12f)
            lineToRelative(0.268f, 1.703f)
            curveToRelative(0.012f, 0.079f, -0.042f, 0.153f, -0.12f, 0.165f)
            lineTo(16.691f, 11.393f)
            close()
        }

        // Main shape (full)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(20.153f, 16.39f)
            verticalLineToRelative(-5.838f)
            curveToRelative(0f, -2.742f, -2.223f, -4.966f, -4.966f, -4.966f)
            horizontalLineTo(6.476f)
            curveToRelative(-2.459f, 0f, -2.632f, 3.348f, -2.632f, 5.543f)
            verticalLineToRelative(1.281f)
            curveToRelative(0f, 3.154f, 2.223f, 6.048f, 4.966f, 6.048f)
            horizontalLineToRelative(8.96f)
            curveTo(19f, 18.459f, 20.153f, 17.883f, 20.153f, 16.39f)
            close()
            moveTo(13.873f, 10.445f)
            curveToRelative(-0.058f, 0f, -0.105f, -0.051f, -0.105f, -0.114f)
            curveToRelative(0f, -0.063f, 0.047f, -0.114f, 0.105f, -0.114f)
            horizontalLineToRelative(0.618f)
            curveToRelative(0.058f, 0f, 0.105f, 0.051f, 0.105f, 0.114f)
            curveToRelative(0f, 0.063f, -0.047f, 0.114f, -0.105f, 0.114f)
            horizontalLineTo(13.873f)
            close()
            moveTo(14.991f, 10.382f)
            curveToRelative(0f, -0.91f, 0.74f, -1.65f, 1.65f, -1.65f)
            curveToRelative(0.91f, 0f, 1.65f, 0.74f, 1.65f, 1.65f)
            curveToRelative(0f, 0.91f, -0.74f, 1.65f, -1.65f, 1.65f)
            curveTo(15.731f, 12.032f, 14.991f, 11.292f, 14.991f, 10.382f)
            close()
            moveTo(7.497f, 14.58f)
            curveToRelative(0f, -0.187f, 0.063f, -0.358f, 0.169f, -0.496f)
            verticalLineToRelative(-0.36f)
            curveToRelative(-0.106f, -0.137f, -0.169f, -0.309f, -0.169f, -0.496f)
            curveToRelative(0f, -0.451f, 0.366f, -0.817f, 0.817f, -0.817f)
            curveToRelative(0.451f, 0f, 0.817f, 0.366f, 0.817f, 0.817f)
            curveToRelative(0f, 0.245f, -0.109f, 0.465f, -0.28f, 0.614f)
            curveToRelative(-0.117f, -0.086f, -0.279f, -0.149f, -0.406f, -0.165f)
            curveToRelative(-0.054f, -0.007f, -0.326f, -0.521f, -0.326f, -0.521f)
            reflectiveCurveToRelative(0.126f, -0.054f, 0.212f, -0.017f)
            curveToRelative(0.103f, 0.045f, 0.301f, 0.373f, 0.362f, 0.378f)
            curveToRelative(0.061f, 0.006f, 0.143f, -0.147f, 0.143f, -0.289f)
            curveToRelative(0f, -0.288f, -0.233f, -0.521f, -0.521f, -0.521f)
            curveToRelative(-0.288f, 0f, -0.521f, 0.233f, -0.521f, 0.521f)
            curveToRelative(0f, 0.143f, 0.058f, 0.272f, 0.151f, 0.366f)
            verticalLineToRelative(0.052f)
            verticalLineToRelative(0.206f)
            verticalLineToRelative(0.102f)
            verticalLineToRelative(0.198f)
            verticalLineToRelative(0.06f)
            curveToRelative(-0.093f, 0.094f, -0.151f, 0.224f, -0.151f, 0.366f)
            curveToRelative(0f, 0.288f, 0.233f, 0.521f, 0.521f, 0.521f)
            curveToRelative(0.288f, 0f, 0.521f, -0.233f, 0.521f, -0.521f)
            curveToRelative(0f, -0.288f, -0.233f, -0.521f, -0.521f, -0.521f)
            curveToRelative(-0.093f, 0f, -0.18f, 0.025f, -0.256f, 0.068f)
            verticalLineToRelative(-0.321f)
            curveToRelative(0.081f, -0.027f, 0.167f, -0.042f, 0.256f, -0.042f)
            curveToRelative(0.451f, 0f, 0.817f, 0.366f, 0.817f, 0.817f)
            curveToRelative(0f, 0.451f, -0.366f, 0.817f, -0.817f, 0.817f)
            curveTo(7.863f, 15.396f, 7.497f, 15.031f, 7.497f, 14.58f)
            close()
        }

        // Small opacity 0.6 path
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(13.873f, 10.445f)
            curveToRelative(-0.058f, 0f, -0.105f, -0.051f, -0.105f, -0.114f)
            lineToRelative(0f, 0f)
            curveToRelative(0f, -0.063f, 0.047f, -0.114f, 0.105f, -0.114f)
            horizontalLineToRelative(0.618f)
            curveToRelative(0.058f, 0f, 0.105f, 0.051f, 0.105f, 0.114f)
            lineToRelative(0f, 0f)
            curveToRelative(0f, 0.063f, -0.047f, 0.114f, -0.105f, 0.114f)
            horizontalLineTo(13.873f)
            close()
        }

        // Last large path with opacity 0.8
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(22.8f, 16.618f)
            verticalLineToRelative(-5.963f)
            curveToRelative(0f, -4.262f, -3.455f, -7.716f, -7.716f, -7.716f)
            horizontalLineTo(7.14f)
            curveToRelative(-5.665f, 0f, -5.94f, 5.436f, -5.94f, 7.716f)
            verticalLineToRelative(1.889f)
            curveToRelative(0f, 2.172f, 1.119f, 8.517f, 7.027f, 8.517f)
            horizontalLineToRelative(10.183f)
            curveTo(20.494f, 21.061f, 22.8f, 19.048f, 22.8f, 16.618f)
            close()
            moveTo(15.673f, 4.015f)
            curveToRelative(0f, -0.214f, 0.173f, -0.387f, 0.387f, -0.387f)
            curveToRelative(0.214f, 0f, 0.387f, 0.173f, 0.387f, 0.387f)
            reflectiveCurveToRelative(-0.173f, 0.387f, -0.387f, 0.387f)
            curveTo(15.846f, 4.402f, 15.673f, 4.229f, 15.673f, 4.015f)
            close()
            moveTo(13.873f, 10.445f)
            curveToRelative(-0.058f, 0f, -0.105f, -0.051f, -0.105f, -0.114f)
            curveToRelative(0f, -0.063f, 0.047f, -0.114f, 0.105f, -0.114f)
            horizontalLineToRelative(0.618f)
            curveToRelative(0.058f, 0f, 0.105f, 0.051f, 0.105f, 0.114f)
            curveToRelative(0f, 0.063f, -0.047f, 0.114f, -0.105f, 0.114f)
            horizontalLineTo(13.873f)
            close()
            moveTo(14.991f, 10.382f)
            curveToRelative(0f, -0.91f, 0.74f, -1.65f, 1.65f, -1.65f)
            curveToRelative(0.91f, 0f, 1.65f, 0.74f, 1.65f, 1.65f)
            curveToRelative(0f, 0.91f, -0.74f, 1.65f, -1.65f, 1.65f)
            curveTo(15.731f, 12.032f, 14.991f, 11.292f, 14.991f, 10.382f)
            close()
            moveTo(1.899f, 12.007f)
            curveToRelative(0f, -0.214f, 0.173f, -0.387f, 0.387f, -0.387f)
            curveToRelative(0.214f, 0f, 0.387f, 0.173f, 0.387f, 0.387f)
            curveToRelative(0f, 0.214f, -0.173f, 0.387f, -0.387f, 0.387f)
            curveTo(2.072f, 12.394f, 1.899f, 12.221f, 1.899f, 12.007f)
            close()
            moveTo(7.497f, 14.58f)
            curveToRelative(0f, -0.187f, 0.063f, -0.358f, 0.169f, -0.496f)
            verticalLineToRelative(-0.36f)
            curveToRelative(-0.106f, -0.137f, -0.169f, -0.309f, -0.169f, -0.496f)
            curveToRelative(0f, -0.451f, 0.366f, -0.817f, 0.817f, -0.817f)
            curveToRelative(0.451f, 0f, 0.817f, 0.366f, 0.817f, 0.817f)
            curveToRelative(0f, 0.245f, -0.109f, 0.465f, -0.28f, 0.614f)
            curveToRelative(-0.117f, -0.086f, -0.279f, -0.149f, -0.406f, -0.165f)
            curveToRelative(-0.054f, -0.007f, -0.326f, -0.521f, -0.326f, -0.521f)
            reflectiveCurveToRelative(0.126f, -0.054f, 0.212f, -0.017f)
            curveToRelative(0.103f, 0.045f, 0.301f, 0.373f, 0.362f, 0.378f)
            curveToRelative(0.061f, 0.006f, 0.143f, -0.147f, 0.143f, -0.289f)
            curveToRelative(0f, -0.288f, -0.233f, -0.521f, -0.521f, -0.521f)
            curveToRelative(-0.288f, 0f, -0.521f, 0.233f, -0.521f, 0.521f)
            curveToRelative(0f, 0.143f, 0.058f, 0.272f, 0.151f, 0.366f)
            verticalLineToRelative(0.052f)
            verticalLineToRelative(0.206f)
            verticalLineToRelative(0.102f)
            verticalLineToRelative(0.198f)
            verticalLineToRelative(0.06f)
            curveToRelative(-0.093f, 0.094f, -0.151f, 0.224f, -0.151f, 0.366f)
            curveToRelative(0f, 0.288f, 0.233f, 0.521f, 0.521f, 0.521f)
            curveToRelative(0.288f, 0f, 0.521f, -0.233f, 0.521f, -0.521f)
            curveToRelative(0f, -0.288f, -0.233f, -0.521f, -0.521f, -0.521f)
            curveToRelative(-0.093f, 0f, -0.18f, 0.025f, -0.256f, 0.068f)
            verticalLineToRelative(-0.321f)
            curveToRelative(0.081f, -0.027f, 0.167f, -0.042f, 0.256f, -0.042f)
            curveToRelative(0.451f, 0f, 0.817f, 0.366f, 0.817f, 0.817f)
            curveToRelative(0f, 0.451f, -0.366f, 0.817f, -0.817f, 0.817f)
            curveTo(7.863f, 15.396f, 7.497f, 15.031f, 7.497f, 14.58f)
            close()
            moveTo(15.673f, 20.059f)
            curveToRelative(0f, -0.214f, 0.173f, -0.387f, 0.387f, -0.387f)
            curveToRelative(0.214f, 0f, 0.387f, 0.173f, 0.387f, 0.387f)
            reflectiveCurveToRelative(-0.173f, 0.387f, -0.387f, 0.387f)
            curveTo(15.846f, 20.446f, 15.673f, 20.273f, 15.673f, 20.059f)
            close()
        }
    }.build()
}
