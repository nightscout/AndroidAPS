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
 * Icon for AutoSens Below.
 * Represents AutoSensitivity below target range.
 *
 * Bounding box: x: 7.4-16.6, y: 5.7-18.3 (viewport: 24x24, ~53% height)
 */
val IcAsBelow: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsBelow",
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
            moveTo(13.151f, 13.738f)
            verticalLineTo(5.669f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineTo(7.396f)
            lineTo(12f, 18.331f)
            lineToRelative(4.604f, -4.593f)
            horizontalLineTo(13.151f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAsBelowIconPreview() {
    Icon(
        imageVector = IcAsBelow,
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
<g id="ic_as_below">
	<g>
		<path fill="#008585" d="M13.151,13.738V5.669h-2.302v8.069H7.396L12,18.331l4.604-4.593H13.151z"/>
	</g>
</g>
</svg>
 */