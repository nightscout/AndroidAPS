package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Open Loop.
 * Represents open loop insulin delivery mode.
 *
 * Bounding box: x: 1.3-22.7, y: 2.1-21.9 (viewport: 24x24, ~90% width)
 *
 * @see IcLoopOpenIconPreview
 */
val IcLoopOpen: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopOpen",
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
            moveTo(5.437f, 7.639f)
            curveToRelative(0.401f, -0.533f, 0.875f, -1.006f, 1.409f, -1.405f)
            lineTo(5.468f, 3.852f)
            curveToRelative(-0.94f, 0.657f, -1.758f, 1.475f, -2.416f, 2.414f)
            lineTo(5.437f, 7.639f)
            close()

            moveTo(2.214f, 7.722f)
            curveToRelative(-0.477f, 1.017f, -0.788f, 2.125f, -0.888f, 3.296f)
            lineToRelative(2.749f, -0.003f)
            curveTo(4.156f, 10.34f, 4.329f, 9.693f, 4.588f, 9.09f)
            lineTo(2.214f, 7.722f)
            close()

            moveTo(19.907f, 7.733f)
            lineToRelative(-2.372f, 1.373f)
            curveToRelative(0.258f, 0.604f, 0.429f, 1.252f, 0.509f, 1.928f)
            lineToRelative(2.747f, -0.003f)
            curveTo(20.691f, 9.86f, 20.383f, 8.75f, 19.907f, 7.733f)
            close()

            moveTo(16.689f, 7.654f)
            lineToRelative(2.382f, -1.378f)
            curveToRelative(-0.657f, -0.94f, -1.475f, -1.758f, -2.414f, -2.416f)
            lineToRelative(-1.374f, 2.385f)
            curveTo(15.816f, 6.646f, 16.289f, 7.12f, 16.689f, 7.654f)
            close()

            moveTo(18.041f, 12.714f)
            curveToRelative(-0.42f, 3.486f, -3.384f, 6.19f, -6.983f, 6.19f)
            curveToRelative(-3.606f, 0f, -6.574f, -2.713f, -6.986f, -6.209f)
            lineToRelative(-2.747f, 0.003f)
            curveToRelative(0.424f, 5.008f, 4.616f, 8.942f, 9.733f, 8.942f)
            curveToRelative(5.113f, 0f, 9.303f, -3.927f, 9.732f, -8.929f)
            lineTo(18.041f, 12.714f)
            close()

            moveTo(8.299f, 5.388f)
            curveToRelative(0.603f, -0.257f, 1.251f, -0.429f, 1.927f, -0.509f)
            lineToRelative(-0.003f, -2.747f)
            curveTo(9.052f, 2.231f, 7.943f, 2.54f, 6.926f, 3.016f)
            lineTo(8.299f, 5.388f)
            close()

            moveTo(11.906f, 4.882f)
            curveToRelative(0.676f, 0.081f, 1.323f, 0.255f, 1.926f, 0.514f)
            lineTo(15.2f, 3.021f)
            curveToRelative(-1.017f, -0.477f, -2.125f, -0.788f, -3.297f, -0.888f)
            lineTo(11.906f, 4.882f)
            close()
        }
    }.build()
}
