package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Random BG Plugin.
 *
 * replacing ic_dice
 *
 * Bounding box: x: 1.2-22.8, y: 3.0-21.0 (viewport: 24x24, ~90% width)
 *
 * @see IcPluginRandomBgIconPreview
 */
val IcPluginRandomBg: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginRandomBg",
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
            moveTo(21.18f, 9.84f)
            horizontalLineToRelative(-4.007f)
            curveToRelative(0.428f, 0.999f, 0.24f, 2.201f, -0.574f, 3.015f)
            lineTo(12f, 17.453f)
            verticalLineToRelative(1.567f)
            curveToRelative(0f, 0.895f, 0.725f, 1.62f, 1.62f, 1.62f)
            horizontalLineToRelative(7.56f)
            curveToRelative(0.895f, 0f, 1.62f, -0.725f, 1.62f, -1.62f)
            verticalLineToRelative(-7.56f)
            curveTo(22.8f, 10.565f, 22.075f, 9.84f, 21.18f, 9.84f)
            close()

            moveTo(17.4f, 16.05f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(18.21f, 15.687f, 17.847f, 16.05f, 17.4f, 16.05f)
            close()

            moveTo(15.835f, 9.749f)
            lineTo(9.931f, 3.845f)
            curveToRelative(-0.647f, -0.647f, -1.695f, -0.647f, -2.342f, 0f)
            lineTo(1.685f, 9.749f)
            curveToRelative(-0.647f, 0.647f, -0.647f, 1.695f, 0f, 2.342f)
            lineToRelative(5.904f, 5.904f)
            curveToRelative(0.647f, 0.647f, 1.695f, 0.647f, 2.342f, 0f)
            lineToRelative(5.904f, -5.904f)
            curveTo(16.482f, 11.444f, 16.482f, 10.396f, 15.835f, 9.749f)
            lineTo(15.835f, 9.749f)
            close()

            moveTo(4.44f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(5.25f, 11.367f, 4.887f, 11.73f, 4.44f, 11.73f)
            close()

            moveTo(8.76f, 16.05f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 15.687f, 9.207f, 16.05f, 8.76f, 16.05f)
            close()

            moveTo(8.76f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 11.367f, 9.207f, 11.73f, 8.76f, 11.73f)
            close()

            moveTo(8.76f, 7.41f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(9.57f, 7.047f, 9.207f, 7.41f, 8.76f, 7.41f)
            close()

            moveTo(13.08f, 11.73f)
            curveToRelative(-0.447f, 0f, -0.81f, -0.363f, -0.81f, -0.81f)
            curveToRelative(0f, -0.448f, 0.363f, -0.81f, 0.81f, -0.81f)
            reflectiveCurveToRelative(0.81f, 0.362f, 0.81f, 0.81f)
            curveTo(13.89f, 11.367f, 13.527f, 11.73f, 13.08f, 11.73f)
            close()
        }
    }.build()
}
