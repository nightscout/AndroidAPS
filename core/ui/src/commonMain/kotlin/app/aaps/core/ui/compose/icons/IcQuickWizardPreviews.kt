package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun IcQuickwizardIconPreview() {
    Icon(
        imageVector = IcQuickwizard,
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
<g id="ic_quickwizard">
	<path display="inline" fill="none" d="M0,0h24v24H0V0z"/>
	<g display="inline">
		<path fill="#FEAF05" d="M10.874,5.812C10.251,6.578,9.99,6.985,9.486,7.806c-2.794,0.264-4.365,3.054-3.751,5.177
			c0.27,0.935,0.864,1.638,1.663,2.174c2.606,1.746,6.93,1.586,9.421-0.339c1.318-1.019,1.912-2.313,1.481-3.969
			c-0.457-1.754-1.604-2.869-3.373-3.185c-1.235-0.221-2.19,0.411-2.839,1.457c-0.548,0.884-0.799,1.794-1.808,2.603
			c-0.432-0.478-0.508-1.067-0.536-1.644c-0.133-2.76,2.242-4.927,5.1-4.676c2.626,0.23,4.957,2.171,5.676,4.724
			c0.679,2.411-0.251,4.919-2.431,6.559c-3.325,2.501-8.764,2.583-12.147,0.183c-2.284-1.62-3.197-4.308-2.33-6.86
			C4.515,7.352,7.118,5.494,9.784,5.6C10.119,5.614,10.445,5.666,10.874,5.812z"/>
		<path fill="#FEAF05" d="M6.866,13.378c0.584-0.114,1.308-0.244,1.848-0.357c1.689-0.354,2.914-1.365,3.633-2.973
			c0.321-0.718,0.779-1.249,1.608-1.576c0.464,0.901,0.504,1.821,0.248,2.767c-0.315,1.164-0.987,2.068-2.019,2.697
			c-0.684,0.417-1.425,0.708-2.182,0.963C8.723,15.328,7.594,14.895,6.866,13.378z"/>
		<path fill="#FEAF05" d="M12.607,14.262c0.815-0.596,1.388-1.209,1.63-1.789c0.992,0.176,2.132,0.348,3.112,0.522
			C16.74,14.924,14.393,15.372,12.607,14.262z"/>
		<path fill="#FEAF05" d="M1.578,13.906c0.199-0.137,0.839-0.21,1.323-0.136c0.288,0.701,0.652,1.36,1.23,2.134
			c-0.635,0.129-1.152,0.281-1.74,0.249c-0.547-0.03-1.102-0.391-1.179-1.097C1.174,14.715,1.211,14.158,1.578,13.906z"/>
		<path fill="#FEAF05" d="M20.189,15.392c0.218-0.552,0.402-1.067,0.622-1.566c0.261-0.59,0.687-0.844,1.244-0.756
			c0.465,0.074,0.639,0.417,0.715,0.839c0.109,0.6-0.086,1.098-0.587,1.347C21.562,15.565,20.904,15.472,20.189,15.392z"/>
	</g>
</g>
</svg>
 */
