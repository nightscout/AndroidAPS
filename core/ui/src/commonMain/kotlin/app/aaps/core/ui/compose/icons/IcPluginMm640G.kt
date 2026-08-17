package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for MM640G CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginMM640GPreview
 */
val IcPluginMM640G: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginMM640G",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // First path (opacity 0.3)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.3f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.216f, 6.125f)
            curveToRelative(-0.11f, -0.305f, -0.203f, -0.563f, -0.203f, -0.6f)
            lineToRelative(-0.765f, -2.428f)
            curveToRelative(0f, -0.098f, -0.167f, -0.177f, -0.265f, -0.177f)
            horizontalLineTo(12f)
            horizontalLineToRelative(-0.983f)
            curveToRelative(-0.098f, 0f, -0.265f, 0.079f, -0.265f, 0.177f)
            lineTo(9.987f, 5.525f)
            curveToRelative(0f, 0.037f, -0.093f, 0.295f, -0.203f, 0.6f)
            curveToRelative(-0.039f, 0.11f, -0.35f, 0.035f, -0.35f, 0.156f)
            curveToRelative(0f, 0.545f, 0f, 0.863f, 0f, 1.01f)
            horizontalLineTo(12f)
            horizontalLineToRelative(2.565f)
            curveToRelative(0f, -0.147f, 0f, -0.465f, 0f, -1.01f)
            curveToRelative(0f, -0.121f, -0.31f, -0.046f, -0.349f, -0.156f)
            close()
        }

        // Main path inside group (no explicit opacity, so inherits group opacity 0.5)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(17.194f, 3.731f)
            curveTo(17.194f, 2.219f, 14.869f, 1.2f, 12f, 1.2f)
            reflectiveCurveTo(6.806f, 2.219f, 6.806f, 3.731f)
            curveToRelative(0f, 0.397f, 0.331f, 0.375f, 0.331f, 0.589f)
            curveToRelative(0f, 0.313f, -0.228f, 0.397f, -0.228f, 0.647f)
            curveToRelative(0f, 0.25f, 0.324f, 0.249f, 0.324f, 0.471f)
            curveToRelative(0f, 0.186f, -0.184f, 0.287f, -0.184f, 0.53f)
            curveToRelative(0f, 0.309f, 0.272f, 0.383f, 0.272f, 0.633f)
            curveToRelative(0f, 0.221f, -0.177f, 0.206f, -0.177f, 0.424f)
            curveToRelative(0f, 0.216f, 0.045f, 0.268f, 0.191f, 0.268f)
            horizontalLineToRelative(1.739f)
            verticalLineTo(6.078f)
            curveToRelative(0f, -0.09f, 0.068f, -0.161f, 0.154f, -0.172f)
            verticalLineTo(4.22f)
            lineTo(8.147f, 3.475f)
            curveToRelative(-0.025f, -0.094f, 0.047f, -0.196f, 0.162f, -0.228f)
            lineToRelative(0.848f, -0.231f)
            curveToRelative(0.115f, -0.031f, 0.229f, 0.02f, 0.255f, 0.114f)
            lineToRelative(0.105f, 0.383f)
            curveToRelative(0.011f, 0.041f, 0.065f, 0.253f, 0.112f, 0.48f)
            horizontalLineToRelative(0.543f)
            curveToRelative(0.077f, 0f, 0.194f, 0.084f, 0.229f, 0.22f)
            lineToRelative(0.352f, -1.116f)
            curveToRelative(0f, -0.098f, 0.167f, -0.177f, 0.265f, -0.177f)
            horizontalLineTo(12f)
            horizontalLineToRelative(0.983f)
            curveToRelative(0.098f, 0f, 0.265f, 0.079f, 0.265f, 0.177f)
            lineTo(13.6f, 4.213f)
            curveToRelative(0.035f, -0.136f, 0.152f, -0.22f, 0.229f, -0.22f)
            horizontalLineToRelative(0.543f)
            curveToRelative(0.047f, -0.227f, 0.1f, -0.439f, 0.112f, -0.48f)
            lineToRelative(0.105f, -0.383f)
            curveToRelative(0.026f, -0.094f, 0.14f, -0.145f, 0.255f, -0.113f)
            lineToRelative(0.848f, 0.231f)
            curveToRelative(0.115f, 0.031f, 0.188f, 0.133f, 0.162f, 0.227f)
            lineTo(14.771f, 4.22f)
            verticalLineToRelative(1.685f)
            curveToRelative(0.087f, 0.011f, 0.154f, 0.082f, 0.154f, 0.172f)
            verticalLineToRelative(1.214f)
            horizontalLineToRelative(1.739f)
            curveToRelative(0.146f, 0f, 0.191f, -0.052f, 0.191f, -0.268f)
            curveToRelative(0f, -0.218f, -0.177f, -0.203f, -0.177f, -0.424f)
            curveToRelative(0f, -0.25f, 0.272f, -0.324f, 0.272f, -0.633f)
            curveToRelative(0f, -0.243f, -0.184f, -0.343f, -0.184f, -0.53f)
            curveToRelative(0f, -0.222f, 0.324f, -0.221f, 0.324f, -0.471f)
            curveToRelative(0f, -0.25f, -0.228f, -0.334f, -0.228f, -0.647f)
            curveToRelative(0f, -0.214f, 0.331f, -0.192f, 0.331f, -0.589f)
            close()
        }

        // Last inner path inside group (opacity 0.8 -> final 0.4)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.29f, 6.18f)
            curveToRelative(-0.002f, -0.001f, -0.004f, -0.002f, -0.007f, -0.002f)
            curveToRelative(0.002f, 0f, 0.004f, 0.001f, 0.007f, 0.002f)
            close()
        }

        // Final main path (full opacity)
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
            moveTo(16.664f, 7.292f)
            curveToRelative(-1.677f, 0f, -2.999f, 0f, -4.708f, 0f)
            curveToRelative(-1.704f, 0f, -3.325f, 0f, -4.65f, 0f)
            curveToRelative(0f, 3.502f, -4.914f, 2.178f, -4.914f, 6.71f)
            curveToRelative(0f, 4.708f, 4.623f, 8.799f, 9.564f, 8.799f)
            reflectiveCurveToRelative(9.652f, -4.414f, 9.652f, -8.799f)
            curveToRelative(0f, -3.332f, -4.944f, -2.008f, -4.944f, -5.509f)
            close()
            moveTo(17.67f, 14.315f)
            curveToRelative(-0.131f, 0.127f, -0.321f, 0.238f, -0.57f, 0.335f)
            curveToRelative(-0.249f, 0.096f, -0.501f, 0.145f, -0.756f, 0.145f)
            curveToRelative(-0.324f, 0f, -0.607f, -0.068f, -0.848f, -0.204f)
            curveToRelative(-0.241f, -0.136f, -0.422f, -0.33f, -0.543f, -0.583f)
            curveToRelative(-0.121f, -0.253f, -0.182f, -0.528f, -0.182f, -0.825f)
            curveToRelative(0f, -0.323f, 0.068f, -0.61f, 0.203f, -0.86f)
            curveToRelative(0.135f, -0.251f, 0.333f, -0.443f, 0.594f, -0.577f)
            curveToRelative(0.199f, -0.103f, 0.446f, -0.154f, 0.742f, -0.154f)
            curveToRelative(0.385f, 0f, 0.685f, 0.081f, 0.902f, 0.242f)
            curveToRelative(0.216f, 0.161f, 0.355f, 0.384f, 0.417f, 0.669f)
            lineToRelative(-0.621f, 0.116f)
            curveToRelative(-0.044f, -0.152f, -0.126f, -0.272f, -0.246f, -0.36f)
            curveToRelative(-0.12f, -0.088f, -0.271f, -0.132f, -0.451f, -0.132f)
            curveToRelative(-0.273f, 0f, -0.491f, 0.087f, -0.652f, 0.26f)
            curveToRelative(-0.161f, 0.173f, -0.242f, 0.431f, -0.242f, 0.772f)
            curveToRelative(0f, 0.368f, 0.082f, 0.644f, 0.245f, 0.827f)
            curveToRelative(0.163f, 0.184f, 0.378f, 0.276f, 0.643f, 0.276f)
            curveToRelative(0.131f, 0f, 0.262f, -0.026f, 0.394f, -0.077f)
            curveToRelative(0.132f, -0.052f, 0.245f, -0.114f, 0.339f, -0.187f)
            verticalLineToRelative(-0.393f)
            horizontalLineToRelative(-0.717f)
            verticalLineTo(13.08f)
            horizontalLineToRelative(1.349f)
            verticalLineTo(14.315f)
            close()
        }
    }.build()
}
