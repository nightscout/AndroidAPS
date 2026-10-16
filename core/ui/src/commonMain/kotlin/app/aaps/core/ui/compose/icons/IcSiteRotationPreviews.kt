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
internal fun IcSiteRotationIconPreview() {
    Icon(
        imageVector = IcSiteRotation,
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
<g id="ic_site_rotation">
	<rect x="0" width="24" height="24"/>
	<g display="inline">
		<path fill="#67DFE8" d="M8.128,5.493L7.22,3.968C4.489,5.6,2.649,8.576,2.642,11.981H1.2l2.33,4.035l2.33-4.035h-1.44
			C4.426,9.224,5.916,6.815,8.128,5.493z"/>
		<path fill="#67DFE8" d="M20.47,7.978l-2.33,4.035h1.44c-0.005,2.759-1.496,5.171-3.709,6.493l0.908,1.525
			c2.733-1.633,4.573-4.611,4.578-8.018H22.8L20.47,7.978z"/>
		<path fill="#67DFE8" d="M11.981,19.581c-2.757-0.007-5.166-1.497-6.488-3.709L3.968,16.78c1.632,2.731,4.608,4.571,8.013,4.578
			V22.8l4.035-2.33l-4.035-2.33V19.581z"/>
		<path fill="#66DEE7" d="M12.013,2.642V1.2L7.978,3.53l4.035,2.33v-1.44c2.76,0.005,5.171,1.496,6.493,3.709l1.525-0.908
			C18.398,4.487,15.421,2.647,12.013,2.642z"/>
	</g>
</g>
</svg>
 */
