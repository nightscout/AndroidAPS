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
val IcPluginMedtrum: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMedtrum",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Base layer (solid, #F1F1F2)
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
            moveTo(21.732f, 5.127f)
            curveToRelative(-1.056f, -0.885f, -2.367f, -1.103f, -3.697f, -1.215f)
            curveToRelative(-0.349f, -0.031f, -0.698f, -0.045f, -1.048f, -0.064f)
            curveToRelative(-0.013f, 0.332f, -0.026f, 0.664f, -0.04f, 0.997f)
            verticalLineToRelative(14.309f)
            curveToRelative(0.013f, 0.332f, 0.026f, 0.664f, 0.04f, 0.997f)
            curveToRelative(1.325f, -0.072f, 2.828f, -0.086f, 4.011f, -0.771f)
            curveToRelative(1.22f, -0.683f, 1.803f, -1.842f, 1.803f, -3.226f)
            curveToRelative(0f, -1.276f, 0f, -2.551f, 0f, -3.827f)
            curveToRelative(0f, -1.42f, 0f, -2.84f, 0f, -4.26f)
            curveTo(22.8f, 6.983f, 22.593f, 5.875f, 21.732f, 5.127f)
            close()
        }

        // Reservoir layer (opacity 0.5)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(16.947f, 4.845f)
            curveToRelative(0.013f, -0.332f, 0.026f, -0.665f, 0.04f, -0.997f)
            curveTo(14.002f, 3.731f, 11f, 3.769f, 8.017f, 3.907f)
            curveTo(6.57f, 3.974f, 5.043f, 3.98f, 3.683f, 4.541f)
            curveTo(2.301f, 5.11f, 1.511f, 6.318f, 1.321f, 7.774f)
            curveToRelative(-0.181f, 1.384f, -0.112f, 2.821f, -0.09f, 4.212f)
            curveToRelative(0.021f, 1.331f, -0.058f, 2.66f, 0.059f, 3.99f)
            curveToRelative(0.122f, 1.382f, 0.782f, 2.737f, 2.091f, 3.344f)
            curveToRelative(1.314f, 0.669f, 2.868f, 0.683f, 4.306f, 0.756f)
            curveToRelative(3.091f, 0.156f, 6.205f, 0.196f, 9.299f, 0.075f)
            curveToRelative(-0.013f, -0.332f, -0.026f, -0.665f, -0.04f, -0.997f)
            verticalLineTo(4.845f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginMedtrumIconPreview() {
    Icon(
        imageVector = IcPluginMedtrum,
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
<g id="plugin_medtrum">
	<g>
		<path id="Tape" opacity="0.2" fill="#FFFFFF" d="M17.44,3.108H6.56c-2.96,0-5.36,2.411-5.36,5.386v7.014
			c0,2.974,2.4,5.386,5.36,5.386H17.44c2.96,0,5.36-2.411,5.36-5.386V8.493C22.8,5.519,20.4,3.108,17.44,3.108z M10.651,19.63
			c-0.231,0-0.417-0.187-0.417-0.417s0.187-0.417,0.417-0.417c0.231,0,0.417,0.187,0.417,0.417S10.882,19.63,10.651,19.63z
			 M10.651,5.205c-0.231,0-0.417-0.187-0.417-0.417c0-0.231,0.187-0.417,0.417-0.417c0.231,0,0.417,0.187,0.417,0.417
			C11.069,5.019,10.882,5.205,10.651,5.205z M20.665,13.634v-3.268c0.44,0.406,0.716,0.988,0.716,1.634S21.105,13.228,20.665,13.634
			z"/>
		<path id="Base" fill="#F1F1F2" d="M19.809,6.477C18.961,5.765,17.907,5.59,16.838,5.5c-0.28-0.025-0.561-0.036-0.842-0.052
			c-0.011,0.267-0.021,0.534-0.032,0.801V17.75c0.011,0.267,0.021,0.534,0.032,0.801c1.065-0.058,2.273-0.069,3.223-0.62
			c0.98-0.549,1.449-1.48,1.449-2.593c0-1.025,0-2.051,0-3.076c0-1.141,0-2.283,0-3.424C20.668,7.968,20.501,7.077,19.809,6.477z"/>
		<path id="Reservoir" opacity="0.5" fill="#FFFFFF" d="M15.964,6.25c0.011-0.267,0.021-0.534,0.032-0.802
			c-2.399-0.094-4.811-0.063-7.209,0.048C7.623,5.55,6.396,5.555,5.302,6.005c-1.11,0.457-1.745,1.428-1.898,2.599
			c-0.145,1.112-0.09,2.268-0.072,3.385c0.017,1.07-0.047,2.138,0.047,3.207c0.098,1.111,0.629,2.2,1.681,2.687
			c1.056,0.538,2.305,0.549,3.461,0.608c2.485,0.126,4.987,0.158,7.474,0.061c-0.011-0.267-0.021-0.534-0.032-0.802V6.25z"/>
	</g>
</g>
</svg>
 */