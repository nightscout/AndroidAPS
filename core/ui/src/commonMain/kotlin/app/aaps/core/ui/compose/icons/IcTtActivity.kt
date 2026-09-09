package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Activity Temp Target.
 *
 * replaces ic_target_activity
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.0 (viewport: 24x24, ~90% height)
 *
 * @see IcTtActivityIconPreview
 */
val IcTtActivity: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTtActivity",
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
            moveTo(19.392f, 17.722f)
            curveToRelative(1.246f, 0f, 2.26f, -1.068f, 2.26f, -2.382f)
            curveToRelative(0f, -1.314f, -1.014f, -2.382f, -2.26f, -2.382f)
            curveToRelative(-1.246f, 0f, -2.26f, 1.069f, -2.26f, 2.382f)
            curveTo(17.132f, 16.654f, 18.146f, 17.722f, 19.392f, 17.722f)
            close()

            moveTo(19.392f, 13.69f)
            curveToRelative(0.863f, 0f, 1.567f, 0.74f, 1.567f, 1.65f)
            curveToRelative(0f, 0.91f, -0.703f, 1.65f, -1.567f, 1.65f)
            curveToRelative(-0.863f, 0f, -1.567f, -0.74f, -1.567f, -1.65f)
            curveTo(17.826f, 14.43f, 18.529f, 13.69f, 19.392f, 13.69f)
            close()

            moveTo(22.732f, 20.5f)
            curveToRelative(-0.032f, -0.045f, -0.781f, -1.088f, -1.886f, -1.088f)
            curveToRelative(-0.822f, 0f, -1.356f, 0.539f, -1.828f, 1.015f)
            curveToRelative(-0.418f, 0.421f, -0.779f, 0.786f, -1.284f, 0.786f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-0.411f, 0f, -0.748f, -0.253f, -1.082f, -0.563f)
            lineToRelative(1.744f, -1.388f)
            lineToRelative(-4.768f, -6.657f)
            lineToRelative(-4.601f, 3.661f)
            curveToRelative(-0.271f, 0.253f, -0.656f, 0.911f, -0.172f, 1.587f)
            curveToRelative(0.224f, 0.313f, 0.485f, 0.406f, 0.665f, 0.429f)
            curveToRelative(0.428f, 0.055f, 0.773f, -0.228f, 0.801f, -0.251f)
            lineToRelative(2.889f, -2.298f)
            lineToRelative(0.708f, 0.988f)
            lineToRelative(-4.152f, 3.302f)
            curveToRelative(-0.389f, -0.368f, -0.828f, -0.667f, -1.367f, -0.667f)
            horizontalLineTo(8.395f)
            curveToRelative(-0.825f, 0f, -1.526f, 0.67f, -2.145f, 1.262f)
            curveToRelative(-0.369f, 0.353f, -0.788f, 0.754f, -1.019f, 0.754f)
            curveToRelative(-0.448f, 0f, -1.028f, -0.677f, -1.196f, -0.915f)
            curveToRelative(-0.114f, -0.161f, -0.331f, -0.195f, -0.486f, -0.075f)
            curveToRelative(-0.154f, 0.12f, -0.186f, 0.349f, -0.072f, 0.511f)
            curveToRelative(0.087f, 0.124f, 0.877f, 1.21f, 1.753f, 1.21f)
            curveToRelative(0.499f, 0f, 0.956f, -0.437f, 1.485f, -0.943f)
            curveToRelative(0.526f, -0.502f, 1.121f, -1.073f, 1.679f, -1.073f)
            horizontalLineToRelative(0f)
            curveToRelative(0.491f, 0f, 0.92f, 0.471f, 1.375f, 0.97f)
            curveToRelative(0.505f, 0.554f, 1.028f, 1.127f, 1.737f, 1.127f)
            curveToRelative(0.677f, 0f, 1.25f, -0.494f, 1.803f, -0.973f)
            curveToRelative(0.517f, -0.447f, 1.052f, -0.909f, 1.615f, -0.909f)
            curveToRelative(0.336f, 0.001f, 0.651f, 0.307f, 1.016f, 0.66f)
            curveToRelative(0.474f, 0.46f, 1.011f, 0.98f, 1.791f, 0.98f)
            horizontalLineToRelative(0.001f)
            curveToRelative(0.784f, 0f, 1.304f, -0.525f, 1.764f, -0.988f)
            curveToRelative(0.432f, -0.436f, 0.805f, -0.812f, 1.348f, -0.812f)
            curveToRelative(0.753f, 0f, 1.323f, 0.786f, 1.329f, 0.795f)
            curveToRelative(0.116f, 0.162f, 0.332f, 0.192f, 0.486f, 0.073f)
            curveTo(22.813f, 20.89f, 22.846f, 20.663f, 22.732f, 20.5f)
            close()

            moveTo(12.871f, 20.647f)
            curveToRelative(-0.48f, 0.415f, -0.934f, 0.807f, -1.363f, 0.807f)
            curveToRelative(-0.414f, -0.001f, -0.814f, -0.439f, -1.237f, -0.903f)
            curveToRelative(-0.002f, -0.003f, -0.005f, -0.005f, -0.007f, -0.008f)
            lineToRelative(4.624f, -3.678f)
            lineToRelative(-1.545f, -2.156f)
            lineTo(9.89f, 17.456f)
            curveToRelative(-0.023f, 0.02f, -0.169f, 0.125f, -0.289f, 0.101f)
            curveToRelative(-0.025f, -0.003f, -0.101f, -0.013f, -0.195f, -0.143f)
            curveToRelative(-0.204f, -0.286f, 0.011f, -0.533f, 0.058f, -0.581f)
            lineToRelative(4.026f, -3.202f)
            lineToRelative(3.931f, 5.489f)
            lineToRelative(-1.299f, 1.033f)
            curveToRelative(-0.348f, -0.315f, -0.723f, -0.58f, -1.197f, -0.58f)
            curveTo(14.115f, 19.572f, 13.455f, 20.143f, 12.871f, 20.647f)
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
            moveTo(4.835f, 9.393f)
            curveToRelative(-0.004f, 0f, -0.007f, 0f, -0.011f, 0f)
            curveTo(4.367f, 9.385f, 3.957f, 8.841f, 3.7f, 7.901f)
            curveToRelative(-0.187f, -0.686f, -0.357f, -1.4f, -0.521f, -2.092f)
            curveTo(3.076f, 5.376f, 2.974f, 4.943f, 2.866f, 4.514f)
            lineTo(2.795f, 4.225f)
            curveToRelative(-0.243f, -0.99f, -0.494f, -2.014f, -1.148f, -2.129f)
            curveTo(1.57f, 2.083f, 1.52f, 2.009f, 1.533f, 1.932f)
            curveToRelative(0.013f, -0.077f, 0.085f, -0.126f, 0.162f, -0.115f)
            curveToRelative(0.834f, 0.147f, 1.107f, 1.262f, 1.371f, 2.34f)
            lineToRelative(0.071f, 0.287f)
            curveToRelative(0.108f, 0.43f, 0.211f, 0.865f, 0.314f, 1.299f)
            curveTo(3.615f, 6.433f, 3.784f, 7.145f, 3.97f, 7.826f)
            curveTo(4.181f, 8.601f, 4.519f, 9.105f, 4.83f, 9.11f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.291f, 0f, 0.595f, -0.437f, 0.834f, -1.201f)
            curveToRelative(0.123f, -0.394f, 0.229f, -0.819f, 0.332f, -1.23f)
            lineToRelative(0.082f, -0.325f)
            curveToRelative(0.091f, -0.357f, 0.176f, -0.719f, 0.262f, -1.081f)
            curveTo(6.52f, 4.532f, 6.701f, 3.765f, 6.922f, 3.055f)
            curveToRelative(0.245f, -0.787f, 0.611f, -1.223f, 1.029f, -1.227f)
            curveToRelative(0.002f, 0f, 0.004f, 0f, 0.005f, 0f)
            curveToRelative(0.42f, 0f, 0.795f, 0.433f, 1.054f, 1.22f)
            curveTo(9.194f, 3.602f, 9.349f, 4.2f, 9.498f, 4.778f)
            lineToRelative(0.116f, 0.447f)
            curveToRelative(0.097f, 0.368f, 0.189f, 0.741f, 0.283f, 1.113f)
            curveToRelative(0.158f, 0.637f, 0.322f, 1.296f, 0.506f, 1.917f)
            curveToRelative(0.151f, 0.513f, 0.407f, 0.839f, 0.669f, 0.853f)
            curveToRelative(0.234f, 0.029f, 0.479f, -0.241f, 0.665f, -0.693f)
            curveToRelative(0.179f, -0.434f, 0.342f, -0.939f, 0.5f, -1.544f)
            curveToRelative(0.219f, -0.842f, 0.427f, -1.696f, 0.634f, -2.55f)
            curveToRelative(0.096f, -0.394f, 0.191f, -0.788f, 0.288f, -1.181f)
            curveToRelative(0.185f, -0.75f, 0.534f, -1.181f, 1.066f, -1.321f)
            curveToRelative(0.073f, -0.019f, 0.15f, 0.026f, 0.17f, 0.102f)
            curveToRelative(0.019f, 0.076f, -0.026f, 0.153f, -0.101f, 0.173f)
            curveToRelative(-0.429f, 0.112f, -0.703f, 0.466f, -0.864f, 1.115f)
            curveToRelative(-0.097f, 0.393f, -0.193f, 0.786f, -0.288f, 1.18f)
            curveToRelative(-0.208f, 0.855f, -0.416f, 1.711f, -0.635f, 2.555f)
            curveToRelative(-0.161f, 0.618f, -0.329f, 1.135f, -0.513f, 1.581f)
            curveToRelative(-0.321f, 0.779f, -0.721f, 0.879f, -0.938f, 0.867f)
            curveToRelative(-0.388f, -0.02f, -0.733f, -0.414f, -0.922f, -1.055f)
            curveTo(9.949f, 7.709f, 9.785f, 7.047f, 9.625f, 6.407f)
            curveToRelative(-0.092f, -0.371f, -0.184f, -0.742f, -0.281f, -1.11f)
            lineTo(9.228f, 4.849f)
            curveTo(9.08f, 4.276f, 8.926f, 3.683f, 8.747f, 3.138f)
            curveToRelative(-0.213f, -0.643f, -0.508f, -1.026f, -0.79f, -1.026f)
            curveToRelative(-0.001f, 0f, -0.002f, 0f, -0.002f, 0f)
            curveTo(7.675f, 2.114f, 7.389f, 2.498f, 7.189f, 3.14f)
            curveToRelative(-0.218f, 0.701f, -0.398f, 1.463f, -0.572f, 2.2f)
            curveTo(6.531f, 5.703f, 6.444f, 6.067f, 6.354f, 6.425f)
            lineTo(6.273f, 6.749f)
            curveTo(6.169f, 7.164f, 6.062f, 7.593f, 5.936f, 7.995f)
            curveTo(5.575f, 9.148f, 5.14f, 9.393f, 4.835f, 9.393f)
            close()

            moveTo(14.255f, 6.082f)
            horizontalLineToRelative(-3.139f)
            curveToRelative(-0.258f, 0f, -0.466f, -0.214f, -0.466f, -0.478f)
            verticalLineTo(3.7f)
            horizontalLineTo(5.281f)
            lineToRelative(0f, 1.904f)
            curveToRelative(0f, 0.264f, -0.209f, 0.478f, -0.466f, 0.478f)
            horizontalLineTo(1.666f)
            curveTo(1.408f, 6.082f, 1.2f, 5.868f, 1.2f, 5.604f)
            curveToRelative(0f, -0.264f, 0.208f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(2.683f)
            lineToRelative(0f, -1.904f)
            curveToRelative(0f, -0.264f, 0.209f, -0.478f, 0.466f, -0.478f)
            horizontalLineToRelative(6.3f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            verticalLineToRelative(1.904f)
            horizontalLineToRelative(2.673f)
            curveToRelative(0.258f, 0f, 0.466f, 0.214f, 0.466f, 0.478f)
            curveTo(14.721f, 5.868f, 14.512f, 6.082f, 14.255f, 6.082f)
            close()
        }
    }.build()
}
