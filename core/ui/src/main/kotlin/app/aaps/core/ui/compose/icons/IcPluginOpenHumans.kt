package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for Open Humans Plugin.
 * Converted from open_humans_white.xml vector drawable, rescaled to 24x24 viewport.
 */
val IcPluginOpenHumans: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginOpenHumans",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Person silhouette (head + body)
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.95f, 12.463f)
            curveToRelative(1.036f, -0.451f, 1.76f, -1.483f, 1.76f, -2.685f)
            curveToRelative(0f, -1.617f, -1.311f, -2.928f, -2.928f, -2.928f)
            curveToRelative(-1.617f, 0f, -2.928f, 1.311f, -2.928f, 2.928f)
            curveToRelative(0f, 1.202f, 0.724f, 2.234f, 1.76f, 2.685f)
            curveToRelative(-1.57f, 0.318f, -2.911f, 1.264f, -3.752f, 2.563f)
            curveToRelative(1.194f, 1.379f, 2.955f, 2.254f, 4.918f, 2.254f)
            curveToRelative(1.964f, 0f, 3.727f, -0.876f, 4.92f, -2.257f)
            curveToRelative(-0.841f, -1.298f, -2.181f, -2.243f, -3.75f, -2.561f)
            close()
        }
        // Top-left arrow
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.338f, 6.666f)
            lineTo(2f, 4.329f)
            curveToRelative(-1.031f, 1.4f, -1.735f, 3.054f, -2f, 4.85f)
            lineToRelative(3.305f, 0f)
            curveToRelative(0.194f, -0.907f, 0.548f, -1.755f, 1.033f, -2.513f)
            close()
        }
        // Top-right arrow
        path(fill = SolidColor(Color.Black)) {
            moveTo(18.255f, 9.179f)
            lineToRelative(3.305f, 0f)
            curveTo(21.295f, 7.382f, 20.589f, 5.727f, 19.557f, 4.326f)
            lineTo(17.22f, 6.663f)
            curveToRelative(0.486f, 0.758f, 0.841f, 1.607f, 1.035f, 2.515f)
            close()
        }
        // Top-center-right wedge
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.261f, 2.021f)
            curveTo(15.861f, 0.982f, 14.204f, 0.27f, 12.404f, 0f)
            lineToRelative(0f, 3.084f)
            lineToRelative(2.612f, 1.182f)
            close()
        }
        // Top-center-left wedge
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.151f, 3.068f)
            lineTo(9.151f, 0.001f)
            curveToRelative(-1.799f, 0.271f, -3.455f, 0.983f, -4.855f, 2.023f)
            lineToRelative(2.193f, 2.193f)
            close()
        }
        // Bottom arc
        path(fill = SolidColor(Color.Black)) {
            moveTo(5.312f, 16.111f)
            curveToRelative(-0.976f, -1f, -1.681f, -2.267f, -1.994f, -3.679f)
            lineToRelative(-3.309f, 0f)
            curveToRelative(0.8f, 5.226f, 5.326f, 9.241f, 10.772f, 9.241f)
            curveToRelative(5.446f, 0f, 9.971f, -4.015f, 10.772f, -9.241f)
            lineToRelative(-3.309f, 0f)
            curveToRelative(-0.313f, 1.411f, -1.016f, 2.676f, -1.991f, 3.676f)
            curveToRelative(-1.389f, 1.425f, -3.329f, 2.312f, -5.471f, 2.312f)
            curveToRelative(-2.141f, 0f, -4.079f, -0.885f, -5.468f, -2.309f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginOpenHumansPreview() {
    Icon(
        imageVector = IcPluginOpenHumans,
        contentDescription = "Open Humans",
        modifier = Modifier
            .size(128.dp)
            .padding(16.dp),
        tint = Color.Black
    )
}
