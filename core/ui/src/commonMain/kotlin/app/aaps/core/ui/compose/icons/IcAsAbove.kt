package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens Above.
 * Represents AutoSensitivity above target range.
 *
 * Bounding box: x: 7.4-16.6, y: 5.7-18.3 (viewport: 24x24, ~53% height)
 *
 * @see IcAsAboveIconPreview
 */
val IcAsAbove: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAsAbove",
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
            moveTo(12f, 5.669f)
            lineToRelative(-4.604f, 4.593f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineToRelative(-8.069f)
            horizontalLineToRelative(3.453f)
            lineTo(12f, 5.669f)
            close()
        }
    }.build()
}
