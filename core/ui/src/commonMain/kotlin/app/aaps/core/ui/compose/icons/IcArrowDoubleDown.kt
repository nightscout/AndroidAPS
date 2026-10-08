package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Double Down Arrow.
 * Represents double downward trend or direction.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcArrowDoubleDownIconPreview
 */
val IcArrowDoubleDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowDoubleDown",
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
            moveTo(1.895f, 16.653f)
            curveToRelative(2.121f, 1.967f, 4.65f, 4.43f, 5.771f, 6.145f)
            verticalLineTo(22.8f)
            curveToRelative(0f, 0f, 0.001f, -0.001f, 0.001f, -0.001f)
            lineTo(7.668f, 22.8f)
            verticalLineToRelative(-0.002f)
            curveToRelative(1.121f, -1.715f, 3.65f, -4.178f, 5.771f, -6.145f)
            lineToRelative(-1.44f, -1.979f)
            curveToRelative(0f, 0f, -1.715f, 1.53f, -3.188f, 2.964f)
            verticalLineTo(1.2f)
            horizontalLineTo(6.524f)
            verticalLineToRelative(16.438f)
            curveToRelative(-1.474f, -1.434f, -3.189f, -2.964f, -3.189f, -2.964f)
            lineTo(1.895f, 16.653f)
            close()

            moveTo(10.561f, 16.653f)
            curveToRelative(2.121f, 1.967f, 4.65f, 4.43f, 5.771f, 6.145f)
            verticalLineTo(22.8f)
            lineToRelative(0.001f, -0.001f)
            curveToRelative(0f, 0f, 0.001f, 0.001f, 0.001f, 0.001f)
            lineToRelative(0f, -0.002f)
            curveToRelative(1.121f, -1.715f, 3.65f, -4.178f, 5.771f, -6.145f)
            lineToRelative(-1.44f, -1.979f)
            curveToRelative(0f, 0f, -1.715f, 1.53f, -3.188f, 2.964f)
            verticalLineTo(1.2f)
            horizontalLineTo(15.19f)
            verticalLineToRelative(16.438f)
            curveToRelative(-1.474f, -1.434f, -3.189f, -2.964f, -3.189f, -2.964f)
            lineTo(10.561f, 16.653f)
            close()
        }
    }.build()
}
