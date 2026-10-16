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
internal fun IcArrowLeftDownIconPreview() {
    Icon(
        imageVector = IcArrowLeftDown,
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
<g id="ic_arrow_left_down">
	<path display="inline" fill="#36FF00" d="M3.821,12.017c0.109,2.891,0.156,6.42-0.264,8.426l-0.002,0.002l0.001,0l0,0.001
		l0.002-0.001c2.006-0.42,5.535-0.373,8.426-0.264l0.381-2.418c0,0-2.295-0.13-4.351-0.158L19.637,5.98l-1.617-1.617L6.397,15.986
		c-0.028-2.056-0.158-4.351-0.158-4.351L3.821,12.017z"/>
</g>
</svg>
 */
