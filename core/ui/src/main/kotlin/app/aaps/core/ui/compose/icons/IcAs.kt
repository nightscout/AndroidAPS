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
 * Icon for AutoSens.
 * Represents AutoSensitivity feature.
 *
 * Bounding box: x: 4.5-19.5, y: 1.6-22.4 (viewport: 24x24, ~90% height)
 */
val IcAs: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAs",
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
            moveTo(16.086f, 17.767f)
            verticalLineToRelative(-8.069f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(-3.454f)
            lineToRelative(4.604f, 4.593f)
            lineToRelative(4.604f, -4.593f)
            horizontalLineToRelative(-3.452f)
            close()

            moveTo(9.066f, 1.64f)
            lineTo(4.461f, 6.233f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineTo(6.233f)
            horizontalLineToRelative(3.453f)
            lineTo(9.066f, 1.64f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAsIconPreview() {
    Icon(
        imageVector = IcAs,
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
<g id="ic_as">
	<g>
		<path fill="#008585" d="M16.086,17.767V9.698h-2.302v8.069H10.33l4.604,4.593l4.604-4.593H16.086z M9.066,1.64L4.461,6.233h3.453
			v8.069h2.302V6.233h3.453L9.066,1.64z"/>
	</g>
</g>
</svg>
 */