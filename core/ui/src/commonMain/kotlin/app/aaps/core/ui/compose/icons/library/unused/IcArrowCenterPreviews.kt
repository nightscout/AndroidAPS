package app.aaps.core.ui.compose.icons.library.unused

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
internal fun IcArrowCenterIconPreview() {
    Icon(
        imageVector = IcArrowCenter,
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
<g id="ic_arrow_center">
	<g>
		<path d="M12,1.2C6.035,1.2,1.2,6.035,1.2,12S6.035,22.8,12,22.8S22.8,17.965,22.8,12S17.965,1.2,12,1.2z M12,19.312
			c-4.038,0-7.312-3.274-7.312-7.312S7.962,4.688,12,4.688S19.312,7.962,19.312,12S16.038,19.312,12,19.312z"/>
		<circle cx="12" cy="12" r="1.744"/>
	</g>
</g>
</svg>
 */
