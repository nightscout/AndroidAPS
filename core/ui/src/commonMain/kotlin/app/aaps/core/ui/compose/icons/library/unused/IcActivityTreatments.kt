package app.aaps.core.ui.compose.icons.library.unused

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Activity Treatments.
 *
 * Bounding box: x: 3.1-20.9, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcActivityTreatmentsIconPreview
 */
val IcActivityTreatments: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcActivityTreatments",
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
            moveTo(3.147f, 20.616f)
            verticalLineTo(3.384f)
            curveToRelative(0f, -1.206f, 0.979f, -2.184f, 2.184f, -2.184f)
            horizontalLineToRelative(10.851f)
            curveToRelative(1.206f, 0f, 2.184f, 0.979f, 2.184f, 2.184f)
            verticalLineToRelative(17.232f)
            curveToRelative(0f, 1.206f, -0.979f, 2.184f, -2.184f, 2.184f)
            horizontalLineTo(5.331f)
            curveTo(4.126f, 22.8f, 3.147f, 21.821f, 3.147f, 20.616f)
            close()

            moveTo(10.757f, 16.817f)
            horizontalLineToRelative(0.114f)
            curveToRelative(2.933f, 0f, 5.314f, -2.381f, 5.314f, -5.314f)
            curveToRelative(0f, -2.933f, -2.381f, -5.314f, -5.314f, -5.314f)
            curveToRelative(-0.019f, 0f, -0.038f, 0f, -0.057f, 0f)
            curveToRelative(-0.019f, 0f, -0.038f, 0f, -0.057f, 0f)
            curveToRelative(-2.933f, 0f, -5.314f, 2.381f, -5.314f, 5.314f)
            curveTo(5.443f, 14.436f, 7.824f, 16.817f, 10.757f, 16.817f)
            close()

            moveTo(14.865f, 12.706f)
            verticalLineTo(10.3f)
            horizontalLineToRelative(-7.99f)
            verticalLineToRelative(2.406f)
            horizontalLineTo(14.865f)
            close()

            moveTo(9.668f, 15.498f)
            lineToRelative(2.406f, 0f)
            lineToRelative(-0.001f, -7.99f)
            lineToRelative(-2.406f, 0f)
            lineTo(9.668f, 15.498f)
            close()

            moveTo(19.974f, 8.255f)
            verticalLineTo(7.508f)
            horizontalLineTo(18.57f)
            verticalLineToRelative(0.747f)
            horizontalLineTo(19.974f)
            close()

            moveTo(19.974f, 15.497f)
            verticalLineTo(14.75f)
            horizontalLineTo(18.57f)
            verticalLineToRelative(0.747f)
            horizontalLineTo(19.974f)
            close()

            moveTo(20.853f, 8.06f)
            curveToRelative(0f, 0.305f, -0.745f, 0.856f, -0.79f, 0.553f)
            curveToRelative(-0.054f, -0.358f, -0.484f, -0.535f, -0.791f, -0.553f)
            curveToRelative(-0.435f, -0.025f, 0.354f, -0.552f, 0.791f, -0.552f)
            curveTo(20.499f, 7.508f, 20.853f, 7.756f, 20.853f, 8.06f)
            close()

            moveTo(20.853f, 14.968f)
            verticalLineTo(8.036f)
            horizontalLineToRelative(-0.88f)
            verticalLineToRelative(6.932f)
            horizontalLineTo(20.853f)
            close()

            moveTo(20.853f, 14.914f)
            curveToRelative(0f, 0.322f, -0.354f, 0.584f, -0.79f, 0.584f)
            curveToRelative(-0.436f, 0f, -1.206f, -0.486f, -0.791f, -0.584f)
            curveToRelative(0.242f, -0.057f, 0.757f, -0.142f, 0.791f, -0.584f)
            curveTo(20.086f, 14.009f, 20.853f, 14.592f, 20.853f, 14.914f)
            close()
        }
    }.build()
}
