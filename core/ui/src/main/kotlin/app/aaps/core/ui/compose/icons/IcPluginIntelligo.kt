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
 * Icon for Intelligo CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
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

@Preview(showBackground = true)
@Composable
private fun IcPluginIntelligoPreview() {
    Icon(
        imageVector = IcPluginIntelligo,
        contentDescription = "Intelligo Plugin Icon",
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
<g id="Plugin_intelligo">

		<text transform="matrix(4.489659e-011 -1 1 4.489659e-011 11.9999 20.486)" display="none" font-family="'Arial-BoldMT'" font-size="4.0173">intelliGO</text>
	<g>
		<path fill="#FFFFFF" d="M17.784,7.309h-1.538V5.874h1.178c-0.647-2.123-1.832-4.078-4-4.078h-3.339
			c-1.945,0-3.008,1.955-3.589,4.078h1.258v1.435H6.173c-0.366,2.011-0.376,3.81-0.376,3.851v1.68c0,0.094,0.058,9.364,4.78,9.364
			h2.847c4.726,0,4.78-9.271,4.78-9.364v-1.68C18.204,11.119,18.193,9.319,17.784,7.309z M9.634,20.198h-0.51v-0.552h0.51V20.198z
			 M12,11.388v0.551H9.125v-0.551H12z M9.125,10.823v-0.551h0.51v0.551H9.125z M9.917,10.823v-0.551H12v0.551H9.917z M12,12.505
			v0.552H9.125v-0.552H12z M12,20.198H9.917v-0.552H12V20.198z M12,17.738h-1.063c-0.225,0-0.371,0.012-0.437,0.035
			s-0.118,0.062-0.154,0.114c-0.037,0.054-0.055,0.117-0.055,0.191c0,0.096,0.026,0.182,0.079,0.257
			c0.052,0.076,0.122,0.128,0.208,0.156c0.086,0.028,0.246,0.042,0.479,0.042H12v0.552H9.917v-0.513h0.306
			c-0.235-0.182-0.353-0.41-0.353-0.687c0-0.121,0.022-0.232,0.066-0.333s0.1-0.177,0.167-0.229
			c0.068-0.052,0.146-0.088,0.231-0.107c0.086-0.021,0.21-0.031,0.371-0.031H12V17.738z M11.997,16.331
			c-0.033,0.08-0.077,0.139-0.129,0.176c-0.053,0.037-0.125,0.063-0.215,0.078c-0.064,0.012-0.193,0.018-0.388,0.018h-0.908v0.253
			H9.917v-0.253H9.503l-0.322-0.554h0.736v-0.376h0.439v0.376h0.839c0.17,0,0.269-0.003,0.297-0.011
			c0.028-0.007,0.051-0.023,0.069-0.049c0.019-0.025,0.027-0.057,0.027-0.093c0-0.052-0.018-0.125-0.053-0.222l0.428-0.048
			c0.055,0.129,0.082,0.273,0.082,0.436C12.047,16.162,12.03,16.252,11.997,16.331z M11.692,15.245
			c-0.189,0.137-0.429,0.205-0.718,0.205c-0.345,0-0.616-0.09-0.811-0.271c-0.196-0.181-0.293-0.408-0.293-0.685
			c0-0.31,0.103-0.555,0.307-0.733s0.518-0.265,0.94-0.257v1.381c0.164-0.004,0.291-0.049,0.382-0.134
			c0.091-0.085,0.136-0.19,0.136-0.317c0-0.086-0.023-0.159-0.071-0.218c-0.047-0.059-0.123-0.104-0.228-0.134l0.092-0.549
			c0.201,0.07,0.354,0.182,0.46,0.334s0.158,0.344,0.158,0.572C12.047,14.803,11.929,15.07,11.692,15.245z M11.86,9.13
			c-0.126,0.224-0.307,0.392-0.542,0.504s-0.49,0.168-0.766,0.168c-0.299,0-0.565-0.063-0.798-0.188
			C9.521,9.49,9.343,9.306,9.219,9.064C9.123,8.879,9.075,8.65,9.075,8.375c0-0.357,0.075-0.636,0.225-0.837
			c0.15-0.201,0.357-0.33,0.621-0.387l0.108,0.577C9.887,7.769,9.776,7.845,9.694,7.957C9.612,8.068,9.572,8.208,9.572,8.375
			c0,0.253,0.08,0.455,0.241,0.605c0.161,0.15,0.399,0.225,0.716,0.225c0.341,0,0.597-0.076,0.768-0.228
			c0.17-0.152,0.256-0.351,0.256-0.596c0-0.122-0.024-0.244-0.072-0.366c-0.047-0.123-0.105-0.228-0.173-0.315h-0.365v0.665h-0.485
			V7.114h1.146c0.118,0.122,0.221,0.298,0.311,0.528c0.09,0.231,0.134,0.465,0.134,0.701C12.049,8.645,11.986,8.907,11.86,9.13z
			 M11.658,6.317c-0.261,0.252-0.62,0.378-1.078,0.378c-0.292,0-0.539-0.043-0.737-0.131C9.696,6.499,9.564,6.41,9.448,6.297
			C9.332,6.184,9.245,6.06,9.189,5.925C9.113,5.746,9.075,5.54,9.075,5.305c0-0.424,0.131-0.763,0.395-1.017
			c0.263-0.254,0.628-0.381,1.096-0.381c0.464,0,0.828,0.126,1.09,0.378s0.393,0.59,0.393,1.012
			C12.049,5.725,11.918,6.065,11.658,6.317z"/>
		<path fill="#FFFFFF" d="M10.292,14.462c0,0.12,0.044,0.22,0.131,0.298c0.088,0.079,0.207,0.117,0.357,0.116v-0.824
			c-0.159,0.004-0.281,0.045-0.364,0.124C10.333,14.254,10.292,14.35,10.292,14.462z"/>
		<path fill="#FFFFFF" d="M10.552,4.507c-0.33,0-0.575,0.072-0.737,0.217S9.572,5.06,9.572,5.299s0.082,0.432,0.246,0.579
			s0.412,0.22,0.742,0.22c0.326,0,0.573-0.075,0.741-0.226s0.252-0.341,0.252-0.573s-0.083-0.421-0.25-0.57
			C11.136,4.581,10.886,4.507,10.552,4.507z"/>
		<path opacity="0.1" fill="#FFFFFF" d="M5.201,11.16v1.68c0,0.407,0.061,9.96,5.376,9.96h2.847c5.315,0,5.376-9.553,5.376-9.96
			v-1.68c0-0.407-0.061-9.96-5.376-9.96h-3.339C5.257,1.2,5.201,10.753,5.201,11.16z M13.423,1.795c2.168,0,3.353,1.955,4,4.078
			h-1.178v1.435h1.538c0.409,2.011,0.42,3.81,0.42,3.851v1.68c0,0.094-0.055,9.364-4.78,9.364h-2.847
			c-4.722,0-4.78-9.271-4.78-9.364v-1.68c0-0.041,0.01-1.84,0.376-3.851h1.581V5.874H6.496c0.581-2.123,1.643-4.078,3.589-4.078
			H13.423z"/>
	</g>
</g>
</svg>
 */