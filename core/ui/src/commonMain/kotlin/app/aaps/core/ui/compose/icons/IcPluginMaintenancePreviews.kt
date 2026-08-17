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
internal fun IcPluginMaintenanceIconPreview() {
    Icon(
        imageVector = IcPluginMaintenance,
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
<g id="ic_plugin_maintenance">
	<g id="Maintenance" display="inline">
		<path fill="#FFFFFF" d="M11.195,9.574c0.559,1.506,1.709,2.721,3.164,3.354c-0.041,0.405-0.217,0.798-0.527,1.107l-8.171,8.171
			c-0.715,0.715-1.876,0.715-2.591,0l-1.335-1.335c-0.715-0.715-0.715-1.876,0-2.591l8.171-8.171
			C10.263,9.754,10.729,9.576,11.195,9.574z"/>
		<path fill="#FFFFFF" d="M22.798,7.029C22.8,7.071,22.8,7.114,22.8,7.156c0,3.256-2.594,5.9-5.789,5.9s-5.789-2.644-5.789-5.9
			c0-3.256,2.594-5.899,5.789-5.899c1.386,0,2.659,0.497,3.656,1.327l-4.452,2.651v2.91l2.562,1.355l0.032,0.053l0.031-0.02
			l0.037,0.02l0.01-0.05L22.798,7.029z"/>
	</g>
</g>
</svg>
 */
