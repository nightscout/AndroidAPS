package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Food Plugin.
 * Represents food database and meal tracking.
 *
 * replacing ic_food
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginFoodIconPreview
 */
val IcPluginFood: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginFood",
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
            moveTo(17.95f, 22.795f)
            horizontalLineToRelative(1.63f)
            curveToRelative(0.825f, 0f, 1.502f, -0.628f, 1.6f, -1.433f)
            lineToRelative(1.62f, -16.18f)
            horizontalLineToRelative(-4.909f)
            verticalLineTo(1.205f)
            horizontalLineToRelative(-1.934f)
            verticalLineToRelative(3.976f)
            horizontalLineToRelative(-4.88f)
            lineToRelative(0.295f, 2.297f)
            curveToRelative(1.679f, 0.461f, 3.25f, 1.296f, 4.192f, 2.219f)
            curveToRelative(1.414f, 1.394f, 2.386f, 2.837f, 2.386f, 5.194f)
            lineTo(17.95f, 22.795f)
            close()

            moveTo(1.2f, 21.813f)
            verticalLineToRelative(-0.972f)
            horizontalLineToRelative(14.757f)
            verticalLineToRelative(0.972f)
            curveToRelative(0f, 0.54f, -0.442f, 0.982f, -0.992f, 0.982f)
            horizontalLineTo(2.192f)
            curveTo(1.642f, 22.795f, 1.2f, 22.353f, 1.2f, 21.813f)
            close()

            moveTo(15.957f, 14.941f)
            curveToRelative(0f, -7.855f, -14.757f, -7.855f, -14.757f, 0f)
            horizontalLineTo(15.957f)
            close()

            moveTo(1.22f, 16.914f)
            horizontalLineToRelative(14.727f)
            verticalLineToRelative(1.964f)
            horizontalLineTo(1.22f)
            lineTo(1.22f, 16.914f)
            close()
        }
    }.build()
}
