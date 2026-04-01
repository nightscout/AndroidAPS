package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for Bolus treatment type.
 * Represents insulin bolus entries.
 *
 * replaces ic_bolus
 *
 * Bounding box: x: 1.2-22.6, y: 3.3-21.0 (viewport: 24x24, ~90% width)
 */
val IcBolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcBolus",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
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
            moveTo(18.733f, 12.133f)
            lineToRelative(-0.443f, 0.584f)
            lineTo(8.043f, 4.948f)
            curveToRelative(-0.095f, -0.072f, -0.22f, -0.093f, -0.334f, -0.057f)
            lineTo(4.87f, 5.803f)
            lineTo(1.2f, 3.329f)
            lineTo(4.497f, 6.29f)
            lineTo(4.385f, 9.275f)
            curveToRelative(-0.004f, 0.12f, 0.049f, 0.234f, 0.145f, 0.307f)
            lineToRelative(10.247f, 7.769f)
            lineToRelative(-0.443f, 0.584f)
            curveToRelative(-0.269f, 0.355f, -0.2f, 0.861f, 0.156f, 1.131f)
            curveToRelative(0.355f, 0.269f, 0.861f, 0.2f, 1.131f, -0.156f)
            lineToRelative(1.712f, -2.258f)
            lineToRelative(2.298f, 1.743f)
            lineToRelative(-0.744f, 0.982f)
            curveToRelative(-0.269f, 0.355f, -0.2f, 0.861f, 0.156f, 1.131f)
            curveToRelative(0.355f, 0.269f, 0.861f, 0.2f, 1.131f, -0.156f)
            lineToRelative(2.464f, -3.249f)
            curveToRelative(0.269f, -0.355f, 0.2f, -0.861f, -0.156f, -1.131f)
            curveToRelative(-0.355f, -0.269f, -0.861f, -0.2f, -1.131f, 0.156f)
            lineToRelative(-0.744f, 0.982f)
            lineToRelative(-2.298f, -1.743f)
            lineToRelative(1.712f, -2.258f)
            curveToRelative(0.269f, -0.355f, 0.2f, -0.861f, -0.156f, -1.131f)
            curveTo(19.508f, 11.708f, 19.002f, 11.777f, 18.733f, 12.133f)
            close()

            moveTo(5.225f, 6.46f)
            lineToRelative(2.527f, -0.811f)
            lineToRelative(10.095f, 7.653f)
            lineToRelative(-0.4f, 0.527f)
            lineToRelative(-5.959f, -4.518f)
            curveToRelative(-0.094f, -0.071f, -0.228f, -0.055f, -0.304f, 0.036f)
            lineToRelative(-2.201f, 2.689f)
            lineTo(8.29f, 11.511f)
            lineToRelative(1.58f, -1.899f)
            curveTo(9.872f, 9.61f, 9.875f, 9.607f, 9.876f, 9.605f)
            curveToRelative(0.071f, -0.093f, 0.056f, -0.226f, -0.035f, -0.302f)
            curveTo(9.749f, 9.225f, 9.61f, 9.237f, 9.532f, 9.331f)
            lineToRelative(-1.593f, 1.914f)
            lineToRelative(-0.797f, -0.604f)
            lineToRelative(1.58f, -1.899f)
            curveTo(8.724f, 8.74f, 8.727f, 8.737f, 8.728f, 8.734f)
            curveToRelative(0.071f, -0.093f, 0.056f, -0.226f, -0.035f, -0.302f)
            curveTo(8.601f, 8.355f, 8.462f, 8.367f, 8.384f, 8.46f)
            lineToRelative(-1.593f, 1.914f)
            lineTo(5.994f, 9.771f)
            lineToRelative(1.58f, -1.899f)
            curveTo(7.576f, 7.87f, 7.579f, 7.866f, 7.58f, 7.864f)
            curveToRelative(0.071f, -0.094f, 0.057f, -0.227f, -0.034f, -0.302f)
            curveTo(7.453f, 7.484f, 7.314f, 7.497f, 7.236f, 7.59f)
            lineTo(5.643f, 9.504f)
            lineTo(5.126f, 9.112f)
            lineTo(5.225f, 6.46f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcBolusIconPreview() {
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