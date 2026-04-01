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
 * Icon for Extended Bolus.
 * Represents extended or multi-wave insulin bolus.
 *
 * replaces ic_actions_start_extended_bolus
 *
 * Bounding box: x: 3.0-21.2, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcExtendedBolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcExtendedBolus",
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
            moveTo(17.07f, 5.852f)
            lineToRelative(0.482f, -0.934f)
            curveToRelative(0.245f, -0.474f, 0.059f, -1.058f, -0.416f, -1.303f)
            curveToRelative(-0.478f, -0.247f, -1.058f, -0.059f, -1.303f, 0.416f)
            lineToRelative(-0.483f, 0.935f)
            curveToRelative(-0.517f, -0.202f, -1.054f, -0.362f, -1.61f, -0.469f)
            verticalLineTo(3.681f)
            curveToRelative(0.614f, -0.083f, 1.094f, -0.588f, 1.094f, -1.224f)
            curveToRelative(0f, -0.694f, -0.562f, -1.257f, -1.256f, -1.257f)
            horizontalLineToRelative(-3.155f)
            curveToRelative(-0.694f, 0f, -1.257f, 0.563f, -1.257f, 1.257f)
            curveToRelative(0f, 0.637f, 0.481f, 1.141f, 1.095f, 1.224f)
            verticalLineToRelative(0.816f)
            curveToRelative(-4.263f, 0.817f, -7.496f, 4.569f, -7.496f, 9.067f)
            curveToRelative(0f, 5.092f, 4.144f, 9.236f, 9.236f, 9.236f)
            curveToRelative(5.092f, 0f, 9.236f, -4.144f, 9.236f, -9.236f)
            curveTo(21.236f, 10.343f, 19.576f, 7.506f, 17.07f, 5.852f)
            close()

            moveTo(12f, 21.436f)
            curveToRelative(-4.341f, 0f, -7.872f, -3.531f, -7.872f, -7.872f)
            reflectiveCurveTo(7.66f, 5.692f, 12f, 5.692f)
            reflectiveCurveToRelative(7.872f, 3.531f, 7.872f, 7.872f)
            reflectiveCurveTo(16.341f, 21.436f, 12f, 21.436f)
            close()

            moveTo(12.003f, 7.377f)
            curveToRelative(-0.118f, 0f, -0.231f, 0.047f, -0.314f, 0.131f)
            curveToRelative(-0.083f, 0.083f, -0.131f, 0.197f, -0.131f, 0.314f)
            verticalLineToRelative(5.728f)
            curveToRelative(0f, 0.109f, 0.04f, 0.215f, 0.113f, 0.296f)
            lineToRelative(3.805f, 4.283f)
            curveToRelative(0.079f, 0.088f, 0.189f, 0.141f, 0.308f, 0.148f)
            curveToRelative(0.008f, 0.001f, 0.017f, 0.001f, 0.025f, 0.001f)
            curveToRelative(0.109f, 0f, 0.215f, -0.04f, 0.296f, -0.113f)
            curveToRelative(1.324f, -1.182f, 2.084f, -2.858f, 2.084f, -4.601f)
            curveTo(18.188f, 10.154f, 15.414f, 7.379f, 12.003f, 7.377f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcExtendedBolusIconPreview() {
    Icon(
        imageVector = IcExtendedBolus,
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
<g id="ic_extended_bolus">
	<g display="inline">
		<path fill="#67DFE8" d="M17.07,5.852l0.482-0.934c0.245-0.474,0.059-1.058-0.416-1.303c-0.478-0.247-1.058-0.059-1.303,0.416
			l-0.483,0.935c-0.517-0.202-1.054-0.362-1.61-0.469V3.681c0.614-0.083,1.094-0.588,1.094-1.224c0-0.694-0.562-1.257-1.256-1.257
			h-3.155c-0.694,0-1.257,0.563-1.257,1.257c0,0.637,0.481,1.141,1.095,1.224v0.816c-4.263,0.817-7.496,4.569-7.496,9.067
			c0,5.092,4.144,9.236,9.236,9.236c5.092,0,9.236-4.144,9.236-9.236C21.236,10.343,19.576,7.506,17.07,5.852z M12,21.436
			c-4.341,0-7.872-3.531-7.872-7.872S7.66,5.692,12,5.692s7.872,3.531,7.872,7.872S16.341,21.436,12,21.436z"/>
		<path fill="#67DFE8" d="M12.003,7.377c-0.118,0-0.231,0.047-0.314,0.131c-0.083,0.083-0.131,0.197-0.131,0.314v5.728
			c0,0.109,0.04,0.215,0.113,0.296l3.805,4.283c0.079,0.088,0.189,0.141,0.308,0.148c0.008,0.001,0.017,0.001,0.025,0.001
			c0.109,0,0.215-0.04,0.296-0.113c1.324-1.182,2.084-2.858,2.084-4.601C18.188,10.154,15.414,7.379,12.003,7.377z"/>
	</g>
</g>
</svg>
 */