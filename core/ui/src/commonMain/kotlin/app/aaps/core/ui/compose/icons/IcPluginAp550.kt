package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for the AP550 (uTouch) pump plugin.
 *
 * The paths are taken from `ic_ap550_128.xml` in the ap550 module. That drawable paints a white body
 * with black details; the icon here is drawn with one colour and different opacities instead, because
 * the plugin icon is tinted by the theme wherever it is shown. The body is kept light and the display
 * with its buttons stays solid, so the parts remain apart after tinting.
 *
 * Bounding box: (viewport: 64x64, full bleed)
 */
val IcPluginAp550: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginAp550",
        defaultWidth = 64.dp,
        defaultHeight = 64.dp,
        viewportWidth = 64f,
        viewportHeight = 64f
    ).apply {
        // Pump body: two rounded shapes, kept light so the display stays readable on top of it.
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(41f, 53.0926f)
            curveTo(41f, 57.6742f, 33.0897f, 61.4847f, 23.2273f, 61.657f)
            curveTo(13.3648f, 61.8319f, 4.8549f, 58.3075f, 4.1102f, 53.7391f)
            curveTo(3.3654f, 49.1734f, 10.635f, 45.0953f, 20.4399f, 44.5785f)
            curveTo(30.2476f, 44.0592f, 39.3051f, 47.2761f, 40.7891f, 51.8047f)
            lineTo(41f, 53.0926f)
            close()
            moveTo(33.599f, 33.571f)
            curveTo(33.599f, 39.0032f, 29.1004f, 43.5239f, 23.4929f, 43.7279f)
            curveTo(17.8826f, 43.9346f, 13.0472f, 39.7531f, 12.6228f, 34.3394f)
            curveTo(12.1984f, 28.9231f, 16.3328f, 24.0871f, 21.9075f, 23.4723f)
            curveTo(27.4849f, 22.8575f, 32.6352f, 26.6733f, 33.4785f, 32.042f)
            lineTo(33.599f, 33.571f)
            close()
        }

        // Display bubble at the top right.
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(59.9421f, 12.9495f)
            curveTo(59.9421f, 7.0918f, 54.161f, 2.3354f, 47.0401f, 2.3354f)
            curveTo(39.9191f, 2.3354f, 34.138f, 7.0918f, 34.138f, 12.9495f)
            curveTo(34.138f, 15.849f, 35.5788f, 18.6213f, 38.1273f, 20.6243f)
            lineTo(34.138f, 28.8708f)
            lineTo(41.8559f, 22.6694f)
            curveTo(43.4901f, 23.259f, 45.2556f, 23.5637f, 47.0401f, 23.5637f)
            curveTo(54.161f, 23.5637f, 59.9421f, 18.8073f, 59.9421f, 12.9495f)
            close()
        }

        // Display and buttons inside the bubble. Solid, because this is the part that has to read as
        // the pump's screen.
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(39.0941f, 17.8588f)
            lineTo(39.0949f, 13.9134f)
            curveTo(39.0949f, 13.752f, 39.226f, 13.621f, 39.3873f, 13.6211f)
            lineTo(39.7008f, 13.6211f)
            curveTo(39.8546f, 13.6212f, 39.9806f, 13.74f, 39.9925f, 13.8905f)
            curveTo(40.0238f, 13.8875f, 40.0559f, 13.8859f, 40.0879f, 13.8859f)
            lineTo(41.0815f, 13.8861f)
            lineTo(41.0825f, 8.7741f)
            curveTo(41.0827f, 7.7211f, 41.8725f, 6.8662f, 42.8454f, 6.8664f)
            lineTo(53.1736f, 6.8685f)
            curveTo(54.1464f, 6.8687f, 54.9359f, 7.7238f, 54.9357f, 8.7769f)
            lineTo(54.9343f, 15.9526f)
            curveTo(54.9341f, 17.0056f, 54.1442f, 17.8605f, 53.1714f, 17.8603f)
            lineTo(42.8432f, 17.8582f)
            curveTo(42.7769f, 17.8582f, 42.7112f, 17.8542f, 42.6465f, 17.8466f)
            curveTo(42.5964f, 17.8542f, 42.5452f, 17.8582f, 42.4934f, 17.8582f)
            lineTo(40.0871f, 17.8577f)
            curveTo(40.0551f, 17.8577f, 40.0237f, 17.8564f, 39.9927f, 17.8534f)
            lineTo(39.9927f, 17.859f)
            curveTo(39.9927f, 18.0204f, 39.8613f, 18.1513f, 39.6999f, 18.1513f)
            lineTo(39.3864f, 18.1512f)
            curveTo(39.2251f, 18.1512f, 39.0941f, 18.0202f, 39.0941f, 17.8588f)
            close()
            moveTo(43.7517f, 9.1657f)
            lineTo(43.7507f, 14.2169f)
            lineTo(52.2649f, 14.2186f)
            lineTo(52.2659f, 9.1674f)
            lineTo(43.7517f, 9.1657f)
            close()
            moveTo(50.8092f, 15.1565f)
            lineTo(50.8088f, 16.7322f)
            lineTo(52.2646f, 15.9448f)
            lineTo(50.8092f, 15.1565f)
            close()
            moveTo(48.5703f, 15.9622f)
            curveTo(48.5703f, 15.956f, 48.5703f, 15.95f, 48.5703f, 15.9441f)
            curveTo(48.5704f, 15.4702f, 48.222f, 15.0857f, 47.7927f, 15.0856f)
            curveTo(47.363f, 15.0855f, 47.0145f, 15.4699f, 47.0144f, 15.9438f)
            lineTo(47.0144f, 15.9791f)
            curveTo(47.0143f, 16.4332f, 47.3627f, 16.8022f, 47.7923f, 16.8022f)
            curveTo(48.2217f, 16.8023f, 48.5702f, 16.4335f, 48.5703f, 15.9794f)
            curveTo(48.5703f, 15.9734f, 48.5703f, 15.9678f, 48.5703f, 15.9622f)
            close()
            moveTo(45.2062f, 15.1554f)
            lineTo(43.7504f, 15.9431f)
            lineTo(45.2059f, 16.7311f)
            lineTo(45.2062f, 15.1554f)
            close()
        }
    }.build()
}