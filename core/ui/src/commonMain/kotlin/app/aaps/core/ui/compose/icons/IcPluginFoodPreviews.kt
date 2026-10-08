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
internal fun IcPluginFoodIconPreview() {
    Icon(
        imageVector = IcPluginFood,
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
<g id="ic_plugin_food">
	<g display="inline">
		<path fill="#FFFFFF" d="M17.95,22.795h1.63c0.825,0,1.502-0.628,1.6-1.433l1.62-16.18h-4.909V1.205h-1.934v3.976h-4.88
			l0.295,2.297c1.679,0.461,3.25,1.296,4.192,2.219c1.414,1.394,2.386,2.837,2.386,5.194L17.95,22.795z M1.2,21.813v-0.972h14.757
			v0.972c0,0.54-0.442,0.982-0.992,0.982H2.192C1.642,22.795,1.2,22.353,1.2,21.813z M15.957,14.941c0-7.855-14.757-7.855-14.757,0
			H15.957z M1.22,16.914h14.727v1.964H1.22L1.22,16.914z"/>
	</g>
</g>
</svg>
 */
