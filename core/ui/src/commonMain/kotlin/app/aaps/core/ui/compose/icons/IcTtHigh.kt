package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for High Temp Target.
 *
 * replaces ic_temptarget_high
 *
 * Bounding box: x: 1.2-22.8, y: 5.9-18.0 (viewport: 24x24, ~90% width)
 *
 * @see IcTtHighIconPreview
 */
val IcTtHigh: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtHigh",
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
            moveTo(7f, 17.978f)
            curveToRelative(-0.006f, 0f, -0.011f, 0f, -0.017f, 0f)
            curveToRelative(-0.731f, -0.013f, -1.385f, -0.871f, -1.796f, -2.354f)
            curveToRelative(-0.299f, -1.082f, -0.57f, -2.209f, -0.832f, -3.301f)
            curveToRelative(-0.164f, -0.683f, -0.328f, -1.367f, -0.5f, -2.044f)
            lineTo(3.74f, 9.824f)
            curveTo(3.352f, 8.262f, 2.952f, 6.647f, 1.906f, 6.465f)
            curveToRelative(-0.122f, -0.021f, -0.203f, -0.137f, -0.182f, -0.259f)
            curveToRelative(0.021f, -0.121f, 0.136f, -0.198f, 0.259f, -0.181f)
            curveToRelative(1.332f, 0.232f, 1.769f, 1.991f, 2.191f, 3.692f)
            lineToRelative(0.113f, 0.454f)
            curveToRelative(0.172f, 0.678f, 0.337f, 1.364f, 0.502f, 2.05f)
            curveToRelative(0.261f, 1.088f, 0.532f, 2.211f, 0.829f, 3.286f)
            curveToRelative(0.338f, 1.222f, 0.877f, 2.017f, 1.374f, 2.027f)
            curveToRelative(0.003f, 0f, 0.006f, 0f, 0.009f, 0f)
            curveToRelative(0.465f, 0f, 0.951f, -0.69f, 1.333f, -1.895f)
            curveToRelative(0.197f, -0.622f, 0.366f, -1.292f, 0.53f, -1.94f)
            lineToRelative(0.13f, -0.512f)
            curveToRelative(0.145f, -0.563f, 0.281f, -1.135f, 0.418f, -1.706f)
            curveToRelative(0.28f, -1.17f, 0.57f, -2.38f, 0.923f, -3.501f)
            curveToRelative(0.391f, -1.242f, 0.975f, -1.93f, 1.644f, -1.935f)
            curveToRelative(0.003f, 0f, 0.006f, 0f, 0.009f, 0f)
            curveToRelative(0.671f, 0f, 1.269f, 0.683f, 1.684f, 1.925f)
            curveToRelative(0.292f, 0.875f, 0.54f, 1.817f, 0.778f, 2.73f)
            lineToRelative(0.186f, 0.705f)
            curveToRelative(0.155f, 0.581f, 0.303f, 1.169f, 0.451f, 1.757f)
            curveToRelative(0.252f, 1.005f, 0.514f, 2.044f, 0.808f, 3.025f)
            curveToRelative(0.241f, 0.809f, 0.651f, 1.324f, 1.068f, 1.345f)
            curveToRelative(0.375f, 0.045f, 0.766f, -0.381f, 1.062f, -1.094f)
            curveToRelative(0.285f, -0.684f, 0.547f, -1.482f, 0.799f, -2.436f)
            curveToRelative(0.35f, -1.329f, 0.681f, -2.676f, 1.013f, -4.024f)
            curveToRelative(0.153f, -0.622f, 0.306f, -1.244f, 0.461f, -1.863f)
            curveTo(20.593f, 6.929f, 21.15f, 6.248f, 22f, 6.028f)
            curveToRelative(0.117f, -0.029f, 0.24f, 0.042f, 0.272f, 0.161f)
            curveToRelative(0.031f, 0.12f, -0.041f, 0.241f, -0.161f, 0.272f)
            curveToRelative(-0.685f, 0.177f, -1.124f, 0.736f, -1.38f, 1.759f)
            curveToRelative(-0.155f, 0.619f, -0.308f, 1.241f, -0.46f, 1.862f)
            curveToRelative(-0.332f, 1.349f, -0.665f, 2.699f, -1.015f, 4.031f)
            curveToRelative(-0.258f, 0.975f, -0.525f, 1.791f, -0.819f, 2.495f)
            curveToRelative(-0.512f, 1.229f, -1.152f, 1.387f, -1.498f, 1.368f)
            curveToRelative(-0.62f, -0.032f, -1.171f, -0.654f, -1.474f, -1.664f)
            curveToRelative(-0.296f, -0.989f, -0.558f, -2.034f, -0.813f, -3.044f)
            curveToRelative(-0.147f, -0.586f, -0.295f, -1.171f, -0.448f, -1.751f)
            lineToRelative(-0.187f, -0.708f)
            curveToRelative(-0.236f, -0.905f, -0.481f, -1.84f, -0.769f, -2.701f)
            curveToRelative(-0.34f, -1.015f, -0.811f, -1.619f, -1.262f, -1.619f)
            curveToRelative(-0.002f, 0f, -0.003f, 0f, -0.004f, 0f)
            curveTo(11.536f, 6.493f, 11.079f, 7.1f, 10.76f, 8.112f)
            curveToRelative(-0.348f, 1.106f, -0.636f, 2.308f, -0.914f, 3.47f)
            curveToRelative(-0.138f, 0.574f, -0.275f, 1.147f, -0.42f, 1.713f)
            lineToRelative(-0.13f, 0.511f)
            curveToRelative(-0.166f, 0.655f, -0.337f, 1.332f, -0.538f, 1.966f)
            curveTo(8.182f, 17.59f, 7.487f, 17.978f, 7f, 17.978f)
            close()

            moveTo(22.055f, 12.745f)
            horizontalLineTo(17.04f)
            curveToRelative(-0.411f, 0f, -0.745f, -0.333f, -0.745f, -0.745f)
            verticalLineTo(9.034f)
            horizontalLineTo(7.72f)
            lineTo(7.72f, 12f)
            curveToRelative(0f, 0.411f, -0.333f, 0.745f, -0.744f, 0.745f)
            horizontalLineTo(1.944f)
            curveTo(1.533f, 12.745f, 1.2f, 12.411f, 1.2f, 12f)
            curveToRelative(0f, -0.411f, 0.333f, -0.744f, 0.744f, -0.744f)
            horizontalLineToRelative(4.286f)
            lineToRelative(0.001f, -2.966f)
            curveToRelative(0f, -0.411f, 0.333f, -0.744f, 0.744f, -0.744f)
            horizontalLineTo(17.04f)
            curveToRelative(0.411f, 0f, 0.745f, 0.333f, 0.745f, 0.744f)
            verticalLineToRelative(2.966f)
            horizontalLineToRelative(4.27f)
            curveToRelative(0.411f, 0f, 0.745f, 0.333f, 0.745f, 0.744f)
            curveTo(22.8f, 12.411f, 22.467f, 12.745f, 22.055f, 12.745f)
            close()
        }
    }.build()
}
