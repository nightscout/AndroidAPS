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
internal fun IcPluginOmnipodPreview() {
    Icon(
        imageVector = IcPluginOmnipod,
        contentDescription = "Omnipod Plugin Icon",
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
<g id="Plugin_Omnipod">
	<path id="Omnipod" fill="#FFFFFF" d="M21.59,13.399c0.09-1.244,0.625-2.221,1.208-2.218l0.002-0.853
		c0.009-3.52-2.896-6.391-6.476-6.4l-13.031,0c-1.134,0-2.058,0.903-2.061,2.012L1.2,17.994c-0.003,1.143,0.944,2.075,2.112,2.078
		h12.977c2.816,0,5.284-1.73,6.166-4.311C21.886,15.665,21.503,14.629,21.59,13.399z"/>
</g>
</svg>
 */
