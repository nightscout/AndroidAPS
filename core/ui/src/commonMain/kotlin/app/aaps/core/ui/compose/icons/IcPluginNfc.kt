package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcPluginNfc: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginNfc",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
        ) {
            // Outer card border (clockwise winding)
            moveTo(20f, 2f)
            lineTo(4f, 2f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(22f, 4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            // Inner square cutout (counter-clockwise winding creates hole)
            moveTo(20f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 4f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(16f)
            close()
            // NFC chip symbol
            moveTo(18f, 6f)
            horizontalLineToRelative(-5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(2.28f)
            curveToRelative(-0.6f, 0.35f, -1f, 0.98f, -1f, 1.72f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            curveToRelative(0f, -0.74f, -0.4f, -1.38f, -1f, -1.72f)
            lineTo(13f, 8f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(8f)
            lineTo(8f, 16f)
            lineTo(8f, 8f)
            horizontalLineToRelative(2f)
            lineTo(10f, 6f)
            lineTo(6f, 6f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(12f)
            lineTo(18f, 6f)
            close()
        }
    }.build()
}
