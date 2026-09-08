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
internal fun IcLoopPausedIconPreview() {
    Icon(
        imageVector = IcLoopPaused,
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
<g id="ic_loop_paused">
	<g display="inline">
		<path fill="#FFFF13" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,3.888-3.152,7.039-7.039,7.039c-3.888,0-7.039-3.152-7.039-7.039
			c0-3.888,3.152-7.039,7.039-7.039c1.054,0,2.051,0.238,2.949,0.654c0.32,0.148,0.629,0.316,0.921,0.508l0.002-0.002l-0.346-1.755
			l1.845-0.529c-1.542-1.017-3.386-1.612-5.371-1.612c-5.399,0-9.775,4.376-9.775,9.775c0,5.399,4.376,9.775,9.775,9.775
			c5.399,0,9.775-4.376,9.775-9.775c0-0.747-0.091-1.471-0.25-2.17c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
		<rect x="7.861" y="8.315" fill="#FFFF13" width="2.09" height="6.978"/>
		<rect x="12.041" y="8.315" fill="#FFFF13" width="2.09" height="6.978"/>
	</g>
</g>
</svg>
 */
