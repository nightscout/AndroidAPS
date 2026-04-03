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
 * Icon for Calibration treatment type.
 * Represents sensor calibration entries.
 *
 * replaces ic_calibration
 *
 * Bounding box: x: 1.2-22.8, y: 2.3-21.3 (viewport: 24x24, ~90% width)
 */
val IcCalibration: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCalibration",
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
            moveTo(6.327f, 2.286f)
            curveToRelative(2.218f, 3.627f, 5.391f, 6.697f, 4.873f, 11.263f)
            curveToRelative(-0.318f, 2.805f, -3.148f, 4.523f, -5.952f, 3.981f)
            curveToRelative(-2.669f, -0.516f, -4.511f, -3.168f, -3.946f, -5.884f)
            curveTo(2.037f, 8.118f, 4.127f, 5.288f, 6.327f, 2.286f)
            close()
            moveTo(3.558f, 9.23f)
            curveToRelative(-0.264f, 0.793f, -0.609f, 1.57f, -0.773f, 2.384f)
            curveToRelative(-0.255f, 1.265f, -0.081f, 2.481f, 0.951f, 3.399f)
            curveToRelative(0.369f, 0.328f, 0.846f, 0.44f, 1.292f, 0.095f)
            curveToRelative(0.301f, -0.233f, 0.335f, -0.573f, 0.119f, -0.861f)
            curveTo(4.041f, 12.766f, 3.499f, 11.131f, 3.558f, 9.23f)
            close()

            moveTo(19.586f, 2.392f)
            curveToRelative(1.335f, 1.809f, 2.58f, 3.53f, 3.098f, 5.644f)
            curveToRelative(0.348f, 1.422f, -0.085f, 2.614f, -1.291f, 3.438f)
            curveToRelative(-1.173f, 0.802f, -2.44f, 0.815f, -3.614f, 0.011f)
            curveToRelative(-1.204f, -0.824f, -1.64f, -2.035f, -1.295f, -3.447f)
            curveTo(16.999f, 5.928f, 18.237f, 4.2f, 19.586f, 2.392f)
            close()

            moveTo(15.679f, 14.66f)
            curveToRelative(0.992f, 1.362f, 1.91f, 2.618f, 2.264f, 4.175f)
            curveToRelative(0.234f, 1.028f, -0.12f, 1.865f, -0.976f, 2.446f)
            curveToRelative(-0.833f, 0.565f, -1.734f, 0.581f, -2.573f, 0.018f)
            curveToRelative(-0.857f, -0.575f, -1.226f, -1.407f, -0.996f, -2.438f)
            curveTo(13.753f, 17.282f, 14.663f, 16.002f, 15.679f, 14.66f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCalibrationIconPreview() {
    Icon(
        imageVector = IcCalibration,
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
<g id="ic_calibration">
	<g>
		<path fill="#E83258" d="M6.327,2.286c2.218,3.627,5.391,6.697,4.873,11.263c-0.318,2.805-3.148,4.523-5.952,3.981
			c-2.669-0.516-4.511-3.168-3.946-5.884C2.037,8.118,4.127,5.288,6.327,2.286z M3.558,9.23c-0.264,0.793-0.609,1.57-0.773,2.384
			c-0.255,1.265-0.081,2.481,0.951,3.399c0.369,0.328,0.846,0.44,1.292,0.095c0.301-0.233,0.335-0.573,0.119-0.861
			C4.041,12.766,3.499,11.131,3.558,9.23z"/>
		<path fill="#E83258" d="M19.586,2.392c1.335,1.809,2.58,3.53,3.098,5.644c0.348,1.422-0.085,2.614-1.291,3.438
			c-1.173,0.802-2.44,0.815-3.614,0.011c-1.204-0.824-1.64-2.035-1.295-3.447C16.999,5.928,18.237,4.2,19.586,2.392z"/>
		<path fill="#E83258" d="M15.679,14.66c0.992,1.362,1.91,2.618,2.264,4.175c0.234,1.028-0.12,1.865-0.976,2.446
			c-0.833,0.565-1.734,0.581-2.573,0.018c-0.857-0.575-1.226-1.407-0.996-2.438C13.753,17.282,14.663,16.002,15.679,14.66z"/>
	</g>
</g>
</svg>
 */