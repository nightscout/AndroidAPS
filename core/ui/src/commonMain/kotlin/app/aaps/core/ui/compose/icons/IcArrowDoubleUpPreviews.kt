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
internal fun IcArrowDoubleUpIconPreview() {
    Icon(
        imageVector = IcArrowDoubleUp,
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
<g id="ic_arrow_double_up">
	<g display="inline">
		<path fill="#36FF00" d="M1.895,7.347c2.121-1.967,4.65-4.43,5.771-6.145V1.2c0,0,0.001,0.001,0.001,0.001L7.668,1.2v0.002
			c1.121,1.715,3.65,4.178,5.771,6.145l-1.44,1.979c0,0-1.715-1.53-3.188-2.964V22.8H6.524V6.362
			C5.05,7.796,3.335,9.327,3.335,9.327L1.895,7.347z"/>
		<path fill="#36FF00" d="M10.561,7.347c2.121-1.967,4.65-4.43,5.771-6.145V1.2l0.001,0.001c0,0,0.001-0.001,0.001-0.001l0,0.002
			c1.121,1.715,3.65,4.178,5.771,6.145l-1.44,1.979c0,0-1.715-1.53-3.188-2.964V22.8H15.19V6.362
			c-1.474,1.434-3.189,2.964-3.189,2.964L10.561,7.347z"/>
	</g>
</g>
</svg>
 */
