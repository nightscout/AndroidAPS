package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Double Up Arrow.
 * Represents double upward trend or direction.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcArrowDoubleUpIconPreview
 */
val IcArrowDoubleUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowDoubleUp",
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
            moveTo(1.895f, 7.347f)
            curveToRelative(2.121f, -1.967f, 4.65f, -4.43f, 5.771f, -6.145f)
            verticalLineTo(1.2f)
            curveToRelative(0f, 0f, 0.001f, 0.001f, 0.001f, 0.001f)
            lineTo(7.668f, 1.2f)
            verticalLineToRelative(0.002f)
            curveToRelative(1.121f, 1.715f, 3.65f, 4.178f, 5.771f, 6.145f)
            lineToRelative(-1.44f, 1.979f)
            curveToRelative(0f, 0f, -1.715f, -1.53f, -3.188f, -2.964f)
            verticalLineTo(22.8f)
            horizontalLineTo(6.524f)
            verticalLineTo(6.362f)
            curveTo(5.05f, 7.796f, 3.335f, 9.327f, 3.335f, 9.327f)
            lineTo(1.895f, 7.347f)
            close()

            moveTo(10.561f, 7.347f)
            curveToRelative(2.121f, -1.967f, 4.65f, -4.43f, 5.771f, -6.145f)
            verticalLineTo(1.2f)
            lineToRelative(0.001f, 0.001f)
            curveToRelative(0f, 0f, 0.001f, -0.001f, 0.001f, -0.001f)
            lineToRelative(0f, 0.002f)
            curveToRelative(1.121f, 1.715f, 3.65f, 4.178f, 5.771f, 6.145f)
            lineToRelative(-1.44f, 1.979f)
            curveToRelative(0f, 0f, -1.715f, -1.53f, -3.188f, -2.964f)
            verticalLineTo(22.8f)
            horizontalLineTo(15.19f)
            verticalLineTo(6.362f)
            curveToRelative(-1.474f, 1.434f, -3.189f, 2.964f, -3.189f, 2.964f)
            lineTo(10.561f, 7.347f)
            close()
        }
    }.build()
}
