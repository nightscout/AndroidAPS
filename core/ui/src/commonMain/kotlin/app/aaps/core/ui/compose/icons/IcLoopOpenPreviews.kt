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
internal fun IcLoopOpenIconPreview() {
    Icon(
        imageVector = IcLoopOpen,
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
<g id="ic_loop_open">
	<g display="inline">
		<path fill="#4983D7" d="M5.437,7.639c0.401-0.533,0.875-1.006,1.409-1.405L5.468,3.852C4.528,4.509,3.71,5.327,3.052,6.266
			L5.437,7.639z"/>
		<path fill="#4983D7" d="M2.214,7.722c-0.477,1.017-0.788,2.125-0.888,3.296l2.749-0.003C4.156,10.34,4.329,9.693,4.588,9.09
			L2.214,7.722z"/>
		<path fill="#4983D7" d="M19.907,7.733l-2.372,1.373c0.258,0.604,0.429,1.252,0.509,1.928l2.747-0.003
			C20.691,9.86,20.383,8.75,19.907,7.733z"/>
		<path fill="#4983D7" d="M16.689,7.654l2.382-1.378c-0.657-0.94-1.475-1.758-2.414-2.416l-1.374,2.385
			C15.816,6.646,16.289,7.12,16.689,7.654z"/>
		<path fill="#4983D7" d="M18.041,12.714c-0.42,3.486-3.384,6.19-6.983,6.19c-3.606,0-6.574-2.713-6.986-6.209l-2.747,0.003
			c0.424,5.008,4.616,8.942,9.733,8.942c5.113,0,9.303-3.927,9.732-8.929L18.041,12.714z"/>
		<path fill="#4983D7" d="M8.299,5.388c0.603-0.257,1.251-0.429,1.927-0.509l-0.003-2.747C9.052,2.231,7.943,2.54,6.926,3.016
			L8.299,5.388z"/>
		<path fill="#4983D7" d="M11.906,4.882c0.676,0.081,1.323,0.255,1.926,0.514L15.2,3.021c-1.017-0.477-2.125-0.788-3.297-0.888
			L11.906,4.882z"/>
	</g>
</g>
</svg>
 */
