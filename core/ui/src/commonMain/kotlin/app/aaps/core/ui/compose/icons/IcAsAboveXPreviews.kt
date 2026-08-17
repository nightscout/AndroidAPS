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
internal fun IcAsAboveXIconPreview() {
    Icon(
        imageVector = IcAsAboveX,
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
<g id="ic_as_above_x">
	<g>
		<path fill="#008585" d="M17.22,5.669l-4.604,4.593h3.453v8.069h2.302v-8.069h3.453L17.22,5.669z"/>
		<path fill="#008585" d="M10.073,14.547l-2.532-2.532l2.532-2.532c0.354-0.354,0.354-0.93,0.001-1.283
			c-0.172-0.172-0.4-0.267-0.642-0.267c-0.242,0-0.47,0.095-0.642,0.266l-2.532,2.532L3.725,8.199
			c-0.172-0.171-0.4-0.266-0.642-0.266c-0.243,0-0.471,0.095-0.641,0.267C2.088,8.554,2.088,9.13,2.443,9.484l2.532,2.532
			l-2.532,2.532c-0.172,0.171-0.266,0.399-0.266,0.641c-0.001,0.243,0.094,0.471,0.266,0.643c0.172,0.171,0.399,0.265,0.641,0.265
			s0.47-0.094,0.642-0.266l2.532-2.531l2.533,2.532c0.172,0.171,0.399,0.265,0.641,0.265c0.243,0,0.471-0.094,0.642-0.266
			c0.172-0.171,0.266-0.399,0.266-0.642C10.34,14.946,10.245,14.718,10.073,14.547z"/>
	</g>
</g>
</svg>
 */
