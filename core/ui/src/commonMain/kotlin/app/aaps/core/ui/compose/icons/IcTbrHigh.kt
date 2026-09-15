package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for High TBR (Temporary Basal Rate).
 * Represents high temporary basal rate.
 *
 * replacing ic_actions_start_temp_basal
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 *
 * @see IcTbrHighIconPreview
 */
val IcTbrHigh: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrHigh",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFCF8BFE)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(19.151f, 20.281f)
            lineTo(19.151f, 5.105f)
            lineTo(14.068f, 5.105f)
            lineTo(14.068f, 20.281f)
            lineTo(1.2f, 20.281f)
            lineTo(1.2f, 18.893f)
            lineTo(12.681f, 18.893f)
            lineTo(12.681f, 3.719f)
            lineTo(20.539f, 3.719f)
            lineTo(20.539f, 18.893f)
            lineTo(22.8f, 18.893f)
            lineTo(22.8f, 20.281f)
            close()
        }
    }.build()
}
