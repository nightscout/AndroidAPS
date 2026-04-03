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
 * Icon for SMS Plugin.
 *
 * replacing ic_sms
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginSms: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginSms",
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
            moveTo(20.64f, 1.2f)
            horizontalLineTo(3.36f)
            curveToRelative(-1.188f, 0f, -2.149f, 0.972f, -2.149f, 2.16f)
            lineTo(1.2f, 22.8f)
            lineToRelative(4.32f, -4.32f)
            horizontalLineToRelative(15.12f)
            curveToRelative(1.188f, 0f, 2.16f, -0.972f, 2.16f, -2.16f)
            verticalLineTo(3.36f)
            curveTo(22.8f, 2.172f, 21.828f, 1.2f, 20.64f, 1.2f)
            close()

            moveTo(8.76f, 10.92f)
            horizontalLineTo(6.6f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()

            moveTo(13.08f, 10.92f)
            horizontalLineToRelative(-2.16f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()

            moveTo(17.4f, 10.92f)
            horizontalLineToRelative(-2.16f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginSmsIconPreview() {
    Icon(
        imageVector = IcPluginSms,
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
<g id="ic_plugin_sms">
	<g id="SMS" display="inline">
		<path fill="#FFFFFF" d="M20.64,1.2H3.36c-1.188,0-2.149,0.972-2.149,2.16L1.2,22.8l4.32-4.32h15.12c1.188,0,2.16-0.972,2.16-2.16
			V3.36C22.8,2.172,21.828,1.2,20.64,1.2z M8.76,10.92H6.6V8.76h2.16V10.92z M13.08,10.92h-2.16V8.76h2.16V10.92z M17.4,10.92h-2.16
			V8.76h2.16V10.92z"/>
	</g>
</g>
</svg>
 */