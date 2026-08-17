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
internal fun IcCancelExtendedBolusIconPreview() {
    Icon(
        imageVector = IcCancelExtendedBolus,
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
<g id="ic_cancel_extended_bolus">
	<g display="inline">
		<g>
			<path fill="#FEAF05" d="M19.538,9.528l0.378-0.731c0.192-0.371,0.046-0.828-0.326-1.02c-0.375-0.194-0.828-0.046-1.02,0.326
				l-0.378,0.732c-0.405-0.158-0.825-0.284-1.261-0.367V7.828c0.48-0.065,0.857-0.46,0.857-0.959c0-0.544-0.44-0.984-0.984-0.984
				h-2.471c-0.544,0-0.984,0.441-0.984,0.984c0,0.498,0.376,0.894,0.857,0.959v0.639c-3.339,0.64-5.871,3.578-5.871,7.1
				c0,3.988,3.245,7.233,7.233,7.233c3.988,0,7.233-3.245,7.233-7.233C22.8,13.045,21.5,10.823,19.538,9.528z M15.567,21.732
				c-3.399,0-6.165-2.765-6.165-6.164s2.765-6.165,6.165-6.165s6.164,2.765,6.164,6.165S18.967,21.732,15.567,21.732z"/>
			<path fill="#FEAF05" d="M15.569,10.722c-0.092,0-0.181,0.037-0.246,0.102c-0.065,0.065-0.102,0.154-0.102,0.246v4.486
				c0,0.086,0.031,0.168,0.088,0.232l2.979,3.354c0.062,0.069,0.148,0.111,0.241,0.116c0.006,0.001,0.014,0.001,0.02,0.001
				c0.086,0,0.168-0.031,0.232-0.089c1.037-0.925,1.632-2.238,1.632-3.603C20.413,12.897,18.24,10.723,15.569,10.722z"/>
		</g>
		<g>
			<path fill="#FDAE04" d="M1.884,10.1c-0.175,0-0.35-0.067-0.484-0.2C1.133,9.633,1.133,9.201,1.401,8.934L8.934,1.4
				c0.268-0.267,0.699-0.267,0.967,0.001C10.168,1.668,10.168,2.1,9.9,2.368L2.367,9.9C2.233,10.033,2.058,10.1,1.884,10.1z"/>
			<path fill="#FDAE04" d="M9.418,10.1c-0.175,0-0.35-0.067-0.484-0.2L1.401,2.368C1.133,2.1,1.133,1.668,1.4,1.401
				C1.667,1.133,2.099,1.133,2.367,1.4L9.9,8.934c0.267,0.267,0.267,0.699,0.001,0.966C9.768,10.033,9.592,10.1,9.418,10.1z"/>
		</g>
	</g>
</g>
</svg>
 */
