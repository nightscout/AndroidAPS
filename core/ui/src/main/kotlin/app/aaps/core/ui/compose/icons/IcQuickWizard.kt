package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Quick Wizard.
 * Represents fast calculation or quick bolus wizard.
 *
 * Bounding box: x: 1.2-22.8, y: 5.5-18.5 (viewport: 24x24, ~90% width)
 */
val IcQuickwizard: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcQuickwizard",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(0f, 0f)
            horizontalLineTo(24f)
            verticalLineTo(24f)
            horizontalLineTo(0f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(10.874f, 5.812f)
            curveTo(10.251f, 6.578f, 9.99f, 6.985f, 9.486f, 7.806f)
            curveToRelative(-2.794f, 0.264f, -4.365f, 3.054f, -3.751f, 5.177f)
            curveToRelative(0.27f, 0.935f, 0.864f, 1.638f, 1.663f, 2.174f)
            curveToRelative(2.606f, 1.746f, 6.93f, 1.586f, 9.421f, -0.339f)
            curveToRelative(1.318f, -1.019f, 1.912f, -2.313f, 1.481f, -3.969f)
            curveToRelative(-0.457f, -1.754f, -1.604f, -2.869f, -3.373f, -3.185f)
            curveToRelative(-1.235f, -0.221f, -2.19f, 0.411f, -2.839f, 1.457f)
            curveToRelative(-0.548f, 0.884f, -0.799f, 1.794f, -1.808f, 2.603f)
            curveToRelative(-0.432f, -0.478f, -0.508f, -1.067f, -0.536f, -1.644f)
            curveToRelative(-0.133f, -2.76f, 2.242f, -4.927f, 5.1f, -4.676f)
            curveToRelative(2.626f, 0.23f, 4.957f, 2.171f, 5.676f, 4.724f)
            curveToRelative(0.679f, 2.411f, -0.251f, 4.919f, -2.431f, 6.559f)
            curveToRelative(-3.325f, 2.501f, -8.764f, 2.583f, -12.147f, 0.183f)
            curveToRelative(-2.284f, -1.62f, -3.197f, -4.308f, -2.33f, -6.86f)
            curveTo(4.515f, 7.352f, 7.118f, 5.494f, 9.784f, 5.6f)
            curveTo(10.119f, 5.614f, 10.445f, 5.666f, 10.874f, 5.812f)
            close()

            moveTo(6.866f, 13.378f)
            curveToRelative(0.584f, -0.114f, 1.308f, -0.244f, 1.848f, -0.357f)
            curveToRelative(1.689f, -0.354f, 2.914f, -1.365f, 3.633f, -2.973f)
            curveToRelative(0.321f, -0.718f, 0.779f, -1.249f, 1.608f, -1.576f)
            curveToRelative(0.464f, 0.901f, 0.504f, 1.821f, 0.248f, 2.767f)
            curveToRelative(-0.315f, 1.164f, -0.987f, 2.068f, -2.019f, 2.697f)
            curveToRelative(-0.684f, 0.417f, -1.425f, 0.708f, -2.182f, 0.963f)
            curveTo(8.723f, 15.328f, 7.594f, 14.895f, 6.866f, 13.378f)
            close()

            moveTo(12.607f, 14.262f)
            curveToRelative(0.815f, -0.596f, 1.388f, -1.209f, 1.63f, -1.789f)
            curveToRelative(0.992f, 0.176f, 2.132f, 0.348f, 3.112f, 0.522f)
            curveTo(16.74f, 14.924f, 14.393f, 15.372f, 12.607f, 14.262f)
            close()

            moveTo(1.578f, 13.906f)
            curveToRelative(0.199f, -0.137f, 0.839f, -0.21f, 1.323f, -0.136f)
            curveToRelative(0.288f, 0.701f, 0.652f, 1.36f, 1.23f, 2.134f)
            curveToRelative(-0.635f, 0.129f, -1.152f, 0.281f, -1.74f, 0.249f)
            curveToRelative(-0.547f, -0.03f, -1.102f, -0.391f, -1.179f, -1.097f)
            curveTo(1.174f, 14.715f, 1.211f, 14.158f, 1.578f, 13.906f)
            close()

            moveTo(20.189f, 15.392f)
            curveToRelative(0.218f, -0.552f, 0.402f, -1.067f, 0.622f, -1.566f)
            curveToRelative(0.261f, -0.59f, 0.687f, -0.844f, 1.244f, -0.756f)
            curveToRelative(0.465f, 0.074f, 0.639f, 0.417f, 0.715f, 0.839f)
            curveToRelative(0.109f, 0.6f, -0.086f, 1.098f, -0.587f, 1.347f)
            curveTo(21.562f, 15.565f, 20.904f, 15.472f, 20.189f, 15.392f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcQuickwizardIconPreview() {
    Icon(
        imageVector = IcQuickwizard,
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
<g id="ic_quickwizard">
	<path display="inline" fill="none" d="M0,0h24v24H0V0z"/>
	<g display="inline">
		<path fill="#FEAF05" d="M10.874,5.812C10.251,6.578,9.99,6.985,9.486,7.806c-2.794,0.264-4.365,3.054-3.751,5.177
			c0.27,0.935,0.864,1.638,1.663,2.174c2.606,1.746,6.93,1.586,9.421-0.339c1.318-1.019,1.912-2.313,1.481-3.969
			c-0.457-1.754-1.604-2.869-3.373-3.185c-1.235-0.221-2.19,0.411-2.839,1.457c-0.548,0.884-0.799,1.794-1.808,2.603
			c-0.432-0.478-0.508-1.067-0.536-1.644c-0.133-2.76,2.242-4.927,5.1-4.676c2.626,0.23,4.957,2.171,5.676,4.724
			c0.679,2.411-0.251,4.919-2.431,6.559c-3.325,2.501-8.764,2.583-12.147,0.183c-2.284-1.62-3.197-4.308-2.33-6.86
			C4.515,7.352,7.118,5.494,9.784,5.6C10.119,5.614,10.445,5.666,10.874,5.812z"/>
		<path fill="#FEAF05" d="M6.866,13.378c0.584-0.114,1.308-0.244,1.848-0.357c1.689-0.354,2.914-1.365,3.633-2.973
			c0.321-0.718,0.779-1.249,1.608-1.576c0.464,0.901,0.504,1.821,0.248,2.767c-0.315,1.164-0.987,2.068-2.019,2.697
			c-0.684,0.417-1.425,0.708-2.182,0.963C8.723,15.328,7.594,14.895,6.866,13.378z"/>
		<path fill="#FEAF05" d="M12.607,14.262c0.815-0.596,1.388-1.209,1.63-1.789c0.992,0.176,2.132,0.348,3.112,0.522
			C16.74,14.924,14.393,15.372,12.607,14.262z"/>
		<path fill="#FEAF05" d="M1.578,13.906c0.199-0.137,0.839-0.21,1.323-0.136c0.288,0.701,0.652,1.36,1.23,2.134
			c-0.635,0.129-1.152,0.281-1.74,0.249c-0.547-0.03-1.102-0.391-1.179-1.097C1.174,14.715,1.211,14.158,1.578,13.906z"/>
		<path fill="#FEAF05" d="M20.189,15.392c0.218-0.552,0.402-1.067,0.622-1.566c0.261-0.59,0.687-0.844,1.244-0.756
			c0.465,0.074,0.639,0.417,0.715,0.839c0.109,0.6-0.086,1.098-0.587,1.347C21.562,15.565,20.904,15.472,20.189,15.392z"/>
	</g>
</g>
</svg>
 */