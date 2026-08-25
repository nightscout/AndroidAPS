package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Forty-Five Degrees Up Arrow.
 * Represents forty-five degree upward trend.
 *
 * Bounding box: x: 3.5-19.6, y: 3.5-20.5 (viewport: 24x24, ~84% width)
 *
 * @see IcArrowFortyfiveUpIconPreview
 */
val IcArrowFortyfiveUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowFortyfiveUp",
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
            moveTo(19.372f, 12.791f)
            curveToRelative(-0.109f, -2.891f, -0.156f, -6.42f, 0.264f, -8.426f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.001f, 0f)
            lineToRelative(0f, -0.001f)
            lineToRelative(-0.002f, 0.001f)
            curveToRelative(-2.006f, 0.42f, -5.535f, 0.373f, -8.426f, 0.264f)
            lineToRelative(-0.381f, 2.418f)
            curveToRelative(0f, 0f, 2.295f, 0.13f, 4.351f, 0.158f)
            lineTo(3.555f, 18.828f)
            lineToRelative(1.617f, 1.617f)
            lineTo(16.795f, 8.822f)
            curveToRelative(0.028f, 2.056f, 0.158f, 4.351f, 0.158f, 4.351f)
            lineTo(19.372f, 12.791f)
            close()
        }
    }.build()
}
