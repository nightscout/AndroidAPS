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
 * Icon for Glunovo CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginGlunovo: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginGlunovo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
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
            horizontalLineTo(13.423f)
            close()
        }

        // Small path (ellipse-like)
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
            moveTo(11.761f, 6.383f)
            curveToRelative(0f, 0.14f, 0.054f, 0.258f, 0.162f, 0.354f)
            curveToRelative(0.108f, 0.096f, 0.263f, 0.143f, 0.466f, 0.143f)
            curveToRelative(0.203f, 0f, 0.358f, -0.048f, 0.466f, -0.143f)
            curveToRelative(0.108f, -0.096f, 0.162f, -0.213f, 0.162f, -0.354f)
            curveToRelative(0f, -0.14f, -0.054f, -0.258f, -0.162f, -0.352f)
            curveToRelative(-0.108f, -0.095f, -0.265f, -0.142f, -0.47f, -0.142f)
            curveToRelative(-0.2f, 0f, -0.354f, 0.047f, -0.462f, 0.142f)
            curveToRelative(-0.108f, 0.095f, -0.162f, 0.213f, -0.162f, 0.352f)
            close()
        }

        // Main detailed path (rest of the icons)
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
            moveTo(6.496f, 5.874f)
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
            horizontalLineToRelative(-1.538f)
            verticalLineTo(5.874f)
            horizontalLineToRelative(1.178f)
            curveToRelative(-0.647f, -2.123f, -1.832f, -4.078f, -4f, -4.078f)
            horizontalLineToRelative(-3.339f)
            curveTo(8.139f, 1.795f, 7.077f, 3.75f, 6.496f, 5.874f)
            close()
            moveTo(13.023f, 17.226f)
            curveToRelative(0.116f, 0.118f, 0.217f, 0.29f, 0.305f, 0.515f)
            curveToRelative(0.088f, 0.225f, 0.132f, 0.453f, 0.132f, 0.683f)
            curveToRelative(0f, 0.293f, -0.062f, 0.548f, -0.186f, 0.766f)
            curveToRelative(-0.124f, 0.218f, -0.301f, 0.382f, -0.532f, 0.491f)
            curveToRelative(-0.231f, 0.11f, -0.481f, 0.164f, -0.752f, 0.164f)
            curveToRelative(-0.294f, 0f, -0.556f, -0.061f, -0.784f, -0.183f)
            curveToRelative(-0.229f, -0.122f, -0.404f, -0.301f, -0.526f, -0.537f)
            curveToRelative(-0.094f, -0.18f, -0.141f, -0.403f, -0.141f, -0.671f)
            curveToRelative(0f, -0.348f, 0.074f, -0.619f, 0.221f, -0.815f)
            curveToRelative(0.147f, -0.196f, 0.35f, -0.321f, 0.61f, -0.377f)
            lineToRelative(0.106f, 0.562f)
            curveToRelative(-0.139f, 0.039f, -0.248f, 0.114f, -0.328f, 0.223f)
            curveToRelative(-0.08f, 0.109f, -0.12f, 0.245f, -0.12f, 0.408f)
            curveToRelative(0f, 0.247f, 0.079f, 0.444f, 0.237f, 0.589f)
            curveToRelative(0.158f, 0.146f, 0.392f, 0.219f, 0.703f, 0.219f)
            curveToRelative(0.335f, 0f, 0.587f, -0.074f, 0.754f, -0.222f)
            curveToRelative(0.168f, -0.148f, 0.251f, -0.341f, 0.251f, -0.581f)
            curveToRelative(0f, -0.118f, -0.023f, -0.237f, -0.07f, -0.356f)
            curveToRelative(-0.047f, -0.119f, -0.104f, -0.221f, -0.171f, -0.307f)
            horizontalLineToRelative(-0.358f)
            verticalLineToRelative(0.648f)
            horizontalLineToRelative(-0.476f)
            verticalLineToRelative(-1.219f)
            horizontalLineTo(13.023f)
            close()
            moveTo(13.412f, 16.397f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-2.824f)
            verticalLineToRelative(-0.537f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.412f, 14.196f)
            verticalLineToRelative(0.499f)
            horizontalLineToRelative(-0.306f)
            curveToRelative(0.109f, 0.074f, 0.195f, 0.171f, 0.258f, 0.291f)
            curveToRelative(0.063f, 0.12f, 0.094f, 0.248f, 0.094f, 0.381f)
            curveToRelative(0f, 0.136f, -0.03f, 0.259f, -0.091f, 0.367f)
            curveToRelative(-0.06f, 0.108f, -0.145f, 0.187f, -0.254f, 0.235f)
            curveToRelative(-0.109f, 0.048f, -0.26f, 0.073f, -0.453f, 0.073f)
            horizontalLineToRelative(-1.295f)
            verticalLineToRelative(-0.537f)
            horizontalLineToRelative(0.94f)
            curveToRelative(0.288f, 0f, 0.464f, -0.01f, 0.529f, -0.03f)
            curveToRelative(0.065f, -0.02f, 0.116f, -0.056f, 0.154f, -0.108f)
            curveToRelative(0.038f, -0.052f, 0.057f, -0.118f, 0.057f, -0.199f)
            curveToRelative(0f, -0.092f, -0.025f, -0.174f, -0.076f, -0.247f)
            curveToRelative(-0.051f, -0.073f, -0.114f, -0.122f, -0.189f, -0.149f)
            curveToRelative(-0.075f, -0.027f, -0.259f, -0.04f, -0.552f, -0.04f)
            horizontalLineToRelative(-0.863f)
            verticalLineToRelative(-0.537f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.412f, 12f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-1.044f)
            curveToRelative(-0.221f, 0f, -0.364f, 0.011f, -0.429f, 0.034f)
            curveToRelative(-0.065f, 0.023f, -0.115f, 0.06f, -0.151f, 0.112f)
            curveToRelative(-0.036f, 0.052f, -0.054f, 0.114f, -0.054f, 0.186f)
            curveToRelative(0f, 0.093f, 0.026f, 0.176f, 0.077f, 0.25f)
            curveToRelative(0.051f, 0.074f, 0.119f, 0.125f, 0.204f, 0.152f)
            curveToRelative(0.085f, 0.027f, 0.242f, 0.041f, 0.47f, 0.041f)
            horizontalLineToRelative(0.927f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-2.046f)
            verticalLineTo(13.35f)
            horizontalLineToRelative(0.301f)
            curveToRelative(-0.231f, -0.177f, -0.347f, -0.4f, -0.347f, -0.669f)
            curveToRelative(0f, -0.118f, 0.022f, -0.227f, 0.065f, -0.325f)
            curveToRelative(0.043f, -0.098f, 0.098f, -0.172f, 0.165f, -0.222f)
            curveToRelative(0.067f, -0.05f, 0.142f, -0.085f, 0.227f, -0.105f)
            curveToRelative(0.085f, -0.02f, 0.206f, -0.03f, 0.364f, -0.03f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.153f, 9.889f)
            curveToRelative(0.204f, 0.198f, 0.305f, 0.447f, 0.305f, 0.748f)
            curveToRelative(0f, 0.186f, -0.042f, 0.363f, -0.127f, 0.532f)
            curveToRelative(-0.085f, 0.169f, -0.209f, 0.297f, -0.373f, 0.385f)
            curveToRelative(-0.164f, 0.088f, -0.363f, 0.132f, -0.598f, 0.132f)
            curveToRelative(-0.18f, 0f, -0.354f, -0.044f, -0.522f, -0.132f)
            curveToRelative(-0.168f, -0.088f, -0.297f, -0.212f, -0.385f, -0.373f)
            curveToRelative(-0.089f, -0.161f, -0.133f, -0.341f, -0.133f, -0.54f)
            curveToRelative(0f, -0.307f, 0.101f, -0.559f, 0.302f, -0.755f)
            curveToRelative(0.061f, -0.059f, 0.126f, -0.109f, 0.197f, -0.151f)
            curveToRelative(0.106f, -0.062f, 0.198f, -0.043f, 0.255f, 0.014f)
            curveToRelative(0.057f, 0.057f, 0.073f, 0.15f, 0.04f, 0.226f)
            curveToRelative(-0.046f, 0.106f, -0.154f, 0.109f, -0.154f, 0.109f)
            horizontalLineToRelative(-0.15f)
            verticalLineToRelative(0.454f)
            horizontalLineToRelative(0.361f)
            verticalLineToRelative(0.36f)
            horizontalLineToRelative(0.437f)
            verticalLineToRelative(-0.36f)
            horizontalLineToRelative(0.361f)
            verticalLineToRelative(-0.454f)
            horizontalLineToRelative(-0.361f)
            verticalLineTo(9.611f)
            curveToRelative(0.111f, 0.037f, 0.293f, 0.13f, 0.445f, 0.278f)
            close()
            moveTo(13.412f, 8.249f)
            verticalLineToRelative(0.483f)
            lineToRelative(-2.046f, 0.818f)
            verticalLineTo(8.987f)
            lineToRelative(1.393f, -0.493f)
            lineToRelative(-1.393f, -0.499f)
            verticalLineTo(7.443f)
            lineToRelative(2.046f, 0.806f)
            close()
            moveTo(13.153f, 5.633f)
            curveToRelative(0.204f, 0.198f, 0.305f, 0.447f, 0.305f, 0.748f)
            curveToRelative(0f, 0.186f, -0.042f, 0.363f, -0.127f, 0.532f)
            curveToRelative(-0.085f, 0.169f, -0.209f, 0.297f, -0.373f, 0.385f)
            curveToRelative(-0.164f, 0.088f, -0.363f, 0.132f, -0.598f, 0.132f)
            curveToRelative(-0.18f, 0f, -0.354f, -0.044f, -0.522f, -0.132f)
            curveToRelative(-0.168f, -0.088f, -0.297f, -0.212f, -0.385f, -0.373f)
            curveToRelative(-0.089f, -0.161f, -0.133f, -0.341f, -0.133f, -0.54f)
            curveToRelative(0f, -0.307f, 0.101f, -0.559f, 0.302f, -0.755f)
            curveToRelative(0.201f, -0.196f, 0.455f, -0.294f, 0.762f, -0.294f)
            curveToRelative(0.311f, 0f, 0.568f, 0.099f, 0.771f, 0.297f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginGlunovoPreview() {
    Icon(
        imageVector = IcPluginGlunovo,
        contentDescription = "Glunovo Plugin Icon",
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
<g id="Plugin_Glunovo">
	<g>
		<path opacity="0.1" fill="#FFFFFF" d="M5.201,11.16v1.68c0,0.407,0.061,9.96,5.376,9.96h2.847c5.315,0,5.376-9.553,5.376-9.96
			v-1.68c0-0.407-0.061-9.96-5.376-9.96h-3.339C5.257,1.2,5.201,10.753,5.201,11.16z M13.423,1.795c2.168,0,3.353,1.955,4,4.078
			h-1.178v1.435h1.538c0.409,2.011,0.42,3.81,0.42,3.851v1.68c0,0.094-0.055,9.364-4.78,9.364h-2.847
			c-4.722,0-4.78-9.271-4.78-9.364v-1.68c0-0.041,0.01-1.84,0.376-3.851h1.581V5.874H6.496c0.581-2.123,1.643-4.078,3.589-4.078
			H13.423z"/>
		<g>
			<path fill="#FFFFFF" d="M11.761,6.383c0,0.14,0.054,0.258,0.162,0.354c0.108,0.096,0.263,0.143,0.466,0.143
				c0.203,0,0.358-0.048,0.466-0.143c0.108-0.096,0.162-0.213,0.162-0.354c0-0.14-0.054-0.258-0.162-0.352
				c-0.108-0.095-0.265-0.142-0.47-0.142c-0.2,0-0.354,0.047-0.462,0.142C11.815,6.125,11.761,6.243,11.761,6.383z"/>
			<path fill="#FFFFFF" d="M6.496,5.874h1.258v1.435H6.173c-0.366,2.011-0.376,3.81-0.376,3.851v1.68
				c0,0.094,0.058,9.364,4.78,9.364h2.847c4.726,0,4.78-9.271,4.78-9.364v-1.68c0-0.041-0.011-1.84-0.42-3.851h-1.538V5.874h1.178
				c-0.647-2.123-1.832-4.078-4-4.078h-3.339C8.139,1.795,7.077,3.75,6.496,5.874z M13.023,17.226
				c0.116,0.118,0.217,0.29,0.305,0.515c0.088,0.225,0.132,0.453,0.132,0.683c0,0.293-0.062,0.548-0.186,0.766
				c-0.124,0.218-0.301,0.382-0.532,0.491c-0.231,0.11-0.481,0.164-0.752,0.164c-0.294,0-0.556-0.061-0.784-0.183
				c-0.229-0.122-0.404-0.301-0.526-0.537c-0.094-0.18-0.141-0.403-0.141-0.671c0-0.348,0.074-0.619,0.221-0.815
				c0.147-0.196,0.35-0.321,0.61-0.377l0.106,0.562c-0.139,0.039-0.248,0.114-0.328,0.223c-0.08,0.109-0.12,0.245-0.12,0.408
				c0,0.247,0.079,0.444,0.237,0.589c0.158,0.146,0.392,0.219,0.703,0.219c0.335,0,0.587-0.074,0.754-0.222
				c0.168-0.148,0.251-0.341,0.251-0.581c0-0.118-0.023-0.237-0.07-0.356c-0.047-0.119-0.104-0.221-0.171-0.307h-0.358v0.648h-0.476
				v-1.219H13.023z M13.412,16.397v0.537h-2.824v-0.537H13.412z M13.412,14.196v0.499h-0.306c0.109,0.074,0.195,0.171,0.258,0.291
				c0.063,0.12,0.094,0.248,0.094,0.381c0,0.136-0.03,0.259-0.091,0.367c-0.06,0.108-0.145,0.187-0.254,0.235
				c-0.109,0.048-0.26,0.073-0.453,0.073h-1.295v-0.537h0.94c0.288,0,0.464-0.01,0.529-0.03c0.065-0.02,0.116-0.056,0.154-0.108
				c0.038-0.052,0.057-0.118,0.057-0.199c0-0.092-0.025-0.174-0.076-0.247c-0.051-0.073-0.114-0.122-0.189-0.149
				c-0.075-0.027-0.259-0.04-0.552-0.04h-0.863v-0.537H13.412z M13.412,12v0.537h-1.044c-0.221,0-0.364,0.011-0.429,0.034
				c-0.065,0.023-0.115,0.06-0.151,0.112c-0.036,0.052-0.054,0.114-0.054,0.186c0,0.093,0.026,0.176,0.077,0.25
				c0.051,0.074,0.119,0.125,0.204,0.152c0.085,0.027,0.242,0.041,0.47,0.041h0.927v0.537h-2.046V13.35h0.301
				c-0.231-0.177-0.347-0.4-0.347-0.669c0-0.118,0.022-0.227,0.065-0.325c0.043-0.098,0.098-0.172,0.165-0.222
				c0.067-0.05,0.142-0.085,0.227-0.105c0.085-0.02,0.206-0.03,0.364-0.03H13.412z M13.153,9.889
				c0.204,0.198,0.305,0.447,0.305,0.748c0,0.186-0.042,0.363-0.127,0.532c-0.085,0.169-0.209,0.297-0.373,0.385
				c-0.164,0.088-0.363,0.132-0.598,0.132c-0.18,0-0.354-0.044-0.522-0.132c-0.168-0.088-0.297-0.212-0.385-0.373
				c-0.089-0.161-0.133-0.341-0.133-0.54c0-0.307,0.101-0.559,0.302-0.755c0.061-0.059,0.126-0.109,0.197-0.151
				c0.106-0.062,0.198-0.043,0.255,0.014c0.057,0.057,0.073,0.15,0.04,0.226c-0.046,0.106-0.154,0.109-0.154,0.109h-0.15v0.454
				h0.361v0.36h0.437v-0.36h0.361v-0.454h-0.361V9.611C12.819,9.648,13.001,9.741,13.153,9.889z M13.412,8.249v0.483L11.366,9.55
				V8.987l1.393-0.493l-1.393-0.499V7.443L13.412,8.249z M13.153,5.633c0.204,0.198,0.305,0.447,0.305,0.748
				c0,0.186-0.042,0.363-0.127,0.532c-0.085,0.169-0.209,0.297-0.373,0.385C12.795,7.386,12.595,7.43,12.36,7.43
				c-0.18,0-0.354-0.044-0.522-0.132c-0.168-0.088-0.297-0.212-0.385-0.373c-0.089-0.161-0.133-0.341-0.133-0.54
				c0-0.307,0.101-0.559,0.302-0.755c0.201-0.196,0.455-0.294,0.762-0.294C12.693,5.336,12.95,5.435,13.153,5.633z"/>
		</g>
	</g>
</g>
</svg>
 */