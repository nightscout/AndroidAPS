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
internal fun IcArrowLeftPreview() {
    Icon(
        imageVector = IcArrowLeft,
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
<g id="ic_arrow_left">
	<path fill="#36FF00" d="M7.347,17.772c-1.967-2.121-4.43-4.65-6.145-5.771H1.2L1.201,12L1.2,11.999h0.002
		c1.715-1.121,4.178-3.65,6.145-5.771l1.979,1.44c0,0-1.53,1.715-2.964,3.188H22.8v2.286H6.362c1.434,1.474,2.964,3.189,2.964,3.189
		L7.347,17.772z"/>
</g>
</svg>
 */
