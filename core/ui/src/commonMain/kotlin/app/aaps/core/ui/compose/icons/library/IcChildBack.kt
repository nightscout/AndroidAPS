package app.aaps.core.ui.compose.icons.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.TE.Location

/**
 * Icon for Child Back View.
 * Represents human child body back view for site rotation.
 *
 * Bounding box: x: 8.0-40.0, y: 8.0-100.0 (viewport: 48x128, ~72% height)
 *
 * @see ChildBackPreview
 */
val ChildBack: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChildBack",
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
            moveTo(36.66f, 44.223f)
            curveToRelative(-0.037f, -0.298f, -0.088f, -0.595f, -0.09f, -0.897f)
            curveToRelative(-0.006f, -1.129f, 0.069f, -2.266f, -0.028f, -3.387f)
            curveToRelative(-0.164f, -1.895f, -0.068f, -2.946f, -0.143f, -4.839f)
            curveToRelative(-0.019f, -0.495f, -0.182f, -1.83f, -0.286f, -2.319f)
            curveToRelative(-0.363f, -1.702f, -0.514f, -2.971f, -0.67f, -4.87f)
            curveToRelative(-0.019f, -0.226f, -0.093f, -0.742f, -0.093f, -1.669f)
            curveToRelative(0f, -0.755f, -0.089f, -1.574f, -0.158f, -2.331f)
            curveToRelative(-0.063f, -0.695f, -0.145f, -1.352f, -0.397f, -1.985f)
            curveToRelative(-0.356f, -0.895f, -0.991f, -2.33f, -2.518f, -2.916f)
            curveToRelative(-1.476f, -0.567f, -3.678f, -1.398f, -4.928f, -2.228f)
            curveToRelative(-1.381f, -0.917f, -1.481f, -2.139f, -0.603f, -3.017f)
            curveToRelative(1.387f, -1.387f, 1.945f, -3.67f, 2.179f, -4.662f)
            curveToRelative(0.516f, -0.308f, 0.626f, -2.731f, 0.085f, -2.684f)
            curveToRelative(-0.12f, -0.798f, 0.029f, -3.375f, -2.558f, -4.745f)
            curveToRelative(-1.712f, -0.907f, -3.574f, -0.904f, -4.976f, -0.016f)
            curveToRelative(-1.855f, 1.175f, -2.361f, 3.863f, -2.431f, 4.761f)
            curveToRelative(-0.543f, -0.115f, -0.295f, 2.576f, 0.255f, 2.683f)
            curveToRelative(0f, 1.014f, 0.576f, 3.116f, 2.113f, 4.654f)
            curveToRelative(0.947f, 0.947f, 1.143f, 2.101f, -0.772f, 3.32f)
            curveToRelative(-0.952f, 0.606f, -2.111f, 1.109f, -3.184f, 1.459f)
            curveToRelative(-0.809f, 0.264f, -1.667f, 0.604f, -2.427f, 1.015f)
            curveToRelative(-0.971f, 0.524f, -1.274f, 1.306f, -1.606f, 2.391f)
            curveToRelative(-0.311f, 1.016f, -0.447f, 2.276f, -0.625f, 4.826f)
            curveToRelative(-0.22f, 1.525f, -0.443f, 3.56f, -0.8f, 5.722f)
            curveToRelative(-0.12f, 0.727f, -0.41f, 2.547f, -0.402f, 3.284f)
            curveToRelative(0.015f, 1.266f, 0.043f, 1.891f, -0.017f, 3.154f)
            curveToRelative(-0.04f, 0.85f, -0.068f, 1.707f, -0.233f, 2.549f)
            curveToRelative(-0.024f, 0.123f, -0.058f, 0.259f, -0.029f, 0.375f)
            curveToRelative(0.325f, 1.289f, -0.031f, 2.546f, -0.155f, 3.815f)
            curveToRelative(-0.048f, 0.488f, 0.158f, 0.937f, 0.284f, 1.396f)
            curveToRelative(0.367f, 1.342f, 1.237f, 2.217f, 2.515f, 2.717f)
            curveToRelative(0.141f, 0.055f, 0.287f, 0.168f, 0.423f, 0.059f)
            curveToRelative(0.159f, -0.128f, 0.116f, -0.319f, 0.054f, -0.49f)
            curveToRelative(-0.098f, -0.269f, -0.246f, -0.515f, -0.511f, -0.635f)
            curveToRelative(-0.399f, -0.18f, -0.574f, -0.535f, -0.602f, -0.903f)
            curveToRelative(-0.041f, -0.529f, -0.319f, -1.081f, 0.046f, -1.602f)
            curveToRelative(0.07f, -0.099f, 0.108f, -0.249f, 0.269f, -0.215f)
            curveToRelative(0.134f, 0.028f, 0.21f, 0.144f, 0.234f, 0.27f)
            curveToRelative(0.06f, 0.321f, 0.146f, 0.644f, 0.146f, 0.966f)
            curveToRelative(0f, 0.331f, 0.141f, 0.577f, 0.311f, 0.83f)
            curveToRelative(0.101f, 0.15f, 0.189f, 0.333f, 0.403f, 0.253f)
            curveToRelative(0.188f, -0.071f, 0.226f, -0.26f, 0.215f, -0.447f)
            curveToRelative(-0.06f, -1.107f, -0.112f, -2.214f, -0.194f, -3.319f)
            curveToRelative(-0.016f, -0.214f, -0.117f, -0.451f, -0.25f, -0.623f)
            curveToRelative(-0.466f, -0.606f, -0.546f, -1.346f, -0.731f, -2.044f)
            curveToRelative(-0.105f, -0.397f, 0.106f, -0.786f, 0.219f, -1.169f)
            curveToRelative(0.364f, -1.228f, 0.747f, -2.451f, 1.096f, -3.683f)
            curveToRelative(0.391f, -1.379f, 0.621f, -2.787f, 0.797f, -4.21f)
            curveToRelative(0.206f, -1.672f, 0.681f, -5.019f, 0.81f, -5.019f)
            curveToRelative(0.144f, 0f, 0.293f, 3.729f, 0.293f, 4.936f)
            curveToRelative(0f, 2.592f, -0.144f, 5.124f, -0.526f, 8.078f)
            curveToRelative(-0.124f, 0.957f, -0.387f, 3.038f, -0.309f, 5.405f)
            curveToRelative(0.145f, 4.443f, 0.103f, 7.797f, 0.776f, 11.712f)
            curveToRelative(0.457f, 1.635f, 0.214f, 3.31f, 0.299f, 4.967f)
            curveToRelative(0.063f, 1.224f, -0.141f, 2.412f, -0.348f, 3.603f)
            curveToRelative(-0.222f, 1.281f, -0.134f, 2.557f, -0.036f, 3.842f)
            curveToRelative(0.082f, 1.08f, 0.254f, 2.147f, 0.356f, 3.222f)
            curveToRelative(0.09f, 0.952f, 0.303f, 1.881f, 0.424f, 2.827f)
            curveToRelative(0.125f, 0.977f, 0.381f, 1.936f, 0.477f, 2.918f)
            curveToRelative(0.019f, 0.552f, -0.353f, 1.273f, -0.976f, 1.704f)
            curveToRelative(-0.794f, 0.549f, -1.608f, 1.059f, -2.551f, 1.321f)
            curveToRelative(-0.22f, 0.061f, -0.447f, 0.122f, -0.644f, 0.23f)
            curveToRelative(-0.175f, 0.096f, -0.34f, 0.248f, -0.307f, 0.489f)
            curveToRelative(0.034f, 0.245f, 0.224f, 0.292f, 0.434f, 0.356f)
            curveToRelative(0.524f, 0.159f, 1.064f, 0.094f, 1.591f, 0.16f)
            curveToRelative(1.364f, 0.171f, 2.734f, 0.234f, 4.104f, 0.276f)
            curveToRelative(0.926f, 0.029f, 1.32f, -0.431f, 1.38f, -1.348f)
            curveToRelative(0.046f, -0.698f, -0.215f, -1.362f, -0.188f, -2.051f)
            curveToRelative(0.015f, -0.379f, 0.01f, -0.795f, -0.041f, -1.137f)
            curveToRelative(-0.183f, -0.949f, -0.161f, -1.944f, 0.025f, -2.887f)
            curveToRelative(0.339f, -1.722f, 0.811f, -3.427f, 0.967f, -5.175f)
            curveToRelative(0.129f, -1.444f, -0.084f, -2.863f, -0.084f, -4.354f)
            curveToRelative(0f, -1.415f, 0.001f, -2.327f, 0.201f, -3.453f)
            curveToRelative(0.204f, -1.149f, 0.347f, -1.879f, 0.449f, -3.053f)
            curveToRelative(0.065f, -0.756f, 0.285f, -1.613f, 0.273f, -2.372f)
            curveToRelative(-0.024f, -1.595f, 0.308f, -3.151f, 0.531f, -4.717f)
            curveToRelative(0.197f, -1.39f, 0.02f, -0.299f, 0.255f, -1.683f)
            curveToRelative(0.05f, -0.297f, 0.106f, -0.594f, 0.17f, -0.888f)
            curveToRelative(0.028f, -0.126f, 0.106f, -0.243f, 0.238f, -0.269f)
            curveToRelative(0.183f, -0.037f, 0.273f, 0.105f, 0.339f, 0.242f)
            curveToRelative(0.076f, 0.158f, 0.14f, 0.317f, 0.162f, 0.5f)
            curveToRelative(0.215f, 1.819f, 0.143f, 1.065f, 0.32f, 2.886f)
            curveToRelative(0.16f, 1.646f, 0.177f, 3.305f, 0.342f, 4.971f)
            curveToRelative(0.171f, 1.731f, 0.39f, 3.05f, 0.762f, 4.734f)
            curveToRelative(0.302f, 1.367f, 0.351f, 2.283f, 0.355f, 3.632f)
            curveToRelative(0.004f, 1.224f, -0.098f, 2.608f, 0.036f, 3.85f)
            curveToRelative(0.197f, 1.83f, 0.559f, 3.435f, 0.754f, 5.251f)
            curveToRelative(0.099f, 0.923f, 0.111f, 1.827f, 0.032f, 2.784f)
            curveToRelative(-0.088f, 0.989f, -0.217f, 1.975f, -0.3f, 3.087f)
            curveToRelative(-0.009f, 0.126f, -0.018f, 0.258f, 0.004f, 0.383f)
            curveToRelative(0.08f, 0.453f, 0.342f, 0.806f, 0.832f, 0.961f)
            curveToRelative(0.381f, 0.12f, 0.773f, 0.184f, 1.129f, 0.091f)
            curveToRelative(0.635f, -0.167f, 1.268f, -0.092f, 1.902f, -0.125f)
            curveToRelative(1.065f, -0.055f, 2.127f, -0.143f, 3.183f, -0.298f)
            curveToRelative(0.245f, -0.036f, 0.487f, -0.073f, 0.501f, -0.36f)
            curveToRelative(0.013f, -0.263f, -0.088f, -0.519f, -0.4f, -0.562f)
            curveToRelative(-0.867f, -0.12f, -1.539f, -0.659f, -2.267f, -1.063f)
            curveToRelative(-0.763f, -0.424f, -1.794f, -0.91f, -1.856f, -1.906f)
            curveToRelative(-0.004f, -0.063f, 0.012f, -0.148f, 0.01f, -0.207f)
            curveToRelative(0.013f, -0.084f, 0.028f, -0.167f, 0.045f, -0.251f)
            curveToRelative(0.497f, -2.361f, 0.842f, -4.747f, 1.138f, -7.136f)
            curveToRelative(0.109f, -0.884f, 0.099f, -1.806f, 0.099f, -2.709f)
            curveToRelative(0f, -1.73f, -0.155f, -2.962f, -0.329f, -4.614f)
            curveToRelative(-0.034f, -0.323f, -0.033f, -0.656f, -0.035f, -0.985f)
            curveToRelative(-0.003f, -0.861f, 0.032f, -1.724f, -0.008f, -2.584f)
            curveToRelative(-0.05f, -1.067f, 0.081f, -2.109f, 0.301f, -3.146f)
            curveToRelative(0.948f, -4.488f, 0.899f, -9.829f, 0.899f, -12.62f)
            curveToRelative(0f, -1.45f, -0.108f, -2.876f, -0.224f, -4.692f)
            curveToRelative(-0.121f, -1.892f, -0.369f, -3.901f, -0.526f, -5.844f)
            curveToRelative(-0.059f, -0.728f, -0.082f, -1.927f, -0.093f, -2.659f)
            curveToRelative(0.031f, -0.859f, 0.294f, -3.13f, 0.37f, -3.702f)
            curveToRelative(0.015f, -0.111f, 0.055f, -0.297f, 0.162f, -0.337f)
            curveToRelative(0.082f, 0.082f, 0.089f, 0.246f, 0.095f, 0.325f)
            curveToRelative(0.051f, 0.732f, 0.199f, 1.214f, 0.198f, 1.848f)
            curveToRelative(-0.004f, 1.415f, 0.282f, 2.798f, 0.529f, 4.181f)
            curveToRelative(0.406f, 2.277f, 1.067f, 4.494f, 1.666f, 6.726f)
            curveToRelative(0.192f, 0.715f, 0.156f, 1.454f, -0.166f, 2.148f)
            curveToRelative(-0.241f, 0.52f, -0.496f, 1.033f, -0.733f, 1.554f)
            curveToRelative(-0.05f, 0.11f, -0.069f, 0.242f, -0.07f, 0.364f)
            curveToRelative(-0.01f, 0.887f, -0.012f, 1.773f, -0.014f, 2.66f)
            curveToRelative(0f, 0.148f, -0.021f, 0.32f, 0.176f, 0.37f)
            curveToRelative(0.167f, 0.042f, 0.282f, -0.066f, 0.401f, -0.164f)
            curveToRelative(0.396f, -0.323f, 0.37f, -0.805f, 0.468f, -1.237f)
            curveToRelative(0.033f, -0.145f, 0.003f, -0.303f, 0.01f, -0.455f)
            curveToRelative(0.009f, -0.197f, -0.018f, -0.464f, 0.258f, -0.451f)
            curveToRelative(0.206f, 0.01f, 0.347f, 0.227f, 0.354f, 0.44f)
            curveToRelative(0.029f, 0.88f, 0.167f, 1.798f, -0.727f, 2.404f)
            curveToRelative(-0.124f, 0.084f, -0.23f, 0.202f, -0.324f, 0.32f)
            curveToRelative(-0.116f, 0.146f, -0.373f, 0.258f, -0.229f, 0.499f)
            curveToRelative(0.166f, 0.277f, 0.427f, 0.215f, 0.673f, 0.109f)
            curveToRelative(1.342f, -0.578f, 2.153f, -1.594f, 2.584f, -2.985f)
            curveTo(36.981f, 45.838f, 36.759f, 45.037f, 36.66f, 44.223f)
            close()
        }

    }.build()
}

/**
 * Contains SVG path data for each zone of the child body back view.
 */
object ChildBackPaths {

    val pathData: Map<Location, String> = mapOf(
        Location.BACK_LEFT_UPPER_ARM to "M15.544,30.688 c-0.175,1.144-0.537,1.603-0.905,1.559c-0.433-0.051-0.808-0.149-1.191-0.212c-0.557-0.092-0.941-0.317-0.719-1.392 c0.371-1.801,0.458-3.806,0.619-4.527c0.139-0.624,0.352-0.758,0.936-0.704c0.449,0.042,0.649,0.088,1.342,0.152 c0.599,0.056,0.798,0.736,0.631,1.483C16.082,27.831,15.736,29.432,15.544,30.688z",
        Location.BACK_RIGHT_UPPER_ARM to "M32.553,30.647 c0.132,1.149,0.476,1.622,0.846,1.592c0.435-0.035,0.813-0.118,1.198-0.167c0.56-0.071,0.952-0.281,0.771-1.364 c-0.303-1.813-0.315-3.82-0.448-4.547c-0.115-0.629-0.323-0.771-0.909-0.738c-0.451,0.025-0.652,0.063-1.347,0.102 c-0.601,0.033-0.825,0.705-0.687,1.458C32.122,27.772,32.408,29.385,32.553,30.647z",
        Location.BACK_LEFT_BUTTOCK to "M16.87,44.209 c0-0.697,0.207-2.048,0.621-2.664c0.621-0.923,1.31-1.159,3.603-0.834c2.303,0.327,2.3,2.656,1.917,4.963 c-0.29,1.744-3.858,1.96-5.346,0.74C17.068,45.925,16.78,45.051,16.87,44.209z",
        Location.BACK_RIGHT_BUTTOCK to "M31.13,44.209 c0-0.697-0.207-2.048-0.621-2.664c-0.621-0.923-1.31-1.159-3.603-0.834c-2.303,0.327-2.3,2.656-1.917,4.963 c0.29,1.744,3.858,1.96,5.346,0.74C30.932,45.925,31.22,45.051,31.13,44.209z"
    )
    val zones: List<Pair<Location, Path>> by lazy {
        pathData.map { (location, svgData) ->
            location to PathParser().parsePathString(svgData).toPath()
        }
    }
}
