package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Syai CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginSyaiIconPreview
 */
val IcPluginSyai: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginSyai",
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
            // Outer circle
            moveTo(12f, 1.2f)
            curveTo(6.035f, 1.2f, 1.2f, 6.035f, 1.2f, 12f)
            reflectiveCurveTo(6.035f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(22.8f, 17.965f, 22.8f, 12f)
            reflectiveCurveTo(17.965f, 1.2f, 12f, 1.2f)
            close()

            // Inner shape
            moveTo(16.408f, 12.511f)
            curveToRelative(-0.068f, 0.229f, -0.179f, 0.441f, -0.329f, 0.621f)
            curveToRelative(-0.15f, 0.181f, -0.333f, 0.326f, -0.54f, 0.426f)
            curveToRelative(-0.207f, 0.1f, -0.432f, 0.152f, -0.659f, 0.152f)
            horizontalLineToRelative(-0.011f)
            curveToRelative(-0.26f, -0.004f, -0.518f, 0.047f, -0.76f, 0.152f)
            curveToRelative(-0.242f, 0.104f, -0.463f, 0.26f, -0.65f, 0.457f)
            lineToRelative(-0.476f, 0.513f)
            curveToRelative(-0.185f, 0.194f, -0.331f, 0.428f, -0.43f, 0.687f)
            curveToRelative(-0.099f, 0.259f, -0.147f, 0.537f, -0.143f, 0.817f)
            curveToRelative(0.001f, 0.06f, 0f, 0.121f, -0.004f, 0.182f)
            curveToRelative(-0.023f, 0.324f, -0.132f, 0.637f, -0.316f, 0.898f)
            curveToRelative(-0.183f, 0.262f, -0.434f, 0.463f, -0.723f, 0.578f)
            curveToRelative(-0.289f, 0.115f, -0.603f, 0.139f, -0.905f, 0.068f)
            curveToRelative(-0.302f, -0.071f, -0.576f, -0.234f, -0.792f, -0.466f)
            curveToRelative(-0.216f, -0.233f, -0.364f, -0.525f, -0.429f, -0.843f)
            curveToRelative(-0.064f, -0.318f, -0.043f, -0.649f, 0.062f, -0.955f)
            curveToRelative(0.105f, -0.306f, 0.29f, -0.575f, 0.534f, -0.774f)
            curveToRelative(0.244f, -0.199f, 0.538f, -0.319f, 0.846f, -0.345f)
            lineToRelative(0.005f, -0.001f)
            curveToRelative(0.055f, -0.003f, 0.11f, -0.004f, 0.164f, -0.004f)
            horizontalLineToRelative(0.003f)
            curveToRelative(0.255f, 0.005f, 0.509f, -0.047f, 0.747f, -0.152f)
            curveToRelative(0.237f, -0.105f, 0.453f, -0.262f, 0.633f, -0.461f)
            lineToRelative(0.001f, -0.002f)
            lineToRelative(0.318f, -0.343f)
            curveToRelative(0.197f, -0.213f, 0.231f, -0.505f, 0.14f, -0.75f)
            curveToRelative(-0.091f, -0.244f, -0.296f, -0.417f, -0.561f, -0.42f)
            horizontalLineToRelative(-0.25f)
            curveToRelative(-0.619f, 0.001f, -1.217f, 0.253f, -1.676f, 0.709f)
            curveToRelative(-0.185f, 0.183f, -0.409f, 0.317f, -0.653f, 0.39f)
            curveToRelative(-0.244f, 0.073f, -0.501f, 0.084f, -0.75f, 0.03f)
            curveToRelative(-0.249f, -0.053f, -0.481f, -0.169f, -0.679f, -0.336f)
            curveToRelative(-0.198f, -0.167f, -0.355f, -0.382f, -0.46f, -0.625f)
            curveToRelative(-0.105f, -0.243f, -0.155f, -0.509f, -0.147f, -0.776f)
            curveToRelative(0.008f, -0.267f, 0.075f, -0.529f, 0.195f, -0.764f)
            curveToRelative(0.12f, -0.236f, 0.291f, -0.438f, 0.498f, -0.591f)
            curveToRelative(0.208f, -0.153f, 0.447f, -0.252f, 0.699f, -0.287f)
            lineToRelative(0.003f, 0f)
            lineToRelative(0.015f, -0.002f)
            curveToRelative(0.035f, -0.005f, 0.081f, -0.01f, 0.127f, -0.011f)
            curveToRelative(0.623f, -0.03f, 1.214f, -0.311f, 1.658f, -0.791f)
            lineToRelative(0.14f, -0.151f)
            curveToRelative(0.447f, -0.478f, 0.713f, -1.122f, 0.742f, -1.804f)
            verticalLineToRelative(-0.461f)
            curveToRelative(0.024f, -0.324f, 0.134f, -0.635f, 0.318f, -0.896f)
            curveToRelative(0.184f, -0.261f, 0.434f, -0.462f, 0.723f, -0.576f)
            curveToRelative(0.289f, -0.114f, 0.603f, -0.138f, 0.905f, -0.066f)
            curveToRelative(0.301f, 0.072f, 0.575f, 0.235f, 0.791f, 0.467f)
            curveToRelative(0.215f, 0.233f, 0.363f, 0.525f, 0.428f, 0.843f)
            curveToRelative(0.064f, 0.318f, 0.043f, 0.649f, -0.062f, 0.955f)
            curveToRelative(-0.105f, 0.306f, -0.29f, 0.574f, -0.534f, 0.773f)
            curveToRelative(-0.244f, 0.198f, -0.538f, 0.319f, -0.846f, 0.344f)
            lineToRelative(-0.014f, 0.001f)
            lineToRelative(-0.045f, -0.001f)
            curveToRelative(-0.623f, 0.03f, -1.214f, 0.311f, -1.658f, 0.791f)
            lineToRelative(-0.154f, 0.165f)
            curveToRelative(-0.197f, 0.213f, -0.231f, 0.506f, -0.14f, 0.75f)
            curveToRelative(0.091f, 0.244f, 0.296f, 0.418f, 0.561f, 0.42f)
            horizontalLineToRelative(0.249f)
            curveToRelative(0.625f, -0.008f, 1.227f, -0.264f, 1.691f, -0.723f)
            lineToRelative(0.001f, -0.001f)
            curveToRelative(0.219f, -0.21f, 0.488f, -0.352f, 0.779f, -0.41f)
            curveToRelative(0.291f, -0.058f, 0.591f, -0.028f, 0.867f, 0.085f)
            curveToRelative(0.276f, 0.113f, 0.515f, 0.305f, 0.694f, 0.553f)
            curveToRelative(0.179f, 0.248f, 0.291f, 0.543f, 0.324f, 0.853f)
            horizontalLineToRelative(-0.002f)
            curveToRelative(-0.013f, 0.254f, -0.035f, 0.495f, -0.102f, 0.723f)
            close()
        }
    }.build()
}
