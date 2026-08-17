package app.aaps.core.ui.compose.icons.library.unused

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
internal fun IcPluginActionIconPreview() {
    Icon(
        imageVector = IcPluginAction,
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
<g id="ic_plugin_action">
	<g id="Plugin_Action" display="inline">
		<path fill="#FFFFFF" d="M14.143,5.912c-0.518,0.895-0.207,2.054,0.688,2.573c0.895,0.518,2.054,0.207,2.573-0.688
			s0.207-2.054-0.688-2.573S14.661,5.016,14.143,5.912z"/>
		<path fill="#FFFFFF" d="M15.622,10.068c0,0-1.536-0.886-2.45-1.414c-2.243-1.301-3.016-4.184-1.715-6.427l-1.63-0.942
			c-1.498,2.582-1.027,5.768,0.914,7.832l-4.853,8.406l1.63,0.942l1.414-2.45l1.63,0.942l-2.827,4.901l1.63,0.942l5.928-10.263
			c1.074,1.461,1.253,3.478,0.292,5.146l1.63,0.942C18.723,16.033,18.421,12.424,15.622,10.068z"/>
		<path fill="#FFFFFF" d="M12.71,3.838c0.679,0.386,1.536,0.16,1.932-0.518c0.386-0.679,0.16-1.536-0.518-1.932
			c-0.679-0.386-1.536-0.16-1.932,0.518C11.806,2.585,12.032,3.452,12.71,3.838z"/>
	</g>
</g>
</svg>
 */
