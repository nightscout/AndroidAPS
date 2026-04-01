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
 * Icon for Forty-Five Degrees Down Arrow.
 * Represents forty-five degree downward trend.
 *
 * Bounding box: x: 3.5-19.6, y: 3.5-20.5 (viewport: 24x24, ~84% width)
 */
val IcArrowFortyfiveDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowFortyfiveDown",
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
            moveTo(11.209f, 19.372f)
            curveToRelative(2.891f, -0.109f, 6.42f, -0.156f, 8.426f, 0.264f)
            lineToRelative(0.002f, 0.002f)
            lineToRelative(0f, -0.001f)
            lineToRelative(0.001f, 0f)
            lineToRelative(-0.001f, -0.002f)
            curveToRelative(-0.42f, -2.006f, -0.373f, -5.535f, -0.264f, -8.426f)
            lineToRelative(-2.418f, -0.381f)
            curveToRelative(0f, 0f, -0.13f, 2.295f, -0.158f, 4.351f)
            lineTo(5.172f, 3.555f)
            lineTo(3.555f, 5.172f)
            lineToRelative(11.623f, 11.623f)
            curveToRelative(-2.056f, 0.028f, -4.351f, 0.158f, -4.351f, 0.158f)
            lineTo(11.209f, 19.372f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowFortyfiveDownIconPreview() {
    Icon(
        imageVector = IcArrowFortyfiveDown,
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
<g id="ic_arrow_fortyfive_down">
	<path display="inline" fill="#36FF00" d="M11.209,19.372c2.891-0.109,6.42-0.156,8.426,0.264l0.002,0.002l0-0.001l0.001,0
		l-0.001-0.002c-0.42-2.006-0.373-5.535-0.264-8.426l-2.418-0.381c0,0-0.13,2.295-0.158,4.351L5.172,3.555L3.555,5.172
		l11.623,11.623c-2.056,0.028-4.351,0.158-4.351,0.158L11.209,19.372z"/>
</g>
</svg>
 */