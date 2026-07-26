package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for No TBR (Temporary Basal Rate).
 * Represents absence of temporary basal rate.
 *
 * Bounding box: x: 1.2-22.8, y: 11.3-12.7 (viewport: 24x24, ~90% width)
 *
 * @see IcNoTbrIconPreview
 */
val IcNoTbr: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcNoTbr",
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
            moveTo(1.2f, 11.306f)
            horizontalLineToRelative(21.6f)
            verticalLineToRelative(1.387f)
            horizontalLineTo(1.2f)
            verticalLineTo(11.306f)
            close()
        }
    }.build()
}
