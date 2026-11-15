package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Automation user actions (linked chain / infinity symbol).
 *
 * Converted from Material Symbols "linked_services" icon.
 * Original SVG viewport: 0 -960 960 960
 * Coordinate transform: x' = x/40, y' = (y+960)/40
 *
 * Bounding box: x: 2.5-21.5, y: 5.0-19.0 (viewport: 24x24, ~58% height)
 *
 * @see IcAutomationIconPreview
 */
val IcAutomation: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAutomation",
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
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.EvenOdd
        ) {
            // Main chain shape
            moveTo(7.4f, 17.25f)
            quadToRelative(-1.05f, 0.875f, -2.1875f, 0.8f)
            reflectiveQuadTo(3.225f, 17.275f)
            quadToRelative(-0.85f, -0.7f, -1.1625f, -1.8375f)
            reflectiveQuadTo(2.475f, 13.1f)
            lineToRelative(1.875f, -3.1f)
            quadToRelative(-0.625f, -0.55f, -0.9875f, -1.325f)
            reflectiveQuadTo(3f, 7f)
            quadToRelative(0f, -1.65f, 1.175f, -2.825f)
            reflectiveQuadToRelative(2.825f, -1.175f)
            quadToRelative(1.65f, 0f, 2.825f, 1.175f)
            reflectiveQuadToRelative(1.175f, 2.825f)
            quadToRelative(0f, 1.65f, -1.175f, 2.825f)
            reflectiveQuadToRelative(-2.825f, 1.175f)
            quadToRelative(-0.225f, 0f, -0.45f, -0.025f)
            reflectiveQuadToRelative(-0.425f, -0.075f)
            lineToRelative(-1.925f, 3.25f)
            quadToRelative(-0.275f, 0.45f, -0.175f, 0.8875f)
            reflectiveQuadToRelative(0.425f, 0.7125f)
            quadToRelative(0.325f, 0.275f, 0.775f, 0.3125f)
            reflectiveQuadToRelative(0.875f, -0.3125f)
            lineToRelative(10.5f, -9.025f)
            quadToRelative(1.05f, -0.875f, 2.2f, -0.7875f)
            reflectiveQuadToRelative(2f, 0.7875f)
            quadToRelative(0.85f, 0.7f, 1.15f, 1.8375f)
            reflectiveQuadTo(21.525f, 10.9f)
            lineToRelative(-1.875f, 3.1f)
            quadToRelative(0.625f, 0.55f, 0.9875f, 1.325f)
            reflectiveQuadToRelative(0.3625f, 1.675f)
            quadToRelative(0f, 1.65f, -1.175f, 2.825f)
            reflectiveQuadToRelative(-2.825f, 1.175f)
            quadToRelative(-1.65f, 0f, -2.825f, -1.175f)
            reflectiveQuadToRelative(-1.175f, -2.825f)
            quadToRelative(0f, -1.65f, 1.175f, -2.825f)
            reflectiveQuadToRelative(2.825f, -1.175f)
            quadToRelative(0.225f, 0f, 0.4375f, 0.025f)
            reflectiveQuadToRelative(0.4125f, 0.075f)
            lineToRelative(1.95f, -3.25f)
            quadToRelative(0.275f, -0.45f, 0.175f, -0.8875f)
            reflectiveQuadTo(19.55f, 8.25f)
            quadToRelative(-0.325f, -0.275f, -0.775f, -0.3125f)
            reflectiveQuadTo(17.9f, 8.25f)
            lineTo(7.4f, 17.25f)
            close()

            // Left circle cutout
            moveToRelative(1.0125f, -8.8375f)
            quadTo(9f, 7.825f, 9f, 7f)
            reflectiveQuadToRelative(-0.5875f, -1.4125f)
            quadTo(7.825f, 5f, 7f, 5f)
            reflectiveQuadToRelative(-1.4125f, 0.5875f)
            quadTo(5f, 6.175f, 5f, 7f)
            reflectiveQuadToRelative(0.5875f, 1.4125f)
            quadTo(6.175f, 9f, 7f, 9f)
            reflectiveQuadToRelative(1.4125f, -0.5875f)
            close()

            // Right circle cutout
            moveToRelative(10f, 10f)
            quadTo(19f, 17.825f, 19f, 17f)
            reflectiveQuadToRelative(-0.5875f, -1.4125f)
            quadTo(17.825f, 15f, 17f, 15f)
            reflectiveQuadToRelative(-1.4125f, 0.5875f)
            quadTo(15f, 16.175f, 15f, 17f)
            reflectiveQuadToRelative(0.5875f, 1.4125f)
            quadTo(16.175f, 19f, 17f, 19f)
            reflectiveQuadToRelative(1.4125f, -0.5875f)
            close()

            // Rendering hints (zero-area)
            moveTo(7f, 7f)
            close()
            moveToRelative(10f, 10f)
            close()
        }
    }.build()
}
