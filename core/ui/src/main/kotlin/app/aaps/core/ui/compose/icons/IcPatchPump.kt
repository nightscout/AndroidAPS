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
 * Icon for patch pump (e.g., Omnipod, Eopatch).
 * Represents patch-style pumps in status displays.
 *
 * Replacing ic_patch_pump_outline
 *
 * Viewport: 80x80
 */
val IcPatchPump: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPatchPump",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 80f,
        viewportHeight = 80f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f
        ) {
            // Outer shape (cutout)
            moveTo(18f, 54f)
            arcToRelative(3f, 3f, 0f, false, false, 3f, 3f)
            lineTo(45f, 57f)
            arcTo(19f, 19f, 0f, false, false, 62f, 40f)
            arcTo(19f, 19f, 0f, false, false, 45f, 24f)
            lineTo(21f, 24f)
            arcToRelative(3f, 3f, 0f, false, false, -3f, 3f)
            close()
            // Inner cutout (reverse winding to create outline)
            moveTo(67f, 40f)
            arcTo(22f, 22f, 0f, false, true, 45f, 62f)
            lineTo(19f, 62f)
            arcToRelative(6f, 6f, 0f, false, true, -6f, -6f)
            lineTo(13f, 25f)
            arcToRelative(6f, 6f, 0f, false, true, 6f, -6f)
            lineTo(45f, 19f)
            arcTo(22f, 22f, 0f, false, true, 67f, 40f)
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPatchPumpPreview() {
    Icon(
        imageVector = IcPatchPump,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
