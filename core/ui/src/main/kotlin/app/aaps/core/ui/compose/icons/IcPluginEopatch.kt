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

@Preview(showBackground = true)
@Composable
private fun IcPluginEopatchIconPreview() {
    Icon(
        imageVector = IcPluginEopatch,
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
<g id="Plugin_Eopatch">
	<g>
		<path opacity="0.4" fill="#FFFFFF" d="M9.13,14.58c0-0.451-0.366-0.817-0.817-0.817c-0.09,0-0.175,0.015-0.256,0.042v0.321
			c0.076-0.043,0.163-0.068,0.256-0.068c0.288,0,0.521,0.233,0.521,0.521c0,0.288-0.233,0.521-0.521,0.521
			c-0.288,0-0.521-0.233-0.521-0.521c0-0.143,0.058-0.272,0.151-0.366v-0.06v-0.198v-0.102v-0.206v-0.052
			C7.85,13.5,7.792,13.371,7.792,13.228c0-0.288,0.233-0.521,0.521-0.521c0.288,0,0.521,0.233,0.521,0.521
			c0,0.142-0.082,0.295-0.143,0.289s-0.259-0.334-0.362-0.378c-0.086-0.037-0.212,0.017-0.212,0.017s0.271,0.514,0.326,0.521
			c0.127,0.016,0.289,0.079,0.406,0.165c0.171-0.149,0.28-0.369,0.28-0.614c0-0.451-0.366-0.817-0.817-0.817
			c-0.451,0-0.817,0.366-0.817,0.817c0,0.187,0.063,0.358,0.169,0.496v0.36c-0.106,0.137-0.169,0.309-0.169,0.496
			c0,0.451,0.366,0.817,0.817,0.817C8.765,15.396,9.13,15.031,9.13,14.58z"/>
		<path fill="#FFFFFF" d="M16.641,8.827c-0.857,0-1.554,0.697-1.554,1.554s0.697,1.554,1.554,1.554c0.857,0,1.554-0.697,1.554-1.554
			S17.498,8.827,16.641,8.827z M16.904,11.359l-0.213,0.034c-0.079,0.012-0.153-0.041-0.165-0.12L16.258,9.57
			c-0.012-0.079,0.042-0.153,0.12-0.165l0.213-0.034c0.079-0.012,0.153,0.042,0.165,0.12l0.268,1.703
			C17.037,11.273,16.983,11.347,16.904,11.359z"/>
		<path opacity="0.4" fill="#231F20" d="M16.691,11.393c-0.079,0.012-0.153-0.041-0.165-0.12L16.258,9.57
			c-0.012-0.079,0.042-0.153,0.12-0.165l0.213-0.034c0.079-0.012,0.153,0.042,0.165,0.12l0.268,1.703
			c0.012,0.079-0.042,0.153-0.12,0.165L16.691,11.393z"/>
		<path fill="#FFFFFF" d="M20.153,16.39v-5.838c0-2.742-2.223-4.966-4.966-4.966H6.476c-2.459,0-2.632,3.348-2.632,5.543v1.281
			c0,3.154,2.223,6.048,4.966,6.048h8.96C19,18.459,20.153,17.883,20.153,16.39z M13.873,10.445c-0.058,0-0.105-0.051-0.105-0.114
			c0-0.063,0.047-0.114,0.105-0.114h0.618c0.058,0,0.105,0.051,0.105,0.114c0,0.063-0.047,0.114-0.105,0.114H13.873z M14.991,10.382
			c0-0.91,0.74-1.65,1.65-1.65c0.91,0,1.65,0.74,1.65,1.65c0,0.91-0.74,1.65-1.65,1.65C15.731,12.032,14.991,11.292,14.991,10.382z
			 M7.497,14.58c0-0.187,0.063-0.358,0.169-0.496v-0.36c-0.106-0.137-0.169-0.309-0.169-0.496c0-0.451,0.366-0.817,0.817-0.817
			c0.451,0,0.817,0.366,0.817,0.817c0,0.245-0.109,0.465-0.28,0.614c-0.117-0.086-0.279-0.149-0.406-0.165
			c-0.054-0.007-0.326-0.521-0.326-0.521s0.126-0.054,0.212-0.017c0.103,0.045,0.301,0.373,0.362,0.378
			c0.061,0.006,0.143-0.147,0.143-0.289c0-0.288-0.233-0.521-0.521-0.521c-0.288,0-0.521,0.233-0.521,0.521
			c0,0.143,0.058,0.272,0.151,0.366v0.052v0.206v0.102v0.198v0.06c-0.093,0.094-0.151,0.224-0.151,0.366
			c0,0.288,0.233,0.521,0.521,0.521c0.288,0,0.521-0.233,0.521-0.521c0-0.288-0.233-0.521-0.521-0.521
			c-0.093,0-0.18,0.025-0.256,0.068v-0.321c0.081-0.027,0.167-0.042,0.256-0.042c0.451,0,0.817,0.366,0.817,0.817
			c0,0.451-0.366,0.817-0.817,0.817C7.863,15.396,7.497,15.031,7.497,14.58z"/>
		<path opacity="0.6" fill="#FFFFFF" d="M13.873,10.445c-0.058,0-0.105-0.051-0.105-0.114l0,0c0-0.063,0.047-0.114,0.105-0.114
			h0.618c0.058,0,0.105,0.051,0.105,0.114l0,0c0,0.063-0.047,0.114-0.105,0.114H13.873z"/>
		<path opacity="0.8" fill="#FFFFFF" d="M22.8,16.618v-5.963c0-4.262-3.455-7.716-7.716-7.716H7.14c-5.665,0-5.94,5.436-5.94,7.716
			v1.889c0,2.172,1.119,8.517,7.027,8.517h10.183C20.494,21.061,22.8,19.048,22.8,16.618z M15.673,4.015
			c0-0.214,0.173-0.387,0.387-0.387c0.214,0,0.387,0.173,0.387,0.387s-0.173,0.387-0.387,0.387
			C15.846,4.402,15.673,4.229,15.673,4.015z M13.873,10.445c-0.058,0-0.105-0.051-0.105-0.114c0-0.063,0.047-0.114,0.105-0.114
			h0.618c0.058,0,0.105,0.051,0.105,0.114c0,0.063-0.047,0.114-0.105,0.114H13.873z M14.991,10.382c0-0.91,0.74-1.65,1.65-1.65
			c0.91,0,1.65,0.74,1.65,1.65c0,0.91-0.74,1.65-1.65,1.65C15.731,12.032,14.991,11.292,14.991,10.382z M1.899,12.007
			c0-0.214,0.173-0.387,0.387-0.387c0.214,0,0.387,0.173,0.387,0.387c0,0.214-0.173,0.387-0.387,0.387
			C2.072,12.394,1.899,12.221,1.899,12.007z M7.497,14.58c0-0.187,0.063-0.358,0.169-0.496v-0.36
			c-0.106-0.137-0.169-0.309-0.169-0.496c0-0.451,0.366-0.817,0.817-0.817c0.451,0,0.817,0.366,0.817,0.817
			c0,0.245-0.109,0.465-0.28,0.614c-0.117-0.086-0.279-0.149-0.406-0.165c-0.054-0.007-0.326-0.521-0.326-0.521
			s0.126-0.054,0.212-0.017c0.103,0.045,0.301,0.373,0.362,0.378c0.061,0.006,0.143-0.147,0.143-0.289
			c0-0.288-0.233-0.521-0.521-0.521c-0.288,0-0.521,0.233-0.521,0.521c0,0.143,0.058,0.272,0.151,0.366v0.052v0.206v0.102v0.198
			v0.06c-0.093,0.094-0.151,0.224-0.151,0.366c0,0.288,0.233,0.521,0.521,0.521c0.288,0,0.521-0.233,0.521-0.521
			c0-0.288-0.233-0.521-0.521-0.521c-0.093,0-0.18,0.025-0.256,0.068v-0.321c0.081-0.027,0.167-0.042,0.256-0.042
			c0.451,0,0.817,0.366,0.817,0.817c0,0.451-0.366,0.817-0.817,0.817C7.863,15.396,7.497,15.031,7.497,14.58z M15.673,20.059
			c0-0.214,0.173-0.387,0.387-0.387c0.214,0,0.387,0.173,0.387,0.387s-0.173,0.387-0.387,0.387
			C15.846,20.446,15.673,20.273,15.673,20.059z"/>
	</g>
</g>
</svg>
 */