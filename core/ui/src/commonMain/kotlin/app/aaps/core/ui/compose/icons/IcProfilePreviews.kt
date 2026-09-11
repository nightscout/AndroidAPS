package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun IcProfileIconPreview() {
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
