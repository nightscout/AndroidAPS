package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcGlobalClock: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcGlobalClock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.9f, 2.79f)
            quadToRelative(1.83f, 0.79f, 3.17f, 2.14f)
            quadToRelative(1.35f, 1.35f, 2.14f, 3.17f)
            quadTo(22f, 9.92f, 22f, 12f)
            quadToRelative(0f, 0.07f, 0f, 0.14f)
            reflectiveQuadToRelative(0f, 0.14f)
            quadTo(21.53f, 11.95f, 21.03f, 11.7f)
            reflectiveQuadTo(19.98f, 11.27f)
            quadTo(19.78f, 9.05f, 18.45f, 7.24f)
            quadTo(17.13f, 5.43f, 15f, 4.6f)
            verticalLineTo(5f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(13f, 7f)
            horizontalLineTo(11f)
            verticalLineTo(9f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(10f, 10f)
            horizontalLineTo(8f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6.43f)
            quadToRelative(-1.55f, 0.92f, -2.49f, 2.51f)
            reflectiveQuadTo(11f, 18f)
            quadTo(10.18f, 18f, 9.59f, 17.41f)
            reflectiveQuadTo(9f, 16f)
            verticalLineTo(15f)
            lineTo(4.2f, 10.2f)
            quadTo(4.13f, 10.65f, 4.06f, 11.1f)
            reflectiveQuadTo(4f, 12f)
            quadToRelative(0f, 3.13f, 2.08f, 5.41f)
            reflectiveQuadToRelative(5.2f, 2.56f)
            quadToRelative(0.18f, 0.57f, 0.43f, 1.06f)
            reflectiveQuadTo(12.28f, 22f)
            quadToRelative(-0.07f, 0f, -0.14f, 0f)
            reflectiveQuadTo(12f, 22f)
            quadTo(9.93f, 22f, 8.1f, 21.21f)
            quadTo(6.28f, 20.43f, 4.93f, 19.08f)
            quadTo(3.58f, 17.73f, 2.79f, 15.9f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            reflectiveQuadToRelative(3.9f, 0.79f)
            close()
            moveTo(14.46f, 21.54f)
            quadTo(13f, 20.08f, 13f, 18f)
            reflectiveQuadToRelative(1.46f, -3.54f)
            reflectiveQuadTo(18f, 13f)
            reflectiveQuadToRelative(3.54f, 1.46f)
            reflectiveQuadTo(23f, 18f)
            reflectiveQuadToRelative(-1.46f, 3.54f)
            reflectiveQuadTo(18f, 23f)
            quadToRelative(-2.07f, 0f, -3.54f, -1.46f)
            close()
            moveToRelative(5.19f, -1.19f)
            lineToRelative(0.7f, -0.7f)
            lineTo(18.5f, 17.8f)
            verticalLineTo(15f)
            horizontalLineToRelative(-1f)
            verticalLineToRelative(3.2f)
            lineToRelative(2.15f, 2.15f)
            close()
        }
    }.build()
}
