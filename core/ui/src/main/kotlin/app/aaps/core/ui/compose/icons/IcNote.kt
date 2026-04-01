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
 * Icon for Note treatment type.
 * Represents general notes or comments.
 *
 * replaces ic_cp_note
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% width)
 */
val IcNote: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcNote",
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
            moveTo(14.934f, 1.727f)
            lineTo(2.84f, 13.822f)
            curveToRelative(-0.096f, 0.096f, -0.161f, 0.22f, -0.186f, 0.354f)
            lineTo(1.211f, 22.005f)
            curveToRelative(-0.04f, 0.218f, 0.029f, 0.442f, 0.186f, 0.598f)
            curveToRelative(0.157f, 0.157f, 0.38f, 0.226f, 0.598f, 0.186f)
            lineToRelative(7.829f, -1.443f)
            curveToRelative(0.134f, -0.025f, 0.258f, -0.09f, 0.354f, -0.186f)
            lineTo(22.273f, 9.066f)
            curveToRelative(0.705f, -0.705f, 0.703f, -1.854f, -0.004f, -2.561f)
            lineToRelative(-4.772f, -4.772f)
            curveTo(16.789f, 1.024f, 15.639f, 1.022f, 14.934f, 1.727f)
            close()

            moveTo(8.57f, 19.491f)
            curveToRelative(0.154f, 0.154f, 0.21f, 0.381f, 0.144f, 0.589f)
            curveToRelative(-0.029f, 0.091f, -0.079f, 0.172f, -0.144f, 0.237f)
            curveToRelative(-0.083f, 0.083f, -0.191f, 0.141f, -0.311f, 0.162f)
            lineToRelative(-3.901f, 0.689f)
            curveToRelative(-0.387f, -0.012f, -0.771f, -0.163f, -1.066f, -0.459f)
            curveToRelative(-0.296f, -0.296f, -0.447f, -0.679f, -0.459f, -1.066f)
            lineToRelative(0.689f, -3.901f)
            curveToRelative(0.038f, -0.215f, 0.192f, -0.39f, 0.4f, -0.455f)
            curveToRelative(0.208f, -0.065f, 0.434f, -0.01f, 0.589f, 0.144f)
            lineTo(8.57f, 19.491f)
            close()

            moveTo(21.263f, 7.358f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(10.575f, 18.852f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(20.036f, 6.938f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(21.263f, 7.358f)
            close()

            moveTo(19.179f, 5.241f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(8.492f, 16.735f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(17.952f, 4.821f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(19.179f, 5.241f)
            close()

            moveTo(17.095f, 3.124f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(6.408f, 14.618f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(15.869f, 2.704f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(17.095f, 3.124f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcNoteIconPreview() {
    Icon(
        imageVector = IcNote,
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
<g id="ic_note">
	<path display="inline" fill="#FEAF05" d="M14.934,1.727L2.84,13.822c-0.096,0.096-0.161,0.22-0.186,0.354l-1.443,7.829
		c-0.04,0.218,0.029,0.442,0.186,0.598c0.157,0.157,0.38,0.226,0.598,0.186l7.829-1.443c0.134-0.025,0.258-0.09,0.354-0.186
		L22.273,9.066c0.705-0.705,0.703-1.854-0.004-2.561l-4.772-4.772C16.789,1.024,15.639,1.022,14.934,1.727z M8.57,19.491
		c0.154,0.154,0.21,0.381,0.144,0.589c-0.029,0.091-0.079,0.172-0.144,0.237c-0.083,0.083-0.191,0.141-0.311,0.162l-3.901,0.689
		c-0.387-0.012-0.771-0.163-1.066-0.459c-0.296-0.296-0.447-0.679-0.459-1.066l0.689-3.901c0.038-0.215,0.192-0.39,0.4-0.455
		c0.208-0.065,0.434-0.01,0.589,0.144L8.57,19.491z M21.263,7.358c0.223,0.223,0.223,0.584,0,0.807L10.575,18.852
		c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42c-0.223-0.223-0.223-0.584,0-0.807L20.036,6.938c0.223-0.223,0.584-0.223,0.807,0
		L21.263,7.358z M19.179,5.241c0.223,0.223,0.223,0.584,0,0.807L8.492,16.735c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42
		c-0.223-0.223-0.223-0.584,0-0.807L17.952,4.821c0.223-0.223,0.584-0.223,0.807,0L19.179,5.241z M17.095,3.124
		c0.223,0.223,0.223,0.584,0,0.807L6.408,14.618c-0.223,0.223-0.584,0.223-0.807,0l-0.42-0.42c-0.223-0.223-0.223-0.584,0-0.807
		L15.869,2.704c0.223-0.223,0.584-0.223,0.807,0L17.095,3.124z"/>
</g>
</svg>
 */