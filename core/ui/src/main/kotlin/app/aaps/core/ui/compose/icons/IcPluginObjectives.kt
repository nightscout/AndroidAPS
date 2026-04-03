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
 * Icon for Objectives Plugin.
 * Represents objectives and goals tracking.
 *
 * replacing ic_graduation
 *
 * Bounding box: x: 1.8-22.2, y: 5.4-18.6 (viewport: 24x24, ~90% height)
 */
val IcPluginObjectives: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginObjectives",
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
            moveTo(22.204f, 8.531f)
            lineTo(12.79f, 5.638f)
            curveToRelative(-0.513f, -0.158f, -1.067f, -0.158f, -1.579f, 0f)
            lineTo(1.796f, 8.531f)
            curveToRelative(-0.794f, 0.244f, -0.794f, 1.295f, 0f, 1.539f)
            lineToRelative(1.641f, 0.504f)
            curveToRelative(-0.36f, 0.445f, -0.582f, 0.988f, -0.603f, 1.583f)
            curveTo(2.509f, 12.343f, 2.28f, 12.679f, 2.28f, 13.08f)
            curveToRelative(0f, 0.364f, 0.192f, 0.67f, 0.468f, 0.866f)
            lineToRelative(-0.862f, 3.877f)
            curveTo(1.811f, 18.16f, 2.068f, 18.48f, 2.413f, 18.48f)
            horizontalLineToRelative(1.894f)
            curveToRelative(0.346f, 0f, 0.602f, -0.32f, 0.527f, -0.657f)
            lineToRelative(-0.862f, -3.877f)
            curveTo(4.248f, 13.75f, 4.44f, 13.444f, 4.44f, 13.08f)
            curveToRelative(0f, -0.39f, -0.218f, -0.717f, -0.529f, -0.907f)
            curveToRelative(0.026f, -0.507f, 0.285f, -0.955f, 0.698f, -1.239f)
            lineToRelative(6.601f, 2.028f)
            curveToRelative(0.306f, 0.094f, 0.892f, 0.211f, 1.579f, 0f)
            lineToRelative(9.415f, -2.892f)
            curveTo(22.999f, 9.825f, 22.999f, 8.775f, 22.204f, 8.531f)
            lineTo(22.204f, 8.531f)
            close()

            moveTo(13.107f, 13.994f)
            curveToRelative(-0.963f, 0.296f, -1.783f, 0.132f, -2.214f, 0f)
            lineToRelative(-4.894f, -1.504f)
            lineTo(5.52f, 16.32f)
            curveToRelative(0f, 1.193f, 2.901f, 2.16f, 6.48f, 2.16f)
            reflectiveCurveToRelative(6.48f, -0.967f, 6.48f, -2.16f)
            lineToRelative(-0.479f, -3.83f)
            lineTo(13.107f, 13.994f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginObjectivesIconPreview() {
    Icon(
        imageVector = IcPluginObjectives,
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
<g id="ic_plugin_objectives">
	<g display="inline">
		<path fill="#FFFFFF" d="M22.204,8.531L12.79,5.638c-0.513-0.158-1.067-0.158-1.579,0L1.796,8.531
			c-0.794,0.244-0.794,1.295,0,1.539l1.641,0.504c-0.36,0.445-0.582,0.988-0.603,1.583C2.509,12.343,2.28,12.679,2.28,13.08
			c0,0.364,0.192,0.67,0.468,0.866l-0.862,3.877C1.811,18.16,2.068,18.48,2.413,18.48h1.894c0.346,0,0.602-0.32,0.527-0.657
			l-0.862-3.877C4.248,13.75,4.44,13.444,4.44,13.08c0-0.39-0.218-0.717-0.529-0.907c0.026-0.507,0.285-0.955,0.698-1.239
			l6.601,2.028c0.306,0.094,0.892,0.211,1.579,0l9.415-2.892C22.999,9.825,22.999,8.775,22.204,8.531L22.204,8.531z M13.107,13.994
			c-0.963,0.296-1.783,0.132-2.214,0l-4.894-1.504L5.52,16.32c0,1.193,2.901,2.16,6.48,2.16s6.48-0.967,6.48-2.16l-0.479-3.83
			L13.107,13.994z"/>
	</g>
</g>
</svg>
 */