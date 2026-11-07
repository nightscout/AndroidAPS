package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the Aaruy pump plugin: a pump body with a reservoir on top. One color, the holes come from the
 * even-odd fill, so it works with any tint.
 */
val IcPluginAaruy: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAaruy",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // Reservoir
            moveTo(8f, 2f)
            lineTo(12f, 2f)
            lineTo(12f, 9f)
            lineTo(8f, 9f)
            close()
            moveTo(9f, 3f)
            lineTo(9f, 8f)
            lineTo(11f, 8f)
            lineTo(11f, 3f)
            close()
            // Body
            moveTo(6f, 10f)
            lineTo(18f, 10f)
            lineTo(18f, 22f)
            lineTo(6f, 22f)
            close()
            moveTo(8f, 12f)
            lineTo(8f, 20f)
            lineTo(16f, 20f)
            lineTo(16f, 12f)
            close()
        }
    }.build()
}
