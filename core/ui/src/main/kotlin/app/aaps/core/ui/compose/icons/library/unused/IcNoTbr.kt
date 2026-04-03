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
 * Icon for No TBR (Temporary Basal Rate).
 * Represents absence of temporary basal rate.
 *
 * Bounding box: x: 1.2-22.8, y: 11.3-12.7 (viewport: 24x24, ~90% width)
 */
val IcNoTbr: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcNoTbr",
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
            moveTo(1.2f, 11.306f)
            horizontalLineToRelative(21.6f)
            verticalLineToRelative(1.387f)
            horizontalLineTo(1.2f)
            verticalLineTo(11.306f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcNoTbrIconPreview() {
    Icon(
        imageVector = IcNoTbr,
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
<g id="ic_no_tbr">
	<rect x="1.2" y="11.306" display="inline" fill="#CF8BFE" width="21.6" height="1.387"/>
</g>
</svg>
 */