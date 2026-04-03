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
 * Icon for Syai CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginSyai: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginSyai",
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
            // Outer circle
            moveTo(12f, 1.2f)
            curveTo(6.035f, 1.2f, 1.2f, 6.035f, 1.2f, 12f)
            reflectiveCurveTo(6.035f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(22.8f, 17.965f, 22.8f, 12f)
            reflectiveCurveTo(17.965f, 1.2f, 12f, 1.2f)
            close()

            // Inner shape
            moveTo(16.408f, 12.511f)
            curveToRelative(-0.068f, 0.229f, -0.179f, 0.441f, -0.329f, 0.621f)
            curveToRelative(-0.15f, 0.181f, -0.333f, 0.326f, -0.54f, 0.426f)
            curveToRelative(-0.207f, 0.1f, -0.432f, 0.152f, -0.659f, 0.152f)
            horizontalLineToRelative(-0.011f)
            curveToRelative(-0.26f, -0.004f, -0.518f, 0.047f, -0.76f, 0.152f)
            curveToRelative(-0.242f, 0.104f, -0.463f, 0.26f, -0.65f, 0.457f)
            lineToRelative(-0.476f, 0.513f)
            curveToRelative(-0.185f, 0.194f, -0.331f, 0.428f, -0.43f, 0.687f)
            curveToRelative(-0.099f, 0.259f, -0.147f, 0.537f, -0.143f, 0.817f)
            curveToRelative(0.001f, 0.06f, 0f, 0.121f, -0.004f, 0.182f)
            curveToRelative(-0.023f, 0.324f, -0.132f, 0.637f, -0.316f, 0.898f)
            curveToRelative(-0.183f, 0.262f, -0.434f, 0.463f, -0.723f, 0.578f)
            curveToRelative(-0.289f, 0.115f, -0.603f, 0.139f, -0.905f, 0.068f)
            curveToRelative(-0.302f, -0.071f, -0.576f, -0.234f, -0.792f, -0.466f)
            curveToRelative(-0.216f, -0.233f, -0.364f, -0.525f, -0.429f, -0.843f)
            curveToRelative(-0.064f, -0.318f, -0.043f, -0.649f, 0.062f, -0.955f)
            curveToRelative(0.105f, -0.306f, 0.29f, -0.575f, 0.534f, -0.774f)
            curveToRelative(0.244f, -0.199f, 0.538f, -0.319f, 0.846f, -0.345f)
            lineToRelative(0.005f, -0.001f)
            curveToRelative(0.055f, -0.003f, 0.11f, -0.004f, 0.164f, -0.004f)
            horizontalLineToRelative(0.003f)
            curveToRelative(0.255f, 0.005f, 0.509f, -0.047f, 0.747f, -0.152f)
            curveToRelative(0.237f, -0.105f, 0.453f, -0.262f, 0.633f, -0.461f)
            lineToRelative(0.001f, -0.002f)
            lineToRelative(0.318f, -0.343f)
            curveToRelative(0.197f, -0.213f, 0.231f, -0.505f, 0.14f, -0.75f)
            curveToRelative(-0.091f, -0.244f, -0.296f, -0.417f, -0.561f, -0.42f)
            horizontalLineToRelative(-0.25f)
            curveToRelative(-0.619f, 0.001f, -1.217f, 0.253f, -1.676f, 0.709f)
            curveToRelative(-0.185f, 0.183f, -0.409f, 0.317f, -0.653f, 0.39f)
            curveToRelative(-0.244f, 0.073f, -0.501f, 0.084f, -0.75f, 0.03f)
            curveToRelative(-0.249f, -0.053f, -0.481f, -0.169f, -0.679f, -0.336f)
            curveToRelative(-0.198f, -0.167f, -0.355f, -0.382f, -0.46f, -0.625f)
            curveToRelative(-0.105f, -0.243f, -0.155f, -0.509f, -0.147f, -0.776f)
            curveToRelative(0.008f, -0.267f, 0.075f, -0.529f, 0.195f, -0.764f)
            curveToRelative(0.12f, -0.236f, 0.291f, -0.438f, 0.498f, -0.591f)
            curveToRelative(0.208f, -0.153f, 0.447f, -0.252f, 0.699f, -0.287f)
            lineToRelative(0.003f, 0f)
            lineToRelative(0.015f, -0.002f)
            curveToRelative(0.035f, -0.005f, 0.081f, -0.01f, 0.127f, -0.011f)
            curveToRelative(0.623f, -0.03f, 1.214f, -0.311f, 1.658f, -0.791f)
            lineToRelative(0.14f, -0.151f)
            curveToRelative(0.447f, -0.478f, 0.713f, -1.122f, 0.742f, -1.804f)
            verticalLineToRelative(-0.461f)
            curveToRelative(0.024f, -0.324f, 0.134f, -0.635f, 0.318f, -0.896f)
            curveToRelative(0.184f, -0.261f, 0.434f, -0.462f, 0.723f, -0.576f)
            curveToRelative(0.289f, -0.114f, 0.603f, -0.138f, 0.905f, -0.066f)
            curveToRelative(0.301f, 0.072f, 0.575f, 0.235f, 0.791f, 0.467f)
            curveToRelative(0.215f, 0.233f, 0.363f, 0.525f, 0.428f, 0.843f)
            curveToRelative(0.064f, 0.318f, 0.043f, 0.649f, -0.062f, 0.955f)
            curveToRelative(-0.105f, 0.306f, -0.29f, 0.574f, -0.534f, 0.773f)
            curveToRelative(-0.244f, 0.198f, -0.538f, 0.319f, -0.846f, 0.344f)
            lineToRelative(-0.014f, 0.001f)
            lineToRelative(-0.045f, -0.001f)
            curveToRelative(-0.623f, 0.03f, -1.214f, 0.311f, -1.658f, 0.791f)
            lineToRelative(-0.154f, 0.165f)
            curveToRelative(-0.197f, 0.213f, -0.231f, 0.506f, -0.14f, 0.75f)
            curveToRelative(0.091f, 0.244f, 0.296f, 0.418f, 0.561f, 0.42f)
            horizontalLineToRelative(0.249f)
            curveToRelative(0.625f, -0.008f, 1.227f, -0.264f, 1.691f, -0.723f)
            lineToRelative(0.001f, -0.001f)
            curveToRelative(0.219f, -0.21f, 0.488f, -0.352f, 0.779f, -0.41f)
            curveToRelative(0.291f, -0.058f, 0.591f, -0.028f, 0.867f, 0.085f)
            curveToRelative(0.276f, 0.113f, 0.515f, 0.305f, 0.694f, 0.553f)
            curveToRelative(0.179f, 0.248f, 0.291f, 0.543f, 0.324f, 0.853f)
            horizontalLineToRelative(-0.002f)
            curveToRelative(-0.013f, 0.254f, -0.035f, 0.495f, -0.102f, 0.723f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginSyaiIconPreview() {
    Icon(
        imageVector = IcPluginSyai,
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
<g id="Plugin_syai">
	<path fill="#FFFFFF" d="M12,1.2C6.035,1.2,1.2,6.035,1.2,12S6.035,22.8,12,22.8c5.965,0,10.8-4.835,10.8-10.8S17.965,1.2,12,1.2z
		 M16.408,12.511c-0.068,0.229-0.179,0.441-0.329,0.621c-0.15,0.181-0.333,0.326-0.54,0.426c-0.207,0.1-0.432,0.152-0.659,0.152
		h-0.011c-0.26-0.004-0.518,0.047-0.76,0.152c-0.242,0.104-0.463,0.26-0.65,0.457l-0.476,0.513c-0.185,0.194-0.331,0.428-0.43,0.687
		c-0.099,0.259-0.147,0.537-0.143,0.817c0.001,0.06,0,0.121-0.004,0.182c-0.023,0.324-0.132,0.637-0.316,0.898
		c-0.183,0.262-0.434,0.463-0.723,0.578c-0.289,0.115-0.603,0.139-0.905,0.068c-0.302-0.071-0.576-0.234-0.792-0.466
		c-0.216-0.233-0.364-0.525-0.429-0.843c-0.064-0.318-0.043-0.649,0.062-0.955c0.105-0.306,0.29-0.575,0.534-0.774
		c0.244-0.199,0.538-0.319,0.846-0.345l0.005-0.001c0.055-0.003,0.11-0.004,0.164-0.004h0.003c0.255,0.005,0.509-0.047,0.747-0.152
		c0.237-0.105,0.453-0.262,0.633-0.461l0.001-0.002l0.318-0.343c0.197-0.213,0.231-0.505,0.14-0.75
		c-0.091-0.244-0.296-0.417-0.561-0.42h-0.25c-0.619,0.001-1.217,0.253-1.676,0.709c-0.185,0.183-0.409,0.317-0.653,0.39
		c-0.244,0.073-0.501,0.084-0.75,0.03c-0.249-0.053-0.481-0.169-0.679-0.336c-0.198-0.167-0.355-0.382-0.46-0.625
		c-0.105-0.243-0.155-0.509-0.147-0.776c0.008-0.267,0.075-0.529,0.195-0.764c0.12-0.236,0.291-0.438,0.498-0.591
		c0.208-0.153,0.447-0.252,0.699-0.287l0.003,0l0.015-0.002c0.035-0.005,0.081-0.01,0.127-0.011c0.623-0.03,1.214-0.311,1.658-0.791
		l0.14-0.151c0.447-0.478,0.713-1.122,0.742-1.804V7.477c0.024-0.324,0.134-0.635,0.318-0.896c0.184-0.261,0.434-0.462,0.723-0.576
		c0.289-0.114,0.603-0.138,0.905-0.066c0.301,0.072,0.575,0.235,0.791,0.467c0.215,0.233,0.363,0.525,0.428,0.843
		c0.064,0.318,0.043,0.649-0.062,0.955c-0.105,0.306-0.29,0.574-0.534,0.773c-0.244,0.198-0.538,0.319-0.846,0.344l-0.014,0.001
		l-0.045-0.001c-0.623,0.03-1.214,0.311-1.658,0.791l-0.154,0.165c-0.197,0.213-0.231,0.506-0.14,0.75
		c0.091,0.244,0.296,0.418,0.561,0.42h0.249c0.625-0.008,1.227-0.264,1.691-0.723l0.001-0.001c0.219-0.21,0.488-0.352,0.779-0.41
		c0.291-0.058,0.591-0.028,0.867,0.085c0.276,0.113,0.515,0.305,0.694,0.553c0.179,0.248,0.291,0.543,0.324,0.853h-0.002
		C16.497,12.042,16.475,12.283,16.408,12.511z"/>
</g>
</svg>
 */