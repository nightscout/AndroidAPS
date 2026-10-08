package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Invalid BG Value.
 *
 * Bounding box: x: 1.2-22.8, y: 3.0-21.0 (viewport: 24x24, ~90% width)
 *
 * @see IcArrowInvalidIconPreview
 */
val IcArrowInvalid: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcInvalid",
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
            strokeLineMiter = 1.0f
        ) {
            // Premier cercle
            moveTo(17.633f, 19.781f)
            curveToRelative(0.667f, 0f, 1.208f, -0.541f, 1.208f, -1.208f)
            curveToRelative(0f, -0.667f, -0.541f, -1.208f, -1.208f, -1.208f)
            curveToRelative(-0.667f, 0f, -1.208f, 0.541f, -1.208f, 1.208f)
            curveTo(16.425f, 19.24f, 16.966f, 19.781f, 17.633f, 19.781f)
            close()

            // Premier chemin
            moveTo(17.582f, 16.878f)
            curveToRelative(-0.483f, 0f, -0.874f, -0.392f, -0.874f, -0.875f)
            curveToRelative(0f, -3.143f, 1.465f, -4.24f, 2.643f, -5.122f)
            curveToRelative(0.987f, -0.739f, 1.7f, -1.273f, 1.7f, -3.001f)
            curveToRelative(0f, -2.382f, -2.288f, -3.123f, -3.122f, -3.123f)
            curveToRelative(-1.796f, 0f, -3.246f, 1.076f, -3.979f, 2.952f)
            curveToRelative(-0.176f, 0.45f, -0.685f, 0.673f, -1.133f, 0.496f)
            curveToRelative(-0.45f, -0.176f, -0.672f, -0.683f, -0.496f, -1.133f)
            curveToRelative(0.994f, -2.545f, 3.09f, -4.064f, 5.608f, -4.064f)
            curveToRelative(1.964f, 0f, 4.871f, 1.548f, 4.871f, 4.871f)
            curveToRelative(0f, 2.603f, -1.331f, 3.599f, -2.401f, 4.4f)
            curveToRelative(-1.086f, 0.813f, -1.942f, 1.454f, -1.942f, 3.722f)
            curveTo(18.457f, 16.487f, 18.065f, 16.878f, 17.582f, 16.878f)
            close()

            // Deuxième cercle
            moveTo(6.572f, 19.781f)
            curveToRelative(0.667f, 0f, 1.208f, -0.541f, 1.208f, -1.208f)
            curveToRelative(0f, -0.667f, -0.541f, -1.208f, -1.208f, -1.208f)
            curveToRelative(-0.667f, 0f, -1.208f, 0.541f, -1.208f, 1.208f)
            curveTo(5.364f, 19.24f, 5.905f, 19.781f, 6.572f, 19.781f)
            close()

            // Deuxième chemin
            moveTo(6.521f, 16.878f)
            curveToRelative(-0.483f, 0f, -0.874f, -0.392f, -0.874f, -0.875f)
            curveToRelative(0f, -3.143f, 1.465f, -4.24f, 2.643f, -5.122f)
            curveToRelative(0.987f, -0.739f, 1.7f, -1.273f, 1.7f, -3.001f)
            curveToRelative(0f, -2.382f, -2.288f, -3.123f, -3.122f, -3.123f)
            curveToRelative(-1.796f, 0f, -3.246f, 1.076f, -3.979f, 2.952f)
            curveToRelative(-0.176f, 0.45f, -0.685f, 0.673f, -1.133f, 0.496f)
            curveTo(1.306f, 8.031f, 1.085f, 7.525f, 1.26f, 7.075f)
            curveTo(2.254f, 4.53f, 4.35f, 3.01f, 6.868f, 3.01f)
            curveToRelative(1.964f, 0f, 4.871f, 1.548f, 4.871f, 4.871f)
            curveToRelative(0f, 2.603f, -1.331f, 3.599f, -2.401f, 4.4f)
            curveToRelative(-1.086f, 0.813f, -1.942f, 1.454f, -1.942f, 3.722f)
            curveTo(7.395f, 16.487f, 7.004f, 16.878f, 6.521f, 16.878f)
            close()
        }
    }.build()
}
