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
 * Icon for Glimp Plugin.
 * Represents Glimp CGM integration.
 *
 * replacing ic_glimp
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginGlimp: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginGlimp",
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
            moveTo(14.633f, 15.754f)
            lineToRelative(-0.574f, 2.029f)
            curveToRelative(-0.067f, 0.235f, -0.282f, 0.395f, -0.526f, 0.392f)
            curveToRelative(-0.243f, -0.004f, -0.454f, -0.171f, -0.513f, -0.408f)
            lineToRelative(-0.697f, -2.789f)
            lineToRelative(-0.567f, 2.276f)
            curveToRelative(0.64f, 2.58f, 2.965f, 4.492f, 5.735f, 4.492f)
            curveToRelative(2.331f, 0f, 4.346f, -1.354f, 5.308f, -3.32f)
            verticalLineToRelative(-2.671f)
            horizontalLineTo(14.633f)
            close()

            moveTo(12.844f, 12.624f)
            lineToRelative(0.73f, 2.922f)
            lineToRelative(0.136f, -0.479f)
            curveToRelative(0.066f, -0.232f, 0.277f, -0.392f, 0.517f, -0.392f)
            horizontalLineTo(22.8f)
            verticalLineToRelative(-1.56f)
            curveToRelative(-1.195f, -3.18f, -3.877f, -6.988f, -5.308f, -8.423f)
            curveToRelative(-1.341f, 1.345f, -3.666f, 4.604f, -4.965f, 7.566f)
            curveTo(12.681f, 12.323f, 12.801f, 12.455f, 12.844f, 12.624f)
            close()

            moveTo(12.527f, 12.259f)
            curveToRelative(-0.064f, -0.026f, -0.133f, -0.042f, -0.205f, -0.042f)
            curveToRelative(0f, 0f, 0f, 0f, 0f, 0f)
            curveToRelative(-0.247f, 0f, -0.462f, 0.169f, -0.521f, 0.409f)
            lineToRelative(-1.32f, 5.301f)
            lineTo(8.52f, 10.134f)
            curveToRelative(-0.06f, -0.239f, -0.275f, -0.407f, -0.521f, -0.407f)
            curveToRelative(0f, 0f, -0.001f, 0f, -0.001f, 0f)
            curveToRelative(-0.247f, 0.001f, -0.461f, 0.169f, -0.521f, 0.409f)
            lineToRelative(-1.325f, 5.36f)
            lineToRelative(-0.724f, -2.873f)
            curveToRelative(-0.06f, -0.239f, -0.275f, -0.407f, -0.521f, -0.407f)
            curveToRelative(-0.002f, 0f, -0.003f, 0f, -0.005f, 0f)
            curveToRelative(-0.248f, 0.002f, -0.462f, 0.175f, -0.518f, 0.417f)
            lineToRelative(-0.474f, 2.042f)
            horizontalLineTo(1.2f)
            verticalLineToRelative(1.078f)
            horizontalLineToRelative(3.135f)
            curveToRelative(0.25f, 0f, 0.467f, -0.173f, 0.524f, -0.417f)
            lineToRelative(0.069f, -0.296f)
            lineToRelative(0.708f, 2.807f)
            curveToRelative(0.06f, 0.239f, 0.275f, 0.407f, 0.521f, 0.407f)
            curveToRelative(0f, 0f, 0.001f, 0f, 0.001f, 0f)
            curveToRelative(0.247f, -0.001f, 0.461f, -0.169f, 0.521f, -0.409f)
            lineToRelative(1.325f, -5.358f)
            lineToRelative(1.959f, 7.784f)
            curveToRelative(0.06f, 0.239f, 0.275f, 0.407f, 0.521f, 0.407f)
            curveToRelative(0f, 0f, 0f, 0f, 0.001f, 0f)
            curveToRelative(0.246f, 0f, 0.461f, -0.169f, 0.521f, -0.409f)
            lineToRelative(0.751f, -3.018f)
            curveToRelative(-0.114f, -0.46f, -0.176f, -0.941f, -0.176f, -1.436f)
            curveTo(11.581f, 14.815f, 11.955f, 13.565f, 12.527f, 12.259f)
            close()

            moveTo(14.614f, 5.812f)
            curveToRelative(-0.083f, -0.232f, -0.206f, -0.412f, -0.37f, -0.54f)
            curveToRelative(-0.163f, -0.127f, -0.351f, -0.191f, -0.565f, -0.191f)
            curveToRelative(-0.163f, 0f, -0.302f, 0.037f, -0.417f, 0.111f)
            curveToRelative(-0.114f, 0.074f, -0.217f, 0.185f, -0.308f, 0.332f)
            verticalLineTo(5.147f)
            horizontalLineToRelative(-0.386f)
            verticalLineToRelative(4.015f)
            horizontalLineToRelative(0.424f)
            verticalLineTo(7.749f)
            curveToRelative(0.072f, 0.106f, 0.165f, 0.193f, 0.277f, 0.262f)
            curveToRelative(0.112f, 0.069f, 0.239f, 0.104f, 0.38f, 0.104f)
            curveToRelative(0.193f, 0f, 0.377f, -0.063f, 0.552f, -0.19f)
            curveToRelative(0.175f, -0.127f, 0.308f, -0.309f, 0.4f, -0.547f)
            curveToRelative(0.092f, -0.238f, 0.138f, -0.506f, 0.138f, -0.802f)
            curveTo(14.739f, 6.299f, 14.697f, 6.045f, 14.614f, 5.812f)
            close()

            moveTo(14.105f, 7.433f)
            curveToRelative(-0.134f, 0.185f, -0.295f, 0.278f, -0.484f, 0.278f)
            curveToRelative(-0.185f, 0f, -0.343f, -0.089f, -0.473f, -0.268f)
            curveToRelative(-0.13f, -0.178f, -0.195f, -0.454f, -0.195f, -0.828f)
            curveToRelative(0f, -0.373f, 0.069f, -0.658f, 0.208f, -0.854f)
            curveTo(13.3f, 5.565f, 13.46f, 5.467f, 13.64f, 5.467f)
            curveToRelative(0.182f, 0f, 0.339f, 0.092f, 0.47f, 0.276f)
            curveToRelative(0.131f, 0.184f, 0.196f, 0.461f, 0.196f, 0.831f)
            curveTo(14.306f, 6.962f, 14.239f, 7.248f, 14.105f, 7.433f)
            close()

            moveTo(11.169f, 5.081f)
            curveToRelative(-0.317f, 0f, -0.574f, 0.169f, -0.772f, 0.508f)
            curveToRelative(-0.049f, -0.16f, -0.132f, -0.285f, -0.251f, -0.374f)
            curveToRelative(-0.118f, -0.089f, -0.27f, -0.134f, -0.455f, -0.134f)
            curveToRelative(-0.166f, 0f, -0.315f, 0.043f, -0.445f, 0.13f)
            curveToRelative(-0.13f, 0.086f, -0.235f, 0.201f, -0.313f, 0.343f)
            verticalLineTo(5.147f)
            horizontalLineTo(8.554f)
            verticalLineTo(8.05f)
            horizontalLineToRelative(0.424f)
            verticalLineTo(6.543f)
            curveToRelative(0f, -0.264f, 0.023f, -0.466f, 0.069f, -0.607f)
            curveToRelative(0.045f, -0.14f, 0.119f, -0.247f, 0.22f, -0.32f)
            curveToRelative(0.101f, -0.073f, 0.212f, -0.109f, 0.331f, -0.109f)
            curveToRelative(0.157f, 0f, 0.27f, 0.055f, 0.339f, 0.164f)
            curveToRelative(0.069f, 0.109f, 0.103f, 0.273f, 0.103f, 0.492f)
            verticalLineTo(8.05f)
            horizontalLineToRelative(0.424f)
            verticalLineTo(6.363f)
            curveToRelative(0f, -0.299f, 0.058f, -0.516f, 0.174f, -0.652f)
            curveToRelative(0.116f, -0.136f, 0.262f, -0.204f, 0.438f, -0.204f)
            curveToRelative(0.097f, 0f, 0.182f, 0.026f, 0.254f, 0.079f)
            curveToRelative(0.072f, 0.053f, 0.122f, 0.122f, 0.149f, 0.209f)
            curveToRelative(0.028f, 0.086f, 0.041f, 0.228f, 0.041f, 0.425f)
            verticalLineTo(8.05f)
            horizontalLineToRelative(0.421f)
            verticalLineTo(6.057f)
            curveToRelative(0f, -0.332f, -0.067f, -0.577f, -0.2f, -0.737f)
            curveTo(11.608f, 5.161f, 11.417f, 5.081f, 11.169f, 5.081f)
            close()

            moveTo(7.486f, 5.147f)
            horizontalLineToRelative(0.424f)
            verticalLineToRelative(2.903f)
            horizontalLineTo(7.486f)
            verticalLineTo(5.147f)
            close()

            moveTo(6.403f, 4.043f)
            horizontalLineToRelative(0.424f)
            verticalLineToRelative(4.007f)
            horizontalLineTo(6.403f)
            verticalLineTo(4.043f)
            close()

            moveTo(7.486f, 4.043f)
            horizontalLineToRelative(0.424f)
            verticalLineToRelative(0.566f)
            horizontalLineTo(7.486f)
            verticalLineTo(4.043f)
            close()

            moveTo(4.332f, 6.478f)
            horizontalLineToRelative(1.014f)
            verticalLineToRelative(0.746f)
            curveToRelative(-0.097f, 0.1f, -0.24f, 0.195f, -0.428f, 0.283f)
            curveTo(4.73f, 7.596f, 4.537f, 7.64f, 4.34f, 7.64f)
            curveToRelative(-0.228f, 0f, -0.442f, -0.058f, -0.645f, -0.175f)
            curveTo(3.492f, 7.348f, 3.338f, 7.169f, 3.232f, 6.929f)
            curveTo(3.126f, 6.689f, 3.074f, 6.39f, 3.074f, 6.033f)
            curveToRelative(0f, -0.29f, 0.044f, -0.558f, 0.132f, -0.806f)
            curveTo(3.257f, 5.082f, 3.33f, 4.95f, 3.423f, 4.83f)
            curveToRelative(0.093f, -0.12f, 0.216f, -0.218f, 0.368f, -0.291f)
            curveToRelative(0.152f, -0.073f, 0.333f, -0.111f, 0.541f, -0.111f)
            curveToRelative(0.174f, 0f, 0.333f, 0.035f, 0.475f, 0.105f)
            curveToRelative(0.143f, 0.07f, 0.253f, 0.164f, 0.33f, 0.28f)
            curveTo(5.214f, 4.93f, 5.279f, 5.09f, 5.33f, 5.294f)
            lineToRelative(0.412f, -0.131f)
            curveTo(5.683f, 4.894f, 5.595f, 4.675f, 5.481f, 4.507f)
            curveTo(5.367f, 4.34f, 5.208f, 4.209f, 5.007f, 4.115f)
            curveTo(4.805f, 4.021f, 4.58f, 3.974f, 4.33f, 3.974f)
            curveToRelative(-0.344f, 0f, -0.647f, 0.082f, -0.91f, 0.246f)
            curveTo(3.156f, 4.384f, 2.954f, 4.635f, 2.813f, 4.973f)
            curveToRelative(-0.14f, 0.338f, -0.21f, 0.702f, -0.21f, 1.092f)
            curveToRelative(0f, 0.393f, 0.07f, 0.75f, 0.212f, 1.069f)
            curveToRelative(0.141f, 0.319f, 0.35f, 0.563f, 0.627f, 0.731f)
            curveToRelative(0.277f, 0.168f, 0.585f, 0.253f, 0.923f, 0.253f)
            curveToRelative(0.251f, 0f, 0.496f, -0.053f, 0.734f, -0.157f)
            curveTo(5.338f, 7.856f, 5.57f, 7.7f, 5.794f, 7.492f)
            verticalLineTo(6.005f)
            lineTo(4.332f, 6.008f)
            verticalLineTo(6.478f)
            close()

            moveTo(18.898f, 1.625f)
            curveToRelative(1.917f, 0f, 3.476f, 1.56f, 3.476f, 3.476f)
            verticalLineToRelative(13.796f)
            curveToRelative(0f, 1.917f, -1.56f, 3.476f, -3.476f, 3.476f)
            horizontalLineTo(5.102f)
            curveToRelative(-1.917f, 0f, -3.476f, -1.56f, -3.476f, -3.476f)
            verticalLineTo(5.102f)
            curveToRelative(0f, -1.917f, 1.56f, -3.476f, 3.476f, -3.476f)
            horizontalLineTo(18.898f)
            close()

            moveTo(18.898f, 1.2f)
            horizontalLineTo(5.102f)
            curveTo(2.947f, 1.2f, 1.2f, 2.947f, 1.2f, 5.102f)
            verticalLineToRelative(13.796f)
            curveToRelative(0f, 2.155f, 1.747f, 3.902f, 3.902f, 3.902f)
            horizontalLineToRelative(13.796f)
            curveToRelative(2.155f, 0f, 3.902f, -1.747f, 3.902f, -3.902f)
            verticalLineTo(5.102f)
            curveTo(22.8f, 2.947f, 21.053f, 1.2f, 18.898f, 1.2f)
            lineTo(18.898f, 1.2f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun PluginGlimpIconPreview() {
    Icon(
        imageVector = IcPluginGlimp,
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
<g id="Plugin_Glimp">
	<g>
		<path fill="#FFFFFF" d="M14.633,15.754l-0.574,2.029c-0.067,0.235-0.282,0.395-0.526,0.392c-0.243-0.004-0.454-0.171-0.513-0.408
			l-0.697-2.789l-0.567,2.276c0.64,2.58,2.965,4.492,5.735,4.492c2.331,0,4.346-1.354,5.308-3.32v-2.671H14.633z"/>
		<path fill="#FFFFFF" d="M12.844,12.624l0.73,2.922l0.136-0.479c0.066-0.232,0.277-0.392,0.517-0.392H22.8v-1.56
			c-1.195-3.18-3.877-6.988-5.308-8.423c-1.341,1.345-3.666,4.604-4.965,7.566C12.681,12.323,12.801,12.455,12.844,12.624z"/>
		<path fill="#FFFFFF" d="M12.527,12.259c-0.064-0.026-0.133-0.042-0.205-0.042c0,0,0,0,0,0c-0.247,0-0.462,0.169-0.521,0.409
			l-1.32,5.301L8.52,10.134c-0.06-0.239-0.275-0.407-0.521-0.407c0,0-0.001,0-0.001,0c-0.247,0.001-0.461,0.169-0.521,0.409
			l-1.325,5.36l-0.724-2.873c-0.06-0.239-0.275-0.407-0.521-0.407c-0.002,0-0.003,0-0.005,0c-0.248,0.002-0.462,0.175-0.518,0.417
			l-0.474,2.042H1.2v1.078h3.135c0.25,0,0.467-0.173,0.524-0.417l0.069-0.296l0.708,2.807c0.06,0.239,0.275,0.407,0.521,0.407
			c0,0,0.001,0,0.001,0c0.247-0.001,0.461-0.169,0.521-0.409l1.325-5.358l1.959,7.784c0.06,0.239,0.275,0.407,0.521,0.407
			c0,0,0,0,0.001,0c0.246,0,0.461-0.169,0.521-0.409l0.751-3.018c-0.114-0.46-0.176-0.941-0.176-1.436
			C11.581,14.815,11.955,13.565,12.527,12.259z"/>
		<g id="Glimp">
			<path fill="#FFFFFF" d="M14.614,5.812c-0.083-0.232-0.206-0.412-0.37-0.54c-0.163-0.127-0.351-0.191-0.565-0.191
				c-0.163,0-0.302,0.037-0.417,0.111c-0.114,0.074-0.217,0.185-0.308,0.332V5.147h-0.386v4.015h0.424V7.749
				c0.072,0.106,0.165,0.193,0.277,0.262c0.112,0.069,0.239,0.104,0.38,0.104c0.193,0,0.377-0.063,0.552-0.19
				c0.175-0.127,0.308-0.309,0.4-0.547c0.092-0.238,0.138-0.506,0.138-0.802C14.739,6.299,14.697,6.045,14.614,5.812z M14.105,7.433
				c-0.134,0.185-0.295,0.278-0.484,0.278c-0.185,0-0.343-0.089-0.473-0.268c-0.13-0.178-0.195-0.454-0.195-0.828
				c0-0.373,0.069-0.658,0.208-0.854C13.3,5.565,13.46,5.467,13.64,5.467c0.182,0,0.339,0.092,0.47,0.276
				c0.131,0.184,0.196,0.461,0.196,0.831C14.306,6.962,14.239,7.248,14.105,7.433z"/>
			<path fill="#FFFFFF" d="M11.169,5.081c-0.317,0-0.574,0.169-0.772,0.508c-0.049-0.16-0.132-0.285-0.251-0.374
				c-0.118-0.089-0.27-0.134-0.455-0.134c-0.166,0-0.315,0.043-0.445,0.13c-0.13,0.086-0.235,0.201-0.313,0.343V5.147H8.554V8.05
				h0.424V6.543c0-0.264,0.023-0.466,0.069-0.607c0.045-0.14,0.119-0.247,0.22-0.32c0.101-0.073,0.212-0.109,0.331-0.109
				c0.157,0,0.27,0.055,0.339,0.164c0.069,0.109,0.103,0.273,0.103,0.492V8.05h0.424V6.363c0-0.299,0.058-0.516,0.174-0.652
				c0.116-0.136,0.262-0.204,0.438-0.204c0.097,0,0.182,0.026,0.254,0.079c0.072,0.053,0.122,0.122,0.149,0.209
				c0.028,0.086,0.041,0.228,0.041,0.425V8.05h0.421V6.057c0-0.332-0.067-0.577-0.2-0.737C11.608,5.161,11.417,5.081,11.169,5.081z"
				/>
			<rect x="7.486" y="5.147" fill="#FFFFFF" width="0.424" height="2.903"/>
			<rect x="6.403" y="4.043" fill="#FFFFFF" width="0.424" height="4.007"/>
			<rect x="7.486" y="4.043" fill="#FFFFFF" width="0.424" height="0.566"/>
			<path fill="#FFFFFF" d="M4.332,6.478h1.014v0.746c-0.097,0.1-0.24,0.195-0.428,0.283C4.73,7.596,4.537,7.64,4.34,7.64
				c-0.228,0-0.442-0.058-0.645-0.175C3.492,7.348,3.338,7.169,3.232,6.929C3.126,6.689,3.074,6.39,3.074,6.033
				c0-0.29,0.044-0.558,0.132-0.806C3.257,5.082,3.33,4.95,3.423,4.83C3.516,4.71,3.639,4.612,3.791,4.539s0.333-0.111,0.541-0.111
				c0.174,0,0.333,0.035,0.475,0.105c0.143,0.07,0.253,0.164,0.33,0.28C5.214,4.93,5.279,5.09,5.33,5.294l0.412-0.131
				C5.683,4.894,5.595,4.675,5.481,4.507C5.367,4.34,5.208,4.209,5.007,4.115C4.805,4.021,4.58,3.974,4.33,3.974
				c-0.344,0-0.647,0.082-0.91,0.246C3.156,4.384,2.954,4.635,2.813,4.973c-0.14,0.338-0.21,0.702-0.21,1.092
				c0,0.393,0.07,0.75,0.212,1.069c0.141,0.319,0.35,0.563,0.627,0.731c0.277,0.168,0.585,0.253,0.923,0.253
				c0.251,0,0.496-0.053,0.734-0.157C5.338,7.856,5.57,7.7,5.794,7.492V6.005L4.332,6.008V6.478z"/>
		</g>
		<path fill="#FFFFFF" d="M18.898,1.625c1.917,0,3.476,1.56,3.476,3.476v13.796c0,1.917-1.56,3.476-3.476,3.476H5.102
			c-1.917,0-3.476-1.56-3.476-3.476V5.102c0-1.917,1.56-3.476,3.476-3.476H18.898 M18.898,1.2H5.102C2.947,1.2,1.2,2.947,1.2,5.102
			v13.796c0,2.155,1.747,3.902,3.902,3.902h13.796c2.155,0,3.902-1.747,3.902-3.902V5.102C22.8,2.947,21.053,1.2,18.898,1.2
			L18.898,1.2z"/>
	</g>
</g>
</svg>
 */