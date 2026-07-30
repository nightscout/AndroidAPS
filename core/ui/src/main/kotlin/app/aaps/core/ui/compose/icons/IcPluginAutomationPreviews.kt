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
internal fun IcPluginAutomationIconPreview() {
    Icon(
        imageVector = IcPluginAutomation,
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
<g id="ic_plugin_automation">
	<g id="Plguin_Automation" display="inline">
		<path fill="#FFFFFF" d="M16.114,12.514c-2.839,0-5.143,2.304-5.143,5.143s2.304,5.143,5.143,5.143s5.143-2.304,5.143-5.143
			S18.953,12.514,16.114,12.514z M17.811,20.074L15.6,17.863v-3.291h1.029v2.87l1.903,1.903L17.811,20.074z M17.143,3.257h-3.271
			C13.44,2.064,12.309,1.2,10.971,1.2S8.503,2.064,8.071,3.257H4.8c-1.131,0-2.057,0.926-2.057,2.057v15.429
			c0,1.131,0.926,2.057,2.057,2.057h6.285c-0.607-0.586-1.101-1.286-1.461-2.057H4.8V5.314h2.057V8.4h8.229V5.314h2.057v5.225
			c0.73,0.103,1.419,0.319,2.057,0.617V5.314C19.2,4.183,18.274,3.257,17.143,3.257z M10.971,5.314
			c-0.566,0-1.029-0.463-1.029-1.029c0-0.566,0.463-1.029,1.029-1.029C11.537,3.257,12,3.72,12,4.286
			C12,4.851,11.537,5.314,10.971,5.314z"/>
	</g>
</g>
</svg>
 */
