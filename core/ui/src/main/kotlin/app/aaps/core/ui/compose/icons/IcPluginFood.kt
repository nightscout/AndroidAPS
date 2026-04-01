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
 * Icon for Food Plugin.
 * Represents food database and meal tracking.
 *
 * replacing ic_food
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginFood: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginFood",
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
            moveTo(17.95f, 22.795f)
            horizontalLineToRelative(1.63f)
            curveToRelative(0.825f, 0f, 1.502f, -0.628f, 1.6f, -1.433f)
            lineToRelative(1.62f, -16.18f)
            horizontalLineToRelative(-4.909f)
            verticalLineTo(1.205f)
            horizontalLineToRelative(-1.934f)
            verticalLineToRelative(3.976f)
            horizontalLineToRelative(-4.88f)
            lineToRelative(0.295f, 2.297f)
            curveToRelative(1.679f, 0.461f, 3.25f, 1.296f, 4.192f, 2.219f)
            curveToRelative(1.414f, 1.394f, 2.386f, 2.837f, 2.386f, 5.194f)
            lineTo(17.95f, 22.795f)
            close()

            moveTo(1.2f, 21.813f)
            verticalLineToRelative(-0.972f)
            horizontalLineToRelative(14.757f)
            verticalLineToRelative(0.972f)
            curveToRelative(0f, 0.54f, -0.442f, 0.982f, -0.992f, 0.982f)
            horizontalLineTo(2.192f)
            curveTo(1.642f, 22.795f, 1.2f, 22.353f, 1.2f, 21.813f)
            close()

            moveTo(15.957f, 14.941f)
            curveToRelative(0f, -7.855f, -14.757f, -7.855f, -14.757f, 0f)
            horizontalLineTo(15.957f)
            close()

            moveTo(1.22f, 16.914f)
            horizontalLineToRelative(14.727f)
            verticalLineToRelative(1.964f)
            horizontalLineTo(1.22f)
            lineTo(1.22f, 16.914f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginFoodIconPreview() {
    Icon(
        imageVector = IcPluginFood,
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
<g id="ic_plugin_food">
	<g display="inline">
		<path fill="#FFFFFF" d="M17.95,22.795h1.63c0.825,0,1.502-0.628,1.6-1.433l1.62-16.18h-4.909V1.205h-1.934v3.976h-4.88
			l0.295,2.297c1.679,0.461,3.25,1.296,4.192,2.219c1.414,1.394,2.386,2.837,2.386,5.194L17.95,22.795z M1.2,21.813v-0.972h14.757
			v0.972c0,0.54-0.442,0.982-0.992,0.982H2.192C1.642,22.795,1.2,22.353,1.2,21.813z M15.957,14.941c0-7.855-14.757-7.855-14.757,0
			H15.957z M1.22,16.914h14.727v1.964H1.22L1.22,16.914z"/>
	</g>
</g>
</svg>
 */