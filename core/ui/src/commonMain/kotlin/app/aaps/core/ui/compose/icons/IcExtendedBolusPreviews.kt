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
internal fun IcExtendedBolusIconPreview() {
    Icon(
        imageVector = IcExtendedBolus,
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
<g id="ic_extended_bolus">
	<g display="inline">
		<path fill="#67DFE8" d="M17.07,5.852l0.482-0.934c0.245-0.474,0.059-1.058-0.416-1.303c-0.478-0.247-1.058-0.059-1.303,0.416
			l-0.483,0.935c-0.517-0.202-1.054-0.362-1.61-0.469V3.681c0.614-0.083,1.094-0.588,1.094-1.224c0-0.694-0.562-1.257-1.256-1.257
			h-3.155c-0.694,0-1.257,0.563-1.257,1.257c0,0.637,0.481,1.141,1.095,1.224v0.816c-4.263,0.817-7.496,4.569-7.496,9.067
			c0,5.092,4.144,9.236,9.236,9.236c5.092,0,9.236-4.144,9.236-9.236C21.236,10.343,19.576,7.506,17.07,5.852z M12,21.436
			c-4.341,0-7.872-3.531-7.872-7.872S7.66,5.692,12,5.692s7.872,3.531,7.872,7.872S16.341,21.436,12,21.436z"/>
		<path fill="#67DFE8" d="M12.003,7.377c-0.118,0-0.231,0.047-0.314,0.131c-0.083,0.083-0.131,0.197-0.131,0.314v5.728
			c0,0.109,0.04,0.215,0.113,0.296l3.805,4.283c0.079,0.088,0.189,0.141,0.308,0.148c0.008,0.001,0.017,0.001,0.025,0.001
			c0.109,0,0.215-0.04,0.296-0.113c1.324-1.182,2.084-2.858,2.084-4.601C18.188,10.154,15.414,7.379,12.003,7.377z"/>
	</g>
</g>
</svg>
 */
