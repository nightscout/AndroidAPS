package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun IcPluginMedtrumIconPreview() {
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
