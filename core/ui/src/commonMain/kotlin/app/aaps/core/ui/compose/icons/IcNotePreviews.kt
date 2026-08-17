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
internal fun IcNoteIconPreview() {
    Icon(
        imageVector = IcNote,
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
<g id="ic_note">
	<path display="inline" fill="#FEAF05" d="M14.934,1.727L2.84,13.822c-0.096,0.096-0.161,0.22-0.186,0.354l-1.443,7.829
		c-0.04,0.218,0.029,0.442,0.186,0.598c0.157,0.157,0.38,0.226,0.598,0.186l7.829-1.443c0.134-0.025,0.258-0.09,0.354-0.186
		L22.273,9.066c0.705-0.705,0.703-1.854-0.004-2.561l-4.772-4.772C16.789,1.024,15.639,1.022,14.934,1.727z M8.57,19.491
		c0.154,0.154,0.21,0.381,0.144,0.589c-0.029,0.091-0.079,0.172-0.144,0.237c-0.083,0.083-0.191,0.141-0.311,0.162l-3.901,0.689
		c-0.387-0.012-0.771-0.163-1.066-0.459c-0.296-0.296-0.447-0.679-0.459-1.066l0.689-3.901c0.038-0.215,0.192-0.39,0.4-0.455
		c0.208-0.065,0.434-0.01,0.589,0.144L8.57,19.491z M21.263,7.358c0.223,0.223,0.223,0.584,0,0.807L10.575,18.852
		c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42c-0.223-0.223-0.223-0.584,0-0.807L20.036,6.938c0.223-0.223,0.584-0.223,0.807,0
		L21.263,7.358z M19.179,5.241c0.223,0.223,0.223,0.584,0,0.807L8.492,16.735c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42
		c-0.223-0.223-0.223-0.584,0-0.807L17.952,4.821c0.223-0.223,0.584-0.223,0.807,0L19.179,5.241z M17.095,3.124
		c0.223,0.223,0.223,0.584,0,0.807L6.408,14.618c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42c-0.223-0.223-0.223-0.584,0-0.807
		L15.869,2.704c0.223-0.223,0.584-0.223,0.807,0L17.095,3.124z"/>
</g>
</svg>
 */
