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
 * Icon for Profile.
 * Represents user profile or settings.
 *
 * replaces ic_ribbon_profile
 *
 * Bounding box: x: 1.2-22.8, y: 2.5-21.5 (viewport: 24x24, ~90% width)
 */
val IcProfile: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcProfile",
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
            moveTo(22.721f, 9.405f)
            curveToRelative(-0.186f, -0.577f, -0.684f, -0.997f, -1.285f, -1.083f)
            lineToRelative(-5.534f, -0.805f)
            lineToRelative(-2.476f, -5.015f)
            curveToRelative(-0.535f, -1.088f, -2.318f, -1.088f, -2.853f, 0f)
            lineTo(8.097f, 7.516f)
            lineTo(2.562f, 8.321f)
            curveTo(1.964f, 8.408f, 1.465f, 8.828f, 1.278f, 9.405f)
            curveToRelative(-0.188f, 0.576f, -0.032f, 1.208f, 0.402f, 1.631f)
            lineToRelative(4.006f, 3.903f)
            lineToRelative(-0.945f, 5.514f)
            curveToRelative(-0.103f, 0.599f, 0.143f, 1.202f, 0.633f, 1.557f)
            curveToRelative(0.277f, 0.202f, 0.605f, 0.305f, 0.935f, 0.305f)
            curveToRelative(0.253f, 0f, 0.508f, -0.061f, 0.74f, -0.184f)
            lineTo(12f, 19.529f)
            lineToRelative(4.951f, 2.601f)
            curveToRelative(0.54f, 0.281f, 1.184f, 0.239f, 1.678f, -0.121f)
            curveToRelative(0.489f, -0.355f, 0.735f, -0.961f, 0.632f, -1.557f)
            lineToRelative(-0.945f, -5.514f)
            lineToRelative(4.005f, -3.903f)
            curveTo(22.752f, 10.613f, 22.91f, 9.981f, 22.721f, 9.405f)
            close()

            moveTo(21.261f, 10.181f)
            lineToRelative(-4.376f, 4.266f)
            lineToRelative(1.033f, 6.023f)
            curveToRelative(0.02f, 0.121f, -0.029f, 0.241f, -0.127f, 0.311f)
            curveToRelative(-0.055f, 0.042f, -0.121f, 0.061f, -0.186f, 0.061f)
            curveToRelative(-0.05f, 0f, -0.101f, -0.011f, -0.149f, -0.037f)
            lineToRelative(-5.409f, -2.842f)
            lineToRelative(-5.41f, 2.842f)
            curveToRelative(-0.104f, 0.061f, -0.235f, 0.05f, -0.336f, -0.024f)
            curveToRelative(-0.098f, -0.07f, -0.147f, -0.191f, -0.126f, -0.311f)
            lineToRelative(1.033f, -6.023f)
            lineToRelative(-4.378f, -4.266f)
            curveToRelative(-0.087f, -0.084f, -0.117f, -0.212f, -0.08f, -0.327f)
            curveToRelative(0.037f, -0.115f, 0.137f, -0.198f, 0.257f, -0.216f)
            lineTo(9.057f, 8.76f)
            lineToRelative(2.705f, -5.481f)
            curveToRelative(0.107f, -0.219f, 0.463f, -0.219f, 0.57f, 0f)
            lineToRelative(2.704f, 5.481f)
            lineToRelative(6.049f, 0.878f)
            curveToRelative(0.121f, 0.018f, 0.219f, 0.101f, 0.257f, 0.216f)
            curveTo(21.379f, 9.97f, 21.349f, 10.097f, 21.261f, 10.181f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcProfileIconPreview() {
    Icon(
        imageVector = IcProfile,
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
<g id="ic_profile">
	<path display="inline" fill="#FFFFFF" d="M22.721,9.405c-0.186-0.577-0.684-0.997-1.285-1.083l-5.534-0.805l-2.476-5.015
		c-0.535-1.088-2.318-1.088-2.853,0L8.097,7.516L2.562,8.321C1.964,8.408,1.465,8.828,1.278,9.405
		c-0.188,0.576-0.032,1.208,0.402,1.631l4.006,3.903l-0.945,5.514c-0.103,0.599,0.143,1.202,0.633,1.557
		c0.277,0.202,0.605,0.305,0.935,0.305c0.253,0,0.508-0.061,0.74-0.184L12,19.529l4.951,2.601c0.54,0.281,1.184,0.239,1.678-0.121
		c0.489-0.355,0.735-0.961,0.632-1.557l-0.945-5.514l4.005-3.903C22.752,10.613,22.91,9.981,22.721,9.405z M21.261,10.181
		l-4.376,4.266l1.033,6.023c0.02,0.121-0.029,0.241-0.127,0.311c-0.055,0.042-0.121,0.061-0.186,0.061
		c-0.05,0-0.101-0.011-0.149-0.037l-5.409-2.842l-5.41,2.842c-0.104,0.061-0.235,0.05-0.336-0.024
		c-0.098-0.07-0.147-0.191-0.126-0.311l1.033-6.023l-4.378-4.266c-0.087-0.084-0.117-0.212-0.08-0.327
		c0.037-0.115,0.137-0.198,0.257-0.216L9.057,8.76l2.705-5.481c0.107-0.219,0.463-0.219,0.57,0l2.704,5.481l6.049,0.878
		c0.121,0.018,0.219,0.101,0.257,0.216C21.379,9.97,21.349,10.097,21.261,10.181z"/>
</g>
</svg>
 */