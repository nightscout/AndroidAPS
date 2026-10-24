package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Eversense CGM Plugin.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginEversensePreview
 */
val IcPluginEversense: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginEversense",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Circle with opacity 0.9
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.9f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(14.669f, 12.806f)
            arcToRelative(2.669f, 2.669f, 0f, true, true, -5.338f, 0f)
            arcToRelative(2.669f, 2.669f, 0f, true, true, 5.338f, 0f)
            close()
        }

        // Small rectangle (path) with opacity 0.8
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.779f, 4.934f)
            curveToRelative(0f, 0.134f, -0.108f, 0.242f, -0.242f, 0.242f)
            horizontalLineToRelative(-1.075f)
            curveToRelative(-0.134f, 0f, -0.242f, -0.108f, -0.242f, -0.242f)
            lineToRelative(0f, 0f)
            curveToRelative(0f, -0.134f, 0.108f, -0.242f, 0.242f, -0.242f)
            horizontalLineToRelative(1.075f)
            curveToRelative(0.134f, 0f, 0.242f, 0.108f, 0.242f, 0.242f)
            lineTo(12.779f, 4.934f)
            close()
        }

        // Main shape (outer circle and inner rectangle) with opacity 1
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
            moveTo(12f, 1.2f)
            curveTo(5.221f, 1.2f, 3.537f, 6.035f, 3.537f, 12f)
            reflectiveCurveTo(5.185f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(20.463f, 17.965f, 20.463f, 12f)
            reflectiveCurveTo(18.958f, 1.2f, 12f, 1.2f)
            close()
            moveTo(11.463f, 4.693f)
            horizontalLineToRelative(1.075f)
            curveToRelative(0.134f, 0f, 0.242f, 0.108f, 0.242f, 0.242f)
            curveToRelative(0f, 0.134f, -0.108f, 0.242f, -0.242f, 0.242f)
            horizontalLineToRelative(-1.075f)
            curveToRelative(-0.134f, 0f, -0.242f, -0.108f, -0.242f, -0.242f)
            curveToRelative(0f, -0.134f, 0.108f, -0.242f, 0.242f, -0.242f)
            close()
            moveTo(12f, 15.475f)
            curveToRelative(-1.474f, 0f, -2.669f, -1.195f, -2.669f, -2.669f)
            reflectiveCurveTo(10.526f, 10.137f, 12f, 10.137f)
            reflectiveCurveTo(14.669f, 11.332f, 14.669f, 12.806f)
            reflectiveCurveTo(13.474f, 15.475f, 12f, 15.475f)
            close()
        }
    }.build()
}
