package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Generic gear icon. Used as a fallback for plugin / source display when no specific icon exists.
 *
 * replaces ic_generic_icon
 *
 * Viewport: 64x64 (gear outer cog with inner circular hole via opposing path windings)
 *
 * @see IcGenericIconIconPreview
 */
val IcGenericIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcGenericIcon",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 64f,
        viewportHeight = 64f
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
            // Outer cog (counter-clockwise)
            moveTo(34.9527f, 3.6697f)
            curveTo(32.9895f, 3.458f, 31.0105f, 3.458f, 29.0473f, 3.6697f)
            lineTo(28.2608f, 9.24f)
            curveTo(26.4699f, 9.5448f, 24.7219f, 10.0669f, 23.0535f, 10.7961f)
            lineTo(19.4324f, 6.543f)
            curveTo(17.6686f, 7.4441f, 16.0037f, 8.5334f, 14.4648f, 9.7918f)
            lineTo(16.7622f, 14.9098f)
            curveTo(15.4176f, 16.1519f, 14.2242f, 17.5533f, 13.2085f, 19.0841f)
            lineTo(7.9023f, 17.4981f)
            curveTo(6.898f, 19.227f, 6.0756f, 21.0591f, 5.4492f, 22.9644f)
            lineTo(10.1018f, 26.006f)
            curveTo(9.6302f, 27.7901f, 9.3704f, 29.6257f, 9.3293f, 31.4731f)
            lineTo(4.0231f, 33.0573f)
            curveTo(4.0966f, 35.0648f, 4.3783f, 37.0588f, 4.8639f, 39.0057f)
            lineTo(10.3931f, 39.0057f)
            curveTo(10.9443f, 40.7654f, 11.7011f, 42.4527f, 12.6477f, 44.0287f)
            lineTo(9.0266f, 48.2817f)
            curveTo(10.1543f, 49.9301f, 11.4509f, 51.4518f, 12.8935f, 52.8233f)
            lineTo(17.5453f, 49.7808f)
            curveTo(18.9442f, 50.9578f, 20.477f, 51.9602f, 22.1113f, 52.7663f)
            lineTo(21.323f, 58.3357f)
            curveTo(23.148f, 59.102f, 25.0473f, 59.6692f, 26.9896f, 60.0293f)
            lineTo(29.2861f, 54.9105f)
            curveTo(31.0893f, 55.1312f, 32.9108f, 55.1312f, 34.7139f, 54.9105f)
            lineTo(37.0104f, 60.0293f)
            curveTo(38.9527f, 59.6692f, 40.852f, 59.102f, 42.677f, 58.3357f)
            lineTo(41.8888f, 52.7663f)
            curveTo(43.523f, 51.9602f, 45.0558f, 50.9578f, 46.4548f, 49.7808f)
            lineTo(51.1065f, 52.8233f)
            curveTo(52.5492f, 51.4518f, 53.8457f, 49.9301f, 54.9735f, 48.2817f)
            lineTo(51.3523f, 44.0287f)
            curveTo(52.299f, 42.4527f, 53.0557f, 40.7654f, 53.6069f, 39.0057f)
            lineTo(59.1361f, 39.0057f)
            curveTo(59.6217f, 37.0588f, 59.9034f, 35.0648f, 59.9769f, 33.0573f)
            lineTo(54.6707f, 31.4731f)
            curveTo(54.6296f, 29.6257f, 54.3698f, 27.7901f, 53.8982f, 26.006f)
            lineTo(58.5508f, 22.9644f)
            curveTo(57.9244f, 21.0591f, 57.102f, 19.227f, 56.0977f, 17.4981f)
            lineTo(50.7915f, 19.0841f)
            curveTo(49.7758f, 17.5533f, 48.5825f, 16.1519f, 47.2378f, 14.9098f)
            lineTo(49.5352f, 9.7918f)
            curveTo(47.9963f, 8.5334f, 46.3314f, 7.4441f, 44.5676f, 6.543f)
            lineTo(40.9465f, 10.7961f)
            curveTo(39.2781f, 10.0669f, 37.5301f, 9.5448f, 35.7392f, 9.24f)
            lineTo(34.9527f, 3.6697f)
            close()
            // Inner circle (clockwise — creates hole via nonzero fill rule)
            moveTo(32f, 18.61f)
            curveTo(39.2624f, 18.61f, 45.1582f, 24.61f, 45.1582f, 31.9997f)
            curveTo(45.1582f, 39.3902f, 39.2624f, 45.3893f, 32f, 45.3893f)
            curveTo(24.7376f, 45.3893f, 18.8418f, 39.3902f, 18.8418f, 31.9997f)
            curveTo(18.8418f, 24.61f, 24.7376f, 18.61f, 32f, 18.61f)
            close()
        }
    }.build()
}
