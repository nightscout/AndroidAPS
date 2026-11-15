package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for None Arrow.
 * Represents no direction or neutral.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcArrowNonePreview
 */
val IcArrowNone: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowNone",
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
            moveTo(22.8f, 11.999f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-1.133f, -0.741f, -2.76f, -2.412f, -4.06f, -3.813f)
            lineTo(17.43f, 9.138f)
            curveToRelative(0f, 0f, 1.011f, 1.133f, 1.959f, 2.107f)
            horizontalLineTo(15.28f)
            curveToRelative(-0.288f, -1.254f, -1.271f, -2.237f, -2.525f, -2.525f)
            verticalLineTo(4.611f)
            curveToRelative(0.974f, 0.948f, 2.107f, 1.959f, 2.107f, 1.959f)
            lineToRelative(0.952f, -1.308f)
            curveToRelative(-1.401f, -1.3f, -3.072f, -2.927f, -3.813f, -4.06f)
            verticalLineTo(1.2f)
            lineTo(12f, 1.201f)
            lineTo(12f, 1.2f)
            lineToRelative(0f, 0.001f)
            curveToRelative(-0.741f, 1.133f, -2.412f, 2.76f, -3.813f, 4.06f)
            lineTo(9.138f, 6.57f)
            curveToRelative(0f, 0f, 1.133f, -1.011f, 2.107f, -1.959f)
            verticalLineTo(8.72f)
            curveTo(9.991f, 9.008f, 9.008f, 9.991f, 8.72f, 11.245f)
            horizontalLineTo(4.611f)
            curveTo(5.558f, 10.271f, 6.57f, 9.138f, 6.57f, 9.138f)
            lineTo(5.262f, 8.186f)
            curveToRelative(-1.3f, 1.401f, -2.927f, 3.072f, -4.06f, 3.813f)
            horizontalLineTo(1.2f)
            lineTo(1.201f, 12f)
            lineTo(1.2f, 12.001f)
            horizontalLineToRelative(0.001f)
            curveToRelative(1.133f, 0.741f, 2.76f, 2.412f, 4.06f, 3.813f)
            lineToRelative(1.308f, -0.952f)
            curveToRelative(0f, 0f, -1.011f, -1.133f, -1.959f, -2.107f)
            horizontalLineTo(8.72f)
            curveToRelative(0.288f, 1.254f, 1.271f, 2.237f, 2.525f, 2.525f)
            verticalLineToRelative(4.109f)
            curveToRelative(-0.974f, -0.948f, -2.107f, -1.959f, -2.107f, -1.959f)
            lineToRelative(-0.952f, 1.308f)
            curveToRelative(1.401f, 1.3f, 3.072f, 2.927f, 3.813f, 4.06f)
            verticalLineTo(22.8f)
            lineTo(12f, 22.799f)
            lineToRelative(0.001f, 0.001f)
            verticalLineToRelative(-0.001f)
            curveToRelative(0.741f, -1.133f, 2.412f, -2.76f, 3.813f, -4.06f)
            lineToRelative(-0.952f, -1.308f)
            curveToRelative(0f, 0f, -1.133f, 1.011f, -2.107f, 1.959f)
            verticalLineTo(15.28f)
            curveToRelative(1.254f, -0.288f, 2.237f, -1.271f, 2.525f, -2.525f)
            horizontalLineToRelative(4.109f)
            curveToRelative(-0.948f, 0.974f, -1.959f, 2.107f, -1.959f, 2.107f)
            lineToRelative(1.308f, 0.952f)
            curveToRelative(1.3f, -1.401f, 2.927f, -3.072f, 4.06f, -3.813f)
            horizontalLineTo(22.8f)
            verticalLineTo(11.999f)
            lineTo(22.8f, 11.999f)
            close()
        }
    }.build()
}
