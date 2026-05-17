package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Generic gear icon. Used as a fallback for plugin / source display when no specific icon exists.
 *
 * replaces ic_generic_icon
 *
 * Viewport: 64x64 (gear outer cog with inner circular hole via opposing path windings)
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

@Preview(showBackground = true)
@Composable
private fun IcGenericIconIconPreview() {
    Icon(
        imageVector = IcGenericIcon,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="64"
    android:viewportHeight="64">
  <path
      android:pathData="M34.9527,3.6697C32.9895,3.458 31.0105,3.458 29.0473,3.6697L28.2608,9.24C26.4699,9.5448 24.7219,10.0669 23.0535,10.7961L19.4324,6.543C17.6686,7.4441 16.0037,8.5334 14.4648,9.7918L16.7622,14.9098C15.4176,16.1519 14.2242,17.5533 13.2085,19.0841L7.9023,17.4981C6.898,19.227 6.0756,21.0591 5.4492,22.9644L10.1018,26.006C9.6302,27.7901 9.3704,29.6257 9.3293,31.4731L4.0231,33.0573C4.0966,35.0648 4.3783,37.0588 4.8639,39.0057L10.3931,39.0057C10.9443,40.7654 11.7011,42.4527 12.6477,44.0287L9.0266,48.2817C10.1543,49.9301 11.4509,51.4518 12.8935,52.8233L17.5453,49.7808C18.9442,50.9578 20.477,51.9602 22.1113,52.7663L21.323,58.3357C23.148,59.102 25.0473,59.6692 26.9896,60.0293L29.2861,54.9105C31.0893,55.1312 32.9108,55.1312 34.7139,54.9105L37.0104,60.0293C38.9527,59.6692 40.852,59.102 42.677,58.3357L41.8888,52.7663C43.523,51.9602 45.0558,50.9578 46.4548,49.7808L51.1065,52.8233C52.5492,51.4518 53.8457,49.9301 54.9735,48.2817L51.3523,44.0287C52.299,42.4527 53.0557,40.7654 53.6069,39.0057L59.1361,39.0057C59.6217,37.0588 59.9034,35.0648 59.9769,33.0573L54.6707,31.4731C54.6296,29.6257 54.3698,27.7901 53.8982,26.006L58.5508,22.9644C57.9244,21.0591 57.102,19.227 56.0977,17.4981L50.7915,19.0841C49.7758,17.5533 48.5825,16.1519 47.2378,14.9098L49.5352,9.7918C47.9963,8.5334 46.3314,7.4441 44.5676,6.543L40.9465,10.7961C39.2781,10.0669 37.5301,9.5448 35.7392,9.24L34.9527,3.6697ZM32,18.61C39.2624,18.61 45.1582,24.61 45.1582,31.9997C45.1582,39.3902 39.2624,45.3893 32,45.3893C24.7376,45.3893 18.8418,39.3902 18.8418,31.9997C18.8418,24.61 24.7376,18.61 32,18.61Z"
      android:fillColor="?attr/colorControlNormal"/>
</vector>
 */
