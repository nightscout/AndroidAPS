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
internal fun IcPluginObjectivesIconPreview() {
    Icon(
        imageVector = IcPluginObjectives,
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
<g id="ic_plugin_objectives">
	<g display="inline">
		<path fill="#FFFFFF" d="M22.204,8.531L12.79,5.638c-0.513-0.158-1.067-0.158-1.579,0L1.796,8.531
			c-0.794,0.244-0.794,1.295,0,1.539l1.641,0.504c-0.36,0.445-0.582,0.988-0.603,1.583C2.509,12.343,2.28,12.679,2.28,13.08
			c0,0.364,0.192,0.67,0.468,0.866l-0.862,3.877C1.811,18.16,2.068,18.48,2.413,18.48h1.894c0.346,0,0.602-0.32,0.527-0.657
			l-0.862-3.877C4.248,13.75,4.44,13.444,4.44,13.08c0-0.39-0.218-0.717-0.529-0.907c0.026-0.507,0.285-0.955,0.698-1.239
			l6.601,2.028c0.306,0.094,0.892,0.211,1.579,0l9.415-2.892C22.999,9.825,22.999,8.775,22.204,8.531L22.204,8.531z M13.107,13.994
			c-0.963,0.296-1.783,0.132-2.214,0l-4.894-1.504L5.52,16.32c0,1.193,2.901,2.16,6.48,2.16s6.48-0.967,6.48-2.16l-0.479-3.83
			L13.107,13.994z"/>
	</g>
</g>
</svg>
 */
