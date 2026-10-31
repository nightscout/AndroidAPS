package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Quick Wizard.
 * Represents fast calculation or quick bolus wizard.
 *
 * Bounding box: x: 1.2-22.8, y: 5.5-18.5 (viewport: 24x24, ~90% width)
 *
 * @see IcQuickwizardIconPreview
 */
val IcQuickwizard: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcQuickwizard",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(0f, 0f)
            horizontalLineTo(24f)
            verticalLineTo(24f)
            horizontalLineTo(0f)
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
            moveTo(10.874f, 5.812f)
            curveTo(10.251f, 6.578f, 9.99f, 6.985f, 9.486f, 7.806f)
            curveToRelative(-2.794f, 0.264f, -4.365f, 3.054f, -3.751f, 5.177f)
            curveToRelative(0.27f, 0.935f, 0.864f, 1.638f, 1.663f, 2.174f)
            curveToRelative(2.606f, 1.746f, 6.93f, 1.586f, 9.421f, -0.339f)
            curveToRelative(1.318f, -1.019f, 1.912f, -2.313f, 1.481f, -3.969f)
            curveToRelative(-0.457f, -1.754f, -1.604f, -2.869f, -3.373f, -3.185f)
            curveToRelative(-1.235f, -0.221f, -2.19f, 0.411f, -2.839f, 1.457f)
            curveToRelative(-0.548f, 0.884f, -0.799f, 1.794f, -1.808f, 2.603f)
            curveToRelative(-0.432f, -0.478f, -0.508f, -1.067f, -0.536f, -1.644f)
            curveToRelative(-0.133f, -2.76f, 2.242f, -4.927f, 5.1f, -4.676f)
            curveToRelative(2.626f, 0.23f, 4.957f, 2.171f, 5.676f, 4.724f)
            curveToRelative(0.679f, 2.411f, -0.251f, 4.919f, -2.431f, 6.559f)
            curveToRelative(-3.325f, 2.501f, -8.764f, 2.583f, -12.147f, 0.183f)
            curveToRelative(-2.284f, -1.62f, -3.197f, -4.308f, -2.33f, -6.86f)
            curveTo(4.515f, 7.352f, 7.118f, 5.494f, 9.784f, 5.6f)
            curveTo(10.119f, 5.614f, 10.445f, 5.666f, 10.874f, 5.812f)
            close()

            moveTo(6.866f, 13.378f)
            curveToRelative(0.584f, -0.114f, 1.308f, -0.244f, 1.848f, -0.357f)
            curveToRelative(1.689f, -0.354f, 2.914f, -1.365f, 3.633f, -2.973f)
            curveToRelative(0.321f, -0.718f, 0.779f, -1.249f, 1.608f, -1.576f)
            curveToRelative(0.464f, 0.901f, 0.504f, 1.821f, 0.248f, 2.767f)
            curveToRelative(-0.315f, 1.164f, -0.987f, 2.068f, -2.019f, 2.697f)
            curveToRelative(-0.684f, 0.417f, -1.425f, 0.708f, -2.182f, 0.963f)
            curveTo(8.723f, 15.328f, 7.594f, 14.895f, 6.866f, 13.378f)
            close()

            moveTo(12.607f, 14.262f)
            curveToRelative(0.815f, -0.596f, 1.388f, -1.209f, 1.63f, -1.789f)
            curveToRelative(0.992f, 0.176f, 2.132f, 0.348f, 3.112f, 0.522f)
            curveTo(16.74f, 14.924f, 14.393f, 15.372f, 12.607f, 14.262f)
            close()

            moveTo(1.578f, 13.906f)
            curveToRelative(0.199f, -0.137f, 0.839f, -0.21f, 1.323f, -0.136f)
            curveToRelative(0.288f, 0.701f, 0.652f, 1.36f, 1.23f, 2.134f)
            curveToRelative(-0.635f, 0.129f, -1.152f, 0.281f, -1.74f, 0.249f)
            curveToRelative(-0.547f, -0.03f, -1.102f, -0.391f, -1.179f, -1.097f)
            curveTo(1.174f, 14.715f, 1.211f, 14.158f, 1.578f, 13.906f)
            close()

            moveTo(20.189f, 15.392f)
            curveToRelative(0.218f, -0.552f, 0.402f, -1.067f, 0.622f, -1.566f)
            curveToRelative(0.261f, -0.59f, 0.687f, -0.844f, 1.244f, -0.756f)
            curveToRelative(0.465f, 0.074f, 0.639f, 0.417f, 0.715f, 0.839f)
            curveToRelative(0.109f, 0.6f, -0.086f, 1.098f, -0.587f, 1.347f)
            curveTo(21.562f, 15.565f, 20.904f, 15.472f, 20.189f, 15.392f)
            close()
        }
    }.build()
}
