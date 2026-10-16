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
internal fun IcLoopHiddenIconPreview() {
    Icon(
        imageVector = IcLoopHidden,
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
<g id="ic_loop_hidden">
	<g display="inline">
		<path fill="#FFFFFF" d="M18.133,18.598l-1.934-1.934l0,0L5.882,6.347L4.325,4.79l0,0l-1.85-1.85L1.2,4.216l1.939,1.939
			C1.975,7.761,1.282,9.73,1.282,11.865c0,5.399,4.376,9.775,9.775,9.775c2.136,0,4.104-0.693,5.711-1.856l1.879,1.879l1.275-1.275
			L18.133,18.598L18.133,18.598z M11.058,18.905c-3.888,0-7.039-3.152-7.039-7.039c0-1.378,0.405-2.656,1.091-3.74l9.688,9.688
			C13.714,18.499,12.436,18.905,11.058,18.905z"/>
		<path fill="#FFFFFF" d="M11.058,4.826c1.054,0,2.051,0.238,2.949,0.654c0.32,0.148,0.629,0.316,0.921,0.508l0.002-0.002
			l-0.346-1.755l1.845-0.529c-1.542-1.017-3.386-1.612-5.371-1.612c-1.959,0-3.779,0.582-5.308,1.574l1.992,1.992
			C8.73,5.128,9.858,4.826,11.058,4.826z"/>
		<path fill="#FFFFFF" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,1.199-0.302,2.328-0.831,3.316l1.992,1.992c0.992-1.529,1.574-3.35,1.574-5.308
			c0-0.747-0.091-1.471-0.25-2.17c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
	</g>
</g>
</svg>
 */
