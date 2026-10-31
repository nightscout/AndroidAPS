package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens Below.
 * Represents AutoSensitivity below target range.
 *
 * Bounding box: x: 7.4-16.6, y: 5.7-18.3 (viewport: 24x24, ~53% height)
 *
 * @see IcAsBelowIconPreview
 */
val IcAsBelow: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsBelow",
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
            moveTo(13.151f, 13.738f)
            verticalLineTo(5.669f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineTo(7.396f)
            lineTo(12f, 18.331f)
            lineToRelative(4.604f, -4.593f)
            horizontalLineTo(13.151f)
            close()
        }
    }.build()
}
