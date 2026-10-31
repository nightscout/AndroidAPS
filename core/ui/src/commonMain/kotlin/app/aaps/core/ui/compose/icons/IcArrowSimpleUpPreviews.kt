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
internal fun IcArrowSimpleUpIconPreview() {
    Icon(
        imageVector = IcArrowSimpleUp,
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
<g id="ic_arrow_simple_up">
	<path display="inline" fill="#36FF00" d="M17.772,7.347c-2.121-1.967-4.65-4.43-5.771-6.145V1.2L12,1.201L11.999,1.2l0,0.002
		c-1.121,1.715-3.65,4.178-5.771,6.145l1.44,1.979c0,0,1.715-1.53,3.188-2.964V22.8h2.286V6.362
		c1.474,1.434,3.189,2.964,3.189,2.964L17.772,7.347z"/>
</g>
</svg>
 */
