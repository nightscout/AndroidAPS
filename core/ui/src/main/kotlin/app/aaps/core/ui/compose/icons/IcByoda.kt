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
 * Icon for BYODA.
 * Represents Build Your Own Dexcom Application icon.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcByoda: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcByoda",
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
            moveTo(12f, 1.201f)
            curveTo(6.036f, 1.201f, 1.201f, 6.036f, 1.201f, 12f)
            reflectiveCurveTo(6.036f, 22.799f, 12f, 22.799f)
            reflectiveCurveTo(22.799f, 17.964f, 22.799f, 12f)
            verticalLineTo(1.201f)
            horizontalLineTo(12f)
            close()

            moveTo(12f, 20.208f)
            curveToRelative(-4.533f, 0f, -8.208f, -3.675f, -8.208f, -8.208f)
            curveToRelative(0f, -4.533f, 3.675f, -8.208f, 8.208f, -8.208f)
            curveToRelative(4.533f, 0f, 8.208f, 3.675f, 8.208f, 8.208f)
            curveTo(20.208f, 16.533f, 16.533f, 20.208f, 12f, 20.208f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcByodaIconPreview() {
    Icon(
        imageVector = IcByoda,
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
<g id="ic_byoda">
	<path display="inline" fill="#FFFFFF" d="M12,1.201C6.036,1.201,1.201,6.036,1.201,12S6.036,22.799,12,22.799
		S22.799,17.964,22.799,12V1.201H12z M12,20.208c-4.533,0-8.208-3.675-8.208-8.208c0-4.533,3.675-8.208,8.208-8.208
		c4.533,0,8.208,3.675,8.208,8.208C20.208,16.533,16.533,20.208,12,20.208z"/>
</g>
</svg>
 */