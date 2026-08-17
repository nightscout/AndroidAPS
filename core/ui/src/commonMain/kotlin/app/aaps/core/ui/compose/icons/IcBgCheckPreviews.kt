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
internal fun IcBgCheckIconPreview() {
    Icon(
        imageVector = IcBgCheck,
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
<g id="ic_bg_check">
	<g display="inline">
		<path fill="#E9375C" d="M3.511,15.551c-0.028-0.497,0.039-0.963,0.644-1.034c0.555-0.065,0.756,0.335,0.819,0.795
			c0.239,1.743,1.188,2.773,2.958,3.001c0.518,0.067,0.912,0.296,0.814,0.883c-0.09,0.541-0.575,0.598-1.008,0.597
			C5.653,19.788,3.535,17.651,3.511,15.551z"/>
		<g>
			<g>
				<path fill="#E83258" d="M22.669,7.574c-0.581-2.289-2.977-5.731-3.477-5.731s-2.904,3.449-3.482,5.734
					c-0.387,1.528,0.103,2.839,1.454,3.731c1.318,0.87,2.739,0.856,4.056-0.012C22.573,10.404,23.06,9.113,22.669,7.574z
					 M20.542,10.266c-0.458,0.302-0.916,0.455-1.365,0.455c-0.441,0-0.889-0.149-1.335-0.442c-0.903-0.597-1.201-1.36-0.938-2.4
					c0.364-1.438,1.536-3.342,2.287-4.335c0.749,0.992,1.917,2.894,2.283,4.333C21.738,8.914,21.441,9.673,20.542,10.266z"/>
			</g>
		</g>
		<g>
			<g>
				<path fill="#E83258" d="M14.841,13.727c-1.119-4.405-5.999-11.21-6.692-11.21c-0.693,0-5.589,6.819-6.702,11.215
					c-0.745,2.94,0.198,5.464,2.798,7.181c2.537,1.675,5.272,1.648,7.807-0.023C14.656,19.174,15.593,16.69,14.841,13.727z
					 M11.373,19.862c-1.07,0.704-2.165,1.062-3.254,1.062c-1.076,0-2.151-0.349-3.195-1.04c-2.14-1.411-2.907-3.38-2.282-5.849
					c0.867-3.421,5.135-9.463,5.507-9.463s4.628,6.035,5.498,9.459C14.273,16.493,13.507,18.455,11.373,19.862z"/>
			</g>
		</g>
	</g>
</g>
</svg>
 */
