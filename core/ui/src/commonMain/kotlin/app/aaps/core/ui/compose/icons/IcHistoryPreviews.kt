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
internal fun IcHistoryIconPreview() {
    Icon(
        imageVector = IcHistory,
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
<g id="ic_history">
	<g display="inline">
		<path fill="#67DFE8" d="M13.198,2.399c-5.107,0-9.283,4.011-9.573,9.047L2.529,10.35c-0.305-0.304-0.797-0.304-1.101,0
			c-0.304,0.305-0.304,0.797,0,1.101l2.397,2.396c0.152,0.151,0.352,0.228,0.551,0.228c0.199,0,0.399-0.076,0.551-0.228l2.396-2.396
			c0.304-0.304,0.304-0.797,0-1.101s-0.797-0.304-1.101,0l-1.036,1.036c0.316-4.149,3.785-7.431,8.013-7.431
			c4.436,0,8.045,3.609,8.045,8.045c0,4.436-3.609,8.045-8.045,8.045c-2.19,0-4.239-0.869-5.77-2.448
			c-0.3-0.308-0.793-0.315-1.101-0.017c-0.309,0.299-0.316,0.793-0.017,1.101c1.827,1.883,4.273,2.92,6.888,2.92
			c5.294,0,9.602-4.307,9.602-9.602S18.493,2.399,13.198,2.399z"/>
		<path fill="#67DFE8" d="M13.198,12.778h4.348c0.43,0,0.778-0.349,0.778-0.778c0-0.43-0.348-0.778-0.778-0.778h-3.57V6.202
			c0-0.43-0.349-0.778-0.778-0.778S12.42,5.773,12.42,6.202V12C12.42,12.429,12.769,12.778,13.198,12.778z"/>
	</g>
</g>
</svg>
 */
