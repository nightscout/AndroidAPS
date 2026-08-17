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
internal fun IcArrowSimpleDownIconPreview() {
    Icon(
        imageVector = IcArrowSimpleDown,
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
<g id="ic_arrow_simple_down">
	<path display="inline" fill="#36FF00" d="M6.228,16.653c2.121,1.967,4.65,4.43,5.771,6.145V22.8L12,22.799l0.001,0.001v-0.002
		c1.121-1.715,3.65-4.178,5.771-6.145l-1.44-1.979c0,0-1.715,1.53-3.188,2.964V1.2h-2.286v16.438
		c-1.474-1.434-3.189-2.964-3.189-2.964L6.228,16.653z"/>
</g>
</svg>
 */
