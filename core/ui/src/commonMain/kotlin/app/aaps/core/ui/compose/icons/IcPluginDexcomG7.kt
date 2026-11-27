package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the Dexcom G7 / ONE+ direct plugin: a drawing of the sensor seen from above.
 * A faint disc is the round adhesive patch, the solid, nearly round shape is the sensor body, and the text
 * "G7" is cut out of the body, where the real sensor has its logo. The letter G is the G of
 * [IcGenericCgm].
 *
 * Bounding box: x: 0.5-23.5, y: 0.5-23.5 for the faint patch, x: 3.2-20.8, y: 3.7-20.3 for the
 * solid body (viewport: 24x24)
 */
val IcPluginDexcomG7: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginDexcomG7",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // The adhesive patch: a faint disc around the body. The icon is drawn in one colour (the
        // screen tints it), so "white patch, grey body" is done with two strengths of that colour.
        // Even-odd with the body outline as a hole: the patch is not drawn under the body, so the
        // letters cut out of the body stay fully empty.
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.45f,
            pathFillType = PathFillType.EvenOdd
        ) {
            // Patch (radius 11.5). A little larger than the 10.8 of the other plugin icons: it is
            // faint, so the eye takes the body as the size of the icon, and the body needs a rim.
            moveTo(12f, 0.5f)
            curveTo(5.649f, 0.5f, 0.5f, 5.649f, 0.5f, 12f)
            reflectiveCurveTo(5.649f, 23.5f, 12f, 23.5f)
            reflectiveCurveTo(23.5f, 18.351f, 23.5f, 12f)
            reflectiveCurveTo(18.351f, 0.5f, 12f, 0.5f)
            close()

            // Hole for the body, the same outline as in the path below
            moveTo(12f, 3.7f)
            curveTo(16.86f, 3.7f, 20.8f, 7.416f, 20.8f, 12f)
            curveTo(20.8f, 16.584f, 16.86f, 20.3f, 12f, 20.3f)
            curveTo(7.14f, 20.3f, 3.2f, 16.584f, 3.2f, 12f)
            curveTo(3.2f, 7.416f, 7.14f, 3.7f, 12f, 3.7f)
            close()
        }
        // The sensor body, with "G7" cut out of it.
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            // Body: nearly a circle, a little wider than high (x: 3.2-20.8, y: 3.7-20.3). The real
            // body is egg-shaped, but at icon size an egg only looked uneven. It fills most of the
            // icon: the faint patch does not count for the eye, so a smaller body made the whole
            // icon look smaller than the other sensor icons.
            moveTo(12f, 3.7f)
            curveTo(16.86f, 3.7f, 20.8f, 7.416f, 20.8f, 12f)
            curveTo(20.8f, 16.584f, 16.86f, 20.3f, 12f, 20.3f)
            curveTo(7.14f, 20.3f, 3.2f, 16.584f, 3.2f, 12f)
            curveTo(3.2f, 7.416f, 7.14f, 3.7f, 12f, 3.7f)
            close()

            // Letter G, the G of IcGenericCgm made 1.24 times larger (x: 5.2-12.4, y: 8.1-15.9)
            moveTo(12.376f, 14.74f)
            curveToRelative(-0.311f, 0.31f, -0.761f, 0.583f, -1.353f, 0.818f)
            curveToRelative(-0.591f, 0.234f, -1.189f, 0.353f, -1.796f, 0.353f)
            curveToRelative(-0.77f, 0f, -1.441f, -0.167f, -2.014f, -0.498f)
            curveToRelative(-0.572f, -0.332f, -1.003f, -0.806f, -1.291f, -1.424f)
            curveToRelative(-0.288f, -0.618f, -0.432f, -1.29f, -0.432f, -2.016f)
            curveToRelative(0f, -0.787f, 0.16f, -1.488f, 0.482f, -2.101f)
            curveToRelative(0.321f, -0.613f, 0.791f, -1.083f, 1.411f, -1.409f)
            curveToRelative(0.472f, -0.252f, 1.059f, -0.377f, 1.762f, -0.377f)
            curveToRelative(0.914f, 0f, 1.628f, 0.197f, 2.143f, 0.591f)
            curveToRelative(0.513f, 0.393f, 0.844f, 0.939f, 0.992f, 1.633f)
            lineToRelative(-1.477f, 0.284f)
            curveToRelative(-0.104f, -0.372f, -0.298f, -0.666f, -0.584f, -0.88f)
            curveToRelative(-0.286f, -0.215f, -0.645f, -0.322f, -1.073f, -0.322f)
            curveToRelative(-0.65f, 0f, -1.166f, 0.212f, -1.549f, 0.635f)
            reflectiveCurveToRelative(-0.575f, 1.052f, -0.575f, 1.885f)
            curveToRelative(0f, 0.899f, 0.195f, 1.572f, 0.583f, 2.021f)
            reflectiveCurveToRelative(0.898f, 0.673f, 1.526f, 0.673f)
            curveToRelative(0.311f, 0f, 0.624f, -0.063f, 0.936f, -0.188f)
            curveToRelative(0.314f, -0.125f, 0.582f, -0.278f, 0.805f, -0.456f)
            verticalLineToRelative(-0.959f)
            horizontalLineToRelative(-1.701f)
            verticalLineToRelative(-1.276f)
            horizontalLineToRelative(3.203f)
            close()

            // Digit 7 (x: 13.5-18.8, y: 8.1-15.9)
            moveTo(13.488f, 8.094f)
            horizontalLineTo(18.82f)
            verticalLineTo(9.334f)
            lineTo(16.402f, 15.906f)
            horizontalLineTo(14.79f)
            lineTo(17.27f, 9.458f)
            horizontalLineTo(13.488f)
            close()
        }
    }.build()
}
