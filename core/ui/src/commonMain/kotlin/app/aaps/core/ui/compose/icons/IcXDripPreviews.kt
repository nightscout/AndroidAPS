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
internal fun IcXdripIconPreview() {
    Icon(
        imageVector = IcXDrip,
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
<g id="ic_xdrip">
	<path display="inline" fill="#B92929" d="M12.046,1.2c1.709,1.721,3.367,3.401,5.036,5.069c1.108,1.108,2.236,2.184,2.957,3.622
		c1.601,3.192,1.091,7.127-1.339,9.92c-2.272,2.611-6.202,3.666-9.472,2.543C2.791,20.143,0.95,12.334,5.736,7.479
		C7.786,5.399,9.875,3.358,12.046,1.2z M11.965,4.442C10.344,6.054,8.812,7.569,7.29,9.093c-1.479,1.481-2.126,3.275-1.986,5.359
		c0.228,3.373,3.434,6.299,6.662,6.041C11.965,15.185,11.965,9.873,11.965,4.442z"/>
</g>
</svg>
 */
