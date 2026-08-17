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
internal fun IcPluginEversensePreview() {
    Icon(
        imageVector = IcPluginEversense,
        contentDescription = "Eversense Plugin Icon",
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
<g id="Eversense">
	<g>
		<circle opacity="0.9" fill="#FFFFFF" cx="12" cy="12.806" r="2.669"/>
		<path opacity="0.8" fill="#FFFFFF" d="M12.779,4.934c0,0.134-0.108,0.242-0.242,0.242h-1.075c-0.134,0-0.242-0.108-0.242-0.242
			l0,0c0-0.134,0.108-0.242,0.242-0.242h1.075C12.671,4.693,12.779,4.801,12.779,4.934L12.779,4.934z"/>
		<path fill="#FFFFFF" d="M12,1.2C5.221,1.2,3.537,6.035,3.537,12S5.185,22.8,12,22.8c6.761,0,8.463-4.835,8.463-10.8
			S18.958,1.2,12,1.2z M11.463,4.693h1.075c0.134,0,0.242,0.108,0.242,0.242c0,0.134-0.108,0.242-0.242,0.242h-1.075
			c-0.134,0-0.242-0.108-0.242-0.242C11.221,4.801,11.329,4.693,11.463,4.693z M12,15.475c-1.474,0-2.669-1.195-2.669-2.669
			s1.195-2.669,2.669-2.669s2.669,1.195,2.669,2.669S13.474,15.475,12,15.475z"/>
	</g>
</g>
</svg>
 */
