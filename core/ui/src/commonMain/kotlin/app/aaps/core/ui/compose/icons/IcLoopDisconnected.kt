package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Disconnected Loop.
 * Represents disconnected loop insulin delivery mode.
 *
 * Bounding box: x: 1.2-22.8, y: 2.0-21.9 (viewport: 24x24, ~90% width)
 *
 * @see IcLoopDisconnectedIconPreview
 */
val IcLoopDisconnected: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopDisconnected",
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
            moveTo(16.428f, 3.702f)
            curveToRelative(-1.542f, -1.017f, -3.386f, -1.612f, -5.371f, -1.612f)
            curveToRelative(-5.399f, 0f, -9.775f, 4.376f, -9.775f, 9.775f)
            curveToRelative(0f, 1.773f, 0.476f, 3.433f, 1.304f, 4.865f)
            lineToRelative(-0.313f, 0.359f)
            curveToRelative(-0.001f, 0.829f, 0.319f, 1.653f, 0.993f, 2.24f)
            lineToRelative(2.377f, 2.069f)
            lineToRelative(0.858f, -0.986f)
            lineToRelative(1.718f, 1.498f)
            lineToRelative(0.465f, -0.533f)
            lineToRelative(-1.718f, -1.498f)
            lineToRelative(1.251f, -1.437f)
            lineToRelative(1.72f, 1.5f)
            lineToRelative(0.465f, -0.533f)
            lineToRelative(-1.72f, -1.5f)
            lineToRelative(0.857f, -0.985f)
            lineToRelative(-2.377f, -2.069f)
            curveToRelative(-0.673f, -0.586f, -1.532f, -0.79f, -2.351f, -0.676f)
            lineToRelative(-0.273f, 0.313f)
            curveToRelative(-0.329f, -0.812f, -0.519f, -1.695f, -0.519f, -2.626f)
            curveToRelative(0f, -3.888f, 3.152f, -7.039f, 7.039f, -7.039f)
            curveToRelative(1.054f, 0f, 2.051f, 0.238f, 2.949f, 0.654f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineTo(16.428f, 3.702f)
            close()

            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 0.923f, -0.18f, 1.803f, -0.503f, 2.61f)
            lineToRelative(-0.259f, -0.297f)
            curveToRelative(-0.819f, -0.114f, -1.678f, 0.09f, -2.351f, 0.676f)
            lineToRelative(-2.377f, 2.069f)
            lineToRelative(3.895f, 4.475f)
            lineToRelative(2.377f, -2.069f)
            curveToRelative(0.674f, -0.587f, 0.995f, -1.411f, 0.993f, -2.24f)
            lineToRelative(-0.34f, -0.39f)
            curveToRelative(0.819f, -1.427f, 1.3f, -3.07f, 1.3f, -4.834f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()
        }
    }.build()
}
