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
 * Icon for OpenAPS Plugin.
 *
 * Bounding box: x: 1.8-22.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginOpenAPS: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOpenAPS",
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
            moveTo(13.128f, 1.261f)
            curveToRelative(-0.75f, -0.081f, -1.507f, -0.081f, -2.257f, 0f)
            lineTo(10.571f, 3.39f)
            curveToRelative(-0.684f, 0.116f, -1.352f, 0.316f, -1.99f, 0.595f)
            lineTo(7.197f, 2.359f)
            curveTo(6.523f, 2.703f, 5.887f, 3.119f, 5.298f, 3.6f)
            lineToRelative(0.878f, 1.956f)
            curveToRelative(-0.514f, 0.475f, -0.97f, 1.01f, -1.358f, 1.595f)
            lineTo(2.79f, 6.546f)
            curveTo(2.407f, 7.206f, 2.092f, 7.907f, 1.853f, 8.635f)
            lineToRelative(1.778f, 1.162f)
            curveToRelative(-0.18f, 0.682f, -0.28f, 1.383f, -0.295f, 2.089f)
            lineToRelative(-2.028f, 0.605f)
            curveToRelative(0.028f, 0.767f, 0.136f, 1.529f, 0.321f, 2.273f)
            horizontalLineToRelative(2.113f)
            curveToRelative(0.211f, 0.673f, 0.5f, 1.317f, 0.862f, 1.92f)
            lineTo(3.22f, 18.31f)
            curveToRelative(0.431f, 0.63f, 0.927f, 1.212f, 1.478f, 1.736f)
            lineToRelative(1.778f, -1.163f)
            curveToRelative(0.535f, 0.45f, 1.12f, 0.833f, 1.745f, 1.141f)
            lineTo(7.92f, 22.153f)
            curveToRelative(0.697f, 0.293f, 1.423f, 0.51f, 2.166f, 0.647f)
            lineToRelative(0.878f, -1.956f)
            curveToRelative(0.689f, 0.084f, 1.385f, 0.084f, 2.074f, 0f)
            lineToRelative(0.878f, 1.956f)
            curveToRelative(0.742f, -0.138f, 1.468f, -0.354f, 2.166f, -0.647f)
            lineToRelative(-0.301f, -2.128f)
            curveToRelative(0.625f, -0.308f, 1.21f, -0.691f, 1.745f, -1.141f)
            lineToRelative(1.778f, 1.163f)
            curveToRelative(0.551f, -0.524f, 1.047f, -1.106f, 1.478f, -1.736f)
            lineToRelative(-1.384f, -1.625f)
            curveToRelative(0.362f, -0.602f, 0.651f, -1.247f, 0.862f, -1.92f)
            horizontalLineToRelative(2.113f)
            curveToRelative(0.186f, -0.744f, 0.293f, -1.506f, 0.321f, -2.273f)
            lineToRelative(-2.028f, -0.605f)
            curveToRelative(-0.016f, -0.706f, -0.115f, -1.408f, -0.295f, -2.089f)
            lineToRelative(1.778f, -1.162f)
            curveToRelative(-0.239f, -0.728f, -0.554f, -1.428f, -0.938f, -2.089f)
            lineToRelative(-2.028f, 0.606f)
            curveToRelative(-0.388f, -0.585f, -0.844f, -1.121f, -1.358f, -1.595f)
            lineTo(18.702f, 3.6f)
            curveToRelative(-0.588f, -0.481f, -1.224f, -0.897f, -1.898f, -1.242f)
            lineToRelative(-1.384f, 1.625f)
            curveToRelative(-0.638f, -0.279f, -1.306f, -0.478f, -1.99f, -0.595f)
            lineTo(13.128f, 1.261f)
            close()

            moveTo(12f, 6.971f)
            curveToRelative(2.776f, 0f, 5.029f, 2.293f, 5.029f, 5.117f)
            curveToRelative(0f, 2.824f, -2.253f, 5.117f, -5.029f, 5.117f)
            reflectiveCurveToRelative(-5.029f, -2.293f, -5.029f, -5.117f)
            curveTo(6.971f, 9.264f, 9.224f, 6.971f, 12f, 6.971f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginOpenAPSIconPreview() {
    Icon(
        imageVector = IcPluginOpenAPS,
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
<g id="ic_plugin_openAps">
	<g display="inline">
		<path fill="#FFFFFF" d="M13.128,1.261c-0.75-0.081-1.507-0.081-2.257,0L10.571,3.39c-0.684,0.116-1.352,0.316-1.99,0.595
			L7.197,2.359C6.523,2.703,5.887,3.119,5.298,3.6l0.878,1.956c-0.514,0.475-0.97,1.01-1.358,1.595L2.79,6.546
			C2.407,7.206,2.092,7.907,1.853,8.635l1.778,1.162c-0.18,0.682-0.28,1.383-0.295,2.089l-2.028,0.605
			c0.028,0.767,0.136,1.529,0.321,2.273h2.113c0.211,0.673,0.5,1.317,0.862,1.92L3.22,18.31c0.431,0.63,0.927,1.212,1.478,1.736
			l1.778-1.163c0.535,0.45,1.12,0.833,1.745,1.141L7.92,22.153c0.697,0.293,1.423,0.51,2.166,0.647l0.878-1.956
			c0.689,0.084,1.385,0.084,2.074,0l0.878,1.956c0.742-0.138,1.468-0.354,2.166-0.647l-0.301-2.128
			c0.625-0.308,1.21-0.691,1.745-1.141l1.778,1.163c0.551-0.524,1.047-1.106,1.478-1.736l-1.384-1.625
			c0.362-0.602,0.651-1.247,0.862-1.92h2.113c0.186-0.744,0.293-1.506,0.321-2.273l-2.028-0.605
			c-0.016-0.706-0.115-1.408-0.295-2.089l1.778-1.162c-0.239-0.728-0.554-1.428-0.938-2.089l-2.028,0.606
			c-0.388-0.585-0.844-1.121-1.358-1.595L18.702,3.6c-0.588-0.481-1.224-0.897-1.898-1.242l-1.384,1.625
			c-0.638-0.279-1.306-0.478-1.99-0.595L13.128,1.261z M12,6.971c2.776,0,5.029,2.293,5.029,5.117c0,2.824-2.253,5.117-5.029,5.117
			s-5.029-2.293-5.029-5.117C6.971,9.264,9.224,6.971,12,6.971z"/>
	</g>
</g>
</svg>
 */