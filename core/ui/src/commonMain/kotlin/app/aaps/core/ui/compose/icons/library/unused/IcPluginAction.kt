package app.aaps.core.ui.compose.icons.library.unused

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Action Plugin.
 * Represents actions and quick commands.
 *
 * Bounding box: x: 4.0-20.5, y: 1.2-20.5 (viewport: 24x24, ~80% width)
 *
 * @see IcPluginActionIconPreview
 */
val IcPluginAction: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAction",
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
            moveTo(14.143f, 5.912f)
            curveToRelative(-0.518f, 0.895f, -0.207f, 2.054f, 0.688f, 2.573f)
            curveToRelative(0.895f, 0.518f, 2.054f, 0.207f, 2.573f, -0.688f)
            curveToRelative(0.518f, -0.895f, 0.207f, -2.054f, -0.688f, -2.573f)
            curveTo(15.821f, 4.706f, 14.661f, 5.016f, 14.143f, 5.912f)
            close()

            moveTo(15.622f, 10.068f)
            curveToRelative(0f, 0f, -1.536f, -0.886f, -2.45f, -1.414f)
            curveToRelative(-2.243f, -1.301f, -3.016f, -4.184f, -1.715f, -6.427f)
            lineToRelative(-1.63f, -0.942f)
            curveToRelative(-1.498f, 2.582f, -1.027f, 5.768f, 0.914f, 7.832f)
            lineToRelative(-4.853f, 8.406f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(1.414f, -2.45f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(-2.827f, 4.901f)
            lineToRelative(1.63f, 0.942f)
            lineToRelative(5.928f, -10.263f)
            curveToRelative(1.074f, 1.461f, 1.253f, 3.478f, 0.292f, 5.146f)
            lineToRelative(1.63f, 0.942f)
            curveTo(18.723f, 16.033f, 18.421f, 12.424f, 15.622f, 10.068f)
            close()

            moveTo(12.71f, 3.838f)
            curveToRelative(0.679f, 0.386f, 1.536f, 0.16f, 1.932f, -0.518f)
            curveToRelative(0.386f, -0.679f, 0.16f, -1.536f, -0.518f, -1.932f)
            curveToRelative(-0.679f, -0.386f, -1.536f, -0.16f, -1.932f, 0.518f)
            curveTo(11.806f, 2.585f, 12.032f, 3.452f, 12.71f, 3.838f)
            close()
        }
    }.build()
}
