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
 * Icon for Body Front View.
 * Represents human male body front view for site rotation.
 *
 * Bounding box: x: 3.0-46.0, y: 1.0-118.0 (viewport: 48x128, ~91% height)
 *
 * @see BodyFrontPreview
 */
val ManFront: ImageVector by lazy {
    ImageVector.Builder(
        name = "ManFront",
        defaultWidth = 48.dp,
        defaultHeight = 128.dp,
        viewportWidth = 48f,
        viewportHeight = 128f
    ).apply {
        // Main body (background)
        path(
            name = "background",
            fill = SolidColor(Color(0xFFEFC3AD)),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 0.5669f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 10f
        ) {
            moveTo(42.956f, 67.199f)
            curveToRelative(-0.18f, -1.843f, -0.697f, -3.67f, -0.225f, -5.542f)
            curveToRelative(0.042f, -0.168f, -0.007f, -0.366f, -0.042f, -0.545f)
            curveToRelative(-0.239f, -1.223f, -0.279f, -2.469f, -0.338f, -3.703f)
            curveToRelative(-0.087f, -1.835f, -0.047f, -3.678f, -0.025f, -5.517f)
            curveToRelative(0.013f, -1.07f, -0.069f, -2.123f, -0.384f, -3.145f)
            curveToRelative(-0.862f, -2.797f, -1.002f, -6.789f, -1.002f, -9.004f)
            curveToRelative(-0.338f, -4.834f, 0.269f, -10.396f, -0.783f, -13.469f)
            curveToRelative(-0.534f, -1.559f, -1.406f, -2.686f, -2.818f, -3.448f)
            curveToRelative(-1.104f, -0.596f, -2.35f, -0.768f, -3.526f, -1.151f)
            curveToRelative(-1.559f, -0.508f, -3.161f, -0.89f, -4.706f, -1.439f)
            curveToRelative(-2.217f, -0.787f, -2.432f, -2.621f, -1.402f, -4.271f)
            curveToRelative(0.625f, -1f, 1.633f, -2.772f, 1.633f, -4.245f)
            curveToRelative(0.8f, -0.157f, 1.159f, -4.066f, 0.371f, -3.898f)
            curveToRelative(-0.102f, -1.305f, -0.182f, -2.979f, -0.874f, -4.162f)
            curveTo(27.633f, 1.6f, 24.604f, 0.422f, 21.919f, 1.27f)
            curveToRelative(-2.289f, 0.724f, -3.565f, 2.234f, -3.934f, 4.618f)
            curveToRelative(-0.17f, 1.1f, -0.063f, 0.772f, -0.238f, 1.932f)
            curveToRelative(-0.786f, -0.069f, -0.626f, 3.451f, 0.124f, 3.899f)
            curveToRelative(0.34f, 1.442f, 1.355f, 3.299f, 2.088f, 4.258f)
            curveToRelative(1.102f, 1.442f, 0.296f, 3.836f, -1.435f, 4.359f)
            curveToRelative(-2.2f, 0.664f, -4.392f, 1.352f, -6.599f, 1.991f)
            curveToRelative(-2.219f, 0.642f, -3.575f, 2.106f, -4.276f, 4.263f)
            curveToRelative(-0.306f, 0.942f, -0.486f, 1.875f, -0.577f, 2.884f)
            curveToRelative(-0.1f, 1.1f, -0.003f, 2.289f, 0.017f, 3.386f)
            curveToRelative(0.046f, 2.525f, -0.111f, 8.129f, -0.098f, 8.458f)
            curveToRelative(0.098f, 2.434f, -0.239f, 4.81f, -0.897f, 7.152f)
            curveToRelative(-0.196f, 0.699f, -0.388f, 1.423f, -0.416f, 2.142f)
            curveToRelative(-0.108f, 2.75f, 0.032f, 5.504f, -0.207f, 8.257f)
            curveToRelative(-0.141f, 1.629f, -0.032f, 3.28f, -0.041f, 4.92f)
            curveToRelative(-0.002f, 0.438f, -0.077f, 0.87f, -0.13f, 1.303f)
            curveToRelative(-0.145f, 1.181f, -0.467f, 2.346f, -0.085f, 3.578f)
            curveToRelative(0.627f, 2.021f, 1.804f, 3.496f, 3.754f, 4.336f)
            curveToRelative(0.357f, 0.154f, 0.736f, 0.245f, 0.977f, -0.158f)
            curveToRelative(0.209f, -0.35f, -0.164f, -0.512f, -0.333f, -0.725f)
            curveToRelative(-0.137f, -0.172f, -0.29f, -0.343f, -0.47f, -0.464f)
            curveToRelative(-1.299f, -0.881f, -1.099f, -2.215f, -1.056f, -3.493f)
            curveToRelative(0.01f, -0.309f, 0.214f, -0.625f, 0.514f, -0.639f)
            curveToRelative(0.402f, -0.019f, 0.363f, 0.37f, 0.375f, 0.656f)
            curveToRelative(0.01f, 0.22f, -0.033f, 0.45f, 0.014f, 0.661f)
            curveToRelative(0.142f, 0.628f, 0.105f, 1.329f, 0.68f, 1.798f)
            curveToRelative(0.174f, 0.142f, 0.341f, 0.299f, 0.583f, 0.238f)
            curveToRelative(0.286f, -0.072f, 0.256f, -0.322f, 0.255f, -0.538f)
            curveToRelative(-0.002f, -1.288f, -0.006f, -2.576f, -0.02f, -3.864f)
            curveToRelative(-0.002f, -0.178f, -0.03f, -0.37f, -0.102f, -0.529f)
            curveToRelative(-0.345f, -0.757f, -0.715f, -1.502f, -1.065f, -2.257f)
            curveToRelative(-0.467f, -1.008f, -0.519f, -2.081f, -0.241f, -3.12f)
            curveToRelative(0.869f, -3.242f, 1.829f, -6.463f, 2.42f, -9.771f)
            curveToRelative(0.359f, -2.009f, 0.774f, -4.02f, 0.769f, -6.075f)
            curveToRelative(-0.002f, -0.921f, 0.213f, -1.62f, 0.287f, -2.684f)
            curveToRelative(0.008f, -0.116f, 0.019f, -0.354f, 0.138f, -0.473f)
            curveToRelative(0.156f, 0.059f, 0.214f, 0.329f, 0.236f, 0.49f)
            curveToRelative(0.111f, 0.832f, 0.891f, 4.91f, 0.936f, 6.157f)
            curveToRelative(-0.015f, 1.063f, 0.028f, 2.132f, -0.057f, 3.19f)
            curveToRelative(-0.228f, 2.822f, -1.133f, 6.657f, -1.919f, 9.296f)
            curveToRelative(-0.786f, 2.639f, -0.835f, 4.139f, -0.915f, 6.243f)
            curveToRelative(-0.152f, 4.001f, 0.391f, 11.566f, 1.768f, 18.086f)
            curveToRelative(0.318f, 1.507f, 0.509f, 3.02f, 0.437f, 4.57f)
            curveToRelative(-0.059f, 1.249f, -0.007f, 2.502f, -0.012f, 3.754f)
            curveToRelative(-0.002f, 0.478f, 0.041f, 0.969f, -0.051f, 1.432f)
            curveToRelative(-0.439f, 2.213f, -0.877f, 4.404f, -0.662f, 6.703f)
            curveToRelative(0.122f, 1.306f, 0.137f, 2.652f, 0.328f, 3.936f)
            curveToRelative(0.515f, 3.459f, 0.931f, 6.937f, 1.653f, 10.367f)
            curveToRelative(0.239f, 1.137f, 0.205f, 2.311f, -0.77f, 3.281f)
            curveToRelative(-0.362f, 0.36f, -0.711f, 0.845f, -0.93f, 1.344f)
            curveToRelative(-0.268f, 0.61f, -0.684f, 1.056f, -1.267f, 1.316f)
            curveToRelative(-0.893f, 0.398f, -1.491f, 1.177f, -2.297f, 1.684f)
            curveToRelative(-0.252f, 0.159f, -0.403f, 0.389f, -0.438f, 0.722f)
            curveToRelative(-0.109f, 1.046f, 0.373f, 1.824f, 1.366f, 2.11f)
            curveToRelative(0.244f, 0.071f, 0.51f, 0.083f, 0.742f, 0.179f)
            curveToRelative(1.412f, 0.584f, 2.055f, 0.471f, 3.146f, -0.582f)
            curveToRelative(0.342f, -0.33f, 0.642f, -0.707f, 1.104f, -0.898f)
            curveToRelative(0.383f, -0.158f, 0.594f, -0.436f, 0.787f, -0.84f)
            curveToRelative(0.364f, -0.762f, 0.95f, -1.339f, 1.862f, -1.54f)
            curveToRelative(1.188f, -0.261f, 1.432f, -0.593f, 1.376f, -1.854f)
            curveToRelative(-0.049f, -1.094f, -0.329f, -2.173f, -0.241f, -3.28f)
            curveToRelative(0.006f, -0.07f, -0.014f, -0.149f, -0.043f, -0.213f)
            curveToRelative(-0.751f, -1.661f, -0.488f, -3.434f, -0.308f, -5.109f)
            curveToRelative(0.284f, -2.637f, 1.048f, -5.208f, 1.334f, -7.867f)
            curveToRelative(0.194f, -1.804f, 0.275f, -3.613f, 0.072f, -5.38f)
            curveToRelative(-0.211f, -1.836f, -0.19f, -3.574f, 0.516f, -5.276f)
            curveToRelative(0.916f, -2.209f, 1.281f, -4.49f, 1.107f, -6.878f)
            curveToRelative(-0.177f, -2.426f, 0.143f, -4.831f, 0.496f, -7.222f)
            curveToRelative(0.389f, -2.63f, 0.959f, -5.233f, 1.271f, -7.876f)
            curveToRelative(0.032f, -0.266f, 0.124f, -0.498f, 0.235f, -0.727f)
            curveToRelative(0.096f, -0.199f, 0.227f, -0.405f, 0.493f, -0.352f)
            curveToRelative(0.192f, 0.039f, 0.305f, 0.208f, 0.345f, 0.391f)
            curveToRelative(0.094f, 0.428f, 0.174f, 0.859f, 0.247f, 1.29f)
            curveToRelative(0.342f, 2.011f, 0.72f, 4.017f, 1.007f, 6.036f)
            curveToRelative(0.323f, 2.275f, 0.805f, 4.535f, 0.771f, 6.853f)
            curveToRelative(-0.016f, 1.104f, 0.04f, 2.211f, -0.019f, 3.312f)
            curveToRelative(-0.089f, 1.655f, 0.357f, 3.184f, 0.943f, 4.695f)
            curveToRelative(0.615f, 1.586f, 1.138f, 3.233f, 0.777f, 4.928f)
            curveToRelative(-0.451f, 2.118f, -0.389f, 4.227f, -0.122f, 6.325f)
            curveToRelative(0.322f, 2.528f, 0.912f, 5.016f, 1.405f, 7.518f)
            curveToRelative(0.289f, 1.469f, 0.31f, 3.025f, -0.022f, 4.491f)
            curveToRelative(-0.35f, 1.548f, -0.414f, 3.107f, -0.531f, 4.666f)
            curveToRelative(-0.071f, 0.946f, 0.262f, 1.352f, 1.202f, 1.533f)
            curveToRelative(0.665f, 0.128f, 1.281f, 0.388f, 1.542f, 0.941f)
            curveToRelative(0.569f, 1.204f, 1.77f, 1.696f, 2.634f, 2.558f)
            curveToRelative(0.942f, 0.941f, 1.724f, 0.944f, 2.945f, 0.234f)
            curveToRelative(0.232f, -0.135f, 0.438f, 0.008f, 0.605f, -0.038f)
            curveToRelative(0.578f, -0.158f, 1.197f, -0.331f, 1.407f, -1.011f)
            curveToRelative(0.229f, -0.739f, 0.03f, -1.517f, -0.473f, -1.9f)
            curveToRelative(-0.611f, -0.465f, -1.19f, -0.988f, -1.854f, -1.362f)
            curveToRelative(-1.002f, -0.565f, -1.682f, -1.39f, -2.218f, -2.364f)
            curveToRelative(-0.145f, -0.264f, -0.318f, -0.497f, -0.534f, -0.695f)
            curveToRelative(-0.5f, -0.457f, -0.645f, -1.025f, -0.655f, -1.686f)
            curveToRelative(-0.028f, -1.846f, 0.518f, -3.607f, 0.749f, -5.414f)
            curveToRelative(0.176f, -1.374f, 0.486f, -2.724f, 0.617f, -4.107f)
            curveToRelative(0.147f, -1.562f, 0.397f, -3.113f, 0.517f, -4.682f)
            curveToRelative(0.142f, -1.867f, 0.271f, -3.722f, -0.052f, -5.582f)
            curveToRelative(-0.3f, -1.73f, -0.597f, -3.455f, -0.506f, -5.234f)
            curveToRelative(0.124f, -2.407f, -0.23f, -4.841f, 0.434f, -7.216f)
            curveTo(36.608f, 79.742f, 36.9f, 74.221f, 36.84f, 68f)
            curveToRelative(-0.022f, -2.246f, -0.329f, -5.463f, -0.731f, -6.805f)
            curveToRelative(-1f, -3.336f, -2.187f, -9.017f, -2.187f, -12.783f)
            curveToRelative(0f, -1.753f, 0.787f, -7.17f, 0.996f, -7.17f)
            curveToRelative(0.188f, 0f, 0.877f, 4.862f, 1.177f, 7.291f)
            curveToRelative(0.255f, 2.068f, 0.59f, 4.114f, 1.157f, 6.116f)
            curveToRelative(0.507f, 1.79f, 1.063f, 3.566f, 1.592f, 5.35f)
            curveToRelative(0.165f, 0.556f, 0.471f, 1.122f, 0.319f, 1.699f)
            curveToRelative(-0.268f, 1.014f, -0.384f, 2.089f, -1.061f, 2.97f)
            curveToRelative(-0.192f, 0.25f, -0.339f, 0.594f, -0.363f, 0.905f)
            curveToRelative(-0.12f, 1.605f, -0.195f, 3.214f, -0.282f, 4.821f)
            curveToRelative(-0.015f, 0.272f, 0.04f, 0.547f, 0.313f, 0.65f)
            curveToRelative(0.31f, 0.117f, 0.439f, -0.149f, 0.585f, -0.367f)
            curveToRelative(0.247f, -0.368f, 0.452f, -0.725f, 0.452f, -1.206f)
            curveToRelative(0f, -0.468f, 0.125f, -0.938f, 0.212f, -1.404f)
            curveToRelative(0.034f, -0.184f, 0.144f, -0.352f, 0.339f, -0.393f)
            curveToRelative(0.233f, -0.049f, 0.289f, 0.168f, 0.39f, 0.312f)
            curveToRelative(0.531f, 0.757f, 0.126f, 1.559f, 0.067f, 2.328f)
            curveToRelative(-0.041f, 0.534f, -0.296f, 1.05f, -0.875f, 1.311f)
            curveToRelative(-0.384f, 0.174f, -0.599f, 0.532f, -0.742f, 0.922f)
            curveToRelative(-0.091f, 0.249f, -0.153f, 0.526f, 0.078f, 0.712f)
            curveToRelative(0.197f, 0.159f, 0.409f, -0.005f, 0.614f, -0.085f)
            curveToRelative(1.857f, -0.727f, 3.121f, -1.998f, 3.654f, -3.948f)
            curveTo(42.725f, 68.56f, 43.025f, 67.907f, 42.956f, 67.199f)
            close()
        }
    }.build()
}

/**
 * Contains SVG path data for each zone of the male body front view.
 */
object ManFrontPaths {

    val pathData: Map<Location, String> = mapOf(
        Location.FRONT_RIGHT_LOWER_THIGH to "M14.105,84.729 c0.207,0.31,1.338,0.342,1.863,0.34 c2.298,-0.007,3.185,-0.484,3.206,-1.896 c0.019,-1.294,0.159,-2.904,0.311,-4.468 c-2.323,-0.042,-4.624,-0.116,-6.9,-0.22 C13.047,81.304,13.711,84.139,14.105,84.729z",
        Location.FRONT_RIGHT_UPPER_THIGH to "M17.014,71.793 c-1.386,-0.1,-3.983,-0.288,-4.726,0.521 c-0.434,0.473,-0.172,3.314,0.296,6.171 c2.276,0.105,4.577,0.179,6.9,0.22 c0.172,-1.775,0.36,-3.491,0.404,-4.618 C19.951,72.459,19.376,71.963,17.014,71.793z",
        Location.FRONT_LEFT_LOWER_THIGH to "M28.826,83.173 c0.021,1.412,0.907,1.888,3.206,1.896 c0.525,0.002,1.655,-0.031,1.863,-0.34 c0.395,-0.59,1.058,-3.424,1.521,-6.244 c-2.276,0.105,-4.577,0.179,-6.9,0.22 C28.667,80.269,28.807,81.879,28.826,83.173z",
        Location.FRONT_LEFT_UPPER_THIGH to "M35.712,72.314 c-0.742,-0.81,-3.339,-0.621,-4.726,-0.521 c-2.362,0.17,-2.938,0.666,-2.875,2.294 c0.044,1.127,0.231,2.843,0.404,4.618 c2.323,-0.042,4.624,-0.116,6.9,-0.22 C35.884,75.628,36.146,72.788,35.712,72.314z",
        Location.SIDE_RIGHT_UPPER_ARM to "M7.071,29.482 c-0.099,1.097,-0.002,2.283,0.018,3.377 c0.035,1.937,-0.049,5.683,-0.084,7.473 c1.456,-0.051,1.801,-0.409,1.801,-1.653 c0,-2.724,0.048,-5.132,-0.034,-7.813 C8.737,29.764,8.584,29.523,7.071,29.482z",
        Location.SIDE_LEFT_UPPER_ARM to "M40.716,29.487 c-1.362,0.055,-1.501,0.317,-1.534,1.38 c-0.082,2.681,-0.034,5.089,-0.034,7.813 c0,1.242,0.344,1.601,1.796,1.653 c-0.002,-0.207,-0.004,-0.406,-0.004,-0.59 C40.7,36.306,40.937,32.503,40.716,29.487z",
        Location.FRONT_LEFT_LOWER_ABDOMEN to "M26.202,55.344 c0.003,0.046,0.007,0.092,0.007,0.139 c0,1.22,-0.989,2.209,-2.209,2.209 v3.864 c3.156,0,6.189,-0.129,9.023,-0.364 c-0.54,-1.868,-0.955,-3.942,-1.219,-6.16 C29.968,55.192,28.099,55.298,26.202,55.344z",
        Location.SIDE_LEFT_LOWER_ABDOMEN to "M31.804,55.031 c0.264,2.218,0.679,4.293,1.219,6.16 c1.026,-0.085,2.028,-0.183,2.998,-0.296 c-0.491,-1.689,-1.018,-3.904,-1.42,-6.154 C33.678,54.853,32.745,54.949,31.804,55.031z",
        Location.FRONT_LEFT_UPPER_ABDOMEN to "M31.501,47.844 c-2.389,-0.162,-4.904,-0.248,-7.501,-0.248 v5.679 c1.173,0,2.13,0.915,2.202,2.07 c1.898,-0.046,3.766,-0.152,5.603,-0.313 c-0.213,-1.796,-0.328,-3.685,-0.328,-5.636 C31.476,48.873,31.485,48.356,31.501,47.844z",
        Location.SIDE_LEFT_UPPER_ABDOMEN to "M33.931,48.038 c-0.794,-0.073,-1.604,-0.138,-2.43,-0.194 c-0.016,0.512,-0.025,1.029,-0.025,1.551 c0,1.951,0.115,3.841,0.328,5.636 c0.941,-0.082,1.874,-0.178,2.796,-0.289 c-0.4,-2.237,-0.679,-4.507,-0.679,-6.33 C33.922,48.301,33.925,48.176,33.931,48.038z",
        Location.FRONT_RIGHT_LOWER_ABDOMEN to "M21.791,55.483 c0,-0.047,0.004,-0.093,0.007,-0.139 c-1.898,-0.046,-3.766,-0.152,-5.603,-0.313 c-0.264,2.218,-0.679,4.292,-1.219,6.16 c2.835,0.235,5.867,0.364,9.023,0.364 v-3.864 C22.78,57.691,21.791,56.703,21.791,55.483z",
        Location.SIDE_RIGHT_LOWER_ABDOMEN to "M11.89,60.7 c-0.018,0.062,-0.034,0.12,-0.052,0.18 c1.014,0.119,2.063,0.222,3.139,0.311 c0.54,-1.868,0.955,-3.942,1.219,-6.16 c-0.962,-0.084,-1.915,-0.182,-2.858,-0.297 C12.946,56.827,12.394,59.008,11.89,60.7z",
        Location.FRONT_RIGHT_UPPER_ABDOMEN to "M16.524,49.395 c0,1.951,-0.115,3.841,-0.328,5.636 c1.836,0.161,3.705,0.267,5.603,0.313 c0.072,-1.155,1.029,-2.07,2.202,-2.07 v-5.679 c-2.598,0,-5.111,0.088,-7.501,0.249 C16.515,48.357,16.524,48.874,16.524,49.395z",
        Location.SIDE_RIGHT_UPPER_ABDOMEN to "M13.858,48.058 c0.004,0.056,0.007,0.109,0.009,0.156 c-0.015,1.063,0.028,2.132,-0.057,3.19 c-0.082,1.014,-0.252,2.159,-0.472,3.331 c0.943,0.114,1.896,0.213,2.858,0.297 c0.213,-1.796,0.328,-3.685,0.328,-5.636 c0,-0.521,-0.009,-1.038,-0.025,-1.55 C15.601,47.906,14.718,47.976,13.858,48.058z",
        Location.FRONT_RIGHT_UPPER_CHEST to "M22.932,25.581 a3.989,2.16 7.0 1,0 -7.978,0 a3.989,2.16 7.0 1,0 7.978,0z",
        Location.FRONT_LEFT_UPPER_CHEST to "M33.046,25.581 a3.989,2.16 -7.0 1,0 -7.978,0 a3.989,2.16 -7.0 1,0 7.978,0z",
    )
    val zones: List<Pair<Location, Path>> = pathData.map { (location, svgData) ->
        location to PathParser().parsePathString(svgData).toPath()
    }
}
