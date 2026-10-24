package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Medtrum Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 *
 * @see IcPluginMedtrumIconPreview
 */
val IcPluginMedtrum: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMedtrum",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Base layer (solid, #F1F1F2)
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
            moveTo(21.732f, 5.127f)
            curveToRelative(-1.056f, -0.885f, -2.367f, -1.103f, -3.697f, -1.215f)
            curveToRelative(-0.349f, -0.031f, -0.698f, -0.045f, -1.048f, -0.064f)
            curveToRelative(-0.013f, 0.332f, -0.026f, 0.664f, -0.04f, 0.997f)
            verticalLineToRelative(14.309f)
            curveToRelative(0.013f, 0.332f, 0.026f, 0.664f, 0.04f, 0.997f)
            curveToRelative(1.325f, -0.072f, 2.828f, -0.086f, 4.011f, -0.771f)
            curveToRelative(1.22f, -0.683f, 1.803f, -1.842f, 1.803f, -3.226f)
            curveToRelative(0f, -1.276f, 0f, -2.551f, 0f, -3.827f)
            curveToRelative(0f, -1.42f, 0f, -2.84f, 0f, -4.26f)
            curveTo(22.8f, 6.983f, 22.593f, 5.875f, 21.732f, 5.127f)
            close()
        }

        // Reservoir layer (opacity 0.5)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(16.947f, 4.845f)
            curveToRelative(0.013f, -0.332f, 0.026f, -0.665f, 0.04f, -0.997f)
            curveTo(14.002f, 3.731f, 11f, 3.769f, 8.017f, 3.907f)
            curveTo(6.57f, 3.974f, 5.043f, 3.98f, 3.683f, 4.541f)
            curveTo(2.301f, 5.11f, 1.511f, 6.318f, 1.321f, 7.774f)
            curveToRelative(-0.181f, 1.384f, -0.112f, 2.821f, -0.09f, 4.212f)
            curveToRelative(0.021f, 1.331f, -0.058f, 2.66f, 0.059f, 3.99f)
            curveToRelative(0.122f, 1.382f, 0.782f, 2.737f, 2.091f, 3.344f)
            curveToRelative(1.314f, 0.669f, 2.868f, 0.683f, 4.306f, 0.756f)
            curveToRelative(3.091f, 0.156f, 6.205f, 0.196f, 9.299f, 0.075f)
            curveToRelative(-0.013f, -0.332f, -0.026f, -0.665f, -0.04f, -0.997f)
            verticalLineTo(4.845f)
            close()
        }
    }.build()
}
