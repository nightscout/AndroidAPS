package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for Tidepool Plugin.
 * Converted from ic_tidepool.xml vector drawable.
 *
 * Bounding box: viewport 24x24
 */
val IcPluginTidepool: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginTidepool",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Circle 1: top-left
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(1.714f, 0f)
            curveTo(0.756f, 0f, 0f, 0.758f, 0f, 1.718f)
            curveTo(0f, 2.678f, 0.756f, 3.436f, 1.714f, 3.436f)
            curveTo(2.672f, 3.436f, 3.428f, 2.678f, 3.428f, 1.718f)
            curveTo(3.428f, 0.758f, 2.672f, 0f, 1.714f, 0f)
            close()
        }
        // Circle 2: top-center
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(6.856f, 0f)
            curveTo(5.898f, 0f, 5.142f, 0.758f, 5.142f, 1.718f)
            curveTo(5.142f, 2.678f, 5.898f, 3.436f, 6.856f, 3.436f)
            curveTo(7.814f, 3.436f, 8.57f, 2.678f, 8.57f, 1.718f)
            curveTo(8.57f, 0.758f, 7.814f, 0f, 6.856f, 0f)
            close()
        }
        // Circle 3: bottom-center
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(11.998f, 20.514f)
            curveTo(11.04f, 20.514f, 10.284f, 21.272f, 10.284f, 22.232f)
            curveTo(10.284f, 23.192f, 11.04f, 23.95f, 11.998f, 23.95f)
            curveTo(12.956f, 23.95f, 13.712f, 23.192f, 13.712f, 22.232f)
            curveTo(13.712f, 21.272f, 12.956f, 20.514f, 11.998f, 20.514f)
            close()
        }
        // L-shaped path
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(22.232f, 0.101f)
            horizontalLineTo(11.998f)
            curveTo(11.091f, 0.101f, 10.385f, 0.809f, 10.385f, 1.718f)
            verticalLineTo(11.975f)
            curveTo(10.385f, 12.884f, 11.091f, 13.592f, 11.998f, 13.592f)
            curveTo(12.906f, 13.592f, 13.612f, 12.884f, 13.612f, 11.975f)
            verticalLineTo(3.335f)
            horizontalLineTo(22.232f)
            curveTo(23.14f, 3.335f, 23.845f, 2.628f, 23.845f, 1.718f)
            curveTo(23.845f, 0.809f, 23.14f, 0.101f, 22.232f, 0.101f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginTidepoolPreview() {
    Icon(
        imageVector = IcPluginTidepool,
        contentDescription = "Tidepool",
        modifier = Modifier
            .size(128.dp)
            .padding(16.dp),
        tint = Color.Black
    )
}
