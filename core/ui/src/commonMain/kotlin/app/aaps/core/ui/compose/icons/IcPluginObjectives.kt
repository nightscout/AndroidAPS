package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Objectives Plugin.
 * Represents objectives and goals tracking.
 *
 * replacing ic_graduation
 *
 * Bounding box: x: 1.8-22.2, y: 5.4-18.6 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginObjectivesIconPreview
 */
val IcPluginObjectives: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginObjectives",
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
            moveTo(22.204f, 8.531f)
            lineTo(12.79f, 5.638f)
            curveToRelative(-0.513f, -0.158f, -1.067f, -0.158f, -1.579f, 0f)
            lineTo(1.796f, 8.531f)
            curveToRelative(-0.794f, 0.244f, -0.794f, 1.295f, 0f, 1.539f)
            lineToRelative(1.641f, 0.504f)
            curveToRelative(-0.36f, 0.445f, -0.582f, 0.988f, -0.603f, 1.583f)
            curveTo(2.509f, 12.343f, 2.28f, 12.679f, 2.28f, 13.08f)
            curveToRelative(0f, 0.364f, 0.192f, 0.67f, 0.468f, 0.866f)
            lineToRelative(-0.862f, 3.877f)
            curveTo(1.811f, 18.16f, 2.068f, 18.48f, 2.413f, 18.48f)
            horizontalLineToRelative(1.894f)
            curveToRelative(0.346f, 0f, 0.602f, -0.32f, 0.527f, -0.657f)
            lineToRelative(-0.862f, -3.877f)
            curveTo(4.248f, 13.75f, 4.44f, 13.444f, 4.44f, 13.08f)
            curveToRelative(0f, -0.39f, -0.218f, -0.717f, -0.529f, -0.907f)
            curveToRelative(0.026f, -0.507f, 0.285f, -0.955f, 0.698f, -1.239f)
            lineToRelative(6.601f, 2.028f)
            curveToRelative(0.306f, 0.094f, 0.892f, 0.211f, 1.579f, 0f)
            lineToRelative(9.415f, -2.892f)
            curveTo(22.999f, 9.825f, 22.999f, 8.775f, 22.204f, 8.531f)
            lineTo(22.204f, 8.531f)
            close()

            moveTo(13.107f, 13.994f)
            curveToRelative(-0.963f, 0.296f, -1.783f, 0.132f, -2.214f, 0f)
            lineToRelative(-4.894f, -1.504f)
            lineTo(5.52f, 16.32f)
            curveToRelative(0f, 1.193f, 2.901f, 2.16f, 6.48f, 2.16f)
            reflectiveCurveToRelative(6.48f, -0.967f, 6.48f, -2.16f)
            lineToRelative(-0.479f, -3.83f)
            lineTo(13.107f, 13.994f)
            close()
        }
    }.build()
}
