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
 * Icon for Tizen Plugin (Galaxy Watch).
 * Converted from ic_gwatch.xml vector drawable, rescaled to 24x24 viewport.
 */
val IcPluginTizen: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginTizen",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Watch outline - circle with notch
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.75f, 23.05f)
            curveToRelative(-4.35f, -2f, -6.75f, -6f, -6.75f, -11.15f)
            curveToRelative(0.05f, -7.1f, 4.85f, -11.9f, 12f, -11.9f)
            curveToRelative(7.2f, 0f, 12f, 4.8f, 12f, 12f)
            curveToRelative(0f, 5.2f, -2.65f, 9.35f, -7.1f, 11.2f)
            curveToRelative(-2.6f, 1.1f, -7.65f, 1f, -10.15f, -0.15f)
            close()
            moveToRelative(8.45f, -1.15f)
            curveToRelative(4.35f, -1.3f, 7.85f, -6.8f, 7.15f, -11.2f)
            curveToRelative(-1f, -6.15f, -7f, -10.3f, -12.75f, -8.8f)
            curveToRelative(-7.95f, 2.15f, -10.65f, 11.6f, -4.85f, 17.35f)
            curveToRelative(2.95f, 3f, 6.35f, 3.85f, 10.45f, 2.65f)
            close()
        }
        // Watch face - top detail
        path(fill = SolidColor(Color.Black)) {
            moveTo(6.35f, 17.35f)
            curveToRelative(-1.85f, -1.05f, -1.9f, -1.15f, -1.75f, -3.6f)
            curveToRelative(0.15f, -2.3f, 0.3f, -2.55f, 2f, -3.5f)
            curveToRelative(1.8f, -0.95f, 1.8f, -0.95f, 3.6f, 0f)
            curveToRelative(1.75f, 0.95f, 1.85f, 0.95f, 3.55f, 0.05f)
            curveToRelative(1.45f, -0.8f, 1.95f, -0.85f, 3.2f, -0.3f)
            curveToRelative(2.65f, 1.05f, 2.8f, 1.7f, 1.3f, 4.35f)
            curveToRelative(-2.75f, 4.7f, -2.5f, 4.6f, -4.35f, 1.35f)
            curveToRelative(-1.1f, -1.95f, -1.7f, -2.65f, -1.8f, -2.05f)
            curveToRelative(-0.1f, 0.45f, -0.45f, 0.85f, -0.75f, 0.85f)
            curveToRelative(-0.4f, 0f, -0.35f, 0.2f, 0.05f, 0.6f)
            curveToRelative(0.95f, 0.95f, 0.75f, 1.3f, -1.3f, 2.35f)
            curveToRelative(-1.9f, 0.95f, -1.9f, 0.95f, -3.75f, -0.1f)
            close()
            moveToRelative(2.65f, -2.25f)
            curveToRelative(0f, -0.2f, -0.25f, -0.65f, -0.6f, -1f)
            curveToRelative(-0.45f, -0.45f, -0.35f, -0.65f, 0.3f, -0.9f)
            curveToRelative(0.65f, -0.25f, 0.75f, -0.45f, 0.25f, -0.75f)
            curveToRelative(-0.8f, -0.45f, -2.45f, 0.35f, -2.45f, 1.2f)
            curveToRelative(0f, 0.35f, 0.25f, 0.9f, 0.6f, 1.25f)
            curveToRelative(0.7f, 0.7f, 1.9f, 0.8f, 1.9f, 0.2f)
            close()
            moveToRelative(7.5f, -2.4f)
            curveToRelative(0f, -0.4f, -0.35f, -0.7f, -0.75f, -0.7f)
            curveToRelative(-0.75f, 0f, -1.05f, 1.05f, -0.45f, 1.65f)
            curveToRelative(0.5f, 0.45f, 1.2f, -0.1f, 1.2f, -0.95f)
            close()
        }
        // Watch face - bottom detail
        path(fill = SolidColor(Color.Black)) {
            moveTo(4.5f, 8.85f)
            curveToRelative(0f, -0.85f, 0.55f, -1.45f, 1.9f, -2.2f)
            curveToRelative(1.85f, -1.05f, 1.85f, -1.05f, 3.7f, -0.1f)
            curveToRelative(1.9f, 0.95f, 1.9f, 0.95f, 3.75f, 0f)
            curveToRelative(1.9f, -0.95f, 1.9f, -0.95f, 3.8f, 0.1f)
            curveToRelative(1.3f, 0.75f, 1.85f, 1.35f, 1.85f, 2.2f)
            curveToRelative(0f, 1.4f, -0.05f, 1.4f, -2.05f, 0.2f)
            curveToRelative(-1.6f, -1f, -1.65f, -1f, -3.55f, 0f)
            lineToRelative(-1.95f, 1f)
            lineToRelative(-1.8f, -1.05f)
            lineToRelative(-1.75f, -1.05f)
            lineToRelative(-1.75f, 1.05f)
            curveToRelative(-2.15f, 1.25f, -2.15f, 1.25f, -2.15f, -0.15f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginTizenPreview() {
    Icon(
        imageVector = IcPluginTizen,
        contentDescription = "Tizen",
        modifier = Modifier
            .size(128.dp)
            .padding(16.dp),
        tint = Color.Black
    )
}
