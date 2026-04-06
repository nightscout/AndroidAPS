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
 * Google Drive logo (monochrome).
 *
 * replaces ic_google_drive
 */
val IcGoogleDrive: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcGoogleDrive",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.71f, 3.5f)
            lineTo(1.15f, 15f)
            lineTo(4.58f, 21f)
            lineTo(11.13f, 9.5f)
            close()
            moveTo(9.73f, 15f)
            lineTo(6.3f, 21f)
            lineTo(19.42f, 21f)
            lineTo(22.85f, 15f)
            close()
            moveTo(22.28f, 14f)
            lineTo(15.42f, 2f)
            lineTo(8.57f, 2f)
            lineTo(15.43f, 14f)
            lineTo(22.28f, 14f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcGoogleDrivePreview() {
    Icon(
        imageVector = IcGoogleDrive,
        contentDescription = null,
        modifier = Modifier.padding(0.dp).size(48.dp),
        tint = Color.Unspecified
    )
}
