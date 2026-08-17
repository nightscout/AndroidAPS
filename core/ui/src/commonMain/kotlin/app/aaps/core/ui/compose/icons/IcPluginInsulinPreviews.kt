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
internal fun IcPluginInsulinIconPreview() {
    Icon(
        imageVector = IcPluginInsulin,
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
<g id="ic_plugin_insulin">
	<path display="inline" fill="#FFFFFF" d="M10.685,18.578V9.348h7.152V8.085c0-1.215-1.219-1.945-2.713-2.2l-1.061-1.061V3.385
		h0.784c0.276,0,0.5-0.224,0.5-0.5V1.7c0-0.276-0.224-0.5-0.5-0.5H9.153c-0.276,0-0.5,0.224-0.5,0.5v1.185
		c0,0.276,0.224,0.5,0.5,0.5h0.784v1.439L8.876,5.885c-1.493,0.255-2.713,0.985-2.713,2.2V20.6c0,1.215,0.985,2.2,2.2,2.2h7.273
		c1.215,0,2.2-0.985,2.2-2.2v-2.022H10.685z"/>
</g>
</svg>
 */
