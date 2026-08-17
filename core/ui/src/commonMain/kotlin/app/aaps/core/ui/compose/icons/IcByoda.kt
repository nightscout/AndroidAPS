package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for BYODA.
 * Represents Build Your Own Dexcom Application icon.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcByodaIconPreview
 */
val IcByoda: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcByoda",
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
            moveTo(12f, 1.201f)
            curveTo(6.036f, 1.201f, 1.201f, 6.036f, 1.201f, 12f)
            reflectiveCurveTo(6.036f, 22.799f, 12f, 22.799f)
            reflectiveCurveTo(22.799f, 17.964f, 22.799f, 12f)
            verticalLineTo(1.201f)
            horizontalLineTo(12f)
            close()

            moveTo(12f, 20.208f)
            curveToRelative(-4.533f, 0f, -8.208f, -3.675f, -8.208f, -8.208f)
            curveToRelative(0f, -4.533f, 3.675f, -8.208f, 8.208f, -8.208f)
            curveToRelative(4.533f, 0f, 8.208f, 3.675f, 8.208f, 8.208f)
            curveTo(20.208f, 16.533f, 16.533f, 20.208f, 12f, 20.208f)
            close()
        }
    }.build()
}
