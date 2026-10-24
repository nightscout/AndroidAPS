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
internal fun IcPluginByodaIconPreview() {
    Icon(
        imageVector = IcPluginByoda,
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
<g id="ic_plugin_byoda">
	<g id="G6_2_" display="inline">
		<path fill="#FFFFFF" d="M16.941,10.734c-0.014,3.375-0.228,6.749-0.636,10.124c-0.042,0.82-0.522,1.643-1.842,1.88
			c-1.628,0.082-3.278,0.082-4.949,0c-1.29-0.189-1.344-0.931-1.556-1.833c-0.779-3.312-0.843-6.732-0.898-10.137
			c0.945,1.64,2.8,2.394,4.931,2.394C14.137,13.162,16.002,12.392,16.941,10.734z"/>
		<path fill="#FFFFFF" d="M12,1.2c2.94,0,5.326,2.386,5.326,5.325c0,2.939-2.387,5.326-5.326,5.326S6.674,9.465,6.674,6.526
			S9.06,1.2,12,1.2z"/>
	</g>
</g>
</svg>
 */
