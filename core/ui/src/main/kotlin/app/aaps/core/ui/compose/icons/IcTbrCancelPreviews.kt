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
internal fun IcTbrCancelIconPreview() {
    Icon(
        imageVector = IcTbrCancel,
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
<g id="ic_tbr_cancel">
	<g display="inline">
		<g>
			<path fill="#CF8BFE" d="M1.948,12.668c-0.175,0-0.35-0.067-0.484-0.2c-0.267-0.267-0.267-0.699,0.001-0.966l7.533-7.534
				c0.268-0.267,0.699-0.267,0.967,0.001c0.267,0.267,0.267,0.699-0.001,0.967l-7.533,7.533C2.297,12.602,2.123,12.668,1.948,12.668
				z"/>
			<path fill="#CF8BFE" d="M9.482,12.668c-0.175,0-0.35-0.067-0.484-0.2L1.465,4.936c-0.267-0.267-0.267-0.7-0.001-0.967
				c0.267-0.268,0.699-0.267,0.967-0.001l7.533,7.534c0.267,0.267,0.267,0.699,0.001,0.966C9.832,12.602,9.657,12.668,9.482,12.668z
				"/>
		</g>
		<polygon fill="#CF8BFE" points="19.151,20.281 19.151,5.105 14.068,5.105 14.068,20.281 1.2,20.281 1.2,18.893 12.681,18.893
			12.681,3.719 20.539,3.719 20.539,18.893 22.8,18.893 22.8,20.281 		"/>
	</g>
</g>
</svg>
 */
