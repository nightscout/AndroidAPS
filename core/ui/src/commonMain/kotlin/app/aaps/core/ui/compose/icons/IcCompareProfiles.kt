package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Two overlapping star outlines — represents profile-vs-profile comparison.
 * Back star uses the tempbasal blue tint; front star uses the examinedProfile red tint.
 *
 * replaces ic_compare_profiles
 *
 * Viewport: 24x24
 *
 * @see IcCompareProfilesIconPreview
 */
val IcCompareProfiles: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCompareProfiles",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Back star — tempbasal (semi-transparent blue)
        path(
            fill = SolidColor(Color(0xC803A9F4)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(18.307f, 8.927f)
            curveToRelative(-0.135f, -0.417f, -0.494f, -0.72f, -0.928f, -0.783f)
            lineToRelative(-3.997f, -0.581f)
            lineToRelative(-1.789f, -3.622f)
            curveToRelative(-0.387f, -0.786f, -1.675f, -0.786f, -2.061f, 0f)
            lineTo(7.743f, 7.563f)
            lineTo(3.746f, 8.145f)
            curveTo(3.313f, 8.207f, 2.953f, 8.511f, 2.818f, 8.927f)
            curveTo(2.683f, 9.343f, 2.795f, 9.8f, 3.109f, 10.105f)
            lineToRelative(2.894f, 2.819f)
            lineToRelative(-0.683f, 3.983f)
            curveToRelative(-0.074f, 0.432f, 0.103f, 0.868f, 0.457f, 1.125f)
            curveToRelative(0.2f, 0.146f, 0.437f, 0.22f, 0.676f, 0.22f)
            curveToRelative(0.183f, 0f, 0.367f, -0.044f, 0.535f, -0.133f)
            lineToRelative(3.576f, -1.879f)
            lineToRelative(3.576f, 1.879f)
            curveToRelative(0.39f, 0.203f, 0.855f, 0.173f, 1.212f, -0.087f)
            curveToRelative(0.353f, -0.257f, 0.531f, -0.694f, 0.456f, -1.125f)
            lineToRelative(-0.683f, -3.983f)
            lineToRelative(2.893f, -2.819f)
            curveTo(18.329f, 9.8f, 18.443f, 9.343f, 18.307f, 8.927f)
            close()
            moveTo(17.252f, 9.488f)
            lineToRelative(-3.16f, 3.081f)
            lineToRelative(0.746f, 4.35f)
            curveToRelative(0.014f, 0.087f, -0.021f, 0.174f, -0.092f, 0.225f)
            curveToRelative(-0.04f, 0.03f, -0.087f, 0.044f, -0.135f, 0.044f)
            curveToRelative(-0.036f, 0f, -0.073f, -0.008f, -0.108f, -0.027f)
            lineToRelative(-3.907f, -2.053f)
            lineToRelative(-3.907f, 2.053f)
            curveToRelative(-0.075f, 0.044f, -0.17f, 0.036f, -0.242f, -0.017f)
            curveToRelative(-0.07f, -0.051f, -0.106f, -0.138f, -0.091f, -0.225f)
            lineToRelative(0.746f, -4.35f)
            lineTo(3.94f, 9.488f)
            curveTo(3.877f, 9.427f, 3.855f, 9.335f, 3.882f, 9.252f)
            reflectiveCurveToRelative(0.099f, -0.143f, 0.185f, -0.156f)
            lineToRelative(4.369f, -0.634f)
            lineToRelative(1.954f, -3.959f)
            curveToRelative(0.078f, -0.158f, 0.334f, -0.158f, 0.412f, 0f)
            lineToRelative(1.953f, 3.959f)
            lineToRelative(4.369f, 0.634f)
            curveToRelative(0.087f, 0.013f, 0.158f, 0.073f, 0.185f, 0.156f)
            reflectiveCurveTo(17.315f, 9.427f, 17.252f, 9.488f)
            close()
        }
        // Front star — examinedProfile (light red)
        path(
            fill = SolidColor(Color(0xFFFF5555)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(21.182f, 11.032f)
            curveToRelative(-0.135f, -0.417f, -0.494f, -0.72f, -0.928f, -0.783f)
            lineToRelative(-3.997f, -0.581f)
            lineToRelative(-1.789f, -3.622f)
            curveToRelative(-0.387f, -0.786f, -1.675f, -0.786f, -2.061f, 0f)
            lineToRelative(-1.789f, 3.622f)
            lineToRelative(-3.998f, 0.581f)
            curveToRelative(-0.432f, 0.063f, -0.793f, 0.366f, -0.928f, 0.783f)
            curveToRelative(-0.135f, 0.416f, -0.023f, 0.873f, 0.291f, 1.178f)
            lineToRelative(2.894f, 2.819f)
            lineToRelative(-0.683f, 3.983f)
            curveToRelative(-0.074f, 0.432f, 0.103f, 0.868f, 0.457f, 1.125f)
            curveToRelative(0.2f, 0.146f, 0.437f, 0.22f, 0.676f, 0.22f)
            curveToRelative(0.183f, 0f, 0.367f, -0.044f, 0.535f, -0.133f)
            lineToRelative(3.576f, -1.879f)
            lineToRelative(3.576f, 1.879f)
            curveToRelative(0.39f, 0.203f, 0.855f, 0.173f, 1.212f, -0.087f)
            curveToRelative(0.353f, -0.257f, 0.531f, -0.694f, 0.456f, -1.125f)
            lineToRelative(-0.683f, -3.983f)
            lineToRelative(2.893f, -2.819f)
            curveTo(21.204f, 11.905f, 21.318f, 11.448f, 21.182f, 11.032f)
            close()
            moveTo(20.127f, 11.593f)
            lineToRelative(-3.16f, 3.081f)
            lineToRelative(0.746f, 4.35f)
            curveToRelative(0.014f, 0.087f, -0.021f, 0.174f, -0.092f, 0.225f)
            curveToRelative(-0.04f, 0.03f, -0.087f, 0.044f, -0.135f, 0.044f)
            curveToRelative(-0.036f, 0f, -0.073f, -0.008f, -0.108f, -0.027f)
            lineToRelative(-3.907f, -2.053f)
            lineToRelative(-3.907f, 2.053f)
            curveToRelative(-0.075f, 0.044f, -0.17f, 0.036f, -0.242f, -0.017f)
            curveToRelative(-0.07f, -0.051f, -0.106f, -0.138f, -0.091f, -0.225f)
            lineToRelative(0.746f, -4.35f)
            lineToRelative(-3.162f, -3.081f)
            curveToRelative(-0.063f, -0.061f, -0.085f, -0.153f, -0.058f, -0.236f)
            curveToRelative(0.027f, -0.083f, 0.099f, -0.143f, 0.185f, -0.156f)
            lineToRelative(4.369f, -0.634f)
            lineToRelative(1.954f, -3.959f)
            curveToRelative(0.078f, -0.158f, 0.334f, -0.158f, 0.412f, 0f)
            lineToRelative(1.953f, 3.959f)
            lineTo(20f, 11.201f)
            curveToRelative(0.087f, 0.013f, 0.158f, 0.073f, 0.185f, 0.156f)
            reflectiveCurveTo(20.19f, 11.532f, 20.127f, 11.593f)
            close()
        }
    }.build()
}
