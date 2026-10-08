package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Glunovo CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginGlunovoPreview
 */
val IcPluginGlunovo: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginGlunovo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(5.201f, 11.16f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.407f, 0.061f, 9.96f, 5.376f, 9.96f)
            horizontalLineToRelative(2.847f)
            curveToRelative(5.315f, 0f, 5.376f, -9.553f, 5.376f, -9.96f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.407f, -0.061f, -9.96f, -5.376f, -9.96f)
            horizontalLineToRelative(-3.339f)
            curveTo(5.257f, 1.2f, 5.201f, 10.753f, 5.201f, 11.16f)
            close()
            moveTo(13.423f, 1.795f)
            curveToRelative(2.168f, 0f, 3.353f, 1.955f, 4f, 4.078f)
            horizontalLineToRelative(-1.178f)
            verticalLineToRelative(1.435f)
            horizontalLineToRelative(1.538f)
            curveToRelative(0.409f, 2.011f, 0.42f, 3.81f, 0.42f, 3.851f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.094f, -0.055f, 9.364f, -4.78f, 9.364f)
            horizontalLineToRelative(-2.847f)
            curveToRelative(-4.722f, 0f, -4.78f, -9.271f, -4.78f, -9.364f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.041f, 0.01f, -1.84f, 0.376f, -3.851f)
            horizontalLineToRelative(1.581f)
            verticalLineTo(5.874f)
            horizontalLineTo(6.496f)
            curveToRelative(0.581f, -2.123f, 1.643f, -4.078f, 3.589f, -4.078f)
            horizontalLineTo(13.423f)
            close()
        }

        // Small path (ellipse-like)
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
            moveTo(11.761f, 6.383f)
            curveToRelative(0f, 0.14f, 0.054f, 0.258f, 0.162f, 0.354f)
            curveToRelative(0.108f, 0.096f, 0.263f, 0.143f, 0.466f, 0.143f)
            curveToRelative(0.203f, 0f, 0.358f, -0.048f, 0.466f, -0.143f)
            curveToRelative(0.108f, -0.096f, 0.162f, -0.213f, 0.162f, -0.354f)
            curveToRelative(0f, -0.14f, -0.054f, -0.258f, -0.162f, -0.352f)
            curveToRelative(-0.108f, -0.095f, -0.265f, -0.142f, -0.47f, -0.142f)
            curveToRelative(-0.2f, 0f, -0.354f, 0.047f, -0.462f, 0.142f)
            curveToRelative(-0.108f, 0.095f, -0.162f, 0.213f, -0.162f, 0.352f)
            close()
        }

        // Main detailed path (rest of the icons)
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
            moveTo(6.496f, 5.874f)
            horizontalLineToRelative(1.258f)
            verticalLineToRelative(1.435f)
            horizontalLineTo(6.173f)
            curveToRelative(-0.366f, 2.011f, -0.376f, 3.81f, -0.376f, 3.851f)
            verticalLineToRelative(1.68f)
            curveToRelative(0f, 0.094f, 0.058f, 9.364f, 4.78f, 9.364f)
            horizontalLineToRelative(2.847f)
            curveToRelative(4.726f, 0f, 4.78f, -9.271f, 4.78f, -9.364f)
            verticalLineToRelative(-1.68f)
            curveToRelative(0f, -0.041f, -0.011f, -1.84f, -0.42f, -3.851f)
            horizontalLineToRelative(-1.538f)
            verticalLineTo(5.874f)
            horizontalLineToRelative(1.178f)
            curveToRelative(-0.647f, -2.123f, -1.832f, -4.078f, -4f, -4.078f)
            horizontalLineToRelative(-3.339f)
            curveTo(8.139f, 1.795f, 7.077f, 3.75f, 6.496f, 5.874f)
            close()
            moveTo(13.023f, 17.226f)
            curveToRelative(0.116f, 0.118f, 0.217f, 0.29f, 0.305f, 0.515f)
            curveToRelative(0.088f, 0.225f, 0.132f, 0.453f, 0.132f, 0.683f)
            curveToRelative(0f, 0.293f, -0.062f, 0.548f, -0.186f, 0.766f)
            curveToRelative(-0.124f, 0.218f, -0.301f, 0.382f, -0.532f, 0.491f)
            curveToRelative(-0.231f, 0.11f, -0.481f, 0.164f, -0.752f, 0.164f)
            curveToRelative(-0.294f, 0f, -0.556f, -0.061f, -0.784f, -0.183f)
            curveToRelative(-0.229f, -0.122f, -0.404f, -0.301f, -0.526f, -0.537f)
            curveToRelative(-0.094f, -0.18f, -0.141f, -0.403f, -0.141f, -0.671f)
            curveToRelative(0f, -0.348f, 0.074f, -0.619f, 0.221f, -0.815f)
            curveToRelative(0.147f, -0.196f, 0.35f, -0.321f, 0.61f, -0.377f)
            lineToRelative(0.106f, 0.562f)
            curveToRelative(-0.139f, 0.039f, -0.248f, 0.114f, -0.328f, 0.223f)
            curveToRelative(-0.08f, 0.109f, -0.12f, 0.245f, -0.12f, 0.408f)
            curveToRelative(0f, 0.247f, 0.079f, 0.444f, 0.237f, 0.589f)
            curveToRelative(0.158f, 0.146f, 0.392f, 0.219f, 0.703f, 0.219f)
            curveToRelative(0.335f, 0f, 0.587f, -0.074f, 0.754f, -0.222f)
            curveToRelative(0.168f, -0.148f, 0.251f, -0.341f, 0.251f, -0.581f)
            curveToRelative(0f, -0.118f, -0.023f, -0.237f, -0.07f, -0.356f)
            curveToRelative(-0.047f, -0.119f, -0.104f, -0.221f, -0.171f, -0.307f)
            horizontalLineToRelative(-0.358f)
            verticalLineToRelative(0.648f)
            horizontalLineToRelative(-0.476f)
            verticalLineToRelative(-1.219f)
            horizontalLineTo(13.023f)
            close()
            moveTo(13.412f, 16.397f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-2.824f)
            verticalLineToRelative(-0.537f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.412f, 14.196f)
            verticalLineToRelative(0.499f)
            horizontalLineToRelative(-0.306f)
            curveToRelative(0.109f, 0.074f, 0.195f, 0.171f, 0.258f, 0.291f)
            curveToRelative(0.063f, 0.12f, 0.094f, 0.248f, 0.094f, 0.381f)
            curveToRelative(0f, 0.136f, -0.03f, 0.259f, -0.091f, 0.367f)
            curveToRelative(-0.06f, 0.108f, -0.145f, 0.187f, -0.254f, 0.235f)
            curveToRelative(-0.109f, 0.048f, -0.26f, 0.073f, -0.453f, 0.073f)
            horizontalLineToRelative(-1.295f)
            verticalLineToRelative(-0.537f)
            horizontalLineToRelative(0.94f)
            curveToRelative(0.288f, 0f, 0.464f, -0.01f, 0.529f, -0.03f)
            curveToRelative(0.065f, -0.02f, 0.116f, -0.056f, 0.154f, -0.108f)
            curveToRelative(0.038f, -0.052f, 0.057f, -0.118f, 0.057f, -0.199f)
            curveToRelative(0f, -0.092f, -0.025f, -0.174f, -0.076f, -0.247f)
            curveToRelative(-0.051f, -0.073f, -0.114f, -0.122f, -0.189f, -0.149f)
            curveToRelative(-0.075f, -0.027f, -0.259f, -0.04f, -0.552f, -0.04f)
            horizontalLineToRelative(-0.863f)
            verticalLineToRelative(-0.537f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.412f, 12f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-1.044f)
            curveToRelative(-0.221f, 0f, -0.364f, 0.011f, -0.429f, 0.034f)
            curveToRelative(-0.065f, 0.023f, -0.115f, 0.06f, -0.151f, 0.112f)
            curveToRelative(-0.036f, 0.052f, -0.054f, 0.114f, -0.054f, 0.186f)
            curveToRelative(0f, 0.093f, 0.026f, 0.176f, 0.077f, 0.25f)
            curveToRelative(0.051f, 0.074f, 0.119f, 0.125f, 0.204f, 0.152f)
            curveToRelative(0.085f, 0.027f, 0.242f, 0.041f, 0.47f, 0.041f)
            horizontalLineToRelative(0.927f)
            verticalLineToRelative(0.537f)
            horizontalLineToRelative(-2.046f)
            verticalLineTo(13.35f)
            horizontalLineToRelative(0.301f)
            curveToRelative(-0.231f, -0.177f, -0.347f, -0.4f, -0.347f, -0.669f)
            curveToRelative(0f, -0.118f, 0.022f, -0.227f, 0.065f, -0.325f)
            curveToRelative(0.043f, -0.098f, 0.098f, -0.172f, 0.165f, -0.222f)
            curveToRelative(0.067f, -0.05f, 0.142f, -0.085f, 0.227f, -0.105f)
            curveToRelative(0.085f, -0.02f, 0.206f, -0.03f, 0.364f, -0.03f)
            horizontalLineTo(13.412f)
            close()
            moveTo(13.153f, 9.889f)
            curveToRelative(0.204f, 0.198f, 0.305f, 0.447f, 0.305f, 0.748f)
            curveToRelative(0f, 0.186f, -0.042f, 0.363f, -0.127f, 0.532f)
            curveToRelative(-0.085f, 0.169f, -0.209f, 0.297f, -0.373f, 0.385f)
            curveToRelative(-0.164f, 0.088f, -0.363f, 0.132f, -0.598f, 0.132f)
            curveToRelative(-0.18f, 0f, -0.354f, -0.044f, -0.522f, -0.132f)
            curveToRelative(-0.168f, -0.088f, -0.297f, -0.212f, -0.385f, -0.373f)
            curveToRelative(-0.089f, -0.161f, -0.133f, -0.341f, -0.133f, -0.54f)
            curveToRelative(0f, -0.307f, 0.101f, -0.559f, 0.302f, -0.755f)
            curveToRelative(0.061f, -0.059f, 0.126f, -0.109f, 0.197f, -0.151f)
            curveToRelative(0.106f, -0.062f, 0.198f, -0.043f, 0.255f, 0.014f)
            curveToRelative(0.057f, 0.057f, 0.073f, 0.15f, 0.04f, 0.226f)
            curveToRelative(-0.046f, 0.106f, -0.154f, 0.109f, -0.154f, 0.109f)
            horizontalLineToRelative(-0.15f)
            verticalLineToRelative(0.454f)
            horizontalLineToRelative(0.361f)
            verticalLineToRelative(0.36f)
            horizontalLineToRelative(0.437f)
            verticalLineToRelative(-0.36f)
            horizontalLineToRelative(0.361f)
            verticalLineToRelative(-0.454f)
            horizontalLineToRelative(-0.361f)
            verticalLineTo(9.611f)
            curveToRelative(0.111f, 0.037f, 0.293f, 0.13f, 0.445f, 0.278f)
            close()
            moveTo(13.412f, 8.249f)
            verticalLineToRelative(0.483f)
            lineToRelative(-2.046f, 0.818f)
            verticalLineTo(8.987f)
            lineToRelative(1.393f, -0.493f)
            lineToRelative(-1.393f, -0.499f)
            verticalLineTo(7.443f)
            lineToRelative(2.046f, 0.806f)
            close()
            moveTo(13.153f, 5.633f)
            curveToRelative(0.204f, 0.198f, 0.305f, 0.447f, 0.305f, 0.748f)
            curveToRelative(0f, 0.186f, -0.042f, 0.363f, -0.127f, 0.532f)
            curveToRelative(-0.085f, 0.169f, -0.209f, 0.297f, -0.373f, 0.385f)
            curveToRelative(-0.164f, 0.088f, -0.363f, 0.132f, -0.598f, 0.132f)
            curveToRelative(-0.18f, 0f, -0.354f, -0.044f, -0.522f, -0.132f)
            curveToRelative(-0.168f, -0.088f, -0.297f, -0.212f, -0.385f, -0.373f)
            curveToRelative(-0.089f, -0.161f, -0.133f, -0.341f, -0.133f, -0.54f)
            curveToRelative(0f, -0.307f, 0.101f, -0.559f, 0.302f, -0.755f)
            curveToRelative(0.201f, -0.196f, 0.455f, -0.294f, 0.762f, -0.294f)
            curveToRelative(0.311f, 0f, 0.568f, 0.099f, 0.771f, 0.297f)
            close()
        }
    }.build()
}
