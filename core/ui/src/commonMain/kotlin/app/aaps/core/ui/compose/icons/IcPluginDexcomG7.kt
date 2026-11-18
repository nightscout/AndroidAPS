package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the Dexcom G7 / ONE+ direct plugin: a drawing of the sensor seen from above.
 * A thin ring is the round patch, the filled oval is the sensor body, and the text "G7" is cut out
 * of the body. The letter G is the G of [IcGenericCgm].
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcPluginDexcomG7: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginDexcomG7",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            // Even-odd: each shape inside another one switches between filled and empty. So the
            // patch is a ring, the body is filled again, and the letters are holes in the body.
            pathFillType = PathFillType.EvenOdd
        ) {
            // Patch, outer edge (radius 10.8)
            moveTo(12f, 1.2f)
            curveTo(6.035f, 1.2f, 1.2f, 6.035f, 1.2f, 12f)
            reflectiveCurveTo(6.035f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(22.8f, 17.965f, 22.8f, 12f)
            reflectiveCurveTo(17.965f, 1.2f, 12f, 1.2f)
            close()

            // Patch, inner edge (radius 9.6)
            moveTo(12f, 2.4f)
            curveTo(17.302f, 2.4f, 21.6f, 6.698f, 21.6f, 12f)
            curveTo(21.6f, 17.302f, 17.302f, 21.6f, 12f, 21.6f)
            curveTo(6.698f, 21.6f, 2.4f, 17.302f, 2.4f, 12f)
            curveTo(2.4f, 6.698f, 6.698f, 2.4f, 12f, 2.4f)
            close()

            // Sensor body: an oval, a little wider than high (x: 4.4-19.6, y: 5.4-18.6)
            moveTo(12f, 5.4f)
            curveTo(16.197f, 5.4f, 19.6f, 8.355f, 19.6f, 12f)
            curveTo(19.6f, 15.645f, 16.197f, 18.6f, 12f, 18.6f)
            curveTo(7.803f, 18.6f, 4.4f, 15.645f, 4.4f, 12f)
            curveTo(4.4f, 8.355f, 7.803f, 5.4f, 12f, 5.4f)
            close()

            // Letter G (x: 6.5-12.3, y: 8.8-15.2)
            moveTo(12.303f, 14.21f)
            curveToRelative(-0.251f, 0.25f, -0.614f, 0.47f, -1.091f, 0.66f)
            curveToRelative(-0.477f, 0.189f, -0.959f, 0.285f, -1.448f, 0.285f)
            curveToRelative(-0.621f, 0f, -1.162f, -0.135f, -1.624f, -0.402f)
            curveToRelative(-0.461f, -0.268f, -0.809f, -0.65f, -1.041f, -1.148f)
            curveToRelative(-0.232f, -0.498f, -0.348f, -1.04f, -0.348f, -1.626f)
            curveToRelative(0f, -0.635f, 0.129f, -1.2f, 0.389f, -1.694f)
            curveToRelative(0.259f, -0.494f, 0.638f, -0.873f, 1.138f, -1.136f)
            curveToRelative(0.381f, -0.203f, 0.854f, -0.304f, 1.421f, -0.304f)
            curveToRelative(0.737f, 0f, 1.313f, 0.159f, 1.728f, 0.477f)
            curveToRelative(0.414f, 0.317f, 0.681f, 0.757f, 0.8f, 1.317f)
            lineToRelative(-1.191f, 0.229f)
            curveToRelative(-0.084f, -0.3f, -0.24f, -0.537f, -0.471f, -0.71f)
            curveToRelative(-0.231f, -0.173f, -0.52f, -0.26f, -0.865f, -0.26f)
            curveToRelative(-0.524f, 0f, -0.94f, 0.171f, -1.249f, 0.512f)
            reflectiveCurveToRelative(-0.464f, 0.848f, -0.464f, 1.52f)
            curveToRelative(0f, 0.725f, 0.157f, 1.268f, 0.47f, 1.63f)
            reflectiveCurveToRelative(0.724f, 0.543f, 1.231f, 0.543f)
            curveToRelative(0.251f, 0f, 0.503f, -0.051f, 0.755f, -0.152f)
            curveToRelative(0.253f, -0.101f, 0.469f, -0.224f, 0.649f, -0.368f)
            verticalLineToRelative(-0.773f)
            horizontalLineToRelative(-1.372f)
            verticalLineToRelative(-1.029f)
            horizontalLineToRelative(2.583f)
            close()

            // Digit 7 (x: 13.2-17.5, y: 8.85-15.15)
            moveTo(13.2f, 8.85f)
            horizontalLineTo(17.5f)
            verticalLineTo(9.85f)
            lineTo(15.55f, 15.15f)
            horizontalLineTo(14.25f)
            lineTo(16.25f, 9.95f)
            horizontalLineTo(13.2f)
            close()
        }
    }.build()
}
