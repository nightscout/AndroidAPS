package app.aaps.core.ui.compose.icons.library.unused

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
 * Icon for Low TBR (Temporary Basal Rate).
 * Represents low temporary basal rate.
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 */
val IcTbrLow: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrLow",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFCF8BFE)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.681f, 20.281f)
            lineTo(12.681f, 5.106f)
            lineTo(1.2f, 5.106f)
            lineTo(1.2f, 3.719f)
            lineTo(14.068f, 3.719f)
            lineTo(14.068f, 18.893f)
            lineTo(19.151f, 18.893f)
            lineTo(19.151f, 3.719f)
            lineTo(22.8f, 3.719f)
            lineTo(22.8f, 5.106f)
            lineTo(20.539f, 5.106f)
            lineTo(20.539f, 20.281f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTbrLowIconPreview() {
    Icon(
        imageVector = IcTbrLow,
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
<g id="ic_tbr_low">
	<polygon display="inline" fill="#CF8BFE" points="12.681,20.281 12.681,5.106 1.2,5.106 1.2,3.719 14.068,3.719 14.068,18.893
		19.151,18.893 19.151,3.719 22.8,3.719 22.8,5.106 20.539,5.106 20.539,20.281 	"/>
</g>
</svg>
 */