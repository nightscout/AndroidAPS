package app.aaps.core.ui.compose.icons.library.unused

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Overview Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 2.8-21.2 (viewport: 24x24, ~90% width)
 *
 * @see IcPluginOverviewIconPreview
 */
val IcPluginOverview: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginOverview",
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
            moveTo(9.84f, 21.18f)
            verticalLineTo(14.7f)
            horizontalLineToRelative(4.32f)
            verticalLineToRelative(6.48f)
            horizontalLineToRelative(5.4f)
            verticalLineToRelative(-8.64f)
            horizontalLineToRelative(3.24f)
            lineTo(12f, 2.82f)
            lineTo(1.2f, 12.54f)
            horizontalLineToRelative(3.24f)
            verticalLineToRelative(8.64f)
            horizontalLineTo(9.84f)
            close()
        }
    }.build()
}
