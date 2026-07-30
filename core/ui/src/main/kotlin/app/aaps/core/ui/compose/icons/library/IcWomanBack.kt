package app.aaps.core.ui.compose.icons.library

import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import app.aaps.core.data.model.TE.Location

/**
 * Icon for Woman Back View.
 * Represents human female body back view for site rotation.
 *
 * Bounding box: x: 8.0-40.0, y: 8.0-108.0 (viewport: 48x128, ~78% height)
 *
 * @see WomanBackPreview
 */
val WomanBack: ImageVector by lazy {
    ImageVector.Builder(
        name = "WomanBack",
        defaultWidth = 48.dp,
        defaultHeight = 128.dp,
        viewportWidth = 48f,
        viewportHeight = 128f
    ).apply {
        // Main body (background)
        path(
            name = "background",
            fill = SolidColor(Color(0xFFEDC3AD)),
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 0.5669f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 10f
        ) {
            moveTo(42.13f, 64.399f)
            curveToRelative(-0.959f, -2.587f, -1.369f, -5.263f, -1.454f, -8.015f)
            curveToRelative(-0.104f, -3.403f, -0.421f, -6.795f, -0.485f, -10.204f)
            curveToRelative(-0.025f, -1.354f, -0.389f, -2.731f, -0.89f, -4.042f)
            curveToRelative(-0.742f, -1.941f, -0.889f, -3.984f, -0.855f, -6.043f)
            curveToRelative(-0.097f, -3.294f, 0.016f, -6.593f, -0.227f, -9.884f)
            curveToRelative(-0.136f, -1.842f, -1.362f, -4.914f, -4.076f, -5.624f)
            curveToRelative(-1.69f, -0.442f, -3.426f, -0.772f, -5f, -1.59f)
            curveToRelative(-0.163f, -0.085f, -0.325f, -0.17f, -0.489f, -0.253f)
            curveToRelative(-0.4f, -0.204f, -0.906f, -0.477f, -0.875f, -0.894f)
            curveToRelative(0.032f, -0.421f, 0.653f, -0.335f, 1.004f, -0.436f)
            curveToRelative(2.605f, -0.755f, 4.41f, -3.432f, 3.861f, -6.287f)
            curveToRelative(-0.444f, -2.312f, -1.038f, -4.538f, -2.098f, -6.643f)
            curveToRelative(-0.614f, -1.22f, -1.583f, -2.084f, -2.659f, -2.861f)
            curveToRelative(-0.059f, -0.042f, -0.129f, -0.1f, -0.193f, -0.098f)
            curveToRelative(-2.101f, 0.052f, -4.149f, -1.001f, -6.286f, -0.274f)
            curveToRelative(-2.2f, 0.749f, -3.698f, 2.074f, -4.15f, 4.479f)
            curveToRelative(-0.363f, 1.926f, -0.469f, 3.854f, -0.386f, 5.795f)
            curveToRelative(0.063f, 1.48f, -0.427f, 2.601f, -1.829f, 3.174f)
            curveToRelative(-0.627f, 0.256f, -0.278f, 0.469f, -0.047f, 0.683f)
            curveToRelative(1.179f, 1.096f, 2.595f, 1.756f, 4.128f, 2.189f)
            curveToRelative(0.14f, 0.039f, 0.3f, 0.018f, 0.432f, 0.07f)
            curveToRelative(0.197f, 0.078f, 0.466f, 0.116f, 0.521f, 0.352f)
            curveToRelative(0.071f, 0.306f, -0.208f, 0.465f, -0.409f, 0.592f)
            curveToRelative(-0.339f, 0.214f, -0.711f, 0.384f, -1.083f, 0.537f)
            curveToRelative(-1.727f, 0.709f, -3.539f, 1.141f, -5.339f, 1.608f)
            curveToRelative(-1.762f, 0.457f, -2.61f, 1.563f, -3.111f, 3.287f)
            curveToRelative(-0.08f, 0.274f, -0.199f, 0.839f, -0.267f, 1.159f)
            curveToRelative(-0.273f, 1.703f, -0.302f, 3.414f, -0.232f, 5.135f)
            curveToRelative(0.04f, 0.984f, 0.037f, 1.972f, -0.104f, 2.952f)
            curveToRelative(0f, 0.604f, -0.218f, 4.189f, -0.172f, 4.877f)
            curveToRelative(0.089f, 1.352f, -0.299f, 2.582f, -0.699f, 3.844f)
            curveToRelative(-0.46f, 1.453f, -0.796f, 2.941f, -0.887f, 4.487f)
            curveToRelative(-0.129f, 2.196f, -0.128f, 4.397f, -0.252f, 6.589f)
            curveToRelative(-0.116f, 2.04f, -0.141f, 4.1f, -0.437f, 6.118f)
            curveToRelative(-0.214f, 1.46f, -0.399f, 2.962f, -1.043f, 4.34f)
            curveToRelative(-0.541f, 1.156f, -0.583f, 2.332f, -0.294f, 3.599f)
            curveToRelative(0.32f, 1.398f, 1.192f, 2.21f, 2.351f, 2.857f)
            curveToRelative(0.325f, 0.181f, 0.597f, 0.182f, 0.925f, 0.002f)
            curveToRelative(0.579f, -0.318f, 0.606f, -0.451f, 0.095f, -0.93f)
            curveToRelative(-0.106f, -0.099f, -0.189f, -0.239f, -0.312f, -0.302f)
            curveToRelative(-1.382f, -0.698f, -1.008f, -1.958f, -1.001f, -3.099f)
            curveToRelative(0.001f, -0.17f, 0.114f, -0.282f, 0.275f, -0.267f)
            curveToRelative(0.092f, 0.009f, 0.193f, 0.104f, 0.256f, 0.187f)
            curveToRelative(0.143f, 0.188f, 0.105f, 0.419f, 0.115f, 0.637f)
            curveToRelative(0.013f, 0.293f, -0.023f, 0.599f, 0.048f, 0.877f)
            curveToRelative(0.067f, 0.264f, 0.138f, 0.63f, 0.52f, 0.611f)
            curveToRelative(0.367f, -0.018f, 0.381f, -0.395f, 0.509f, -0.644f)
            curveToRelative(0.016f, -0.03f, 0.014f, -0.073f, 0.012f, -0.109f)
            curveToRelative(-0.104f, -1.728f, 0.647f, -3.455f, -0.144f, -5.205f)
            curveToRelative(-0.346f, -0.766f, 0.071f, -1.647f, 0.299f, -2.461f)
            curveToRelative(1.072f, -3.826f, 2.218f, -7.636f, 3.077f, -11.515f)
            curveToRelative(0.456f, -2.057f, 0.695f, -4.164f, 1.018f, -6.25f)
            curveToRelative(0.417f, -2.688f, 0.766f, -8.242f, 0.946f, -8.242f)
            curveToRelative(0.515f, 0f, 0.672f, 1.512f, 0.792f, 2.324f)
            curveToRelative(0.435f, 2.96f, 1.07f, 8.067f, -0.597f, 11.768f)
            curveToRelative(-0.675f, 1.498f, -1.305f, 3.445f, -1.888f, 5.581f)
            curveToRelative(-1.019f, 3.739f, -1.507f, 7.836f, -1.507f, 11.694f)
            curveToRelative(0f, 3.088f, 0.782f, 8.205f, 2.223f, 14.073f)
            curveToRelative(0.165f, 0.673f, 0.308f, 1.351f, 0.462f, 2.027f)
            curveToRelative(0.287f, 1.377f, 0.194f, 2.773f, 0.179f, 4.162f)
            curveToRelative(-0.007f, 0.669f, 0.138f, 1.306f, 0.233f, 1.956f)
            curveToRelative(0.176f, 1.204f, -0.339f, 2.304f, -0.577f, 3.441f)
            curveToRelative(-0.181f, 0.865f, -0.33f, 1.718f, -0.316f, 2.607f)
            curveToRelative(0.035f, 2.31f, -0.077f, 4.625f, 0.245f, 6.925f)
            curveToRelative(0.399f, 2.853f, 0.553f, 5.735f, 1.065f, 8.574f)
            curveToRelative(0.139f, 0.77f, 0.218f, 1.54f, 0.235f, 2.311f)
            horizontalLineToRelative(0f)
            curveToRelative(0f, 0.645f, 0.039f, 1.293f, 0.016f, 1.943f)
            curveToRelative(-0.025f, 0.681f, -0.218f, 1.309f, -0.772f, 1.619f)
            curveToRelative(-1.037f, 0.582f, -1.942f, 1.417f, -3.183f, 1.645f)
            curveToRelative(-0.443f, 0.082f, -1.154f, 0.193f, -1.095f, 0.852f)
            curveToRelative(0.061f, 0.677f, 0.813f, 0.576f, 1.231f, 0.595f)
            curveToRelative(2.087f, 0.093f, 4.166f, 0.389f, 6.263f, 0.281f)
            curveToRelative(1.078f, -0.055f, 1.466f, -0.464f, 1.523f, -1.565f)
            curveToRelative(0.04f, -0.781f, -0.276f, -1.505f, -0.242f, -2.29f)
            curveToRelative(0.045f, -1.028f, -0.067f, -2.794f, -0.12f, -3.081f)
            curveToRelative(-0.004f, -0.019f, -0.007f, -0.038f, -0.01f, -0.057f)
            curveToRelative(-0.197f, -0.992f, 0.061f, -2.114f, 0.283f, -3.142f)
            curveToRelative(0.531f, -2.464f, 0.766f, -4.972f, 1.227f, -7.443f)
            curveToRelative(0.255f, -1.365f, 0.342f, -2.749f, 0.598f, -4.111f)
            curveToRelative(0.18f, -0.958f, -0.087f, -1.911f, -0.257f, -2.831f)
            curveToRelative(-0.255f, -1.382f, -0.138f, -2.677f, 0.265f, -3.998f)
            curveToRelative(0.534f, -1.75f, 0.97f, -3.521f, 0.922f, -5.379f)
            curveToRelative(-0.013f, -0.486f, -0.116f, -0.94f, -0.218f, -1.403f)
            curveToRelative(-0.377f, -1.715f, 0.028f, -3.4f, 0.152f, -5.103f)
            curveToRelative(0.151f, -2.08f, 0.557f, -4.122f, 0.761f, -6.194f)
            curveToRelative(0.222f, -2.253f, 0.464f, -4.503f, 0.607f, -6.762f)
            curveToRelative(0.042f, -0.658f, 0.495f, -0.494f, 0.873f, -0.524f)
            curveToRelative(0.431f, -0.034f, 0.686f, 0.08f, 0.686f, 0.596f)
            curveToRelative(0f, 2.774f, 0.545f, 6.074f, 0.764f, 8.507f)
            curveToRelative(0.145f, 1.603f, 0.785f, 6.152f, 0.803f, 6.854f)
            curveToRelative(0.026f, 1.033f, -0.114f, 2.042f, -0.233f, 3.061f)
            curveToRelative(-0.283f, 2.42f, 0.327f, 4.722f, 0.971f, 7.02f)
            curveToRelative(0.296f, 1.058f, 0.438f, 2.108f, 0.145f, 3.234f)
            curveToRelative(-0.438f, 1.684f, -0.331f, 3.405f, -0.026f, 5.119f)
            curveToRelative(0.325f, 1.829f, 0.591f, 3.668f, 0.885f, 5.503f)
            curveToRelative(0.358f, 2.231f, 0.755f, 4.456f, 1.059f, 6.694f)
            curveToRelative(0.016f, 0.12f, 0.02f, 0.24f, 0.015f, 0.359f)
            curveToRelative(-0.006f, 0.055f, -0.011f, 0.11f, -0.018f, 0.165f)
            curveToRelative(-0.246f, 1.799f, -0.266f, 3.591f, -0.316f, 5.386f)
            curveToRelative(-0.032f, 1.139f, 0.217f, 1.327f, 1.345f, 1.386f)
            curveToRelative(2.06f, 0.107f, 4.101f, -0.206f, 6.153f, -0.253f)
            curveToRelative(0.285f, -0.006f, 0.565f, -0.148f, 0.853f, -0.191f)
            curveToRelative(0.291f, -0.043f, 0.483f, -0.138f, 0.48f, -0.473f)
            curveToRelative(-0.003f, -0.294f, -0.135f, -0.492f, -0.398f, -0.598f)
            curveToRelative(-0.204f, -0.082f, -0.412f, -0.159f, -0.626f, -0.209f)
            curveToRelative(-1.168f, -0.272f, -2.116f, -0.971f, -3.075f, -1.626f)
            curveToRelative(-0.409f, -0.279f, -0.686f, -0.684f, -0.815f, -1.247f)
            curveToRelative(-0.182f, -0.794f, -0.134f, -1.656f, -0.123f, -2.339f)
            curveToRelative(0.004f, -0.102f, 0.01f, -0.204f, 0.018f, -0.306f)
            curveToRelative(0.123f, -1.497f, 0.39f, -2.974f, 0.59f, -4.45f)
            curveToRelative(0.298f, -2.205f, 0.526f, -4.423f, 0.739f, -6.629f)
            curveToRelative(0.308f, -3.192f, 0.68f, -6.426f, -0.195f, -9.613f)
            curveToRelative(-0.193f, -0.702f, -0.326f, -1.421f, -0.508f, -2.126f)
            curveToRelative(-0.066f, -0.257f, -0.102f, -0.505f, -0.02f, -0.757f)
            curveToRelative(0.636f, -1.939f, 0.298f, -3.932f, 0.328f, -5.904f)
            curveToRelative(0.653f, -1.787f, 0.917f, -3.67f, 1.327f, -5.514f)
            curveToRelative(0.883f, -3.979f, 1.475f, -9.335f, 1.331f, -13.876f)
            curveToRelative(-0.087f, -2.764f, -0.478f, -5.52f, -1.219f, -8.202f)
            curveToRelative(-0.1f, -0.361f, -1.346f, -4.749f, -2.048f, -6.048f)
            curveToRelative(-1.943f, -3.598f, -0.922f, -10.472f, -0.465f, -12.43f)
            curveToRelative(0.058f, -0.249f, 0.088f, -0.476f, 0.183f, -0.723f)
            curveToRelative(0.083f, -0.215f, 0.291f, -0.742f, 0.5f, -0.715f)
            curveToRelative(0.136f, 0.017f, 0.191f, 0.631f, 0.211f, 0.85f)
            curveToRelative(0.201f, 2.216f, 0.591f, 4.411f, 0.689f, 6.641f)
            curveToRelative(0.12f, 2.739f, 0.554f, 5.449f, 1.356f, 8.065f)
            curveToRelative(1.07f, 3.489f, 1.926f, 7.033f, 2.915f, 10.542f)
            curveToRelative(0.295f, 1.045f, 0.395f, 2.054f, -0.094f, 3.074f)
            curveToRelative(-0.314f, 0.654f, -0.328f, 1.341f, -0.038f, 2.026f)
            curveToRelative(0.233f, 0.551f, 0.267f, 1.134f, 0.245f, 1.726f)
            curveToRelative(-0.012f, 0.328f, 0.002f, 0.663f, 0.059f, 0.985f)
            curveToRelative(0.04f, 0.229f, 0.229f, 0.376f, 0.48f, 0.375f)
            curveToRelative(0.3f, -0.002f, 0.336f, -0.226f, 0.353f, -0.456f)
            curveToRelative(0.037f, -0.509f, -0.041f, -1.029f, 0.186f, -1.519f)
            curveToRelative(0.076f, -0.163f, 0.081f, -0.387f, 0.326f, -0.352f)
            curveToRelative(0.198f, 0.028f, 0.289f, 0.208f, 0.341f, 0.389f)
            curveToRelative(0.325f, 1.12f, -0.104f, 2.262f, -1.08f, 2.992f)
            curveToRelative(-0.326f, 0.244f, -0.934f, 0.506f, -0.559f, 0.992f)
            curveToRelative(0.277f, 0.359f, 0.818f, 0.51f, 1.386f, 0.224f)
            curveTo(42.028f, 68.931f, 42.953f, 66.618f, 42.13f, 64.399f)
            close()
        }

    }.build()
}

/**
 * Contains SVG path data for each zone of the female body back view.
 */
object WomanBackPaths {

    val pathData: Map<Location, String> = mapOf(
        Location.BACK_LEFT_UPPER_ARM to "M13.627,34.526 c-0.087,1.846 -0.497,2.63 -0.975,2.615 c-0.563,-0.017 -1.055,-0.117 -1.554,-0.16 c-0.727,-0.063 -1.246,-0.364 -1.09,-2.108 c0.26,-2.92 0.131,-6.123 0.251,-7.295 c0.103,-1.014 0.361,-1.259 1.117,-1.259 c0.582,0 0.844,0.043 1.741,0.043 c0.776,0 1.113,1.052 0.989,2.265 C13.975,29.901 13.722,32.5 13.627,34.526",
        Location.BACK_RIGHT_UPPER_ARM to "M34.373,34.526 c0.087,1.846 0.497,2.63 0.975,2.615 c0.563,-0.017 1.055,-0.117 1.554,-0.16 c0.727,-0.063 1.246,-0.364 1.09,-2.108 c-0.26,-2.92 -0.131,-6.123 -0.251,-7.295 c-0.103,-1.014 -0.361,-1.259 -1.117,-1.259 c-0.582,0 -0.844,0.043 -1.741,0.043 c-0.776,0 -1.113,1.052 -0.989,2.265 C34.025,29.901 34.278,32.5 34.373,34.526",
        Location.SIDE_LEFT_LOWER_THIGH to "M14.795,77.708 c-0.015,-1.737 -0.289,-3.474 -0.289,-5.211 c-0.678,0.036 -1.37,0.056 -2.069,0.067 c0.346,1.9 0.788,3.966 1.321,6.136 c0.005,0.021 0.009,0.042 0.015,0.062 C14.218,78.594 14.8,78.314 14.795,77.708",
        Location.SIDE_LEFT_UPPER_THIGH to "M14.522,67.359 c0.004,-0.609 -0.331,-0.838 -0.959,-0.954 c-0.653,-0.12 -1.309,-0.148 -1.968,-0.152 c0.116,1.744 0.399,3.889 0.841,6.31 c0.699,-0.011 1.391,-0.031 2.069,-0.067 C14.505,70.784 14.511,69.072 14.522,67.359",
        Location.SIDE_RIGHT_LOWER_THIGH to "M33.084,77.708 c-0.051,0.679 0.579,0.95 1.113,1.112 c0.267,-1.133 0.464,-2.282 0.716,-3.417 c0.196,-0.885 0.377,-1.841 0.54,-2.838 c-0.731,-0.01 -1.454,-0.031 -2.162,-0.069 C33.292,74.234 33.206,76.078 33.084,77.708",
        Location.SIDE_RIGHT_UPPER_THIGH to "M33.275,67.359 c0.011,1.712 0.017,3.425 0.017,5.137 c0.708,0.038 1.431,0.058 2.162,0.069 c0.325,-1.993 0.573,-4.159 0.705,-6.312 c-0.644,0.006 -1.286,0.034 -1.925,0.152 C33.607,66.521 33.271,66.75 33.275,67.359",
        Location.BACK_LEFT_BUTTOCK to "M12.842,57.285 c0.129,-1.13 0.567,-3.081 1.318,-4.021 c1.152,-1.441 1.591,-1.672 5.308,-1.04 c3.716,0.633 3.465,3.887 2.844,7.627 c-0.469,2.826 -6.135,3.293 -8.548,1.317 C12.796,60.376 12.695,58.651 12.842,57.285",
        Location.BACK_RIGHT_BUTTOCK to "M35.158,57.285 c-0.129,-1.13 -0.567,-3.081 -1.318,-4.021 c-1.152,-1.441 -1.591,-1.672 -5.308,-1.04 c-3.716,0.633 -3.465,3.887 -2.844,7.627 c0.469,2.826 6.135,3.293 8.548,1.317 C35.204,60.376 35.305,58.651 35.158,57.285",
        Location.BACK_LEFT_LOWER_BACK to "M15.715,44.755 c-0.17,0.908-0.42,1.786-0.785,2.597c-0.311,0.69-0.611,1.483-0.902,2.337c2.196,0.759,7.003,1.068,8.008,0.358 c0.865-0.611,0.544-2.742-0.084-3.523C21.027,45.372,17.909,44.724,15.715,44.755z",
        Location.BACK_RIGHT_LOWER_BACK to "M32.978,47.277 c-0.393-0.728-0.664-1.591-0.843-2.519c-2.22,0.019-5.199,0.658-6.108,1.766c-0.641,0.782-0.97,2.912-0.086,3.523 c1.001,0.692,5.679,0.415,7.993-0.301C33.599,48.736,33.248,47.777,32.978,47.277z"
    )
    val zones: List<Pair<Location, Path>> by lazy {
        pathData.map { (location, svgData) ->
            location to PathParser.createPathFromPathData(svgData)
        }
    }
}
