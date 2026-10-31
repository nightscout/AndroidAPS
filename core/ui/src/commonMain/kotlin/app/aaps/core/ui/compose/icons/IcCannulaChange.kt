package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Cannula change treatment type.
 * Represents infusion set change entries.
 *
 * replacing ic_cp_pump_cannula
 *
 * Bounding box: x: 1.2-22.8, y: 6.3-19.5 (viewport: 24x24, ~90% width)
 *
 * @see IcCannulaChangeIconPreview
 */
val IcCannulaChange: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCannulaChange",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF67DFE8)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(22.768f, 7.141f)
            curveToRelative(-0.137f, -0.5f, -0.672f, -0.788f, -1.194f, -0.645f)
            lineToRelative(-2.346f, 0.645f)
            curveToRelative(-0.3f, -0.843f, -1.201f, -1.327f, -2.082f, -1.085f)
            lineToRelative(-2.776f, 0.763f)
            curveToRelative(-0.881f, 0.242f, -1.407f, 1.118f, -1.235f, 1.996f)
            lineTo(10.788f, 9.46f)
            curveToRelative(-0.46f, 0.127f, -0.738f, 0.548f, -0.708f, 0.988f)
            curveToRelative(-1.565f, 0.406f, -2.803f, 0.062f, -3.785f, -0.229f)
            curveToRelative(-0.966f, -0.286f, -1.801f, -0.533f, -2.504f, 0.14f)
            curveToRelative(-1.207f, 1.157f, -0.341f, 2.649f, 0.355f, 3.849f)
            curveToRelative(0.462f, 0.796f, 0.94f, 1.621f, 0.707f, 2.123f)
            curveToRelative(-0.479f, 1.038f, -2.519f, 1.041f, -3.265f, 0.954f)
            curveToRelative(-0.187f, -0.02f, -0.362f, 0.113f, -0.385f, 0.303f)
            curveToRelative(-0.022f, 0.19f, 0.113f, 0.361f, 0.303f, 0.383f)
            curveToRelative(0.079f, 0.009f, 1.27f, 0.14f, 2.357f, -0.158f)
            curveToRelative(0.677f, -0.186f, 1.314f, -0.54f, 1.618f, -1.193f)
            curveToRelative(0.384f, -0.827f, -0.16f, -1.764f, -0.736f, -2.757f)
            curveToRelative(-0.779f, -1.344f, -1.233f, -2.279f, -0.475f, -3.006f)
            curveToRelative(0.393f, -0.377f, 0.897f, -0.251f, 1.825f, 0.024f)
            curveToRelative(1.042f, 0.308f, 2.445f, 0.746f, 4.314f, 0.198f)
            curveToRelative(0.236f, 0.19f, 0.552f, 0.28f, 0.876f, 0.191f)
            lineToRelative(5.196f, -1.428f)
            lineToRelative(1.861f, 6.773f)
            lineToRelative(0.118f, -1.11f)
            lineToRelative(-1.586f, -5.771f)
            lineToRelative(5.196f, -1.428f)
            curveTo(22.593f, 8.162f, 22.905f, 7.64f, 22.768f, 7.141f)
            close()
        }
    }.build()
}
