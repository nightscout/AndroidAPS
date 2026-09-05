package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Insulin Plugin.
 *
 * replacing ic_insulin
 *
 * Bounding box: x: 6.0-18.9, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcPluginInsulinIconPreview
 */
val IcPluginInsulin: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginInsulin",
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
            moveTo(10.685f, 18.578f)
            verticalLineTo(9.348f)
            horizontalLineToRelative(7.152f)
            verticalLineTo(8.085f)
            curveToRelative(0f, -1.215f, -1.219f, -1.945f, -2.713f, -2.2f)
            lineToRelative(-1.061f, -1.061f)
            verticalLineTo(3.385f)
            horizontalLineToRelative(0.784f)
            curveToRelative(0.276f, 0f, 0.5f, -0.224f, 0.5f, -0.5f)
            verticalLineTo(1.7f)
            curveToRelative(0f, -0.276f, -0.224f, -0.5f, -0.5f, -0.5f)
            horizontalLineTo(9.153f)
            curveToRelative(-0.276f, 0f, -0.5f, 0.224f, -0.5f, 0.5f)
            verticalLineToRelative(1.185f)
            curveToRelative(0f, 0.276f, 0.224f, 0.5f, 0.5f, 0.5f)
            horizontalLineToRelative(0.784f)
            verticalLineToRelative(1.439f)
            lineTo(8.876f, 5.885f)
            curveToRelative(-1.493f, 0.255f, -2.713f, 0.985f, -2.713f, 2.2f)
            verticalLineTo(20.6f)
            curveToRelative(0f, 1.215f, 0.985f, 2.2f, 2.2f, 2.2f)
            horizontalLineToRelative(7.273f)
            curveToRelative(1.215f, 0f, 2.2f, -0.985f, 2.2f, -2.2f)
            verticalLineToRelative(-2.022f)
            horizontalLineTo(10.685f)
            close()
        }
    }.build()
}
