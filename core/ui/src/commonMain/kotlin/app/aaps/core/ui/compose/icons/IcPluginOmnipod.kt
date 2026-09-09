package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Dash or Eros Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 *
 * @see IcPluginOmnipodPreview
 */
val IcPluginOmnipod: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOmnipod",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Pod
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
            moveTo(21.59f, 13.399f)
            curveToRelative(0.09f, -1.244f, 0.625f, -2.221f, 1.208f, -2.218f)
            lineToRelative(0.002f, -0.853f)
            curveToRelative(0.009f, -3.52f, -2.896f, -6.391f, -6.476f, -6.4f)
            lineToRelative(-13.031f, 0f)
            curveToRelative(-1.134f, 0f, -2.058f, 0.903f, -2.061f, 2.012f)
            lineTo(1.2f, 17.994f)
            curveToRelative(-0.003f, 1.143f, 0.944f, 2.075f, 2.112f, 2.078f)
            horizontalLineToRelative(12.977f)
            curveToRelative(2.816f, 0f, 5.284f, -1.73f, 6.166f, -4.311f)
            curveTo(21.886f, 15.665f, 21.503f, 14.629f, 21.59f, 13.399f)
            close()
        }
    }.build()
}
