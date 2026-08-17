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
internal fun IcByodaIconPreview() {
    Icon(
        imageVector = IcByoda,
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
<g id="ic_byoda">
	<path display="inline" fill="#FFFFFF" d="M12,1.201C6.036,1.201,1.201,6.036,1.201,12S6.036,22.799,12,22.799
		S22.799,17.964,22.799,12V1.201H12z M12,20.208c-4.533,0-8.208-3.675-8.208-8.208c0-4.533,3.675-8.208,8.208-8.208
		c4.533,0,8.208,3.675,8.208,8.208C20.208,16.533,16.533,20.208,12,20.208z"/>
</g>
</svg>
 */
