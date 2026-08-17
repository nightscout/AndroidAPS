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
internal fun IcGenericCgmIconPreview() {
    Icon(
        imageVector = IcGenericCgm,
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
<g id="Plugin_generic_CGM">
	<path fill="#FFFFFF" d="M12,1.2C6.035,1.2,1.2,6.035,1.2,12S6.035,22.8,12,22.8S22.8,17.965,22.8,12S17.965,1.2,12,1.2z
		 M7.098,14.67c-0.414,0.323-0.94,0.485-1.577,0.485c-0.788,0-1.436-0.277-1.943-0.831c-0.508-0.554-0.762-1.311-0.762-2.271
		c0-1.016,0.255-1.805,0.766-2.367s1.181-0.843,2.013-0.843c0.726,0,1.316,0.221,1.77,0.662c0.27,0.261,0.472,0.636,0.607,1.124
		l-1.187,0.292c-0.07-0.316-0.217-0.566-0.439-0.75S5.852,9.897,5.533,9.897c-0.44,0-0.797,0.163-1.071,0.487
		c-0.274,0.325-0.411,0.851-0.411,1.578c0,0.771,0.135,1.321,0.405,1.648s0.621,0.491,1.053,0.491c0.319,0,0.593-0.104,0.822-0.313
		c0.229-0.208,0.394-0.535,0.494-0.981l1.162,0.378C7.809,13.852,7.513,14.347,7.098,14.67z M14.361,14.21
		c-0.251,0.25-0.614,0.47-1.091,0.66c-0.477,0.189-0.959,0.285-1.448,0.285c-0.621,0-1.162-0.135-1.624-0.402
		c-0.461-0.268-0.809-0.65-1.041-1.148c-0.232-0.498-0.348-1.04-0.348-1.626c0-0.635,0.129-1.2,0.389-1.694
		c0.259-0.494,0.638-0.873,1.138-1.136c0.381-0.203,0.854-0.304,1.421-0.304c0.737,0,1.313,0.159,1.728,0.477
		c0.414,0.317,0.681,0.757,0.8,1.317l-1.191,0.229c-0.084-0.3-0.24-0.537-0.471-0.71c-0.231-0.173-0.52-0.26-0.865-0.26
		c-0.524,0-0.94,0.171-1.249,0.512s-0.464,0.848-0.464,1.52c0,0.725,0.157,1.268,0.47,1.63s0.724,0.543,1.231,0.543
		c0.251,0,0.503-0.051,0.755-0.152c0.253-0.101,0.469-0.224,0.649-0.368v-0.773h-1.372v-1.029h2.583V14.21z M20.07,15.051v-4.804
		l-1.178,4.804h-1.154l-1.174-4.804v4.804H15.45V8.949h1.794l1.077,4.163l1.065-4.163h1.798v6.103H20.07z"/>
</g>
</svg>
 */
