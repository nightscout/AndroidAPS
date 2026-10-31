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
internal fun IcPumpCartridgeIconPreview() {
    Icon(
        imageVector = IcPumpCartridge,
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
<g id="ic_pump_cartridge">
	<g display="inline">
		<path fill="#FEAF05" d="M22.366,7.797c-0.398,0.228-0.892,0.114-1.104-0.254l-1.387-2.42c-0.211-0.369-0.06-0.853,0.338-1.081l0,0
			c0.398-0.228,0.892-0.114,1.104,0.254l1.387,2.42C22.916,7.085,22.765,7.569,22.366,7.797L22.366,7.797z"/>
		<path fill="#FEAF05" d="M7.132,18.698l-0.228-0.396l14.352-8.226c0.132-0.076,0.219-0.209,0.235-0.358l0.21-3.573l-0.397-0.693
			l-3.189-1.624c-0.136-0.062-0.295-0.054-0.427,0.022L3.336,12.077L3.108,11.68c-0.274-0.477-0.893-0.636-1.385-0.354
			c-0.492,0.282-0.668,0.896-0.394,1.374l4.024,7.018c0.274,0.477,0.893,0.636,1.385,0.354S7.406,19.176,7.132,18.698z
			 M19.703,9.922L18.052,7.33C17.972,7.203,17.8,7.168,17.67,7.251c-0.127,0.08-0.169,0.242-0.097,0.367
			c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571l-1.102,0.632L16.464,8.24c-0.081-0.126-0.252-0.162-0.382-0.079
			c-0.127,0.08-0.169,0.242-0.097,0.367c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571l-1.102,0.632L14.876,9.15
			c-0.081-0.126-0.252-0.162-0.382-0.079c-0.127,0.08-0.169,0.242-0.097,0.368c0.002,0.003,0.004,0.008,0.006,0.011l1.638,2.571
			l-0.959,0.55l-2.273-3.636c-0.078-0.124-0.242-0.163-0.373-0.088l-8.244,4.725l-0.406-0.708l14.141-8.105l2.837,1.464l-0.17,3.189
			L19.703,9.922z"/>
	</g>
</g>
</svg>
 */
