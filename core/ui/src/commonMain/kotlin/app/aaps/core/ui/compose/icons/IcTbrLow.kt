package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Low TBR (Temporary Basal Rate).
 * Represents low temporary basal rate.
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 *
 * @see IcTbrLowIconPreview
 */
val IcTbrLow: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrLow",
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
            moveTo(12.681f, 20.281f)
            lineTo(12.681f, 5.106f)
            lineTo(1.2f, 5.106f)
            lineTo(1.2f, 3.719f)
            lineTo(14.068f, 3.719f)
            lineTo(14.068f, 18.893f)
            lineTo(19.151f, 18.893f)
            lineTo(19.151f, 3.719f)
            lineTo(22.8f, 3.719f)
            lineTo(22.8f, 5.106f)
            lineTo(20.539f, 5.106f)
            lineTo(20.539f, 20.281f)
            close()
        }
    }.build()
}
