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
 * Icon for High TBR (Temporary Basal Rate).
 * Represents high temporary basal rate.
 *
 * replacing ic_actions_start_temp_basal
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 */
val IcTbrHigh: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrHigh",
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
            moveTo(19.151f, 20.281f)
            lineTo(19.151f, 5.105f)
            lineTo(14.068f, 5.105f)
            lineTo(14.068f, 20.281f)
            lineTo(1.2f, 20.281f)
            lineTo(1.2f, 18.893f)
            lineTo(12.681f, 18.893f)
            lineTo(12.681f, 3.719f)
            lineTo(20.539f, 3.719f)
            lineTo(20.539f, 18.893f)
            lineTo(22.8f, 18.893f)
            lineTo(22.8f, 20.281f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTbrHighIconPreview() {
    Icon(
        imageVector = IcTbrHigh,
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
<g id="ic_tbr_high">
	<polygon fill="#CF8BFE" points="19.151,20.281 19.151,5.105 14.068,5.105 14.068,20.281 1.2,20.281 1.2,18.893 12.681,18.893
		12.681,3.719 20.539,3.719 20.539,18.893 22.8,18.893 22.8,20.281 	"/>
</g>
</svg>
 */