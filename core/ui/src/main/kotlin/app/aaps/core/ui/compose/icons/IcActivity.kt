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
 * Icon for Exercise treatment type.
 * Represents physical activity entries.
 *
 * replaces ic_cp_exercise
 *
 * Bounding box: x: 1.2-22.8, y: 4.0-21.7 (viewport: 24x24, ~90% width)
 */
val IcActivity: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcActivity",
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
            moveTo(19.004f, 12.345f)
            curveToRelative(1.388f, 0f, 2.518f, -1.129f, 2.518f, -2.518f)
            curveToRelative(0f, -1.389f, -1.13f, -2.518f, -2.518f, -2.518f)
            reflectiveCurveToRelative(-2.518f, 1.13f, -2.518f, 2.518f)
            curveTo(16.486f, 11.216f, 17.616f, 12.345f, 19.004f, 12.345f)
            close()
            moveTo(19.004f, 8.083f)
            curveToRelative(0.962f, 0f, 1.745f, 0.782f, 1.745f, 1.745f)
            curveToRelative(0f, 0.962f, -0.783f, 1.745f, -1.745f, 1.745f)
            curveToRelative(-0.962f, 0f, -1.745f, -0.782f, -1.745f, -1.745f)
            curveTo(17.258f, 8.865f, 18.042f, 8.083f, 19.004f, 8.083f)
            close()

            moveTo(22.724f, 15.283f)
            curveToRelative(-0.036f, -0.047f, -0.869f, -1.15f, -2.101f, -1.15f)
            curveToRelative(-0.916f, 0f, -1.511f, 0.569f, -2.037f, 1.073f)
            curveToRelative(-0.465f, 0.445f, -0.868f, 0.83f, -1.43f, 0.83f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-0.458f, 0f, -0.833f, -0.268f, -1.206f, -0.595f)
            lineToRelative(1.943f, -1.467f)
            lineToRelative(-5.312f, -7.037f)
            lineToRelative(-5.126f, 3.871f)
            curveToRelative(-0.302f, 0.268f, -0.731f, 0.963f, -0.192f, 1.678f)
            curveToRelative(0.25f, 0.33f, 0.54f, 0.429f, 0.74f, 0.454f)
            curveToRelative(0.477f, 0.059f, 0.861f, -0.241f, 0.892f, -0.266f)
            lineToRelative(3.219f, -2.43f)
            lineToRelative(0.788f, 1.044f)
            lineToRelative(-4.625f, 3.49f)
            curveToRelative(-0.433f, -0.389f, -0.923f, -0.705f, -1.523f, -0.705f)
            horizontalLineTo(6.753f)
            curveToRelative(-0.919f, 0f, -1.7f, 0.709f, -2.389f, 1.334f)
            curveToRelative(-0.411f, 0.373f, -0.877f, 0.797f, -1.135f, 0.797f)
            curveToRelative(-0.499f, 0f, -1.145f, -0.716f, -1.332f, -0.967f)
            curveToRelative(-0.127f, -0.171f, -0.369f, -0.206f, -0.541f, -0.079f)
            curveToRelative(-0.171f, 0.127f, -0.207f, 0.369f, -0.08f, 0.54f)
            curveToRelative(0.097f, 0.131f, 0.977f, 1.279f, 1.953f, 1.279f)
            curveToRelative(0.556f, 0f, 1.065f, -0.462f, 1.654f, -0.997f)
            curveToRelative(0.586f, -0.531f, 1.249f, -1.134f, 1.87f, -1.134f)
            horizontalLineToRelative(0f)
            curveToRelative(0.547f, 0f, 1.025f, 0.498f, 1.532f, 1.026f)
            curveToRelative(0.563f, 0.586f, 1.145f, 1.192f, 1.935f, 1.192f)
            curveToRelative(0.754f, 0f, 1.392f, -0.522f, 2.009f, -1.028f)
            curveToRelative(0.576f, -0.472f, 1.172f, -0.961f, 1.799f, -0.961f)
            curveToRelative(0.374f, 0.001f, 0.726f, 0.324f, 1.131f, 0.698f)
            curveToRelative(0.528f, 0.486f, 1.126f, 1.036f, 1.995f, 1.036f)
            horizontalLineToRelative(0.001f)
            curveToRelative(0.873f, 0f, 1.453f, -0.555f, 1.965f, -1.044f)
            curveToRelative(0.481f, -0.461f, 0.897f, -0.859f, 1.502f, -0.859f)
            curveToRelative(0.839f, 0f, 1.474f, 0.831f, 1.481f, 0.84f)
            curveToRelative(0.129f, 0.171f, 0.37f, 0.203f, 0.541f, 0.077f)
            curveTo(22.815f, 15.695f, 22.851f, 15.454f, 22.724f, 15.283f)
            close()

            moveTo(11.74f, 15.437f)
            curveToRelative(-0.535f, 0.439f, -1.04f, 0.854f, -1.518f, 0.854f)
            curveToRelative(-0.461f, -0.001f, -0.907f, -0.465f, -1.378f, -0.955f)
            curveToRelative(-0.003f, -0.003f, -0.006f, -0.006f, -0.008f, -0.009f)
            lineToRelative(5.151f, -3.888f)
            lineTo(12.265f, 9.16f)
            lineToRelative(-3.847f, 2.904f)
            curveToRelative(-0.025f, 0.021f, -0.188f, 0.132f, -0.322f, 0.107f)
            curveToRelative(-0.028f, -0.004f, -0.113f, -0.013f, -0.217f, -0.151f)
            curveToRelative(-0.227f, -0.302f, 0.012f, -0.563f, 0.064f, -0.614f)
            lineTo(12.43f, 8.02f)
            lineToRelative(4.379f, 5.802f)
            lineToRelative(-1.447f, 1.092f)
            curveToRelative(-0.387f, -0.333f, -0.805f, -0.613f, -1.333f, -0.613f)
            curveTo(13.125f, 14.301f, 12.389f, 14.904f, 11.74f, 15.437f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcActivityIconPreview() {
    Icon(
        imageVector = IcActivity,
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
<g id="ic_activity">
	<g display="inline">
		<path fill="#67DFE8" d="M19.004,12.345c1.388,0,2.518-1.129,2.518-2.518c0-1.389-1.13-2.518-2.518-2.518s-2.518,1.13-2.518,2.518
			C16.486,11.216,17.616,12.345,19.004,12.345z M19.004,8.083c0.962,0,1.745,0.782,1.745,1.745c0,0.962-0.783,1.745-1.745,1.745
			c-0.962,0-1.745-0.782-1.745-1.745C17.258,8.865,18.042,8.083,19.004,8.083z"/>
		<path fill="#67DFE8" d="M22.724,15.283c-0.036-0.047-0.869-1.15-2.101-1.15c-0.916,0-1.511,0.569-2.037,1.073
			c-0.465,0.445-0.868,0.83-1.43,0.83h-0.001c-0.458,0-0.833-0.268-1.206-0.595l1.943-1.467l-5.312-7.037l-5.126,3.871
			c-0.302,0.268-0.731,0.963-0.192,1.678c0.25,0.33,0.54,0.429,0.74,0.454c0.477,0.059,0.861-0.241,0.892-0.266l3.219-2.43
			l0.788,1.044l-4.625,3.49c-0.433-0.389-0.923-0.705-1.523-0.705H6.753c-0.919,0-1.7,0.709-2.389,1.334
			c-0.411,0.373-0.877,0.797-1.135,0.797c-0.499,0-1.145-0.716-1.332-0.967c-0.127-0.171-0.369-0.206-0.541-0.079
			c-0.171,0.127-0.207,0.369-0.08,0.54c0.097,0.131,0.977,1.279,1.953,1.279c0.556,0,1.065-0.462,1.654-0.997
			c0.586-0.531,1.249-1.134,1.87-1.134h0c0.547,0,1.025,0.498,1.532,1.026c0.563,0.586,1.145,1.192,1.935,1.192
			c0.754,0,1.392-0.522,2.009-1.028c0.576-0.472,1.172-0.961,1.799-0.961c0.374,0.001,0.726,0.324,1.131,0.698
			c0.528,0.486,1.126,1.036,1.995,1.036h0.001c0.873,0,1.453-0.555,1.965-1.044c0.481-0.461,0.897-0.859,1.502-0.859
			c0.839,0,1.474,0.831,1.481,0.84c0.129,0.171,0.37,0.203,0.541,0.077C22.815,15.695,22.851,15.454,22.724,15.283z M11.74,15.437
			c-0.535,0.439-1.04,0.854-1.518,0.854c-0.461-0.001-0.907-0.465-1.378-0.955c-0.003-0.003-0.006-0.006-0.008-0.009l5.151-3.888
			L12.265,9.16l-3.847,2.904c-0.025,0.021-0.188,0.132-0.322,0.107c-0.028-0.004-0.113-0.013-0.217-0.151
			c-0.227-0.302,0.012-0.563,0.064-0.614L12.43,8.02l4.379,5.802l-1.447,1.092c-0.387-0.333-0.805-0.613-1.333-0.613
			C13.125,14.301,12.389,14.904,11.74,15.437z"/>
	</g>
</g>
</svg>
 */