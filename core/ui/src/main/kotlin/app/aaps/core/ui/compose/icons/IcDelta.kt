package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for BG Delta (Greek Δ triangle outline).
 *
 * Replaces ic_auto_delta.
 *
 * Bounding box: x: 5.087-18.913, y: 4-20 (viewport: 24x24, ~67% height)
 *
 * @see IcDeltaIconPreview
 */
val IcDelta: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcDelta",
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
            moveTo(12f, 4f)
            lineTo(5.087f, 20f)
            horizontalLineToRelative(13.826f)
            lineTo(12f, 4f)
            close()

            moveTo(11.375f, 8.236f)
            lineToRelative(4.614f, 10.678f)
            horizontalLineTo(6.761f)
            lineTo(11.375f, 8.236f)
            close()
        }
    }.build()
}
