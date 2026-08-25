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
internal fun IcPluginOpenAPSIconPreview() {
    Icon(
        imageVector = IcPluginOpenAPS,
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
<g id="ic_plugin_openAps">
	<g display="inline">
		<path fill="#FFFFFF" d="M13.128,1.261c-0.75-0.081-1.507-0.081-2.257,0L10.571,3.39c-0.684,0.116-1.352,0.316-1.99,0.595
			L7.197,2.359C6.523,2.703,5.887,3.119,5.298,3.6l0.878,1.956c-0.514,0.475-0.97,1.01-1.358,1.595L2.79,6.546
			C2.407,7.206,2.092,7.907,1.853,8.635l1.778,1.162c-0.18,0.682-0.28,1.383-0.295,2.089l-2.028,0.605
			c0.028,0.767,0.136,1.529,0.321,2.273h2.113c0.211,0.673,0.5,1.317,0.862,1.92L3.22,18.31c0.431,0.63,0.927,1.212,1.478,1.736
			l1.778-1.163c0.535,0.45,1.12,0.833,1.745,1.141L7.92,22.153c0.697,0.293,1.423,0.51,2.166,0.647l0.878-1.956
			c0.689,0.084,1.385,0.084,2.074,0l0.878,1.956c0.742-0.138,1.468-0.354,2.166-0.647l-0.301-2.128
			c0.625-0.308,1.21-0.691,1.745-1.141l1.778,1.163c0.551-0.524,1.047-1.106,1.478-1.736l-1.384-1.625
			c0.362-0.602,0.651-1.247,0.862-1.92h2.113c0.186-0.744,0.293-1.506,0.321-2.273l-2.028-0.605
			c-0.016-0.706-0.115-1.408-0.295-2.089l1.778-1.162c-0.239-0.728-0.554-1.428-0.938-2.089l-2.028,0.606
			c-0.388-0.585-0.844-1.121-1.358-1.595L18.702,3.6c-0.588-0.481-1.224-0.897-1.898-1.242l-1.384,1.625
			c-0.638-0.279-1.306-0.478-1.99-0.595L13.128,1.261z M12,6.971c2.776,0,5.029,2.293,5.029,5.117c0,2.824-2.253,5.117-5.029,5.117
			s-5.029-2.293-5.029-5.117C6.971,9.264,9.224,6.971,12,6.971z"/>
	</g>
</g>
</svg>
 */
