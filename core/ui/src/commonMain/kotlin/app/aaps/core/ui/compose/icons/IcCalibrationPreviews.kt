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
internal fun IcCalibrationIconPreview() {
    Icon(
        imageVector = IcCalibration,
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
<g id="ic_calibration">
	<g>
		<path fill="#E83258" d="M6.327,2.286c2.218,3.627,5.391,6.697,4.873,11.263c-0.318,2.805-3.148,4.523-5.952,3.981
			c-2.669-0.516-4.511-3.168-3.946-5.884C2.037,8.118,4.127,5.288,6.327,2.286z M3.558,9.23c-0.264,0.793-0.609,1.57-0.773,2.384
			c-0.255,1.265-0.081,2.481,0.951,3.399c0.369,0.328,0.846,0.44,1.292,0.095c0.301-0.233,0.335-0.573,0.119-0.861
			C4.041,12.766,3.499,11.131,3.558,9.23z"/>
		<path fill="#E83258" d="M19.586,2.392c1.335,1.809,2.58,3.53,3.098,5.644c0.348,1.422-0.085,2.614-1.291,3.438
			c-1.173,0.802-2.44,0.815-3.614,0.011c-1.204-0.824-1.64-2.035-1.295-3.447C16.999,5.928,18.237,4.2,19.586,2.392z"/>
		<path fill="#E83258" d="M15.679,14.66c0.992,1.362,1.91,2.618,2.264,4.175c0.234,1.028-0.12,1.865-0.976,2.446
			c-0.833,0.565-1.734,0.581-2.573,0.018c-0.857-0.575-1.226-1.407-0.996-2.438C13.753,17.282,14.663,16.002,15.679,14.66z"/>
	</g>
</g>
</svg>
 */
