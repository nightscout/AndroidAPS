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
 * Icon for Left Up Arrow.
 * Represents left-upward diagonal trend.
 *
 * Bounding box: x: 3.5-19.6, y: 3.5-20.5 (viewport: 24x24, ~84% width)
 */
val IcArrowLeftUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowLeftUp",
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
            moveTo(3.821f, 12.791f)
            curveToRelative(0.109f, -2.891f, 0.156f, -6.42f, -0.264f, -8.426f)
            lineTo(3.555f, 4.364f)
            lineToRelative(0.001f, 0f)
            lineToRelative(0f, -0.001f)
            lineToRelative(0.002f, 0.001f)
            curveToRelative(2.006f, 0.42f, 5.535f, 0.373f, 8.426f, 0.264f)
            lineToRelative(0.381f, 2.418f)
            curveToRelative(0f, 0f, -2.295f, 0.13f, -4.351f, 0.158f)
            lineToRelative(11.623f, 11.623f)
            lineToRelative(-1.617f, 1.617f)
            lineTo(6.397f, 8.822f)
            curveToRelative(-0.028f, 2.056f, -0.158f, 4.351f, -0.158f, 4.351f)
            lineTo(3.821f, 12.791f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowLeftUpIconPreview() {
    Icon(
        imageVector = IcArrowLeftUp,
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
<g id="ic_arrow_left_up">
	<path display="inline" fill="#36FF00" d="M3.821,12.791c0.109-2.891,0.156-6.42-0.264-8.426L3.555,4.364l0.001,0l0-0.001
		l0.002,0.001c2.006,0.42,5.535,0.373,8.426,0.264l0.381,2.418c0,0-2.295,0.13-4.351,0.158l11.623,11.623l-1.617,1.617L6.397,8.822
		c-0.028,2.056-0.158,4.351-0.158,4.351L3.821,12.791z"/>
</g>
</svg>
 */