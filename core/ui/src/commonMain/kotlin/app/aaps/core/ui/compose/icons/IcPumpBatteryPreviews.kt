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
internal fun IcPumpBatteryIconPreview() {
    Icon(
        imageVector = IcPumpBattery,
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
<g id="ic_pump_battery">
	<g display="inline">
		<path fill="#36FF00" d="M22.025,10.135c-0.428,0-0.775,0.347-0.775,0.775v0.351h-0.554V8.492c0-1.028-0.837-1.865-1.865-1.865
			H3.065C2.037,6.628,1.2,7.464,1.2,8.492v7.015c0,1.028,0.837,1.865,1.865,1.865h15.766c0.851,0,1.563-0.577,1.786-1.357
			c0.012,0.001,0.021,0.007,0.033,0.007c0.393,0,0.711-0.351,0.711-0.785c0-0.415-0.295-0.747-0.665-0.774v-1.725h0.554v0.351
			c0,0.428,0.347,0.775,0.775,0.775c0.428,0,0.775-0.347,0.775-0.775v-2.178C22.8,10.483,22.453,10.135,22.025,10.135z
			 M19.514,15.508c0,0.376-0.307,0.683-0.683,0.683H3.065c-0.377,0-0.683-0.307-0.683-0.683V8.492c0-0.377,0.306-0.683,0.683-0.683
			h15.766c0.376,0,0.683,0.306,0.683,0.683V15.508z"/>
		<polygon fill="#36FF00" points="9.582,9.96 4.929,13.412 9.009,11.972 11.114,14.058 16.357,9.942 11.28,11.935 		"/>
	</g>
</g>
</svg>
 */
