package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Left Arrow.
 * Represents left direction.
 *
 * Bounding box: x: 1.2-22.8, y: 5.4-18.6 (viewport: 24x24, ~90% width)
 *
 * @see IcArrowLeftPreview
 */
val IcArrowLeft: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowLeft",
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
            moveTo(7.347f, 17.772f)
            curveToRelative(-1.967f, -2.121f, -4.43f, -4.65f, -6.145f, -5.771f)
            horizontalLineTo(1.2f)
            lineTo(1.201f, 12f)
            lineTo(1.2f, 11.999f)
            horizontalLineToRelative(0.002f)
            curveToRelative(1.715f, -1.121f, 4.178f, -3.65f, 6.145f, -5.771f)
            lineToRelative(1.979f, 1.44f)
            curveToRelative(0f, 0f, -1.53f, 1.715f, -2.964f, 3.188f)
            horizontalLineTo(22.8f)
            verticalLineToRelative(2.286f)
            horizontalLineTo(6.362f)
            curveToRelative(1.434f, 1.474f, 2.964f, 3.189f, 2.964f, 3.189f)
            lineTo(7.347f, 17.772f)
            close()
        }
    }.build()
}
