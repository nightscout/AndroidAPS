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
 * Icon for Garmin Plugin (triangle).
 * Converted from ic_garmin_triangle.xml vector drawable.
 */
val IcPluginGarmin: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginGarmin",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20.32f, 20.86f)
            lineTo(3.67f, 20.86f)
            curveTo(3.06f, 20.86f, 2.54f, 20.56f, 2.23f, 20.03f)
            curveTo(1.93f, 19.50f, 1.93f, 18.90f, 2.24f, 18.37f)
            lineTo(10.56f, 3.96f)
            curveTo(10.86f, 3.43f, 11.39f, 3.13f, 12.00f, 3.13f)
            curveTo(12.61f, 3.13f, 13.13f, 3.43f, 13.44f, 3.96f)
            lineTo(21.76f, 18.37f)
            curveTo(22.07f, 18.90f, 22.07f, 19.50f, 21.76f, 20.03f)
            curveTo(21.45f, 20.56f, 20.93f, 20.86f, 20.32f, 20.86f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginGarminPreview() {
    Icon(
        imageVector = IcPluginGarmin,
        contentDescription = "Garmin",
        modifier = Modifier
            .size(128.dp)
            .padding(16.dp),
        tint = Color.Black
    )
}
