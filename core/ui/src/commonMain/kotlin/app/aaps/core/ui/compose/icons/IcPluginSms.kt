package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for SMS Plugin.
 *
 * replacing ic_sms
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginSmsIconPreview
 */
val IcPluginSms: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginSms",
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
            moveTo(20.64f, 1.2f)
            horizontalLineTo(3.36f)
            curveToRelative(-1.188f, 0f, -2.149f, 0.972f, -2.149f, 2.16f)
            lineTo(1.2f, 22.8f)
            lineToRelative(4.32f, -4.32f)
            horizontalLineToRelative(15.12f)
            curveToRelative(1.188f, 0f, 2.16f, -0.972f, 2.16f, -2.16f)
            verticalLineTo(3.36f)
            curveTo(22.8f, 2.172f, 21.828f, 1.2f, 20.64f, 1.2f)
            close()

            moveTo(8.76f, 10.92f)
            horizontalLineTo(6.6f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()

            moveTo(13.08f, 10.92f)
            horizontalLineToRelative(-2.16f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()

            moveTo(17.4f, 10.92f)
            horizontalLineToRelative(-2.16f)
            verticalLineTo(8.76f)
            horizontalLineToRelative(2.16f)
            verticalLineTo(10.92f)
            close()
        }
    }.build()
}
