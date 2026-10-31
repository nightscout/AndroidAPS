package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for AutoSens.
 * Represents AutoSensitivity feature.
 *
 * Bounding box: x: 4.5-19.5, y: 1.6-22.4 (viewport: 24x24, ~90% height)
 *
 * @see IcAsIconPreview
 */
val IcAs: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAs",
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
            moveTo(16.086f, 17.767f)
            verticalLineToRelative(-8.069f)
            horizontalLineToRelative(-2.302f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(-3.454f)
            lineToRelative(4.604f, 4.593f)
            lineToRelative(4.604f, -4.593f)
            horizontalLineToRelative(-3.452f)
            close()

            moveTo(9.066f, 1.64f)
            lineTo(4.461f, 6.233f)
            horizontalLineToRelative(3.453f)
            verticalLineToRelative(8.069f)
            horizontalLineToRelative(2.302f)
            verticalLineTo(6.233f)
            horizontalLineToRelative(3.453f)
            lineTo(9.066f, 1.64f)
            close()
        }
    }.build()
}
