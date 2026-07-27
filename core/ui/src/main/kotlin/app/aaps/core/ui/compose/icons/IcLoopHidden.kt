package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Hide Loop Information.
 * Mainly used to hide Loop information in UserEntry.
 *
 * Bounding box: x: 1.2-22.8, y: 2.1-21.8 (viewport: 24x24, ~90% width)
 *
 * @see IcLoopHiddenIconPreview
 */
val IcLoopHidden: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopHidden",
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
            moveTo(18.133f, 18.598f)
            lineToRelative(-1.934f, -1.934f)
            lineToRelative(0f, 0f)
            lineTo(5.882f, 6.347f)
            lineTo(4.325f, 4.79f)
            lineToRelative(0f, 0f)
            lineToRelative(-1.85f, -1.85f)
            lineTo(1.2f, 4.216f)
            lineToRelative(1.939f, 1.939f)
            curveTo(1.975f, 7.761f, 1.282f, 9.73f, 1.282f, 11.865f)
            curveToRelative(0f, 5.399f, 4.376f, 9.775f, 9.775f, 9.775f)
            curveToRelative(2.136f, 0f, 4.104f, -0.693f, 5.711f, -1.856f)
            lineToRelative(1.879f, 1.879f)
            lineToRelative(1.275f, -1.275f)
            lineTo(18.133f, 18.598f)
            lineTo(18.133f, 18.598f)
            close()

            moveTo(11.058f, 18.905f)
            curveToRelative(-3.888f, 0f, -7.039f, -3.152f, -7.039f, -7.039f)
            curveToRelative(0f, -1.378f, 0.405f, -2.656f, 1.091f, -3.74f)
            lineToRelative(9.688f, 9.688f)
            curveTo(13.714f, 18.499f, 12.436f, 18.905f, 11.058f, 18.905f)
            close()

            moveTo(11.058f, 4.826f)
            curveToRelative(1.054f, 0f, 2.051f, 0.238f, 2.949f, 0.654f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineToRelative(1.845f, -0.529f)
            curveToRelative(-1.542f, -1.017f, -3.386f, -1.612f, -5.371f, -1.612f)
            curveToRelative(-1.959f, 0f, -3.779f, 0.582f, -5.308f, 1.574f)
            lineToRelative(1.992f, 1.992f)
            curveTo(8.73f, 5.128f, 9.858f, 4.826f, 11.058f, 4.826f)
            close()

            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 1.199f, -0.302f, 2.328f, -0.831f, 3.316f)
            lineToRelative(1.992f, 1.992f)
            curveToRelative(0.992f, -1.529f, 1.574f, -3.35f, 1.574f, -5.308f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()
        }
    }.build()
}
