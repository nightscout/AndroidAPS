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
internal fun IcArrowInvalidIconPreview() {
    Icon(
        imageVector = IcArrowInvalid,
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
<g id="ic_arrow_invalid">
	<g display="inline">
		<g>
			<circle fill="#36FF00" cx="17.633" cy="19.781" r="1.208"/>
			<path fill="#36FF00" d="M17.582,16.878c-0.483,0-0.874-0.392-0.874-0.875c0-3.143,1.465-4.24,2.643-5.122
				c0.987-0.739,1.7-1.273,1.7-3.001c0-2.382-2.288-3.123-3.122-3.123c-1.796,0-3.246,1.076-3.979,2.952
				c-0.176,0.45-0.685,0.673-1.133,0.496c-0.45-0.176-0.672-0.683-0.496-1.133c0.994-2.545,3.09-4.064,5.608-4.064
				c1.964,0,4.871,1.548,4.871,4.871c0,2.603-1.331,3.599-2.401,4.4c-1.086,0.813-1.942,1.454-1.942,3.722
				C18.457,16.487,18.065,16.878,17.582,16.878z"/>
		</g>
		<g>
			<circle fill="#36FF00" cx="6.572" cy="19.781" r="1.208"/>
			<path fill="#36FF00" d="M6.521,16.878c-0.483,0-0.874-0.392-0.874-0.875c0-3.143,1.465-4.24,2.643-5.122
				c0.987-0.739,1.7-1.273,1.7-3.001c0-2.382-2.288-3.123-3.122-3.123c-1.796,0-3.246,1.076-3.979,2.952
				c-0.176,0.45-0.685,0.673-1.133,0.496C1.306,8.031,1.085,7.525,1.26,7.075C2.254,4.53,4.35,3.01,6.868,3.01
				c1.964,0,4.871,1.548,4.871,4.871c0,2.603-1.331,3.599-2.401,4.4c-1.086,0.813-1.942,1.454-1.942,3.722
				C7.395,16.487,7.004,16.878,6.521,16.878z"/>
		</g>
	</g>
</g>
</svg>
 */
