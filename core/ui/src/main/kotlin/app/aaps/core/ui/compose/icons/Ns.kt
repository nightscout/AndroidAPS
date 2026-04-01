package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for NSClient background service.
 * Represents the Nightscout client connection status.
 *
 * Bounding box: x: 4.0-19.9 y: 1.1-22.5 (viewport: 24x24, ~89% height)
 */
val Ns: ImageVector by lazy {
    ImageVector.Builder(
        name = "NsClient",
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
            moveTo(19.937f, 8.998f)
            curveToRelative(0f, 0.712f, -0.092f, 1.6f, -0.132f, 1.889f)
            curveToRelative(-0.115f, 0.816f, -0.32f, 1.61f, -0.578f, 2.391f)
            curveToRelative(-0.335f, 1.015f, -0.758f, 1.992f, -1.27f, 2.931f)
            curveToRelative(-0.314f, 0.576f, -0.643f, 1.144f, -1.002f, 1.692f)
            curveToRelative(-0.388f, 0.592f, -0.799f, 1.166f, -1.236f, 1.723f)
            curveToRelative(-0.415f, 0.529f, -0.837f, 1.05f, -1.282f, 1.555f)
            curveToRelative(-0.145f, 0.164f, -0.298f, 0.322f, -0.444f, 0.485f)
            curveToRelative(-0.283f, 0.316f, -0.531f, 0.575f, -0.858f, 0.849f)
            curveToRelative(-0.712f, 0.67f, -1.759f, 0.616f, -2.378f, -0.081f)
            curveToRelative(-0.469f, -0.458f, -0.911f, -0.94f, -1.341f, -1.436f)
            curveToRelative(-0.531f, -0.613f, -1.037f, -1.245f, -1.521f, -1.896f)
            curveToRelative(-0.718f, -0.966f, -1.362f, -1.978f, -1.933f, -3.036f)
            curveToRelative(-0.41f, -0.758f, -0.755f, -1.549f, -1.041f, -2.362f)
            curveToRelative(-0.386f, -1.097f, -0.69f, -2.215f, -0.799f, -3.381f)
            curveToRelative(-0.013f, -0.143f, -0.059f, -0.71f, -0.059f, -1.319f)
            curveToRelative(0f, -0.647f, 0.067f, -1.158f, 0.096f, -1.358f)
            curveTo(4.27f, 6.89f, 4.52f, 6.182f, 4.846f, 5.499f)
            curveTo(5.113f, 4.94f, 5.45f, 4.426f, 5.845f, 3.944f)
            curveTo(6.226f, 3.479f, 6.65f, 3.061f, 7.12f, 2.693f)
            curveTo(7.636f, 2.288f, 8.198f, 1.95f, 8.805f, 1.69f)
            curveToRelative(0.667f, -0.285f, 1.353f, -0.494f, 2.075f, -0.586f)
            curveToRelative(0.127f, -0.016f, 0.598f, -0.062f, 1.166f, -0.062f)
            curveToRelative(0.728f, 0f, 1.44f, 0.108f, 1.764f, 0.184f)
            curveToRelative(0.732f, 0.172f, 1.428f, 0.44f, 2.086f, 0.812f)
            curveToRelative(0.93f, 0.526f, 1.725f, 1.21f, 2.383f, 2.046f)
            curveToRelative(0.473f, 0.601f, 0.853f, 1.26f, 1.128f, 1.976f)
            curveToRelative(0.193f, 0.503f, 0.347f, 1.016f, 0.434f, 1.551f)
            curveTo(19.871f, 7.8f, 19.937f, 8.371f, 19.937f, 8.998f)
            close()

            moveTo(12.052f, 2.302f)
            curveToRelative(-0.311f, -0.005f, -0.618f, 0.035f, -0.926f, 0.068f)
            curveTo(10.484f, 2.439f, 9.876f, 2.628f, 9.29f, 2.888f)
            curveToRelative(-1.007f, 0.447f, -1.839f, 1.12f, -2.525f, 1.983f)
            curveToRelative(-0.477f, 0.6f, -0.832f, 1.261f, -1.083f, 1.98f)
            curveTo(5.545f, 7.245f, 5.455f, 7.654f, 5.399f, 8.066f)
            curveTo(5.354f, 8.405f, 5.324f, 8.752f, 5.336f, 9.091f)
            curveToRelative(0.014f, 0.41f, 0.027f, 0.796f, 0.086f, 1.192f)
            curveToRelative(0.058f, 0.386f, 0.103f, 0.775f, 0.19f, 1.154f)
            curveToRelative(0.247f, 1.084f, 0.608f, 2.131f, 1.076f, 3.14f)
            curveToRelative(0.409f, 0.88f, 0.883f, 1.723f, 1.413f, 2.535f)
            curveToRelative(0.534f, 0.817f, 1.113f, 1.601f, 1.734f, 2.355f)
            curveToRelative(0.563f, 0.683f, 1.143f, 1.349f, 1.772f, 1.972f)
            curveToRelative(0.234f, 0.232f, 0.548f, 0.228f, 0.784f, 0f)
            curveToRelative(0.628f, -0.608f, 1.529f, -1.669f, 1.956f, -2.191f)
            curveToRelative(0.395f, -0.484f, 0.769f, -0.983f, 1.125f, -1.497f)
            curveToRelative(0.456f, -0.659f, 0.881f, -1.336f, 1.263f, -2.041f)
            curveToRelative(0.43f, -0.793f, 0.818f, -1.604f, 1.123f, -2.454f)
            curveToRelative(0.27f, -0.754f, 0.479f, -1.521f, 0.62f, -2.311f)
            curveToRelative(0.105f, -0.586f, 0.16f, -1.175f, 0.194f, -1.767f)
            curveToRelative(0.02f, -0.349f, -0.023f, -0.696f, -0.063f, -1.044f)
            curveToRelative(-0.074f, -0.652f, -0.247f, -1.277f, -0.519f, -1.87f)
            curveToRelative(-0.386f, -0.841f, -0.91f, -1.583f, -1.602f, -2.208f)
            curveToRelative(-0.514f, -0.464f, -1.077f, -0.844f, -1.703f, -1.136f)
            curveTo(13.919f, 2.516f, 13.006f, 2.318f, 12.052f, 2.302f)
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
            moveTo(11.403f, 10.462f)
            curveToRelative(0f, 0.495f, 0.002f, 0.99f, -0.001f, 1.485f)
            curveToRelative(-0.001f, 0.242f, 0.102f, 0.353f, 0.33f, 0.33f)
            curveToRelative(0.175f, -0.017f, 0.352f, -0.003f, 0.529f, -0.003f)
            curveToRelative(0.287f, 0f, 0.337f, -0.049f, 0.337f, -0.329f)
            curveToRelative(0f, -0.967f, -0.024f, -1.935f, 0.007f, -2.901f)
            curveToRelative(0.027f, -0.853f, 0.404f, -1.541f, 1.159f, -1.975f)
            curveToRelative(0.782f, -0.449f, 1.606f, -0.458f, 2.409f, -0.08f)
            curveToRelative(0.674f, 0.317f, 1.073f, 0.88f, 1.242f, 1.612f)
            curveToRelative(0.145f, 0.629f, 0.02f, 1.233f, -0.129f, 1.837f)
            curveToRelative(-0.184f, 0.747f, -0.541f, 1.413f, -1.022f, 2.005f)
            curveToRelative(-0.729f, 0.896f, -1.653f, 1.5f, -2.772f, 1.819f)
            curveToRelative(-0.644f, 0.184f, -1.296f, 0.239f, -1.952f, 0.191f)
            curveToRelative(-1.02f, -0.075f, -1.944f, -0.434f, -2.775f, -1.04f)
            curveToRelative(-0.64f, -0.467f, -1.149f, -1.049f, -1.544f, -1.727f)
            curveToRelative(-0.295f, -0.506f, -0.493f, -1.056f, -0.597f, -1.636f)
            curveToRelative(-0.056f, -0.315f, -0.111f, -0.63f, -0.102f, -0.951f)
            curveToRelative(0.028f, -1.103f, 0.777f, -2.126f, 1.932f, -2.325f)
            curveToRelative(0.66f, -0.114f, 1.28f, -0.031f, 1.85f, 0.314f)
            curveToRelative(0.551f, 0.334f, 0.885f, 0.834f, 1.031f, 1.461f)
            curveToRelative(0.048f, 0.208f, 0.073f, 0.418f, 0.07f, 0.633f)
            curveTo(11.398f, 9.608f, 11.403f, 10.035f, 11.403f, 10.462f)
            close()

            moveTo(15.047f, 7.016f)
            curveToRelative(-1.149f, -0.005f, -2.089f, 0.928f, -2.103f, 2.061f)
            curveToRelative(-0.015f, 1.185f, 0.931f, 2.132f, 2.085f, 2.129f)
            curveToRelative(1.187f, -0.004f, 2.088f, -0.909f, 2.101f, -2.077f)
            curveTo(17.145f, 7.951f, 16.2f, 7.034f, 15.047f, 7.016f)
            close()

            moveTo(11.061f, 9.097f)
            curveToRelative(-0.006f, -1.146f, -0.964f, -2.049f, -2.002f, -2.079f)
            curveTo(7.781f, 6.982f, 6.872f, 7.985f, 6.87f, 9.095f)
            curveToRelative(-0.002f, 1.185f, 0.922f, 2.103f, 2.085f, 2.11f)
            curveTo(10.098f, 11.211f, 11.045f, 10.32f, 11.061f, 9.097f)
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
            moveTo(16.472f, 5.843f)
            curveToRelative(-0.192f, -0.021f, -0.365f, -0.097f, -0.549f, -0.135f)
            curveToRelative(-1.148f, -0.239f, -2.208f, -0.059f, -3.16f, 0.649f)
            curveToRelative(-0.259f, 0.193f, -0.482f, 0.425f, -0.656f, 0.695f)
            curveToRelative(-0.099f, 0.154f, -0.155f, 0.074f, -0.213f, -0.009f)
            curveToRelative(-0.116f, -0.167f, -0.248f, -0.32f, -0.396f, -0.458f)
            curveToRelative(-0.558f, -0.521f, -1.215f, -0.832f, -1.972f, -0.934f)
            curveTo(8.936f, 5.572f, 8.353f, 5.602f, 7.782f, 5.786f)
            curveTo(7.714f, 5.808f, 7.647f, 5.834f, 7.579f, 5.855f)
            curveTo(7.564f, 5.859f, 7.545f, 5.852f, 7.504f, 5.847f)
            curveToRelative(1.186f, -1.6f, 2.768f, -2.406f, 4.734f, -2.334f)
            curveTo(14.013f, 3.579f, 15.429f, 4.395f, 16.472f, 5.843f)
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
            moveTo(12.002f, 20.169f)
            curveToRelative(-0.383f, -0.408f, -0.749f, -0.812f, -1.096f, -1.233f)
            curveToRelative(-0.146f, -0.177f, -0.292f, -0.353f, -0.434f, -0.533f)
            curveToRelative(-0.05f, -0.063f, -0.058f, -0.094f, -0.006f, -0.167f)
            curveToRelative(0.466f, -0.65f, 0.907f, -1.317f, 1.304f, -2.013f)
            curveToRelative(0.088f, -0.154f, 0.179f, -0.307f, 0.259f, -0.466f)
            curveToRelative(0.038f, -0.076f, 0.083f, -0.102f, 0.168f, -0.106f)
            curveToRelative(1.054f, -0.05f, 2.056f, -0.298f, 2.982f, -0.816f)
            curveToRelative(0.329f, -0.184f, 0.647f, -0.385f, 0.959f, -0.607f)
            curveTo(15.124f, 16.459f, 13.679f, 18.388f, 12.002f, 20.169f)
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
            moveTo(7.861f, 14.231f)
            curveToRelative(0.421f, 0.291f, 0.845f, 0.562f, 1.303f, 0.779f)
            curveToRelative(0.483f, 0.229f, 0.985f, 0.403f, 1.51f, 0.5f)
            curveToRelative(0.144f, 0.027f, 0.126f, 0.072f, 0.075f, 0.155f)
            curveToRelative(-0.277f, 0.449f, -0.546f, 0.903f, -0.848f, 1.336f)
            curveToRelative(-0.076f, 0.109f, -0.152f, 0.217f, -0.238f, 0.34f)
            curveTo(8.965f, 16.352f, 8.354f, 15.329f, 7.861f, 14.231f)
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
            moveTo(15.032f, 8.211f)
            curveToRelative(0.475f, -0.065f, 0.935f, 0.407f, 0.92f, 0.924f)
            curveToRelative(-0.013f, 0.432f, -0.413f, 0.897f, -0.925f, 0.891f)
            curveToRelative(-0.474f, -0.005f, -0.915f, -0.445f, -0.903f, -0.948f)
            curveTo(14.134f, 8.626f, 14.579f, 8.147f, 15.032f, 8.211f)
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
            moveTo(9.866f, 9.095f)
            curveToRelative(0.053f, 0.502f, -0.439f, 0.935f, -0.908f, 0.931f)
            curveToRelative(-0.483f, -0.005f, -0.915f, -0.417f, -0.908f, -0.94f)
            curveToRelative(0.006f, -0.404f, 0.373f, -0.92f, 0.985f, -0.884f)
            curveTo(9.424f, 8.224f, 9.917f, 8.615f, 9.866f, 9.095f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun nsIconPreview() {
    Icon(
        imageVector = Ns,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier
            .padding(0.dp)
            .size(96.dp)
    )
}

/* svg source file

<?xml version="1.0" encoding="utf-8"?>
<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" width="24px"
	 height="24px" viewBox="0 0 24 24" enable-background="new 0 0 24 24" xml:space="preserve">
<g id="ic_notif_nsclient">
    <path fill="#FFFFFF" d="M19.937,8.998c0,0.712-0.092,1.6-0.132,1.889c-0.115,0.816-0.32,1.61-0.578,2.391
        c-0.335,1.015-0.758,1.992-1.27,2.931c-0.314,0.576-0.643,1.144-1.002,1.692c-0.388,0.592-0.799,1.166-1.236,1.723
        c-0.415,0.529-0.837,1.05-1.282,1.555c-0.145,0.164-0.298,0.322-0.444,0.485c-0.283,0.316-0.531,0.575-0.858,0.849
        c-0.712,0.67-1.759,0.616-2.378-0.081c-0.469-0.458-0.911-0.94-1.341-1.436c-0.531-0.613-1.037-1.245-1.521-1.896
        c-0.718-0.966-1.362-1.978-1.933-3.036c-0.41-0.758-0.755-1.549-1.041-2.362c-0.386-1.097-0.69-2.215-0.799-3.381
        c-0.013-0.143-0.059-0.71-0.059-1.319c0-0.647,0.067-1.158,0.096-1.358C4.27,6.89,4.52,6.182,4.846,5.499
        C5.113,4.94,5.45,4.426,5.845,3.944C6.226,3.479,6.65,3.061,7.12,2.693C7.636,2.288,8.198,1.95,8.805,1.69
        c0.667-0.285,1.353-0.494,2.075-0.586c0.127-0.016,0.598-0.062,1.166-0.062c0.728,0,1.44,0.108,1.764,0.184
        c0.732,0.172,1.428,0.44,2.086,0.812c0.93,0.526,1.725,1.21,2.383,2.046c0.473,0.601,0.853,1.26,1.128,1.976
        c0.193,0.503,0.347,1.016,0.434,1.551C19.871,7.8,19.937,8.371,19.937,8.998z M12.052,2.302c-0.311-0.005-0.618,0.035-0.926,0.068
        C10.484,2.439,9.876,2.628,9.29,2.888c-1.007,0.447-1.839,1.12-2.525,1.983c-0.477,0.6-0.832,1.261-1.083,1.98
        C5.545,7.245,5.455,7.654,5.399,8.066C5.354,8.405,5.324,8.752,5.336,9.091c0.014,0.41,0.027,0.796,0.086,1.192
        c0.058,0.386,0.103,0.775,0.19,1.154c0.247,1.084,0.608,2.131,1.076,3.14c0.409,0.88,0.883,1.723,1.413,2.535
        c0.534,0.817,1.113,1.601,1.734,2.355c0.563,0.683,1.143,1.349,1.772,1.972c0.234,0.232,0.548,0.228,0.784,0
        c0.628-0.608,1.529-1.669,1.956-2.191c0.395-0.484,0.769-0.983,1.125-1.497c0.456-0.659,0.881-1.336,1.263-2.041
        c0.43-0.793,0.818-1.604,1.123-2.454c0.27-0.754,0.479-1.521,0.62-2.311c0.105-0.586,0.16-1.175,0.194-1.767
        c0.02-0.349-0.023-0.696-0.063-1.044c-0.074-0.652-0.247-1.277-0.519-1.87c-0.386-0.841-0.91-1.583-1.602-2.208
        c-0.514-0.464-1.077-0.844-1.703-1.136C13.919,2.516,13.006,2.318,12.052,2.302z"/>
    <path fill="#FFFFFF" d="M11.403,10.462c0,0.495,0.002,0.99-0.001,1.485c-0.001,0.242,0.102,0.353,0.33,0.33
        c0.175-0.017,0.352-0.003,0.529-0.003c0.287,0,0.337-0.049,0.337-0.329c0-0.967-0.024-1.935,0.007-2.901
        c0.027-0.853,0.404-1.541,1.159-1.975c0.782-0.449,1.606-0.458,2.409-0.08c0.674,0.317,1.073,0.88,1.242,1.612
        c0.145,0.629,0.02,1.233-0.129,1.837c-0.184,0.747-0.541,1.413-1.022,2.005c-0.729,0.896-1.653,1.5-2.772,1.819
        c-0.644,0.184-1.296,0.239-1.952,0.191c-1.02-0.075-1.944-0.434-2.775-1.04c-0.64-0.467-1.149-1.049-1.544-1.727
        c-0.295-0.506-0.493-1.056-0.597-1.636c-0.056-0.315-0.111-0.63-0.102-0.951c0.028-1.103,0.777-2.126,1.932-2.325
        c0.66-0.114,1.28-0.031,1.85,0.314c0.551,0.334,0.885,0.834,1.031,1.461c0.048,0.208,0.073,0.418,0.07,0.633
        C11.398,9.608,11.403,10.035,11.403,10.462z M15.047,7.016c-1.149-0.005-2.089,0.928-2.103,2.061
        c-0.015,1.185,0.931,2.132,2.085,2.129c1.187-0.004,2.088-0.909,2.101-2.077C17.145,7.951,16.2,7.034,15.047,7.016z M11.061,9.097
        c-0.006-1.146-0.964-2.049-2.002-2.079C7.781,6.982,6.872,7.985,6.87,9.095c-0.002,1.185,0.922,2.103,2.085,2.11
        C10.098,11.211,11.045,10.32,11.061,9.097z"/>
    <path fill="#FFFFFF" d="M16.472,5.843c-0.192-0.021-0.365-0.097-0.549-0.135c-1.148-0.239-2.208-0.059-3.16,0.649
        c-0.259,0.193-0.482,0.425-0.656,0.695c-0.099,0.154-0.155,0.074-0.213-0.009c-0.116-0.167-0.248-0.32-0.396-0.458
        c-0.558-0.521-1.215-0.832-1.972-0.934C8.936,5.572,8.353,5.602,7.782,5.786C7.714,5.808,7.647,5.834,7.579,5.855
        C7.564,5.859,7.545,5.852,7.504,5.847c1.186-1.6,2.768-2.406,4.734-2.334C14.013,3.579,15.429,4.395,16.472,5.843z"/>
    <path fill="#FFFFFF" d="M12.002,20.169c-0.383-0.408-0.749-0.812-1.096-1.233c-0.146-0.177-0.292-0.353-0.434-0.533
        c-0.05-0.063-0.058-0.094-0.006-0.167c0.466-0.65,0.907-1.317,1.304-2.013c0.088-0.154,0.179-0.307,0.259-0.466
        c0.038-0.076,0.083-0.102,0.168-0.106c1.054-0.05,2.056-0.298,2.982-0.816c0.329-0.184,0.647-0.385,0.959-0.607
        C15.124,16.459,13.679,18.388,12.002,20.169z"/>
    <path fill="#FFFFFF" d="M7.861,14.231c0.421,0.291,0.845,0.562,1.303,0.779c0.483,0.229,0.985,0.403,1.51,0.5
        c0.144,0.027,0.126,0.072,0.075,0.155c-0.277,0.449-0.546,0.903-0.848,1.336c-0.076,0.109-0.152,0.217-0.238,0.34
        C8.965,16.352,8.354,15.329,7.861,14.231z"/>
    <path fill="#FFFFFF" d="M15.032,8.211c0.475-0.065,0.935,0.407,0.92,0.924c-0.013,0.432-0.413,0.897-0.925,0.891
        c-0.474-0.005-0.915-0.445-0.903-0.948C14.134,8.626,14.579,8.147,15.032,8.211z"/>
    <path fill="#FFFFFF" d="M9.866,9.095c0.053,0.502-0.439,0.935-0.908,0.931c-0.483-0.005-0.915-0.417-0.908-0.94
        c0.006-0.404,0.373-0.92,0.985-0.884C9.424,8.224,9.917,8.615,9.866,9.095z"/>
</g>
</svg>
 */