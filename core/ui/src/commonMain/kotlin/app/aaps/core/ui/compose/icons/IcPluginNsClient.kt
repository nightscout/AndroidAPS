package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for NSClient Plugin.
 * Represents Nightscout client connection.
 *
 * replacing ic_nightscout_syncs
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see PluginNsClientIconPreview
 */
val IcPluginNsClient: ImageVector by lazy {
    ImageVector.Builder(
        name = "PluginNsClient",
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
            moveTo(20.155f, 21.284f)
            lineToRelative(-1.261f, -1.687f)
            curveToRelative(2.376f, -1.886f, 3.905f, -4.834f, 3.905f, -8.147f)
            curveToRelative(0f, -4.138f, -2.386f, -7.707f, -5.828f, -9.348f)
            curveToRelative(-0.424f, 0.972f, -0.975f, 2.235f, -1.502f, 3.442f)
            curveToRelative(2.67f, 1.083f, 4.558f, 3.746f, 4.558f, 6.861f)
            curveToRelative(0f, 2.183f, -0.928f, 4.144f, -2.402f, 5.495f)
            lineToRelative(-1.152f, -1.542f)
            lineToRelative(-2.088f, 5.529f)
            curveTo(14.387f, 21.888f, 20.155f, 21.284f, 20.155f, 21.284f)
            close()

            moveTo(3.847f, 2.716f)
            lineToRelative(1.261f, 1.687f)
            curveTo(2.73f, 6.289f, 1.2f, 9.236f, 1.2f, 12.55f)
            curveToRelative(0f, 4.138f, 2.387f, 7.707f, 5.832f, 9.348f)
            curveToRelative(0.424f, -0.972f, 0.975f, -2.235f, 1.502f, -3.442f)
            curveToRelative(-2.672f, -1.083f, -4.561f, -3.746f, -4.561f, -6.861f)
            curveToRelative(0f, -2.183f, 0.928f, -4.144f, 2.403f, -5.495f)
            lineTo(7.53f, 7.641f)
            lineToRelative(2.089f, -5.529f)
            curveTo(9.619f, 2.112f, 3.847f, 2.716f, 3.847f, 2.716f)
            close()

            moveTo(16.2f, 10.404f)
            curveToRelative(0f, 0.377f, -0.049f, 0.847f, -0.07f, 0.999f)
            curveToRelative(-0.061f, 0.432f, -0.17f, 0.852f, -0.306f, 1.265f)
            curveToRelative(-0.177f, 0.537f, -0.401f, 1.054f, -0.672f, 1.551f)
            curveToRelative(-0.166f, 0.305f, -0.34f, 0.605f, -0.53f, 0.896f)
            curveToRelative(-0.205f, 0.313f, -0.423f, 0.617f, -0.654f, 0.912f)
            curveToRelative(-0.22f, 0.28f, -0.443f, 0.556f, -0.678f, 0.823f)
            curveToRelative(-0.077f, 0.087f, -0.158f, 0.17f, -0.235f, 0.257f)
            curveToRelative(-0.15f, 0.167f, -0.281f, 0.304f, -0.454f, 0.449f)
            curveToRelative(-0.377f, 0.354f, -0.931f, 0.326f, -1.258f, -0.043f)
            curveToRelative(-0.248f, -0.242f, -0.482f, -0.498f, -0.709f, -0.76f)
            curveToRelative(-0.281f, -0.325f, -0.549f, -0.659f, -0.805f, -1.003f)
            curveToRelative(-0.38f, -0.511f, -0.721f, -1.047f, -1.023f, -1.606f)
            curveToRelative(-0.217f, -0.401f, -0.4f, -0.82f, -0.551f, -1.25f)
            curveToRelative(-0.204f, -0.581f, -0.365f, -1.172f, -0.423f, -1.789f)
            curveTo(7.824f, 11.029f, 7.8f, 10.729f, 7.8f, 10.407f)
            curveToRelative(0f, -0.342f, 0.035f, -0.613f, 0.051f, -0.719f)
            curveTo(7.91f, 9.289f, 8.042f, 8.914f, 8.214f, 8.552f)
            curveToRelative(0.141f, -0.296f, 0.32f, -0.568f, 0.529f, -0.823f)
            curveToRelative(0.202f, -0.246f, 0.426f, -0.468f, 0.675f, -0.662f)
            curveToRelative(0.273f, -0.214f, 0.571f, -0.393f, 0.892f, -0.531f)
            curveToRelative(0.353f, -0.151f, 0.716f, -0.262f, 1.098f, -0.31f)
            curveToRelative(0.067f, -0.009f, 0.316f, -0.033f, 0.617f, -0.033f)
            curveToRelative(0.385f, 0f, 0.762f, 0.057f, 0.933f, 0.097f)
            curveToRelative(0.387f, 0.091f, 0.756f, 0.233f, 1.104f, 0.429f)
            curveToRelative(0.492f, 0.278f, 0.913f, 0.64f, 1.261f, 1.083f)
            curveToRelative(0.25f, 0.318f, 0.451f, 0.667f, 0.597f, 1.046f)
            curveToRelative(0.102f, 0.266f, 0.183f, 0.538f, 0.229f, 0.821f)
            curveTo(16.165f, 9.77f, 16.2f, 10.072f, 16.2f, 10.404f)
            close()

            moveTo(12.027f, 6.861f)
            curveToRelative(-0.165f, -0.003f, -0.327f, 0.018f, -0.49f, 0.036f)
            curveToRelative(-0.34f, 0.036f, -0.662f, 0.136f, -0.971f, 0.274f)
            curveTo(10.033f, 7.407f, 9.593f, 7.763f, 9.23f, 8.22f)
            curveTo(8.978f, 8.538f, 8.79f, 8.887f, 8.657f, 9.268f)
            curveToRelative(-0.073f, 0.208f, -0.12f, 0.425f, -0.15f, 0.643f)
            curveToRelative(-0.024f, 0.179f, -0.04f, 0.363f, -0.034f, 0.543f)
            curveToRelative(0.007f, 0.217f, 0.014f, 0.421f, 0.046f, 0.631f)
            curveToRelative(0.031f, 0.204f, 0.055f, 0.41f, 0.1f, 0.611f)
            curveToRelative(0.13f, 0.574f, 0.321f, 1.128f, 0.57f, 1.662f)
            curveToRelative(0.216f, 0.465f, 0.467f, 0.912f, 0.748f, 1.341f)
            curveToRelative(0.282f, 0.433f, 0.589f, 0.847f, 0.918f, 1.246f)
            curveToRelative(0.298f, 0.362f, 0.605f, 0.714f, 0.938f, 1.044f)
            curveToRelative(0.124f, 0.123f, 0.29f, 0.121f, 0.415f, 0f)
            curveToRelative(0.333f, -0.322f, 0.809f, -0.883f, 1.035f, -1.16f)
            curveToRelative(0.209f, -0.256f, 0.407f, -0.52f, 0.595f, -0.792f)
            curveToRelative(0.241f, -0.348f, 0.466f, -0.707f, 0.668f, -1.08f)
            curveToRelative(0.228f, -0.42f, 0.433f, -0.849f, 0.594f, -1.298f)
            curveToRelative(0.143f, -0.399f, 0.254f, -0.805f, 0.328f, -1.223f)
            curveToRelative(0.055f, -0.31f, 0.085f, -0.622f, 0.103f, -0.935f)
            curveToRelative(0.011f, -0.185f, -0.012f, -0.369f, -0.033f, -0.552f)
            curveToRelative(-0.039f, -0.345f, -0.131f, -0.676f, -0.275f, -0.989f)
            curveToRelative(-0.204f, -0.445f, -0.482f, -0.838f, -0.848f, -1.168f)
            curveToRelative(-0.272f, -0.246f, -0.57f, -0.447f, -0.901f, -0.601f)
            curveTo(13.015f, 6.974f, 12.532f, 6.869f, 12.027f, 6.861f)
            close()

            moveTo(11.684f, 11.178f)
            curveToRelative(0f, 0.262f, 0.001f, 0.524f, 0f, 0.786f)
            curveToRelative(-0.001f, 0.128f, 0.054f, 0.187f, 0.174f, 0.175f)
            curveToRelative(0.092f, -0.009f, 0.186f, -0.002f, 0.28f, -0.002f)
            curveToRelative(0.152f, 0f, 0.178f, -0.026f, 0.178f, -0.174f)
            curveToRelative(0f, -0.512f, -0.012f, -1.024f, 0.004f, -1.535f)
            curveToRelative(0.014f, -0.451f, 0.214f, -0.816f, 0.613f, -1.045f)
            curveToRelative(0.414f, -0.238f, 0.85f, -0.242f, 1.275f, -0.042f)
            curveToRelative(0.356f, 0.168f, 0.568f, 0.466f, 0.657f, 0.853f)
            curveToRelative(0.077f, 0.333f, 0.011f, 0.653f, -0.068f, 0.972f)
            curveToRelative(-0.098f, 0.395f, -0.286f, 0.747f, -0.541f, 1.061f)
            curveToRelative(-0.386f, 0.474f, -0.875f, 0.793f, -1.467f, 0.962f)
            curveToRelative(-0.341f, 0.097f, -0.686f, 0.126f, -1.033f, 0.101f)
            curveToRelative(-0.54f, -0.04f, -1.029f, -0.23f, -1.468f, -0.55f)
            curveToRelative(-0.339f, -0.247f, -0.608f, -0.555f, -0.817f, -0.914f)
            curveToRelative(-0.156f, -0.268f, -0.261f, -0.559f, -0.316f, -0.866f)
            curveToRelative(-0.03f, -0.166f, -0.059f, -0.333f, -0.054f, -0.503f)
            curveToRelative(0.015f, -0.584f, 0.411f, -1.125f, 1.023f, -1.23f)
            curveToRelative(0.349f, -0.06f, 0.677f, -0.017f, 0.979f, 0.166f)
            curveToRelative(0.291f, 0.177f, 0.468f, 0.441f, 0.545f, 0.773f)
            curveToRelative(0.026f, 0.11f, 0.039f, 0.221f, 0.037f, 0.335f)
            curveTo(11.682f, 10.727f, 11.684f, 10.953f, 11.684f, 11.178f)
            close()

            moveTo(13.613f, 9.355f)
            curveToRelative(-0.608f, -0.003f, -1.106f, 0.491f, -1.113f, 1.09f)
            curveToRelative(-0.008f, 0.627f, 0.493f, 1.128f, 1.104f, 1.126f)
            curveToRelative(0.628f, -0.002f, 1.105f, -0.481f, 1.112f, -1.099f)
            curveTo(14.722f, 9.85f, 14.223f, 9.364f, 13.613f, 9.355f)
            close()

            moveTo(11.503f, 10.456f)
            curveToRelative(-0.003f, -0.607f, -0.51f, -1.084f, -1.059f, -1.1f)
            curveToRelative(-0.677f, -0.019f, -1.158f, 0.511f, -1.159f, 1.099f)
            curveToRelative(-0.001f, 0.627f, 0.488f, 1.113f, 1.103f, 1.116f)
            curveTo(10.994f, 11.575f, 11.495f, 11.103f, 11.503f, 10.456f)
            close()

            moveTo(14.367f, 8.734f)
            curveToRelative(-0.102f, -0.011f, -0.193f, -0.051f, -0.291f, -0.071f)
            curveToRelative(-0.607f, -0.126f, -1.168f, -0.031f, -1.672f, 0.343f)
            curveToRelative(-0.137f, 0.102f, -0.255f, 0.225f, -0.347f, 0.368f)
            curveToRelative(-0.052f, 0.082f, -0.082f, 0.039f, -0.113f, -0.005f)
            curveToRelative(-0.062f, -0.088f, -0.131f, -0.169f, -0.209f, -0.243f)
            curveToRelative(-0.295f, -0.276f, -0.643f, -0.44f, -1.043f, -0.494f)
            curveToRelative(-0.313f, -0.042f, -0.621f, -0.026f, -0.923f, 0.072f)
            curveTo(9.732f, 8.716f, 9.697f, 8.729f, 9.66f, 8.74f)
            curveTo(9.653f, 8.743f, 9.643f, 8.739f, 9.621f, 8.736f)
            curveToRelative(0.628f, -0.847f, 1.465f, -1.273f, 2.505f, -1.235f)
            curveTo(13.065f, 7.536f, 13.814f, 7.968f, 14.367f, 8.734f)
            close()

            moveTo(12.001f, 16.315f)
            curveToRelative(-0.202f, -0.216f, -0.396f, -0.43f, -0.58f, -0.653f)
            curveToRelative(-0.077f, -0.094f, -0.155f, -0.187f, -0.23f, -0.282f)
            curveToRelative(-0.026f, -0.033f, -0.031f, -0.05f, -0.003f, -0.088f)
            curveToRelative(0.247f, -0.344f, 0.48f, -0.697f, 0.69f, -1.065f)
            curveToRelative(0.047f, -0.082f, 0.095f, -0.163f, 0.137f, -0.247f)
            curveToRelative(0.02f, -0.04f, 0.044f, -0.054f, 0.089f, -0.056f)
            curveToRelative(0.558f, -0.026f, 1.088f, -0.158f, 1.578f, -0.432f)
            curveToRelative(0.174f, -0.097f, 0.342f, -0.204f, 0.507f, -0.321f)
            curveTo(13.653f, 14.352f, 12.888f, 15.373f, 12.001f, 16.315f)
            close()

            moveTo(9.81f, 13.173f)
            curveToRelative(0.223f, 0.154f, 0.447f, 0.297f, 0.69f, 0.412f)
            curveToRelative(0.256f, 0.121f, 0.521f, 0.213f, 0.799f, 0.265f)
            curveToRelative(0.076f, 0.014f, 0.067f, 0.038f, 0.04f, 0.082f)
            curveToRelative(-0.147f, 0.237f, -0.289f, 0.478f, -0.449f, 0.707f)
            curveToRelative(-0.04f, 0.057f, -0.08f, 0.115f, -0.126f, 0.18f)
            curveTo(10.394f, 14.295f, 10.071f, 13.754f, 9.81f, 13.173f)
            close()

            moveTo(13.605f, 9.988f)
            curveToRelative(0.251f, -0.034f, 0.495f, 0.215f, 0.487f, 0.489f)
            curveToRelative(-0.007f, 0.229f, -0.219f, 0.475f, -0.489f, 0.472f)
            curveToRelative(-0.251f, -0.003f, -0.484f, -0.235f, -0.478f, -0.502f)
            curveTo(13.129f, 10.207f, 13.365f, 9.954f, 13.605f, 9.988f)
            close()

            moveTo(10.871f, 10.455f)
            curveToRelative(0.028f, 0.266f, -0.232f, 0.495f, -0.48f, 0.492f)
            curveToRelative(-0.256f, -0.003f, -0.484f, -0.221f, -0.48f, -0.497f)
            curveToRelative(0.003f, -0.214f, 0.197f, -0.487f, 0.521f, -0.468f)
            curveTo(10.637f, 9.994f, 10.898f, 10.201f, 10.871f, 10.455f)
            close()
        }
    }.build()
}
