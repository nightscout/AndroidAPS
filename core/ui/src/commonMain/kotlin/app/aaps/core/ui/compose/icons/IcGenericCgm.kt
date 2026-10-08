package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Generic CGM Plugin.
 * Represents a generic CGM integration.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcGenericCgmIconPreview
 */
val IcGenericCgm: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcGenericCgm",
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

            // First inner shape (left side)
            moveTo(7.098f, 14.67f)
            curveToRelative(-0.414f, 0.323f, -0.94f, 0.485f, -1.577f, 0.485f)
            curveToRelative(-0.788f, 0f, -1.436f, -0.277f, -1.943f, -0.831f)
            curveToRelative(-0.508f, -0.554f, -0.762f, -1.311f, -0.762f, -2.271f)
            curveToRelative(0f, -1.016f, 0.255f, -1.805f, 0.766f, -2.367f)
            reflectiveCurveToRelative(1.181f, -0.843f, 2.013f, -0.843f)
            curveToRelative(0.726f, 0f, 1.316f, 0.221f, 1.77f, 0.662f)
            curveToRelative(0.27f, 0.261f, 0.472f, 0.636f, 0.607f, 1.124f)
            lineToRelative(-1.187f, 0.292f)
            curveToRelative(-0.07f, -0.316f, -0.217f, -0.566f, -0.439f, -0.75f)
            reflectiveCurveTo(5.852f, 9.897f, 5.533f, 9.897f)
            curveToRelative(-0.44f, 0f, -0.797f, 0.163f, -1.071f, 0.487f)
            curveToRelative(-0.274f, 0.325f, -0.411f, 0.851f, -0.411f, 1.578f)
            curveToRelative(0f, 0.771f, 0.135f, 1.321f, 0.405f, 1.648f)
            reflectiveCurveToRelative(0.621f, 0.491f, 1.053f, 0.491f)
            curveToRelative(0.319f, 0f, 0.593f, -0.104f, 0.822f, -0.313f)
            curveToRelative(0.229f, -0.208f, 0.394f, -0.535f, 0.494f, -0.981f)
            lineToRelative(1.162f, 0.378f)
            curveToRelative(-0.124f, 0.477f, -0.42f, 0.972f, -0.835f, 1.295f)
            close()

            // Second inner shape (center)
            moveTo(14.361f, 14.21f)
            curveToRelative(-0.251f, 0.25f, -0.614f, 0.47f, -1.091f, 0.66f)
            curveToRelative(-0.477f, 0.189f, -0.959f, 0.285f, -1.448f, 0.285f)
            curveToRelative(-0.621f, 0f, -1.162f, -0.135f, -1.624f, -0.402f)
            curveToRelative(-0.461f, -0.268f, -0.809f, -0.65f, -1.041f, -1.148f)
            curveToRelative(-0.232f, -0.498f, -0.348f, -1.04f, -0.348f, -1.626f)
            curveToRelative(0f, -0.635f, 0.129f, -1.2f, 0.389f, -1.694f)
            curveToRelative(0.259f, -0.494f, 0.638f, -0.873f, 1.138f, -1.136f)
            curveToRelative(0.381f, -0.203f, 0.854f, -0.304f, 1.421f, -0.304f)
            curveToRelative(0.737f, 0f, 1.313f, 0.159f, 1.728f, 0.477f)
            curveToRelative(0.414f, 0.317f, 0.681f, 0.757f, 0.8f, 1.317f)
            lineToRelative(-1.191f, 0.229f)
            curveToRelative(-0.084f, -0.3f, -0.24f, -0.537f, -0.471f, -0.71f)
            curveToRelative(-0.231f, -0.173f, -0.52f, -0.26f, -0.865f, -0.26f)
            curveToRelative(-0.524f, 0f, -0.94f, 0.171f, -1.249f, 0.512f)
            reflectiveCurveToRelative(-0.464f, 0.848f, -0.464f, 1.52f)
            curveToRelative(0f, 0.725f, 0.157f, 1.268f, 0.47f, 1.63f)
            reflectiveCurveToRelative(0.724f, 0.543f, 1.231f, 0.543f)
            curveToRelative(0.251f, 0f, 0.503f, -0.051f, 0.755f, -0.152f)
            curveToRelative(0.253f, -0.101f, 0.469f, -0.224f, 0.649f, -0.368f)
            verticalLineToRelative(-0.773f)
            horizontalLineToRelative(-1.372f)
            verticalLineToRelative(-1.029f)
            horizontalLineToRelative(2.583f)
            verticalLineToRelative(2.033f)
            close()

            // Third inner shape (right side)
            moveTo(20.07f, 15.051f)
            verticalLineToRelative(-4.804f)
            lineToRelative(-1.178f, 4.804f)
            horizontalLineToRelative(-1.154f)
            lineToRelative(-1.174f, -4.804f)
            verticalLineToRelative(4.804f)
            horizontalLineToRelative(-1.114f)
            verticalLineTo(8.949f)
            horizontalLineToRelative(1.794f)
            lineToRelative(1.077f, 4.163f)
            lineToRelative(1.065f, -4.163f)
            horizontalLineToRelative(1.798f)
            verticalLineToRelative(6.103f)
            horizontalLineTo(20.07f)
            close()
        }
    }.build()
}
