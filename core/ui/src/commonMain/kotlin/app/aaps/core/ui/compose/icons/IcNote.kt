package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Note treatment type.
 * Represents general notes or comments.
 *
 * replaces ic_cp_note
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% width)
 *
 * @see IcNoteIconPreview
 */
val IcNote: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcNote",
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
            moveTo(14.934f, 1.727f)
            lineTo(2.84f, 13.822f)
            curveToRelative(-0.096f, 0.096f, -0.161f, 0.22f, -0.186f, 0.354f)
            lineTo(1.211f, 22.005f)
            curveToRelative(-0.04f, 0.218f, 0.029f, 0.442f, 0.186f, 0.598f)
            curveToRelative(0.157f, 0.157f, 0.38f, 0.226f, 0.598f, 0.186f)
            lineToRelative(7.829f, -1.443f)
            curveToRelative(0.134f, -0.025f, 0.258f, -0.09f, 0.354f, -0.186f)
            lineTo(22.273f, 9.066f)
            curveToRelative(0.705f, -0.705f, 0.703f, -1.854f, -0.004f, -2.561f)
            lineToRelative(-4.772f, -4.772f)
            curveTo(16.789f, 1.024f, 15.639f, 1.022f, 14.934f, 1.727f)
            close()

            moveTo(8.57f, 19.491f)
            curveToRelative(0.154f, 0.154f, 0.21f, 0.381f, 0.144f, 0.589f)
            curveToRelative(-0.029f, 0.091f, -0.079f, 0.172f, -0.144f, 0.237f)
            curveToRelative(-0.083f, 0.083f, -0.191f, 0.141f, -0.311f, 0.162f)
            lineToRelative(-3.901f, 0.689f)
            curveToRelative(-0.387f, -0.012f, -0.771f, -0.163f, -1.066f, -0.459f)
            curveToRelative(-0.296f, -0.296f, -0.447f, -0.679f, -0.459f, -1.066f)
            lineToRelative(0.689f, -3.901f)
            curveToRelative(0.038f, -0.215f, 0.192f, -0.39f, 0.4f, -0.455f)
            curveToRelative(0.208f, -0.065f, 0.434f, -0.01f, 0.589f, 0.144f)
            lineTo(8.57f, 19.491f)
            close()

            moveTo(21.263f, 7.358f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(10.575f, 18.852f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(20.036f, 6.938f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(21.263f, 7.358f)
            close()

            moveTo(19.179f, 5.241f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(8.492f, 16.735f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(17.952f, 4.821f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(19.179f, 5.241f)
            close()

            moveTo(17.095f, 3.124f)
            curveToRelative(0.223f, 0.223f, 0.223f, 0.584f, 0f, 0.807f)
            lineTo(6.408f, 14.618f)
            curveToRelative(-0.223f, 0.223f, -0.584f, 0.223f, -0.807f, 0f)
            lineToRelative(-0.42f, -0.42f)
            curveToRelative(-0.223f, -0.223f, -0.223f, -0.584f, 0f, -0.807f)
            lineTo(15.869f, 2.704f)
            curveToRelative(0.223f, -0.223f, 0.584f, -0.223f, 0.807f, 0f)
            lineTo(17.095f, 3.124f)
            close()
        }
    }.build()
}
