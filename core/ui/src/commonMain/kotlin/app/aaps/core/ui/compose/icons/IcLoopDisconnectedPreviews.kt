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
internal fun IcLoopDisconnectedIconPreview() {
    Icon(
        imageVector = IcLoopDisconnected,
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
<g id="ic_loop_disconnected">
	<g display="inline">
		<path fill="#939393" d="M16.428,3.702c-1.542-1.017-3.386-1.612-5.371-1.612c-5.399,0-9.775,4.376-9.775,9.775
			c0,1.773,0.476,3.433,1.304,4.865l-0.313,0.359c-0.001,0.829,0.319,1.653,0.993,2.24l2.377,2.069l0.858-0.986l1.718,1.498
			l0.465-0.533l-1.718-1.498l1.251-1.437l1.72,1.5l0.465-0.533l-1.72-1.5l0.857-0.985l-2.377-2.069
			c-0.673-0.586-1.532-0.79-2.351-0.676l-0.273,0.313c-0.329-0.812-0.519-1.695-0.519-2.626c0-3.888,3.152-7.039,7.039-7.039
			c1.054,0,2.051,0.238,2.949,0.654c0.32,0.148,0.629,0.316,0.921,0.508l0.002-0.002l-0.346-1.755L16.428,3.702z"/>
		<path fill="#939393" d="M22.8,9.19l-5.687-3.903l-1.306,6.578l2.068-1.728c0.014,0.055,0.03,0.109,0.042,0.165
			c0.114,0.503,0.18,1.025,0.18,1.563c0,0.923-0.18,1.803-0.503,2.61l-0.259-0.297c-0.819-0.114-1.678,0.09-2.351,0.676
			l-2.377,2.069l3.895,4.475l2.377-2.069c0.674-0.587,0.995-1.411,0.993-2.24l-0.34-0.39c0.819-1.427,1.3-3.07,1.3-4.834
			c0-0.747-0.091-1.471-0.25-2.17c-0.039-0.173-0.084-0.344-0.132-0.514L22.8,9.19L22.8,9.19z"/>
	</g>
</g>
</svg>
 */
