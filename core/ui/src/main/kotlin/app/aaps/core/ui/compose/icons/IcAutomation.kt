package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for Automation user actions (linked chain / infinity symbol).
 *
 * Converted from Material Symbols "linked_services" icon.
 * Original SVG viewport: 0 -960 960 960
 * Coordinate transform: x' = x/40, y' = (y+960)/40
 *
 * Bounding box: x: 2.5-21.5, y: 5.0-19.0 (viewport: 24x24, ~58% height)
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

@Preview(showBackground = true)
@Composable
private fun IcAutomationIconPreview() {
    Icon(
        imageVector = IcAutomation,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*
<svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px" fill="#000000">
  <path d="M296-270q-42 35-87.5 32T129-269q-34-28-46.5-73.5T99-436l75-124q-25-22-39.5-53T120-680q0-66 47-113t113-47q66 0 113 47t47 113q0 66-47 113t-113 47q-9 0-18-1t-17-3l-77 130q-11 18-7 35.5t17 28.5q13 11 31 12.5t35-12.5l420-361q42-35 88-31.5t80 31.5q34 28 46 73.5T861-524l-75 124q25 22 39.5 53t14.5 67q0 66-47 113t-113 47q-66 0-113-47t-47-113q0-66 47-113t113-47q9 0 17.5 1t16.5 3l78-130q11-18 7-35.5T782-630q-13-11-31-12.5T716-630L296-270Zm40.5-353.5Q360-647 360-680t-23.5-56.5Q313-760 280-760t-56.5 23.5Q200-713 200-680t23.5 56.5Q247-600 280-600t56.5-23.5Zm400 400Q760-247 760-280t-23.5-56.5Q713-360 680-360t-56.5 23.5Q600-313 600-280t23.5 56.5Q647-200 680-200t56.5-23.5ZM280-680Zm400 400Z"/>
</svg>
 */
