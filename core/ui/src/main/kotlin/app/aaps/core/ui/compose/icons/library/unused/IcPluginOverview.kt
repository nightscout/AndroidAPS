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
 * Icon for Overview Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 2.8-21.2 (viewport: 24x24, ~90% width)
 */
val IcPluginOverview: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOverview",
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
            moveTo(9.84f, 21.18f)
            verticalLineTo(14.7f)
            horizontalLineToRelative(4.32f)
            verticalLineToRelative(6.48f)
            horizontalLineToRelative(5.4f)
            verticalLineToRelative(-8.64f)
            horizontalLineToRelative(3.24f)
            lineTo(12f, 2.82f)
            lineTo(1.2f, 12.54f)
            horizontalLineToRelative(3.24f)
            verticalLineToRelative(8.64f)
            horizontalLineTo(9.84f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginOverviewIconPreview() {
    Icon(
        imageVector = IcPluginOverview,
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
<g id="ic_plugin_overview">
	<g id="Plugin_Overview">
		<path display="inline" fill="#FFFFFF" d="M9.84,21.18V14.7h4.32v6.48h5.4v-8.64h3.24L12,2.82L1.2,12.54h3.24v8.64H9.84z"/>
	</g>
</g>
</svg>
 */