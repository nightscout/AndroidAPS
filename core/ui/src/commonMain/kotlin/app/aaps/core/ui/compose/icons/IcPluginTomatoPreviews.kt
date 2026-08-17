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
internal fun IcPluginTomatoIconPreview() {
    Icon(
        imageVector = IcPluginTomato,
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
<g id="ic_plugin_tomato">
	<g display="inline">
		<path fill="#FFFFFF" d="M12,1.2c5.961,0,10.8,4.839,10.8,10.8c0,5.961-4.839,10.8-10.8,10.8C6.039,22.8,1.2,17.961,1.2,12
			C1.2,6.039,6.039,1.2,12,1.2z M21.058,11.86c0-0.024,0-0.049,0-0.072c0-5.055-4.112-9.158-9.176-9.158
			c-5.064,0-9.175,4.104-9.175,9.158c0,5.054,4.111,9.158,9.175,9.158l0.059,0c0.02,0,0.04,0,0.059,0
			c4.999,0,9.058-4.039,9.058-9.014C21.059,11.908,21.059,11.884,21.058,11.86z"/>
		<path fill="#FFFFFF" d="M12,1.821c4.817,0,8.728,3.957,8.728,8.831c0,4.874-3.911,8.831-8.728,8.831
			c-4.817,0-8.728-3.957-8.728-8.831C3.272,5.778,7.183,1.821,12,1.821z M11.028,11.086c0,0.006,0,0.011,0,0.017
			C11.028,11.598,11.464,12,12,12c0.536,0,0.972-0.402,0.972-0.897v-0.018c0-0.005,0-0.011,0-0.017c0-0.476-0.435-0.863-0.972-0.863
			c-0.536,0-0.972,0.387-0.972,0.863V11.086z"/>
	</g>
</g>
</svg>
 */
