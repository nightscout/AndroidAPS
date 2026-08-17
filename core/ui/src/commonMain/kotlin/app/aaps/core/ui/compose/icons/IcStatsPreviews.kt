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
internal fun IcStatsIconPreview() {
    Icon(
        imageVector = IcStats,
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
<g id="ic_tdd">
	<g display="inline">
		<path fill="#FEAF05" d="M4.145,22.8c-0.805,0-1.458-0.653-1.458-1.458V11.96c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v9.382C5.602,22.147,4.949,22.8,4.145,22.8z"/>
		<path fill="#FEAF05" d="M9.381,22.8c-0.805,0-1.458-0.653-1.458-1.458V7.051c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v14.291C10.839,22.147,10.186,22.8,9.381,22.8z"/>
		<path fill="#FEAF05" d="M14.618,22.8c-0.805,0-1.458-0.653-1.458-1.458V8.979c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v12.363C16.076,22.147,15.423,22.8,14.618,22.8z"/>
		<path fill="#FEAF05" d="M19.855,22.8c-0.805,0-1.458-0.653-1.458-1.458V2.658c0-0.805,0.653-1.458,1.458-1.458
			c0.805,0,1.458,0.653,1.458,1.458v18.684C21.313,22.147,20.66,22.8,19.855,22.8z"/>
	</g>
</g>
</svg>
 */
