package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Cancel Extended Bolus.
 * Represents cancellation of an extended/multi-wave insulin bolus.
 *
 * replaces ic_actions_cancel_extended_bolus
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-21.7 (viewport: 24x24, ~90% height)
 *
 * @see IcCancelExtendedBolusIconPreview
 */
val IcCancelExtendedBolus: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCancelExtendedBolus",
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
            moveTo(19.538f, 9.528f)
            lineToRelative(0.378f, -0.731f)
            curveToRelative(0.192f, -0.371f, 0.046f, -0.828f, -0.326f, -1.02f)
            curveToRelative(-0.375f, -0.194f, -0.828f, -0.046f, -1.02f, 0.326f)
            lineToRelative(-0.378f, 0.732f)
            curveToRelative(-0.405f, -0.158f, -0.825f, -0.284f, -1.261f, -0.367f)
            verticalLineTo(7.828f)
            curveToRelative(0.48f, -0.065f, 0.857f, -0.46f, 0.857f, -0.959f)
            curveToRelative(0f, -0.544f, -0.44f, -0.984f, -0.984f, -0.984f)
            horizontalLineToRelative(-2.471f)
            curveToRelative(-0.544f, 0f, -0.984f, 0.441f, -0.984f, 0.984f)
            curveToRelative(0f, 0.498f, 0.376f, 0.894f, 0.857f, 0.959f)
            verticalLineToRelative(0.639f)
            curveToRelative(-3.339f, 0.64f, -5.871f, 3.578f, -5.871f, 7.1f)
            curveToRelative(0f, 3.988f, 3.245f, 7.233f, 7.233f, 7.233f)
            curveToRelative(3.988f, 0f, 7.233f, -3.245f, 7.233f, -7.233f)
            curveTo(22.8f, 13.045f, 21.5f, 10.823f, 19.538f, 9.528f)
            close()

            moveTo(15.567f, 21.732f)
            curveToRelative(-3.399f, 0f, -6.165f, -2.765f, -6.165f, -6.164f)
            reflectiveCurveToRelative(2.765f, -6.165f, 6.165f, -6.165f)
            reflectiveCurveToRelative(6.164f, 2.765f, 6.164f, 6.165f)
            reflectiveCurveTo(18.967f, 21.732f, 15.567f, 21.732f)
            close()

            moveTo(15.569f, 10.722f)
            curveToRelative(-0.092f, 0f, -0.181f, 0.037f, -0.246f, 0.102f)
            curveToRelative(-0.065f, 0.065f, -0.102f, 0.154f, -0.102f, 0.246f)
            verticalLineToRelative(4.486f)
            curveToRelative(0f, 0.086f, 0.031f, 0.168f, 0.088f, 0.232f)
            lineToRelative(2.979f, 3.354f)
            curveToRelative(0.062f, 0.069f, 0.148f, 0.111f, 0.241f, 0.116f)
            curveToRelative(0.006f, 0.001f, 0.014f, 0.001f, 0.02f, 0.001f)
            curveToRelative(0.086f, 0f, 0.168f, -0.031f, 0.232f, -0.089f)
            curveToRelative(1.037f, -0.925f, 1.632f, -2.238f, 1.632f, -3.603f)
            curveTo(20.413f, 12.897f, 18.24f, 10.723f, 15.569f, 10.722f)
            close()
        }

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
            moveTo(1.884f, 10.1f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            curveTo(1.133f, 9.633f, 1.133f, 9.201f, 1.401f, 8.934f)
            lineTo(8.934f, 1.4f)
            curveToRelative(0.268f, -0.267f, 0.699f, -0.267f, 0.967f, 0.001f)
            curveTo(10.168f, 1.668f, 10.168f, 2.1f, 9.9f, 2.368f)
            lineTo(2.367f, 9.9f)
            curveTo(2.233f, 10.033f, 2.058f, 10.1f, 1.884f, 10.1f)
            close()

            moveTo(9.418f, 10.1f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            lineTo(1.401f, 2.368f)
            curveTo(1.133f, 2.1f, 1.133f, 1.668f, 1.4f, 1.401f)
            curveTo(1.667f, 1.133f, 2.099f, 1.133f, 2.367f, 1.4f)
            lineTo(9.9f, 8.934f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, 0.001f, 0.966f)
            curveTo(9.768f, 10.033f, 9.592f, 10.1f, 9.418f, 10.1f)
            close()
        }
    }.build()
}
