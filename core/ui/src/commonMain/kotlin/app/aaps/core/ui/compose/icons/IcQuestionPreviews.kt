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
internal fun IcQuestionIconPreview() {
    Icon(
        imageVector = IcQuestion,
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
<g id="ic_question">
	<g display="inline">
		<circle fill="#FEAF05" cx="12.124" cy="21.349" r="1.451"/>
		<path fill="#FEAF05" d="M12.062,17.861c-0.58,0-1.05-0.47-1.05-1.051c0-3.776,1.76-5.094,3.175-6.153
			c1.185-0.888,2.042-1.529,2.042-3.605c0-2.862-2.749-3.752-3.75-3.752c-2.157,0-3.9,1.293-4.78,3.547
			C7.487,7.388,6.876,7.656,6.338,7.444c-0.541-0.211-0.807-0.82-0.596-1.361C6.936,3.025,9.454,1.2,12.479,1.2
			c2.359,0,5.852,1.86,5.852,5.852c0,3.127-1.599,4.324-2.885,5.286c-1.304,0.976-2.333,1.746-2.333,4.472
			C13.113,17.39,12.642,17.861,12.062,17.861z"/>
	</g>
</g>
</svg>
 */
