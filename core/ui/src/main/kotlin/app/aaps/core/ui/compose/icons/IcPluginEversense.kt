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
 * Icon for Eversense CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginEversense: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginEversense",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Circle with opacity 0.9
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.9f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.669f, 12.806f)
            arcToRelative(2.669f, 2.669f, 0f, true, true, -5.338f, 0f)
            arcToRelative(2.669f, 2.669f, 0f, true, true, 5.338f, 0f)
            close()
        }

        // Small rectangle (path) with opacity 0.8
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.779f, 4.934f)
            curveToRelative(0f, 0.134f, -0.108f, 0.242f, -0.242f, 0.242f)
            horizontalLineToRelative(-1.075f)
            curveToRelative(-0.134f, 0f, -0.242f, -0.108f, -0.242f, -0.242f)
            lineToRelative(0f, 0f)
            curveToRelative(0f, -0.134f, 0.108f, -0.242f, 0.242f, -0.242f)
            horizontalLineToRelative(1.075f)
            curveToRelative(0.134f, 0f, 0.242f, 0.108f, 0.242f, 0.242f)
            lineTo(12.779f, 4.934f)
            close()
        }

        // Main shape (outer circle and inner rectangle) with opacity 1
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
            moveTo(12f, 1.2f)
            curveTo(5.221f, 1.2f, 3.537f, 6.035f, 3.537f, 12f)
            reflectiveCurveTo(5.185f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(20.463f, 17.965f, 20.463f, 12f)
            reflectiveCurveTo(18.958f, 1.2f, 12f, 1.2f)
            close()
            moveTo(11.463f, 4.693f)
            horizontalLineToRelative(1.075f)
            curveToRelative(0.134f, 0f, 0.242f, 0.108f, 0.242f, 0.242f)
            curveToRelative(0f, 0.134f, -0.108f, 0.242f, -0.242f, 0.242f)
            horizontalLineToRelative(-1.075f)
            curveToRelative(-0.134f, 0f, -0.242f, -0.108f, -0.242f, -0.242f)
            curveToRelative(0f, -0.134f, 0.108f, -0.242f, 0.242f, -0.242f)
            close()
            moveTo(12f, 15.475f)
            curveToRelative(-1.474f, 0f, -2.669f, -1.195f, -2.669f, -2.669f)
            reflectiveCurveTo(10.526f, 10.137f, 12f, 10.137f)
            reflectiveCurveTo(14.669f, 11.332f, 14.669f, 12.806f)
            reflectiveCurveTo(13.474f, 15.475f, 12f, 15.475f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginEversensePreview() {
    Icon(
        imageVector = IcPluginEversense,
        contentDescription = "Eversense Plugin Icon",
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
<g id="Eversense">
	<g>
		<circle opacity="0.9" fill="#FFFFFF" cx="12" cy="12.806" r="2.669"/>
		<path opacity="0.8" fill="#FFFFFF" d="M12.779,4.934c0,0.134-0.108,0.242-0.242,0.242h-1.075c-0.134,0-0.242-0.108-0.242-0.242
			l0,0c0-0.134,0.108-0.242,0.242-0.242h1.075C12.671,4.693,12.779,4.801,12.779,4.934L12.779,4.934z"/>
		<path fill="#FFFFFF" d="M12,1.2C5.221,1.2,3.537,6.035,3.537,12S5.185,22.8,12,22.8c6.761,0,8.463-4.835,8.463-10.8
			S18.958,1.2,12,1.2z M11.463,4.693h1.075c0.134,0,0.242,0.108,0.242,0.242c0,0.134-0.108,0.242-0.242,0.242h-1.075
			c-0.134,0-0.242-0.108-0.242-0.242C11.221,4.801,11.329,4.693,11.463,4.693z M12,15.475c-1.474,0-2.669-1.195-2.669-2.669
			s1.195-2.669,2.669-2.669s2.669,1.195,2.669,2.669S13.474,15.475,12,15.475z"/>
	</g>
</g>
</svg>
 */