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
internal fun IcBolusIconPreview() {
    Icon(
        imageVector = IcBolus,
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
<g id="ic_bolus">
	<path display="inline" fill="#67DFE8" d="M18.733,12.133l-0.443,0.584L8.043,4.948c-0.095-0.072-0.22-0.093-0.334-0.057L4.87,5.803
		L1.2,3.329L4.497,6.29L4.385,9.275C4.381,9.395,4.434,9.509,4.53,9.582l10.247,7.769l-0.443,0.584
		c-0.269,0.355-0.2,0.861,0.156,1.131c0.355,0.269,0.861,0.2,1.131-0.156l1.712-2.258l2.298,1.743l-0.744,0.982
		c-0.269,0.355-0.2,0.861,0.156,1.131c0.355,0.269,0.861,0.2,1.131-0.156l2.464-3.249c0.269-0.355,0.2-0.861-0.156-1.131
		c-0.355-0.269-0.861-0.2-1.131,0.156l-0.744,0.982l-2.298-1.743l1.712-2.258c0.269-0.355,0.2-0.861-0.156-1.131
		C19.508,11.708,19.002,11.777,18.733,12.133z M5.225,6.46l2.527-0.811l10.095,7.653l-0.4,0.527l-5.959-4.518
		c-0.094-0.071-0.228-0.055-0.304,0.036l-2.201,2.689L8.29,11.511l1.58-1.899C9.872,9.61,9.875,9.607,9.876,9.605
		c0.071-0.093,0.056-0.226-0.035-0.302C9.749,9.225,9.61,9.237,9.532,9.331l-1.593,1.914l-0.797-0.604l1.58-1.899
		C8.724,8.74,8.727,8.737,8.728,8.734c0.071-0.093,0.056-0.226-0.035-0.302C8.601,8.355,8.462,8.367,8.384,8.46l-1.593,1.914
		L5.994,9.771l1.58-1.899C7.576,7.87,7.579,7.866,7.58,7.864C7.651,7.77,7.637,7.637,7.546,7.562
		C7.453,7.484,7.314,7.497,7.236,7.59L5.643,9.504L5.126,9.112L5.225,6.46z"/>
</g>
</svg>
 */
