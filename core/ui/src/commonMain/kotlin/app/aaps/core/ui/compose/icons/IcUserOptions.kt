package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for User Entry.
 *
 * replaces ic_user_options
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcUserEntryIconPreview
 */
val IcUserOptions: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcUserEntry",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF6AE86D)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(13.892f, 13.296f)
            curveToRelative(-0.192f, -0.004f, -0.396f, 0.017f, -0.472f, -0.238f)
            curveToRelative(-0.064f, -0.217f, 0.003f, -0.372f, 0.171f, -0.512f)
            curveToRelative(0.879f, -0.732f, 1.037f, -1.724f, 0.995f, -2.858f)
            curveToRelative(0f, -0.207f, 0.018f, -0.487f, -0.003f, -0.764f)
            curveToRelative(-0.099f, -1.274f, -1.099f, -2.248f, -2.404f, -2.36f)
            curveToRelative(-1.183f, -0.102f, -2.393f, 0.679f, -2.606f, 1.881f)
            curveToRelative(-0.258f, 1.454f, -0.389f, 2.954f, 0.89f, 4.105f)
            curveToRelative(0.161f, 0.146f, 0.241f, 0.288f, 0.168f, 0.508f)
            curveToRelative(-0.083f, 0.248f, -0.281f, 0.239f, -0.476f, 0.237f)
            curveToRelative(-0.486f, -0.007f, -0.935f, 0.116f, -1.358f, 0.348f)
            curveToRelative(-1.362f, 0.749f, -1.46f, 1.965f, -0.231f, 2.884f)
            curveToRelative(1.818f, 1.363f, 5.527f, 1.177f, 7.199f, -0.36f)
            curveToRelative(0.659f, -0.606f, 0.711f, -1.297f, 0.118f, -1.971f)
            curveTo(15.36f, 13.604f, 14.686f, 13.311f, 13.892f, 13.296f)
            close()

            moveTo(14.201f, 16.043f)
            curveToRelative(-1.682f, 0.68f, -3.369f, 0.698f, -4.975f, -0.221f)
            curveToRelative(-0.81f, -0.463f, -0.767f, -0.928f, 0.064f, -1.366f)
            curveToRelative(0.347f, -0.183f, 0.724f, -0.299f, 1.121f, -0.273f)
            curveToRelative(0.487f, 0.034f, 0.89f, -0.066f, 1.071f, -0.579f)
            curveToRelative(0.196f, -0.558f, 0.428f, -1.152f, -0.147f, -1.608f)
            curveToRelative(-0.925f, -0.733f, -0.987f, -1.714f, -0.932f, -2.771f)
            curveToRelative(0.055f, -1.02f, 0.714f, -1.762f, 1.622f, -1.747f)
            curveToRelative(0.926f, 0.015f, 1.581f, 0.74f, 1.604f, 1.776f)
            curveToRelative(0.004f, 0.167f, 0f, 0.334f, 0f, 0.638f)
            curveToRelative(0.136f, 0.704f, -0.12f, 1.383f, -0.789f, 1.927f)
            curveToRelative(-0.553f, 0.449f, -0.539f, 1.033f, -0.329f, 1.661f)
            curveToRelative(0.21f, 0.627f, 0.668f, 0.759f, 1.246f, 0.708f)
            curveToRelative(0.055f, -0.004f, 0.112f, 0.006f, 0.165f, 0.02f)
            curveToRelative(0.544f, 0.13f, 1.23f, 0.234f, 1.327f, 0.838f)
            curveTo(15.349f, 15.66f, 14.662f, 15.857f, 14.201f, 16.043f)
            close()

            moveTo(21.948f, 10.127f)
            horizontalLineToRelative(-1.857f)
            curveToRelative(-0.21f, -0.907f, -0.568f, -1.754f, -1.048f, -2.521f)
            lineToRelative(1.317f, -1.317f)
            curveToRelative(0.334f, -0.334f, 0.334f, -0.876f, 0f, -1.209f)
            lineToRelative(-1.437f, -1.437f)
            curveToRelative(-0.334f, -0.334f, -0.876f, -0.334f, -1.209f, 0f)
            lineTo(16.396f, 4.96f)
            curveToRelative(-0.767f, -0.48f, -1.615f, -0.838f, -2.521f, -1.048f)
            verticalLineTo(2.054f)
            curveToRelative(0f, -0.473f, -0.383f, -0.855f, -0.855f, -0.855f)
            horizontalLineToRelative(-2.033f)
            curveToRelative(-0.473f, 0f, -0.855f, 0.383f, -0.855f, 0.855f)
            verticalLineToRelative(1.857f)
            curveToRelative(-0.907f, 0.21f, -1.754f, 0.568f, -2.521f, 1.048f)
            lineToRelative(-1.32f, -1.317f)
            curveToRelative(-0.334f, -0.334f, -0.876f, -0.334f, -1.209f, 0f)
            lineTo(3.644f, 5.081f)
            curveToRelative(-0.334f, 0.334f, -0.334f, 0.876f, 0f, 1.209f)
            lineTo(4.96f, 7.607f)
            curveToRelative(-0.48f, 0.767f, -0.838f, 1.615f, -1.048f, 2.521f)
            horizontalLineTo(2.055f)
            curveToRelative(-0.473f, 0f, -0.855f, 0.383f, -0.855f, 0.855f)
            verticalLineToRelative(2.033f)
            curveToRelative(0f, 0.473f, 0.383f, 0.855f, 0.855f, 0.855f)
            horizontalLineToRelative(1.857f)
            curveToRelative(0.21f, 0.907f, 0.568f, 1.754f, 1.048f, 2.521f)
            lineTo(3.644f, 17.71f)
            curveToRelative(-0.334f, 0.334f, -0.334f, 0.876f, 0f, 1.209f)
            lineToRelative(1.437f, 1.437f)
            curveToRelative(0.334f, 0.334f, 0.876f, 0.334f, 1.209f, 0f)
            lineToRelative(1.317f, -1.317f)
            curveToRelative(0.767f, 0.48f, 1.615f, 0.838f, 2.521f, 1.048f)
            verticalLineToRelative(1.859f)
            curveToRelative(0f, 0.473f, 0.383f, 0.855f, 0.855f, 0.855f)
            horizontalLineToRelative(2.033f)
            curveToRelative(0.473f, 0f, 0.855f, -0.383f, 0.855f, -0.855f)
            verticalLineToRelative(-1.857f)
            curveToRelative(0.907f, -0.21f, 1.755f, -0.568f, 2.521f, -1.048f)
            lineToRelative(1.317f, 1.317f)
            curveToRelative(0.334f, 0.334f, 0.876f, 0.334f, 1.209f, 0f)
            lineToRelative(1.437f, -1.437f)
            curveToRelative(0.334f, -0.334f, 0.334f, -0.876f, 0f, -1.209f)
            lineToRelative(-1.317f, -1.317f)
            curveToRelative(0.48f, -0.767f, 0.838f, -1.615f, 1.048f, -2.521f)
            horizontalLineToRelative(1.859f)
            curveToRelative(0.473f, 0f, 0.855f, -0.383f, 0.855f, -0.855f)
            verticalLineToRelative(-2.033f)
            curveTo(22.804f, 10.511f, 22.421f, 10.127f, 21.948f, 10.127f)
            close()

            moveTo(12.001f, 18.911f)
            curveToRelative(-3.811f, 0f, -6.911f, -3.1f, -6.911f, -6.911f)
            reflectiveCurveToRelative(3.1f, -6.909f, 6.911f, -6.909f)
            reflectiveCurveTo(18.912f, 8.189f, 18.912f, 12f)
            reflectiveCurveTo(15.812f, 18.911f, 12.001f, 18.911f)
            close()
        }
    }.build()
}
