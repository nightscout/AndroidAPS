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
 * replacing ic_nsclient_bg
 *
 * Bounding box: x: 3.5-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% width)
 *
 * @see IcPluginNsClientIconPreview
 */
val IcPluginNsClientBg: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginNsClient",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(17.761f, 8.438f)
            curveToRelative(0f, 0.648f, -0.083f, 1.456f, -0.12f, 1.718f)
            curveToRelative(-0.105f, 0.743f, -0.291f, 1.465f, -0.526f, 2.176f)
            curveToRelative(-0.304f, 0.923f, -0.689f, 1.812f, -1.155f, 2.666f)
            curveToRelative(-0.286f, 0.524f, -0.585f, 1.04f, -0.912f, 1.54f)
            curveToRelative(-0.353f, 0.538f, -0.727f, 1.061f, -1.125f, 1.568f)
            curveToRelative(-0.378f, 0.481f, -0.761f, 0.956f, -1.166f, 1.414f)
            curveToRelative(-0.132f, 0.15f, -0.271f, 0.293f, -0.404f, 0.441f)
            curveToRelative(-0.258f, 0.287f, -0.483f, 0.523f, -0.78f, 0.772f)
            curveToRelative(-0.647f, 0.609f, -1.6f, 0.561f, -2.163f, -0.074f)
            curveToRelative(-0.427f, -0.417f, -0.829f, -0.856f, -1.22f, -1.307f)
            curveToRelative(-0.483f, -0.558f, -0.943f, -1.132f, -1.384f, -1.725f)
            curveToRelative(-0.653f, -0.879f, -1.239f, -1.8f, -1.759f, -2.762f)
            curveToRelative(-0.373f, -0.69f, -0.687f, -1.409f, -0.947f, -2.149f)
            curveToRelative(-0.351f, -0.998f, -0.628f, -2.016f, -0.727f, -3.076f)
            curveToRelative(-0.012f, -0.13f, -0.054f, -0.646f, -0.054f, -1.2f)
            curveToRelative(0f, -0.588f, 0.061f, -1.054f, 0.088f, -1.235f)
            curveToRelative(0.101f, -0.687f, 0.328f, -1.331f, 0.625f, -1.952f)
            curveTo(4.273f, 4.746f, 4.58f, 4.279f, 4.94f, 3.84f)
            curveToRelative(0.347f, -0.423f, 0.733f, -0.804f, 1.16f, -1.139f)
            curveToRelative(0.47f, -0.368f, 0.981f, -0.676f, 1.534f, -0.912f)
            curveTo(8.24f, 1.53f, 8.863f, 1.34f, 9.52f, 1.256f)
            curveTo(9.636f, 1.241f, 10.065f, 1.2f, 10.582f, 1.2f)
            curveToRelative(0.662f, 0f, 1.31f, 0.098f, 1.605f, 0.167f)
            curveToRelative(0.666f, 0.156f, 1.299f, 0.401f, 1.897f, 0.738f)
            curveToRelative(0.847f, 0.478f, 1.569f, 1.101f, 2.168f, 1.862f)
            curveToRelative(0.43f, 0.547f, 0.776f, 1.147f, 1.026f, 1.798f)
            curveToRelative(0.176f, 0.457f, 0.315f, 0.925f, 0.394f, 1.411f)
            curveTo(17.701f, 7.348f, 17.761f, 7.867f, 17.761f, 8.438f)
            close()

            moveTo(10.587f, 2.347f)
            curveToRelative(-0.283f, -0.005f, -0.562f, 0.032f, -0.842f, 0.062f)
            curveTo(9.16f, 2.47f, 8.607f, 2.643f, 8.074f, 2.879f)
            curveTo(7.158f, 3.286f, 6.401f, 3.898f, 5.777f, 4.684f)
            curveTo(5.343f, 5.23f, 5.02f, 5.83f, 4.792f, 6.485f)
            curveTo(4.667f, 6.844f, 4.585f, 7.215f, 4.534f, 7.59f)
            curveTo(4.493f, 7.899f, 4.466f, 8.214f, 4.476f, 8.523f)
            curveTo(4.489f, 8.896f, 4.5f, 9.247f, 4.554f, 9.608f)
            curveToRelative(0.053f, 0.351f, 0.094f, 0.705f, 0.173f, 1.05f)
            curveToRelative(0.224f, 0.986f, 0.553f, 1.939f, 0.979f, 2.857f)
            curveToRelative(0.372f, 0.8f, 0.804f, 1.568f, 1.286f, 2.306f)
            curveToRelative(0.486f, 0.744f, 1.013f, 1.456f, 1.578f, 2.142f)
            curveToRelative(0.512f, 0.622f, 1.04f, 1.227f, 1.612f, 1.794f)
            curveToRelative(0.213f, 0.211f, 0.498f, 0.207f, 0.713f, 0f)
            curveToRelative(0.572f, -0.553f, 1.391f, -1.519f, 1.779f, -1.994f)
            curveToRelative(0.359f, -0.44f, 0.7f, -0.895f, 1.023f, -1.362f)
            curveToRelative(0.415f, -0.599f, 0.801f, -1.215f, 1.149f, -1.857f)
            curveToRelative(0.391f, -0.722f, 0.745f, -1.459f, 1.021f, -2.232f)
            curveToRelative(0.245f, -0.686f, 0.436f, -1.384f, 0.564f, -2.103f)
            curveToRelative(0.095f, -0.533f, 0.146f, -1.069f, 0.177f, -1.608f)
            curveToRelative(0.018f, -0.318f, -0.021f, -0.634f, -0.057f, -0.95f)
            curveToRelative(-0.067f, -0.593f, -0.225f, -1.162f, -0.472f, -1.701f)
            curveToRelative(-0.351f, -0.765f, -0.828f, -1.44f, -1.457f, -2.009f)
            curveToRelative(-0.467f, -0.422f, -0.98f, -0.768f, -1.549f, -1.034f)
            curveTo(12.285f, 2.541f, 11.454f, 2.361f, 10.587f, 2.347f)
            close()

            moveTo(9.996f, 9.77f)
            curveToRelative(0f, 0.45f, 0.002f, 0.901f, -0.001f, 1.351f)
            curveToRelative(-0.001f, 0.22f, 0.093f, 0.321f, 0.3f, 0.301f)
            curveToRelative(0.159f, -0.016f, 0.321f, -0.003f, 0.481f, -0.003f)
            curveToRelative(0.261f, 0f, 0.307f, -0.045f, 0.307f, -0.299f)
            curveToRelative(0f, -0.88f, -0.021f, -1.761f, 0.006f, -2.64f)
            curveToRelative(0.025f, -0.776f, 0.368f, -1.402f, 1.054f, -1.797f)
            curveToRelative(0.712f, -0.409f, 1.461f, -0.417f, 2.192f, -0.073f)
            curveToRelative(0.613f, 0.289f, 0.976f, 0.801f, 1.13f, 1.466f)
            curveToRelative(0.132f, 0.572f, 0.019f, 1.122f, -0.117f, 1.671f)
            curveToRelative(-0.168f, 0.679f, -0.492f, 1.285f, -0.93f, 1.824f)
            curveToRelative(-0.663f, 0.816f, -1.504f, 1.364f, -2.522f, 1.655f)
            curveToRelative(-0.586f, 0.167f, -1.179f, 0.217f, -1.776f, 0.173f)
            curveToRelative(-0.928f, -0.068f, -1.769f, -0.395f, -2.524f, -0.946f)
            curveToRelative(-0.582f, -0.425f, -1.046f, -0.954f, -1.405f, -1.572f)
            curveToRelative(-0.268f, -0.461f, -0.449f, -0.961f, -0.543f, -1.488f)
            curveTo(5.597f, 9.108f, 5.547f, 8.821f, 5.555f, 8.529f)
            curveToRelative(0.025f, -1.004f, 0.706f, -1.934f, 1.758f, -2.115f)
            curveToRelative(0.6f, -0.104f, 1.164f, -0.029f, 1.683f, 0.286f)
            curveToRelative(0.501f, 0.304f, 0.805f, 0.759f, 0.938f, 1.329f)
            curveTo(9.978f, 8.219f, 10f, 8.41f, 9.997f, 8.605f)
            curveTo(9.992f, 8.993f, 9.996f, 9.382f, 9.996f, 9.77f)
            close()

            moveTo(13.312f, 6.635f)
            curveToRelative(-1.045f, -0.005f, -1.901f, 0.844f, -1.914f, 1.875f)
            curveToRelative(-0.013f, 1.078f, 0.847f, 1.94f, 1.897f, 1.937f)
            curveToRelative(1.079f, -0.003f, 1.899f, -0.827f, 1.912f, -1.889f)
            curveTo(15.22f, 7.485f, 14.361f, 6.651f, 13.312f, 6.635f)
            close()

            moveTo(9.685f, 8.528f)
            curveToRelative(-0.005f, -1.043f, -0.877f, -1.864f, -1.821f, -1.891f)
            curveToRelative(-1.163f, -0.033f, -1.99f, 0.879f, -1.992f, 1.889f)
            curveToRelative(-0.002f, 1.078f, 0.839f, 1.914f, 1.897f, 1.92f)
            curveTo(8.809f, 10.452f, 9.671f, 9.641f, 9.685f, 8.528f)
            close()

            moveTo(14.608f, 5.567f)
            curveToRelative(-0.175f, -0.019f, -0.332f, -0.088f, -0.5f, -0.123f)
            curveToRelative(-1.044f, -0.217f, -2.009f, -0.053f, -2.875f, 0.59f)
            curveToRelative(-0.236f, 0.175f, -0.438f, 0.387f, -0.596f, 0.633f)
            curveToRelative(-0.09f, 0.14f, -0.141f, 0.068f, -0.194f, -0.009f)
            curveToRelative(-0.106f, -0.152f, -0.225f, -0.291f, -0.36f, -0.417f)
            curveTo(9.576f, 5.768f, 8.978f, 5.485f, 8.29f, 5.393f)
            curveTo(7.752f, 5.321f, 7.221f, 5.349f, 6.702f, 5.516f)
            curveTo(6.64f, 5.536f, 6.579f, 5.559f, 6.517f, 5.578f)
            curveToRelative(-0.013f, 0.004f, -0.03f, -0.003f, -0.067f, -0.007f)
            curveToRelative(1.079f, -1.455f, 2.519f, -2.189f, 4.307f, -2.123f)
            curveTo(12.371f, 3.508f, 13.659f, 4.251f, 14.608f, 5.567f)
            close()

            moveTo(10.541f, 18.602f)
            curveToRelative(-0.348f, -0.372f, -0.681f, -0.739f, -0.997f, -1.122f)
            curveToRelative(-0.132f, -0.161f, -0.266f, -0.321f, -0.395f, -0.485f)
            curveToRelative(-0.045f, -0.057f, -0.053f, -0.085f, -0.005f, -0.152f)
            curveToRelative(0.424f, -0.592f, 0.825f, -1.199f, 1.187f, -1.831f)
            curveToRelative(0.08f, -0.14f, 0.163f, -0.28f, 0.235f, -0.424f)
            curveToRelative(0.035f, -0.069f, 0.075f, -0.093f, 0.153f, -0.096f)
            curveToRelative(0.959f, -0.046f, 1.871f, -0.271f, 2.713f, -0.742f)
            curveToRelative(0.299f, -0.167f, 0.589f, -0.35f, 0.872f, -0.552f)
            curveTo(13.381f, 15.226f, 12.067f, 16.981f, 10.541f, 18.602f)
            close()

            moveTo(6.774f, 13.199f)
            curveToRelative(0.383f, 0.264f, 0.769f, 0.511f, 1.186f, 0.709f)
            curveToRelative(0.44f, 0.208f, 0.896f, 0.367f, 1.374f, 0.455f)
            curveToRelative(0.131f, 0.024f, 0.115f, 0.065f, 0.068f, 0.141f)
            curveToRelative(-0.252f, 0.408f, -0.497f, 0.821f, -0.771f, 1.216f)
            curveToRelative(-0.069f, 0.099f, -0.138f, 0.197f, -0.216f, 0.309f)
            curveTo(7.778f, 15.129f, 7.222f, 14.198f, 6.774f, 13.199f)
            close()

            moveTo(13.298f, 7.722f)
            curveToRelative(0.432f, -0.059f, 0.85f, 0.37f, 0.837f, 0.84f)
            curveToRelative(-0.011f, 0.393f, -0.376f, 0.816f, -0.841f, 0.811f)
            curveToRelative(-0.431f, -0.004f, -0.832f, -0.405f, -0.822f, -0.862f)
            curveTo(12.481f, 8.1f, 12.886f, 7.664f, 13.298f, 7.722f)
            close()

            moveTo(8.598f, 8.527f)
            curveToRelative(0.048f, 0.457f, -0.399f, 0.851f, -0.826f, 0.847f)
            curveToRelative(-0.44f, -0.004f, -0.833f, -0.38f, -0.826f, -0.855f)
            curveToRelative(0.005f, -0.368f, 0.339f, -0.837f, 0.896f, -0.805f)
            curveTo(8.196f, 7.734f, 8.644f, 8.09f, 8.598f, 8.527f)
            close()

            moveTo(18.505f, 16.35f)
            curveToRelative(0.37f, 0f, 1.525f, 1.4f, 2.175f, 3f)
            curveToRelative(0.631f, 1.554f, -0.451f, 3.45f, -2.175f, 3.45f)
            curveToRelative(-1.662f, 0f, -2.74f, -1.663f, -2.183f, -3.449f)
            curveTo(16.747f, 17.989f, 18.156f, 16.35f, 18.505f, 16.35f)
            close()

            moveTo(18.018f, 18.712f)
            curveToRelative(-0.45f, -0.15f, -1.312f, 0.825f, -1.087f, 1.762f)
            curveToRelative(0.225f, 0.862f, 1.087f, 1.8f, 1.312f, 1.725f)
            curveToRelative(0.525f, -0.187f, -0.525f, -1.425f, -0.562f, -1.95f)
            curveTo(17.605f, 19.575f, 18.28f, 18.825f, 18.018f, 18.712f)
            close()
        }
    }.build()
}
