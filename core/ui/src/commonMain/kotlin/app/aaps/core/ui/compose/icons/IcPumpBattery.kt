package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Pump Battery.
 * Represents insulin pump battery status.
 *
 * replaces ic_cp_pump_battery
 *
 * Bounding box: x: 1.2-22.8, y: 6.6-17.4 (viewport: 24x24, ~90% width)
 *
 * @see IcPumpBatteryIconPreview
 */
val IcPumpBattery: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPumpBattery",
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
            moveTo(22.025f, 10.135f)
            curveToRelative(-0.428f, 0f, -0.775f, 0.347f, -0.775f, 0.775f)
            verticalLineToRelative(0.351f)
            horizontalLineToRelative(-0.554f)
            verticalLineTo(8.492f)
            curveToRelative(0f, -1.028f, -0.837f, -1.865f, -1.865f, -1.865f)
            horizontalLineTo(3.065f)
            curveTo(2.037f, 6.628f, 1.2f, 7.464f, 1.2f, 8.492f)
            verticalLineToRelative(7.015f)
            curveToRelative(0f, 1.028f, 0.837f, 1.865f, 1.865f, 1.865f)
            horizontalLineToRelative(15.766f)
            curveToRelative(0.851f, 0f, 1.563f, -0.577f, 1.786f, -1.357f)
            curveToRelative(0.012f, 0.001f, 0.021f, 0.007f, 0.033f, 0.007f)
            curveToRelative(0.393f, 0f, 0.711f, -0.351f, 0.711f, -0.785f)
            curveToRelative(0f, -0.415f, -0.295f, -0.747f, -0.665f, -0.774f)
            verticalLineToRelative(-1.725f)
            horizontalLineToRelative(0.554f)
            verticalLineToRelative(0.351f)
            curveToRelative(0f, 0.428f, 0.347f, 0.775f, 0.775f, 0.775f)
            curveToRelative(0.428f, 0f, 0.775f, -0.347f, 0.775f, -0.775f)
            verticalLineToRelative(-2.178f)
            curveTo(22.8f, 10.483f, 22.453f, 10.135f, 22.025f, 10.135f)
            close()

            moveTo(19.514f, 15.508f)
            curveToRelative(0f, 0.376f, -0.307f, 0.683f, -0.683f, 0.683f)
            horizontalLineTo(3.065f)
            curveToRelative(-0.377f, 0f, -0.683f, -0.307f, -0.683f, -0.683f)
            verticalLineTo(8.492f)
            curveToRelative(0f, -0.377f, 0.306f, -0.683f, 0.683f, -0.683f)
            horizontalLineToRelative(15.766f)
            curveToRelative(0.376f, 0f, 0.683f, 0.306f, 0.683f, 0.683f)
            verticalLineTo(15.508f)
            close()

            moveTo(9.582f, 9.96f)
            lineTo(4.929f, 13.412f)
            lineTo(9.009f, 11.972f)
            lineTo(11.114f, 14.058f)
            lineTo(16.357f, 9.942f)
            lineTo(11.28f, 11.935f)
            close()
        }
    }.build()
}
