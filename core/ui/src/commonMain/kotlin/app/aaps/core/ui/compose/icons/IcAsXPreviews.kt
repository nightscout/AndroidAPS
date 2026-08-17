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
internal fun IcAsXIconPreview() {
    Icon(
        imageVector = IcAsX,
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
<g id="ic_as_x">
	<g>
		<path fill="#008585" d="M19.347,17.767V9.698h-2.302v8.069h-3.453l4.604,4.593l4.604-4.593H19.347z M12.327,1.64L7.722,6.233
			h3.453v8.069h2.302V6.233h3.453L12.327,1.64z"/>
		<path fill="#008585" d="M9.097,14.547l-2.532-2.532l2.532-2.532C9.452,9.13,9.452,8.554,9.098,8.2
			c-0.172-0.172-0.4-0.267-0.642-0.267c-0.242,0-0.47,0.095-0.642,0.266l-2.532,2.532L2.749,8.199
			c-0.172-0.171-0.4-0.266-0.642-0.266c-0.243,0-0.471,0.095-0.641,0.267C1.112,8.554,1.112,9.13,1.466,9.484l2.532,2.532
			l-2.532,2.532C1.295,14.718,1.2,14.946,1.2,15.188c-0.001,0.243,0.094,0.471,0.266,0.643c0.172,0.171,0.399,0.265,0.641,0.265
			s0.47-0.094,0.642-0.266l2.532-2.531l2.533,2.532c0.172,0.171,0.399,0.265,0.641,0.265c0.243,0,0.471-0.094,0.642-0.266
			c0.172-0.171,0.266-0.399,0.266-0.642C9.364,14.946,9.269,14.718,9.097,14.547z"/>
	</g>
</g>
</svg>
 */
