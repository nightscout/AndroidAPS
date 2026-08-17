package app.aaps.core.ui.compose.icons.library

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.PathParser
import app.aaps.core.data.model.TE.Location

/**
 * Icon for Child Front View.
 * Represents human child body front view for site rotation.
 *
 * Bounding box: x: 8.0-40.0, y: 3.0-95.0 (viewport: 48x128, ~72% height)
 *
 * @see ChildFrontPreview
 */
val ChildFront: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChildFront",
        defaultWidth = 48.dp,
        defaultHeight = 90.dp,
        viewportWidth = 48f,
        viewportHeight = 90f
    ).apply {
        // Main body (background)
        path(
            name = "background",
            fill = SolidColor(Color(0xFFEFC3AD)),
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 0.5669f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 10f
        ) {
            moveTo(36.835f, 45.666f)
            curveToRelative(-0.124f, -1.269f, -0.479f, -2.526f, -0.155f, -3.815f)
            curveToRelative(0.029f, -0.116f, -0.005f, -0.252f, -0.029f, -0.375f)
            curveToRelative(-0.164f, -0.842f, -0.192f, -1.699f, -0.233f, -2.549f)
            curveToRelative(-0.06f, -1.263f, -0.032f, -1.888f, -0.017f, -3.154f)
            curveToRelative(0.009f, -0.737f, -0.282f, -2.557f, -0.402f, -3.284f)
            curveToRelative(-0.357f, -2.162f, -0.58f, -4.198f, -0.8f, -5.722f)
            curveToRelative(-0.179f, -2.55f, -0.314f, -3.809f, -0.625f, -4.826f)
            curveToRelative(-0.332f, -1.085f, -0.634f, -1.867f, -1.606f, -2.391f)
            curveToRelative(-0.76f, -0.41f, -1.618f, -0.751f, -2.427f, -1.015f)
            curveToRelative(-1.073f, -0.35f, -2.232f, -0.853f, -3.184f, -1.459f)
            curveToRelative(-1.915f, -1.219f, -1.719f, -2.374f, -0.772f, -3.32f)
            curveToRelative(1.538f, -1.538f, 2.113f, -3.64f, 2.113f, -4.654f)
            curveToRelative(0.551f, -0.108f, 0.798f, -2.799f, 0.255f, -2.683f)
            curveToRelative(-0.071f, -0.898f, -0.576f, -3.586f, -2.431f, -4.761f)
            curveToRelative(-1.402f, -0.888f, -3.263f, -0.892f, -4.976f, 0.016f)
            curveTo(18.96f, 3.044f, 19.11f, 5.62f, 18.989f, 6.419f)
            curveToRelative(-0.541f, -0.047f, -0.431f, 2.375f, 0.085f, 2.684f)
            curveToRelative(0.234f, 0.992f, 0.792f, 3.275f, 2.179f, 4.662f)
            curveToRelative(0.878f, 0.878f, 0.778f, 2.1f, -0.603f, 3.017f)
            curveToRelative(-1.249f, 0.83f, -3.451f, 1.661f, -4.928f, 2.228f)
            curveToRelative(-1.527f, 0.586f, -2.162f, 2.021f, -2.518f, 2.916f)
            curveToRelative(-0.252f, 0.634f, -0.334f, 1.291f, -0.397f, 1.985f)
            curveToRelative(-0.069f, 0.757f, -0.158f, 1.575f, -0.158f, 2.331f)
            curveToRelative(0f, 0.927f, -0.074f, 1.443f, -0.093f, 1.669f)
            curveToRelative(-0.156f, 1.9f, -0.307f, 3.168f, -0.67f, 4.87f)
            curveToRelative(-0.104f, 0.489f, -0.267f, 1.824f, -0.286f, 2.319f)
            curveToRelative(-0.074f, 1.893f, 0.022f, 2.944f, -0.143f, 4.839f)
            curveToRelative(-0.097f, 1.121f, -0.022f, 2.257f, -0.028f, 3.387f)
            curveToRelative(-0.002f, 0.302f, -0.053f, 0.599f, -0.09f, 0.897f)
            curveToRelative(-0.1f, 0.813f, -0.321f, 1.615f, -0.058f, 2.463f)
            curveToRelative(0.431f, 1.391f, 1.242f, 2.406f, 2.584f, 2.985f)
            curveToRelative(0.246f, 0.106f, 0.507f, 0.168f, 0.673f, -0.109f)
            curveToRelative(0.144f, -0.241f, -0.113f, -0.353f, -0.229f, -0.499f)
            curveToRelative(-0.094f, -0.118f, -0.2f, -0.236f, -0.324f, -0.32f)
            curveToRelative(-0.894f, -0.607f, -0.757f, -1.524f, -0.727f, -2.404f)
            curveToRelative(0.007f, -0.212f, 0.148f, -0.43f, 0.354f, -0.44f)
            curveToRelative(0.277f, -0.013f, 0.25f, 0.255f, 0.258f, 0.451f)
            curveToRelative(0.007f, 0.152f, -0.023f, 0.31f, 0.01f, 0.455f)
            curveToRelative(0.098f, 0.433f, 0.072f, 0.915f, 0.468f, 1.237f)
            curveToRelative(0.12f, 0.098f, 0.235f, 0.206f, 0.401f, 0.164f)
            curveToRelative(0.197f, -0.05f, 0.176f, -0.222f, 0.176f, -0.37f)
            curveToRelative(-0.002f, -0.887f, -0.004f, -1.773f, -0.014f, -2.66f)
            curveToRelative(-0.001f, -0.122f, -0.02f, -0.254f, -0.07f, -0.364f)
            curveToRelative(-0.237f, -0.521f, -0.492f, -1.034f, -0.733f, -1.554f)
            curveToRelative(-0.321f, -0.694f, -0.357f, -1.433f, -0.166f, -2.148f)
            curveToRelative(0.598f, -2.232f, 1.259f, -4.448f, 1.666f, -6.726f)
            curveToRelative(0.247f, -1.383f, 0.533f, -2.767f, 0.529f, -4.181f)
            curveToRelative(-0.002f, -0.634f, 0.147f, -1.115f, 0.198f, -1.848f)
            curveToRelative(0.006f, -0.08f, 0.013f, -0.244f, 0.095f, -0.325f)
            curveToRelative(0.107f, 0.04f, 0.148f, 0.226f, 0.162f, 0.337f)
            curveToRelative(0.076f, 0.572f, 0.339f, 2.844f, 0.37f, 3.702f)
            curveToRelative(-0.011f, 0.732f, -0.034f, 1.931f, -0.093f, 2.659f)
            curveToRelative(-0.157f, 1.943f, -0.405f, 3.953f, -0.526f, 5.844f)
            curveToRelative(-0.116f, 1.817f, -0.224f, 3.243f, -0.224f, 4.692f)
            curveToRelative(0f, 2.79f, -0.049f, 8.132f, 0.899f, 12.62f)
            curveToRelative(0.219f, 1.037f, 0.351f, 2.079f, 0.301f, 3.146f)
            curveToRelative(-0.04f, 0.86f, -0.005f, 1.722f, -0.008f, 2.584f)
            curveToRelative(-0.001f, 0.329f, -0.001f, 0.663f, -0.035f, 0.985f)
            curveToRelative(-0.174f, 1.652f, -0.329f, 2.884f, -0.329f, 4.614f)
            curveToRelative(0f, 0.903f, -0.01f, 1.826f, 0.099f, 2.709f)
            curveToRelative(0.295f, 2.389f, 0.641f, 4.775f, 1.138f, 7.136f)
            curveToRelative(0.165f, 0.783f, 0.141f, 1.591f, -0.53f, 2.258f)
            curveToRelative(-0.25f, 0.248f, -0.49f, 0.582f, -0.64f, 0.925f)
            curveToRelative(-0.184f, 0.42f, -0.471f, 0.727f, -0.872f, 0.906f)
            curveToRelative(-0.615f, 0.274f, -1.027f, 0.81f, -1.581f, 1.159f)
            curveToRelative(-0.174f, 0.109f, -0.277f, 0.268f, -0.301f, 0.497f)
            curveToRelative(-0.075f, 0.72f, 0.257f, 1.256f, 0.94f, 1.453f)
            curveToRelative(0.168f, 0.049f, 0.351f, 0.057f, 0.511f, 0.123f)
            curveToRelative(0.972f, 0.402f, 1.415f, 0.324f, 2.166f, -0.401f)
            curveToRelative(0.236f, -0.227f, 0.442f, -0.487f, 0.76f, -0.618f)
            curveToRelative(0.264f, -0.109f, 0.409f, -0.3f, 0.542f, -0.578f)
            curveToRelative(0.251f, -0.525f, 0.654f, -0.922f, 1.282f, -1.06f)
            curveToRelative(0.818f, -0.18f, 0.986f, -0.408f, 0.947f, -1.276f)
            curveToRelative(-0.034f, -0.753f, -0.197f, -1.503f, -0.259f, -2.265f)
            curveToRelative(-0.004f, -0.048f, -0.016f, -0.1f, -0.03f, -0.147f)
            curveToRelative(-0.342f, -1.234f, -0.243f, -2.357f, -0.119f, -3.51f)
            curveToRelative(0.195f, -1.815f, 0.557f, -3.421f, 0.754f, -5.251f)
            curveToRelative(0.134f, -1.242f, 0.032f, -2.625f, 0.036f, -3.85f)
            curveToRelative(0.005f, -1.349f, 0.053f, -2.264f, 0.355f, -3.632f)
            curveToRelative(0.372f, -1.685f, 0.591f, -3.004f, 0.762f, -4.734f)
            curveToRelative(0.164f, -1.666f, 0.182f, -3.326f, 0.342f, -4.971f)
            curveToRelative(0.177f, -1.822f, 0.105f, -1.067f, 0.32f, -2.886f)
            curveToRelative(0.022f, -0.183f, 0.086f, -0.343f, 0.162f, -0.5f)
            curveToRelative(0.066f, -0.137f, 0.156f, -0.279f, 0.339f, -0.242f)
            curveToRelative(0.132f, 0.027f, 0.21f, 0.143f, 0.238f, 0.269f)
            curveToRelative(0.064f, 0.294f, 0.12f, 0.591f, 0.17f, 0.888f)
            curveToRelative(0.235f, 1.384f, 0.058f, 0.293f, 0.255f, 1.683f)
            curveToRelative(0.222f, 1.566f, 0.554f, 3.122f, 0.531f, 4.717f)
            curveToRelative(-0.011f, 0.76f, 0.208f, 1.616f, 0.273f, 2.372f)
            curveToRelative(0.101f, 1.174f, 0.245f, 1.904f, 0.449f, 3.053f)
            curveToRelative(0.2f, 1.125f, 0.201f, 2.038f, 0.201f, 3.453f)
            curveToRelative(0f, 1.491f, -0.213f, 2.909f, -0.084f, 4.354f)
            curveToRelative(0.156f, 1.747f, 0.628f, 3.453f, 0.967f, 5.175f)
            curveToRelative(0.199f, 1.011f, 0.213f, 2.082f, -0.015f, 3.092f)
            curveToRelative(-0.241f, 1.065f, -0.285f, 2.139f, -0.365f, 3.212f)
            curveToRelative(-0.049f, 0.651f, 0.18f, 0.93f, 0.828f, 1.055f)
            curveToRelative(0.458f, 0.088f, 0.882f, 0.267f, 1.062f, 0.648f)
            curveToRelative(0.392f, 0.829f, 1.219f, 1.167f, 1.813f, 1.761f)
            curveToRelative(0.649f, 0.648f, 1.187f, 0.65f, 2.027f, 0.161f)
            curveToRelative(0.16f, -0.093f, 0.301f, 0.006f, 0.417f, -0.026f)
            curveToRelative(0.398f, -0.109f, 0.824f, -0.228f, 0.969f, -0.696f)
            curveToRelative(0.157f, -0.509f, 0.021f, -1.044f, -0.326f, -1.308f)
            curveToRelative(-0.42f, -0.32f, -0.819f, -0.68f, -1.276f, -0.937f)
            curveToRelative(-0.69f, -0.389f, -1.158f, -0.957f, -1.527f, -1.627f)
            curveToRelative(-0.1f, -0.182f, -0.219f, -0.342f, -0.368f, -0.478f)
            curveToRelative(-0.344f, -0.315f, -0.444f, -0.705f, -0.451f, -1.16f)
            curveToRelative(-0.019f, -1.271f, 0.356f, -2.483f, 0.515f, -3.726f)
            curveToRelative(0.121f, -0.946f, 0.335f, -1.875f, 0.424f, -2.827f)
            curveToRelative(0.101f, -1.075f, 0.273f, -2.143f, 0.356f, -3.222f)
            curveToRelative(0.098f, -1.285f, 0.187f, -2.562f, -0.036f, -3.842f)
            curveToRelative(-0.207f, -1.191f, -0.411f, -2.379f, -0.348f, -3.603f)
            curveToRelative(0.085f, -1.657f, -0.158f, -3.332f, 0.299f, -4.967f)
            curveToRelative(0.673f, -3.915f, 0.631f, -7.269f, 0.776f, -11.712f)
            curveToRelative(0.077f, -2.367f, -0.186f, -4.448f, -0.309f, -5.405f)
            curveToRelative(-0.381f, -2.955f, -0.526f, -5.486f, -0.526f, -8.078f)
            curveToRelative(0f, -1.206f, 0.149f, -4.936f, 0.293f, -4.936f)
            curveToRelative(0.129f, 0f, 0.604f, 3.347f, 0.81f, 5.019f)
            curveToRelative(0.176f, 1.423f, 0.406f, 2.831f, 0.797f, 4.21f)
            curveToRelative(0.349f, 1.232f, 0.732f, 2.455f, 1.096f, 3.683f)
            curveToRelative(0.113f, 0.383f, 0.324f, 0.772f, 0.219f, 1.169f)
            curveToRelative(-0.185f, 0.698f, -0.264f, 1.438f, -0.731f, 2.044f)
            curveToRelative(-0.132f, 0.172f, -0.234f, 0.409f, -0.25f, 0.623f)
            curveToRelative(-0.083f, 1.105f, -0.134f, 2.212f, -0.194f, 3.319f)
            curveToRelative(-0.01f, 0.187f, 0.027f, 0.377f, 0.215f, 0.447f)
            curveToRelative(0.214f, 0.08f, 0.302f, -0.103f, 0.403f, -0.253f)
            curveToRelative(0.17f, -0.254f, 0.311f, -0.499f, 0.311f, -0.83f)
            curveToRelative(0f, -0.322f, 0.086f, -0.646f, 0.146f, -0.966f)
            curveToRelative(0.024f, -0.127f, 0.099f, -0.242f, 0.234f, -0.27f)
            curveToRelative(0.16f, -0.034f, 0.199f, 0.116f, 0.269f, 0.215f)
            curveToRelative(0.366f, 0.521f, 0.087f, 1.073f, 0.046f, 1.602f)
            curveToRelative(-0.028f, 0.368f, -0.203f, 0.723f, -0.602f, 0.903f)
            curveToRelative(-0.264f, 0.119f, -0.412f, 0.366f, -0.511f, 0.635f)
            curveToRelative(-0.063f, 0.171f, -0.105f, 0.362f, 0.054f, 0.49f)
            curveToRelative(0.136f, 0.109f, 0.282f, -0.003f, 0.423f, -0.059f)
            curveToRelative(1.278f, -0.5f, 2.149f, -1.375f, 2.515f, -2.717f)
            curveTo(36.676f, 46.603f, 36.883f, 46.154f, 36.835f, 45.666f)
            close()
        }

    }.build()
}

/**
 * Contains SVG path data for each zone of the child body front view.
 */
object ChildFrontPaths {

    val pathData: Map<Location, String> = mapOf(
        Location.FRONT_RIGHT_LOWER_THIGH to "M17.357,55.586 c0.269,1.641 0.655,3.29 0.885,3.633 c0.121,0.18 0.779,0.199 1.084,0.198 c1.337,-0.004 1.853,-0.281 1.865,-1.103 c0.011,-0.753 0.093,-1.69 0.181,-2.6 C20.021,55.69 18.682,55.647 17.357,55.586z",
        Location.FRONT_RIGHT_UPPER_THIGH to "M19.935,51.691 c-0.807,-0.058 -2.318,-0.168 -2.75,0.303 c-0.253,0.275 -0.1,1.929 0.173,3.591 c1.325,0.061 2.663,0.104 4.015,0.128 c0.1,-1.033 0.209,-2.031 0.235,-2.687 C21.644,52.079 21.309,51.791 19.935,51.691z",
        Location.FRONT_LEFT_LOWER_THIGH to "M26.808,58.314 c0.012,0.822 0.528,1.099 1.865,1.103 c0.305,0.001 0.963,-0.018 1.084,-0.198 c0.23,-0.343 0.616,-1.993 0.885,-3.633 c-1.325,0.061 -2.663,0.104 -4.015,0.128 C26.716,56.624 26.797,57.561 26.808,58.314z",
        Location.FRONT_LEFT_UPPER_THIGH to "M28.065,51.691 c-1.374,0.099 -1.709,0.388 -1.673,1.335 c0.025,0.656 0.135,1.655 0.235,2.687 c1.352,-0.024 2.691,-0.067 4.015,-0.128 c0.273,-1.662 0.425,-3.315 0.173,-3.591 C30.383,51.524 28.872,51.633 28.065,51.691z",
        Location.FRONT_RIGHT_LOWER_ABDOMEN to "M18.257,43.738 c1.851,0.062 3.771,0.095 5.743,0.095 v-2.177 c-0.879,0 -1.59,-0.709 -1.598,-1.586 c-1.3,-0.017 -2.582,-0.055 -3.842,-0.113 C18.486,41.258 18.385,42.523 18.257,43.738z",
        Location.FRONT_RIGHT_UPPER_ABDOMEN to "M24,38.458 v-2.918 c-1.815,0 -3.585,0.029 -5.299,0.081 c-0.011,1.483 -0.06,2.931 -0.14,4.337 c1.26,0.058 2.542,0.096 3.842,0.113 c0,-0.005 -0.001,-0.009 -0.001,-0.014 C22.401,39.174 23.117,38.458 24,38.458z",
        Location.SIDE_RIGHT_LOWER_ABDOMEN to "M16.345,40.573 c-0.073,1.148 -0.143,2.14 -0.185,3.081 c0.687,0.032 1.388,0.06 2.097,0.084 c0.128,-1.216 0.229,-2.48 0.303,-3.78 c-0.727,-0.034 -1.443,-0.076 -2.154,-0.123 C16.387,40.08 16.36,40.332 16.345,40.573z",
        Location.SIDE_RIGHT_UPPER_ABDOMEN to "M16.785,35.691 c-0.128,1.389 -0.273,2.785 -0.378,4.145 c0.711,0.047 1.428,0.089 2.154,0.123 c0.08,-1.406 0.129,-2.854 0.14,-4.337 C18.054,35.641 17.414,35.664 16.785,35.691z",
        Location.FRONT_LEFT_LOWER_ABDOMEN to "M24,41.657 v2.177 c1.972,0 3.892,-0.033 5.743,-0.095 c-0.128,-1.216 -0.229,-2.48 -0.303,-3.78 c-1.26,0.058 -2.542,0.096 -3.842,0.113 C25.59,40.948 24.879,41.657 24,41.657z",
        Location.FRONT_LEFT_UPPER_ABDOMEN to "M24,35.54 v2.918 c0.883,0 1.599,0.716 1.599,1.599 c0,0.005 -0.001,0.009 -0.001,0.014 c1.3,-0.017 2.582,-0.055 3.842,-0.113 c-0.08,-1.406 -0.129,-2.854 -0.14,-4.337 C27.586,35.568 25.814,35.54 24,35.54z",
        Location.SIDE_LEFT_LOWER_ABDOMEN to "M29.743,43.738 c0.699,-0.023 1.388,-0.051 2.065,-0.082 c-0.07,-1.242 -0.198,-2.255 -0.274,-2.842 c-0.042,-0.329 -0.079,-0.647 -0.115,-0.966 c-0.654,0.042 -1.313,0.08 -1.979,0.111 C29.514,41.258 29.615,42.523 29.743,43.738z",
        Location.SIDE_LEFT_UPPER_ABDOMEN to "M29.299,35.621 c0.011,1.483 0.06,2.931 0.14,4.337 c0.667,-0.031 1.326,-0.069 1.979,-0.111 c-0.168,-1.46 -0.28,-2.83 -0.343,-4.162 C30.492,35.66 29.899,35.639 29.299,35.621z"
    )
    val zones: List<Pair<Location, Path>> by lazy {
        pathData.map { (location, svgData) ->
            location to PathParser().parsePathString(svgData).toPath()
        }
    }
}
