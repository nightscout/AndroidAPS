package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Calculator.
 * Represents calculation or bolus calculator.
 *
 * replaces ic_calculator
 *
 * Bounding box: x: 3.0-21.0, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcCalculatorIconPreview
 */
val IcCalculator: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCalculator",
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
            moveTo(17.877f, 15.934f)
            horizontalLineToRelative(-4.495f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(4.495f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveTo(18.001f, 15.99f, 17.946f, 15.934f, 17.877f, 15.934f)
            close()

            moveTo(16.465f, 14.494f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineToRelative(-1.422f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.119f)
            horizontalLineToRelative(1.672f)
            verticalLineTo(14.494f)
            close()

            moveTo(14.794f, 18.988f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(1.422f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.119f)
            horizontalLineToRelative(-1.672f)
            verticalLineTo(18.988f)
            close()

            moveTo(20.311f, 1.2f)
            horizontalLineTo(3.689f)
            curveToRelative(-0.379f, 0f, -0.686f, 0.307f, -0.686f, 0.686f)
            verticalLineToRelative(20.227f)
            curveToRelative(0f, 0.379f, 0.307f, 0.687f, 0.686f, 0.687f)
            horizontalLineToRelative(16.622f)
            curveToRelative(0.379f, 0f, 0.687f, -0.307f, 0.687f, -0.687f)
            verticalLineTo(1.886f)
            curveTo(20.997f, 1.507f, 20.69f, 1.2f, 20.311f, 1.2f)
            close()

            moveTo(19.624f, 21.426f)
            horizontalLineTo(4.375f)
            verticalLineTo(6.792f)
            horizontalLineToRelative(15.248f)
            verticalLineTo(21.426f)
            close()

            moveTo(19.624f, 5.419f)
            horizontalLineTo(4.375f)
            verticalLineTo(2.573f)
            horizontalLineToRelative(15.248f)
            verticalLineTo(5.419f)
            close()

            moveTo(13.382f, 11.551f)
            horizontalLineToRelative(4.495f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineToRelative(-4.495f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveTo(13.257f, 11.495f, 13.313f, 11.551f, 13.382f, 11.551f)
            close()

            moveTo(6.226f, 11.551f)
            horizontalLineToRelative(1.412f)
            verticalLineToRelative(1.411f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(1.422f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.411f)
            horizontalLineToRelative(1.412f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineTo(9.31f)
            verticalLineTo(8.468f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineTo(7.763f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineTo(9.88f)
            horizontalLineTo(6.226f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveTo(6.102f, 11.495f, 6.158f, 11.551f, 6.226f, 11.551f)
            close()

            moveTo(9.656f, 16.741f)
            lineToRelative(0.998f, -0.998f)
            curveToRelative(0.024f, -0.024f, 0.037f, -0.055f, 0.037f, -0.088f)
            curveToRelative(0f, -0.033f, -0.013f, -0.064f, -0.037f, -0.088f)
            lineToRelative(-1.006f, -1.005f)
            curveToRelative(-0.048f, -0.049f, -0.128f, -0.049f, -0.176f, 0f)
            lineToRelative(-0.998f, 0.998f)
            lineToRelative(-0.998f, -0.998f)
            curveToRelative(-0.048f, -0.049f, -0.128f, -0.049f, -0.176f, 0f)
            lineToRelative(-1.005f, 1.005f)
            curveToRelative(-0.049f, 0.048f, -0.049f, 0.128f, 0f, 0.176f)
            lineToRelative(0.998f, 0.998f)
            lineToRelative(-0.998f, 0.998f)
            curveToRelative(-0.049f, 0.048f, -0.049f, 0.128f, 0f, 0.176f)
            lineToRelative(1.005f, 1.006f)
            curveToRelative(0.023f, 0.024f, 0.055f, 0.037f, 0.088f, 0.037f)
            curveToRelative(0.033f, 0f, 0.065f, -0.013f, 0.088f, -0.037f)
            lineToRelative(0.998f, -0.998f)
            lineToRelative(0.998f, 0.998f)
            curveToRelative(0.023f, 0.024f, 0.055f, 0.037f, 0.088f, 0.037f)
            curveToRelative(0.033f, 0f, 0.065f, -0.013f, 0.088f, -0.037f)
            lineToRelative(1.006f, -1.006f)
            curveToRelative(0.049f, -0.048f, 0.049f, -0.128f, 0f, -0.176f)
            lineTo(9.656f, 16.741f)
            close()
        }
    }.build()
}
