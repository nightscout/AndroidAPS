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
 * Icon for Woman Front View.
 * Represents human body front view for site rotation.
 *
 * Bounding box: x: 5.0-43.0, y: 3.0-112.0 (viewport: 48x128, ~85% height)
 *
 * @see WomanFrontPreview
 */
val WomanFront: ImageVector by lazy {
    ImageVector.Builder(
        name = "WomanFront",
        defaultWidth = 48.dp,
        defaultHeight = 128.dp,
        viewportWidth = 48f,
        viewportHeight = 128f
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
            moveTo(41.958f, 63.514f)
            curveToRelative(-0.645f, -1.377f, -0.83f, -2.879f, -1.043f, -4.34f)
            curveToRelative(-0.295f, -2.018f, -0.321f, -4.077f, -0.437f, -6.118f)
            curveToRelative(-0.125f, -2.192f, -0.123f, -4.392f, -0.252f, -6.589f)
            curveToRelative(-0.091f, -1.547f, -0.427f, -3.034f, -0.887f, -4.487f)
            curveToRelative(-0.399f, -1.262f, -0.788f, -2.492f, -0.699f, -3.844f)
            curveToRelative(0.045f, -0.688f, -0.172f, -4.273f, -0.172f, -4.877f)
            curveToRelative(-0.141f, -0.98f, -0.144f, -1.969f, -0.104f, -2.952f)
            curveToRelative(0.07f, -1.72f, 0.041f, -3.432f, -0.232f, -5.135f)
            curveToRelative(-0.068f, -0.319f, -0.188f, -0.884f, -0.267f, -1.159f)
            curveToRelative(-0.501f, -1.724f, -1.349f, -2.831f, -3.111f, -3.287f)
            curveToRelative(-1.8f, -0.467f, -3.612f, -0.899f, -5.339f, -1.608f)
            curveToRelative(-0.373f, -0.153f, -0.744f, -0.323f, -1.083f, -0.537f)
            curveToRelative(-0.201f, -0.126f, -0.48f, -0.285f, -0.409f, -0.592f)
            curveToRelative(0.055f, -0.236f, 0.324f, -0.274f, 0.521f, -0.352f)
            curveToRelative(0.133f, -0.052f, 0.293f, -0.031f, 0.432f, -0.07f)
            curveToRelative(1.533f, -0.433f, 2.949f, -1.093f, 4.128f, -2.189f)
            curveToRelative(0.231f, -0.215f, 0.58f, -0.427f, -0.047f, -0.683f)
            curveToRelative(-1.402f, -0.573f, -1.893f, -1.694f, -1.829f, -3.174f)
            curveToRelative(0.083f, -1.941f, -0.023f, -3.869f, -0.386f, -5.795f)
            curveToRelative(-0.453f, -2.405f, -1.951f, -3.73f, -4.15f, -4.479f)
            curveToRelative(-2.138f, -0.728f, -4.185f, 0.326f, -6.286f, 0.274f)
            curveToRelative(-0.064f, -0.002f, -0.135f, 0.056f, -0.193f, 0.098f)
            curveToRelative(-1.076f, 0.777f, -2.045f, 1.64f, -2.659f, 2.861f)
            curveToRelative(-1.06f, 2.105f, -1.654f, 4.332f, -2.098f, 6.643f)
            curveToRelative(-0.548f, 2.855f, 1.257f, 5.532f, 3.861f, 6.287f)
            curveToRelative(0.351f, 0.102f, 0.973f, 0.015f, 1.004f, 0.436f)
            curveToRelative(0.031f, 0.417f, -0.475f, 0.69f, -0.875f, 0.894f)
            curveToRelative(-0.164f, 0.083f, -0.326f, 0.169f, -0.489f, 0.253f)
            curveToRelative(-1.574f, 0.818f, -3.311f, 1.148f, -5f, 1.59f)
            curveToRelative(-2.714f, 0.71f, -3.94f, 3.782f, -4.076f, 5.624f)
            curveToRelative(-0.242f, 3.291f, -0.13f, 6.59f, -0.227f, 9.884f)
            curveToRelative(0.034f, 2.06f, -0.113f, 4.102f, -0.855f, 6.043f)
            curveToRelative(-0.501f, 1.31f, -0.865f, 2.688f, -0.89f, 4.042f)
            curveToRelative(-0.064f, 3.409f, -0.381f, 6.801f, -0.485f, 10.204f)
            curveToRelative(-0.084f, 2.753f, -0.495f, 5.428f, -1.454f, 8.015f)
            curveToRelative(-0.823f, 2.22f, 0.102f, 4.533f, 2.158f, 5.566f)
            curveTo(8.596f, 70.25f, 9.136f, 70.1f, 9.414f, 69.74f)
            curveToRelative(0.375f, -0.486f, -0.233f, -0.748f, -0.559f, -0.992f)
            curveToRelative(-0.976f, -0.73f, -1.405f, -1.872f, -1.08f, -2.992f)
            curveToRelative(0.053f, -0.181f, 0.143f, -0.36f, 0.341f, -0.389f)
            curveToRelative(0.245f, -0.035f, 0.25f, 0.189f, 0.326f, 0.352f)
            curveToRelative(0.227f, 0.49f, 0.149f, 1.01f, 0.186f, 1.519f)
            curveToRelative(0.017f, 0.229f, 0.053f, 0.454f, 0.353f, 0.456f)
            curveToRelative(0.251f, 0.001f, 0.44f, -0.146f, 0.48f, -0.375f)
            curveToRelative(0.057f, -0.322f, 0.07f, -0.657f, 0.059f, -0.985f)
            curveToRelative(-0.022f, -0.593f, 0.012f, -1.175f, 0.245f, -1.726f)
            curveToRelative(0.29f, -0.684f, 0.276f, -1.371f, -0.038f, -2.026f)
            curveToRelative(-0.489f, -1.021f, -0.389f, -2.029f, -0.094f, -3.074f)
            curveToRelative(0.989f, -3.509f, 1.845f, -7.053f, 2.915f, -10.542f)
            curveToRelative(0.802f, -2.616f, 1.237f, -5.325f, 1.356f, -8.065f)
            curveToRelative(0.098f, -2.23f, 0.488f, -4.425f, 0.689f, -6.641f)
            curveToRelative(0.02f, -0.219f, 0.075f, -0.833f, 0.211f, -0.85f)
            curveToRelative(0.209f, -0.026f, 0.418f, 0.5f, 0.5f, 0.715f)
            curveToRelative(0.095f, 0.248f, 0.125f, 0.474f, 0.183f, 0.723f)
            curveToRelative(0.457f, 1.957f, 1.478f, 8.831f, -0.465f, 12.43f)
            curveToRelative(-0.702f, 1.299f, -1.948f, 5.687f, -2.048f, 6.048f)
            curveToRelative(-0.741f, 2.681f, -1.132f, 5.438f, -1.219f, 8.202f)
            curveToRelative(-0.144f, 4.541f, 0.448f, 9.897f, 1.331f, 13.876f)
            curveToRelative(0.409f, 1.844f, 0.674f, 3.727f, 1.327f, 5.514f)
            curveToRelative(0.03f, 1.972f, -0.308f, 3.965f, 0.328f, 5.904f)
            curveToRelative(0.083f, 0.252f, 0.047f, 0.499f, -0.02f, 0.757f)
            curveToRelative(-0.182f, 0.706f, -0.315f, 1.424f, -0.508f, 2.126f)
            curveToRelative(-0.875f, 3.187f, -0.503f, 6.421f, -0.195f, 9.613f)
            curveToRelative(0.213f, 2.206f, 0.441f, 4.424f, 0.739f, 6.629f)
            curveToRelative(0.2f, 1.477f, 0.467f, 2.953f, 0.59f, 4.45f)
            curveToRelative(0.134f, 1.626f, -0.316f, 3.084f, -0.755f, 4.576f)
            curveToRelative(-0.048f, 0.164f, -0.196f, 0.311f, -0.325f, 0.437f)
            curveToRelative(-0.814f, 0.793f, -1.529f, 1.683f, -2.48f, 2.341f)
            curveToRelative(-0.84f, 0.582f, -0.713f, 1.475f, 0.22f, 1.866f)
            curveToRelative(0.47f, 0.198f, 0.969f, 0.332f, 1.428f, 0.55f)
            curveToRelative(1.056f, 0.502f, 1.705f, 0.375f, 2.596f, -0.306f)
            curveToRelative(1.035f, -0.791f, 1.873f, -1.839f, 3.089f, -2.39f)
            curveToRelative(0.429f, -0.194f, 0.566f, -0.51f, 0.568f, -0.966f)
            curveToRelative(0.005f, -1.213f, -0.29f, -2.391f, -0.383f, -3.591f)
            curveToRelative(-0.068f, -0.876f, -0.582f, -1.697f, -0.463f, -2.57f)
            curveToRelative(0.304f, -2.238f, 0.701f, -4.463f, 1.059f, -6.694f)
            curveToRelative(0.294f, -1.834f, 0.56f, -3.674f, 0.885f, -5.503f)
            curveToRelative(0.304f, -1.714f, 0.412f, -3.435f, -0.026f, -5.119f)
            curveToRelative(-0.293f, -1.126f, -0.152f, -2.176f, 0.145f, -3.234f)
            curveToRelative(0.644f, -2.299f, 1.254f, -4.601f, 0.971f, -7.02f)
            curveToRelative(-0.119f, -1.019f, -0.259f, -2.028f, -0.233f, -3.061f)
            curveToRelative(0.018f, -0.702f, 0.658f, -5.251f, 0.803f, -6.854f)
            curveToRelative(0.22f, -2.432f, 0.764f, -5.733f, 0.764f, -8.507f)
            curveToRelative(0f, -0.516f, 0.255f, -0.629f, 0.686f, -0.596f)
            curveToRelative(0.377f, 0.029f, 0.831f, -0.135f, 0.873f, 0.524f)
            curveToRelative(0.143f, 2.259f, 0.384f, 4.51f, 0.607f, 6.762f)
            curveToRelative(0.204f, 2.072f, 0.61f, 4.114f, 0.761f, 6.194f)
            curveToRelative(0.124f, 1.702f, 0.529f, 3.388f, 0.152f, 5.103f)
            curveToRelative(-0.102f, 0.463f, -0.205f, 0.918f, -0.218f, 1.403f)
            curveToRelative(-0.048f, 1.858f, 0.388f, 3.629f, 0.922f, 5.379f)
            curveToRelative(0.403f, 1.321f, 0.52f, 2.616f, 0.265f, 3.998f)
            curveToRelative(-0.169f, 0.92f, -0.437f, 1.873f, -0.257f, 2.831f)
            curveToRelative(0.256f, 1.363f, 0.343f, 2.746f, 0.598f, 4.111f)
            curveToRelative(0.461f, 2.471f, 0.696f, 4.979f, 1.227f, 7.443f)
            curveToRelative(0.221f, 1.028f, 0.48f, 2.15f, 0.283f, 3.142f)
            curveToRelative(-0.334f, 1.684f, -0.252f, 3.421f, -0.778f, 5.078f)
            curveToRelative(-0.271f, 0.855f, 0.018f, 1.564f, 0.777f, 1.845f)
            curveToRelative(1.086f, 0.402f, 1.645f, 1.39f, 2.531f, 2.002f)
            curveToRelative(0.422f, 0.291f, 0.822f, 0.604f, 1.287f, 0.826f)
            curveToRelative(0.352f, 0.168f, 0.745f, 0.192f, 1.079f, 0.081f)
            curveToRelative(0.795f, -0.265f, 1.58f, -0.574f, 2.336f, -0.936f)
            curveToRelative(0.7f, -0.336f, 0.742f, -1.046f, 0.136f, -1.566f)
            curveToRelative(-0.36f, -0.309f, -0.752f, -0.597f, -1.093f, -0.912f)
            curveToRelative(-1.098f, -1.016f, -2.148f, -2.075f, -2.449f, -3.661f)
            curveToRelative(-0.318f, -1.681f, -0.284f, -3.345f, 0.017f, -5.012f)
            curveToRelative(0.513f, -2.839f, 0.666f, -5.722f, 1.065f, -8.574f)
            curveToRelative(0.322f, -2.3f, 0.21f, -4.615f, 0.245f, -6.925f)
            curveToRelative(0.014f, -0.889f, -0.135f, -1.742f, -0.316f, -2.607f)
            curveToRelative(-0.239f, -1.138f, -0.753f, -2.237f, -0.577f, -3.441f)
            curveToRelative(0.095f, -0.65f, 0.24f, -1.288f, 0.233f, -1.956f)
            curveToRelative(-0.015f, -1.389f, -0.108f, -2.785f, 0.179f, -4.162f)
            curveToRelative(0.154f, -0.676f, 0.296f, -1.354f, 0.462f, -2.027f)
            curveToRelative(1.44f, -5.869f, 2.223f, -10.985f, 2.223f, -14.073f)
            curveToRelative(0f, -3.857f, -0.488f, -7.955f, -1.507f, -11.694f)
            curveToRelative(-0.582f, -2.137f, -1.213f, -4.083f, -1.888f, -5.581f)
            curveToRelative(-1.667f, -3.701f, -1.032f, -8.808f, -0.597f, -11.768f)
            curveToRelative(0.119f, -0.812f, 0.276f, -2.324f, 0.792f, -2.324f)
            curveToRelative(0.179f, 0f, 0.529f, 5.554f, 0.946f, 8.242f)
            curveToRelative(0.323f, 2.086f, 0.563f, 4.193f, 1.018f, 6.25f)
            curveToRelative(0.859f, 3.88f, 2.005f, 7.689f, 3.077f, 11.515f)
            curveToRelative(0.228f, 0.814f, 0.645f, 1.695f, 0.299f, 2.461f)
            curveToRelative(-0.791f, 1.75f, -0.04f, 3.477f, -0.144f, 5.205f)
            curveToRelative(-0.002f, 0.036f, -0.004f, 0.078f, 0.012f, 0.109f)
            curveToRelative(0.128f, 0.25f, 0.142f, 0.626f, 0.509f, 0.644f)
            curveToRelative(0.382f, 0.019f, 0.452f, -0.347f, 0.52f, -0.611f)
            curveToRelative(0.071f, -0.278f, 0.035f, -0.583f, 0.048f, -0.877f)
            curveToRelative(0.009f, -0.218f, -0.028f, -0.45f, 0.115f, -0.637f)
            curveToRelative(0.063f, -0.083f, 0.164f, -0.178f, 0.256f, -0.187f)
            curveToRelative(0.16f, -0.016f, 0.274f, 0.097f, 0.275f, 0.267f)
            curveToRelative(0.006f, 1.141f, 0.381f, 2.401f, -1.001f, 3.099f)
            curveToRelative(-0.123f, 0.062f, -0.206f, 0.202f, -0.312f, 0.302f)
            curveToRelative(-0.51f, 0.479f, -0.484f, 0.612f, 0.095f, 0.93f)
            curveToRelative(0.328f, 0.18f, 0.6f, 0.179f, 0.925f, -0.002f)
            curveToRelative(1.16f, -0.647f, 2.032f, -1.459f, 2.351f, -2.857f)
            curveTo(42.541f, 65.847f, 42.499f, 64.671f, 41.958f, 63.514f)
            close()
        }

    }.build()
}

/**
 * Contains SVG path data for each zone of the female body front view.
 */
object WomanFrontPaths {

    val pathData: Map<Location, String> = mapOf(
        Location.FRONT_RIGHT_LOWER_THIGH to "M15.062,78.416 c0.207,0.31,1.338,0.342,1.863,0.34c2.298-0.007,3.185-0.483,3.206-1.896c0.019-1.294,0.159-2.904,0.311-4.468 c-2.323-0.042-4.624-0.116-6.9-0.22C14.004,74.992,14.668,77.827,15.062,78.416z",
        Location.FRONT_RIGHT_UPPER_THIGH to "M17.971,65.481 c-1.386-0.1-3.983-0.288-4.726,0.521c-0.434,0.473-0.172,3.314,0.296,6.171c2.276,0.105,4.577,0.179,6.9,0.22 c0.172-1.775,0.36-3.491,0.404-4.618C20.909,66.147,20.333,65.651,17.971,65.481z",
        Location.FRONT_LEFT_LOWER_THIGH to "M27.869,76.861 c0.021,1.412,0.907,1.888,3.206,1.896c0.525,0.002,1.655-0.031,1.863-0.34c0.395-0.59,1.058-3.424,1.521-6.244 c-2.276,0.105-4.577,0.179-6.9,0.22C27.71,73.957,27.85,75.567,27.869,76.861z",
        Location.FRONT_LEFT_UPPER_THIGH to "M34.755,66.002 c-0.742-0.81-3.339-0.621-4.726-0.521c-2.362,0.17-2.938,0.666-2.875,2.294c0.044,1.127,0.231,2.843,0.404,4.618 c2.323-0.042,4.624-0.116,6.9-0.22C34.927,69.316,35.189,66.475,34.755,66.002z",
        Location.SIDE_RIGHT_UPPER_ARM to "M11.368,35.504 c0-2.724,0.054-5.132-0.038-7.813c-0.035-1.02-0.188-1.302-1.556-1.371c-0.232,3.254-0.124,6.516-0.22,9.774 c0.006,0.352,0.006,0.703-0.001,1.054C11.012,37.071,11.368,36.691,11.368,35.504z",
        Location.SIDE_LEFT_UPPER_ARM to "M36.67,27.691 c-0.092,2.681-0.038,5.089-0.038,7.813c0,1.236,0.385,1.598,2,1.652c-0.042-1.328-0.164-3.444-0.164-3.895 c-0.141-0.98-0.144-1.969-0.104-2.952c0.054-1.336,0.047-2.666-0.086-3.991C36.86,26.382,36.706,26.658,36.67,27.691z",
        Location.FRONT_RIGHT_LOWER_ABDOMEN to "M24,49.407 c-1.201,0-2.176-0.959-2.206-2.153c-1.442-0.029-2.859-0.088-4.254-0.172c-0.299,1.957-0.7,3.82-1.189,5.561 c2.412,0.201,4.98,0.312,7.648,0.312V49.407z",
        Location.SIDE_RIGHT_LOWER_ABDOMEN to "M15.2,46.92 c-0.057,0.122-0.116,0.241-0.178,0.357c-0.517,0.957-1.328,3.586-1.76,5.052c0.995,0.123,2.029,0.227,3.091,0.315 c0.489-1.741,0.889-3.604,1.189-5.561C16.753,47.036,15.971,46.983,15.2,46.92z",
        Location.FRONT_RIGHT_UPPER_ABDOMEN to "M18.149,40.064 c-0.058,2.436-0.268,4.788-0.609,7.019c1.395,0.083,2.812,0.143,4.254,0.172c0-0.019-0.003-0.037-0.003-0.055 c0-1.22,0.989-2.209,2.209-2.209v-5.105C21.985,39.885,20.027,39.947,18.149,40.064z",
        Location.SIDE_RIGHT_UPPER_ABDOMEN to "M16.146,40.211 c0.086,2.323-0.095,4.868-0.945,6.708c0.77,0.064,1.553,0.116,2.34,0.163c0.341-2.23,0.55-4.583,0.609-7.019 C17.471,40.106,16.802,40.155,16.146,40.211z",
        Location.FRONT_LEFT_LOWER_ABDOMEN to "M26.206,47.254 c-0.03,1.194-1.005,2.153-2.206,2.153v3.548c2.668,0,5.236-0.111,7.648-0.312c-0.489-1.741-0.889-3.604-1.189-5.561 C29.065,47.165,27.648,47.225,26.206,47.254z",
        Location.SIDE_LEFT_LOWER_ABDOMEN to "M33.07,47.352 c-0.065-0.144-0.126-0.291-0.184-0.44c-0.799,0.067-1.61,0.121-2.427,0.17c0.299,1.958,0.7,3.82,1.189,5.561 c1.079-0.09,2.13-0.196,3.14-0.321C34.253,50.427,33.68,48.706,33.07,47.352z",
        Location.FRONT_LEFT_UPPER_ABDOMEN to "M24,39.885v5.105 c1.22,0,2.209,0.989,2.209,2.209c0,0.019-0.002,0.037-0.003,0.055c1.442-0.029,2.859-0.089,4.254-0.172 c-0.341-2.23-0.55-4.582-0.609-7.018C27.973,39.947,26.015,39.885,24,39.885z",
        Location.SIDE_LEFT_UPPER_ABDOMEN to "M32.004,40.224 c-0.704-0.062-1.423-0.114-2.153-0.16c0.058,2.436,0.268,4.788,0.609,7.018c0.817-0.049,1.628-0.103,2.427-0.17 C32.084,44.865,31.92,42.452,32.004,40.224z"
    )
    val zones: List<Pair<Location, Path>> by lazy {
        pathData.map { (location, svgData) ->
            location to PathParser().parsePathString(svgData).toPath()
        }
    }
}
