package app.aaps.pump.equil.compose

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

private val SuspendInnerColor = Color(0xFFFFBF00)
private val OutlineColor = Color(0xFF9E9E9E)

/**
 * Equil pump icon — suspend delivery (amber play/pause symbol inside pump outline).
 *
 * Replaces ic_equil_overview_suspend_delivery.xml
 */
val IcEquilSuspendDelivery: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcEquilSuspendDelivery",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f
    ).apply {
        // Inner symbol (play + pause)
        path(fill = SolidColor(SuspendInnerColor)) {
            moveTo(24f, 17.7028f)
            curveToRelative(-5.5651f, 0f, -10.077f, 4.5118f, -10.077f, 10.077f)
            curveToRelative(0f, 5.5654f, 4.5119f, 10.077f, 10.077f, 10.077f)
            curveToRelative(5.5654f, 0f, 10.077f, -4.5116f, 10.077f, -10.077f)
            curveToRelative(0.0001f, -5.5651f, -4.5116f, -10.077f, -10.077f, -10.077f)
            close()
            moveTo(23.1066f, 28.5497f)
            lineTo(19.4541f, 30.6443f)
            curveToRelative(-0.6291f, 0.3604f, -1.139f, 0.0651f, -1.139f, -0.6609f)
            lineToRelative(0f, -4.1748f)
            curveToRelative(0f, -0.7255f, 0.5101f, -1.0214f, 1.139f, -0.66f)
            lineToRelative(3.6525f, 2.0946f)
            curveToRelative(0.6291f, 0.3606f, 0.6291f, 0.9454f, 0f, 1.3065f)
            close()
            moveTo(25.7184f, 29.8873f)
            curveToRelative(0f, 0.7252f, -0.3978f, 1.3134f, -0.8895f, 1.3134f)
            curveToRelative(-0.4913f, 0f, -0.8896f, -0.5881f, -0.8896f, -1.3134f)
            lineTo(23.9393f, 25.9048f)
            curveToRelative(0f, -0.7252f, 0.3983f, -1.3136f, 0.8896f, -1.3136f)
            curveToRelative(0.4917f, 0f, 0.8895f, 0.5884f, 0.8895f, 1.3136f)
            close()
            moveTo(29.4102f, 29.8873f)
            curveToRelative(0f, 0.7252f, -0.3981f, 1.3134f, -0.8899f, 1.3134f)
            curveToRelative(-0.4916f, 0f, -0.8899f, -0.5881f, -0.8899f, -1.3134f)
            lineTo(27.6305f, 25.9048f)
            curveToRelative(0f, -0.7252f, 0.3982f, -1.3136f, 0.8899f, -1.3136f)
            curveToRelative(0.4917f, 0f, 0.8899f, 0.5884f, 0.8899f, 1.3136f)
            close()
        }
        // Pump outline
        path(
            fill = SolidColor(OutlineColor),
            strokeLineWidth = 3.17110634f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter
        ) {
            moveTo(26.0503f, 0.0008f)
            curveToRelative(-0.9065f, -0.008f, -1.8118f, 0.0786f, -2.692f, 0.2591f)
            curveToRelative(-1.1007f, 0.2257f, -2.2782f, 0.6223f, -3.2035f, 1.5466f)
            curveToRelative(-0.2205f, 0.0252f, -0.4473f, 0.052f, -0.6097f, 0.071f)
            curveToRelative(-0.5056f, 0.0591f, -0.7161f, 0.0939f, -0.8686f, 0.1313f)
            curveToRelative(-0.1524f, 0.0374f, -0.1031f, 0.0336f, -0.5942f, 0.1155f)
            curveToRelative(-2.3881f, 0.3984f, -4.4585f, 1.4641f, -5.8895f, 2.378f)
            curveToRelative(-1.914f, 1.2224f, -3.5657f, 3.3782f, -4.5216f, 4.9718f)
            curveToRelative(-1.9111f, 3.1468f, -1.7552f, 7.0686f, -1.7552f, 7.0686f)
            curveToRelative(-0.0001f, 0.0185f, -0.0174f, 6.2667f, -0.0293f, 12.7069f)
            curveToRelative(-0.006f, 3.2249f, -0.0108f, 6.4932f, -0.0111f, 9.0432f)
            curveToRelative(0f, 2.5501f, -0.002f, 4.2657f, 0.0147f, 4.7735f)
            lineToRelative(0.005f, 0.1722f)
            lineToRelative(0.033f, 0.1306f)
            curveToRelative(0.0963f, 0.9894f, 0.5193f, 1.9759f, 1.3625f, 2.87f)
            curveToRelative(0.9195f, 0.9751f, 2.4149f, 1.7154f, 4.2247f, 1.6459f)
            curveToRelative(0.0485f, 0.0005f, 5.9201f, 0.0609f, 12.0882f, 0.094f)
            curveToRelative(3.0968f, 0.0166f, 6.2604f, 0.0262f, 8.7892f, 0.0177f)
            curveToRelative(2.5287f, -0.008f, 4.2277f, -0.009f, 5.0914f, -0.099f)
            curveToRelative(1.5021f, -0.1569f, 2.7134f, -0.8701f, 3.402f, -1.8006f)
            curveToRelative(0.6885f, -0.9305f, 0.8822f, -1.9493f, 0.9035f, -2.7672f)
            curveToRelative(0.244f, -9.3552f, 0.3048f, -24.6538f, 0.3331f, -26.3083f)
            curveToRelative(0.031f, -1.8127f, -0.4748f, -4.0265f, -1.0874f, -5.5736f)
            curveToRelative(-0.3338f, -0.843f, -0.5501f, -1.3391f, -0.6966f, -1.6497f)
            curveToRelative(-0.1465f, -0.3106f, -0.3248f, -0.5865f, -0.3248f, -0.5865f)
            lineToRelative(0.0971f, 0.149f)
            curveToRelative(0f, 0f, -0.6209f, -1.1087f, -1.3966f, -2.0456f)
            curveToRelative(-0.3075f, -0.3714f, -1.0319f, -1.2428f, -2.1678f, -2.2179f)
            curveToRelative(-0.7205f, -0.6185f, -1.5583f, -1.2076f, -2.4701f, -1.6563f)
            curveToRelative(-0.1218f, -0.145f, -0.251f, -0.231f, -0.3743f, -0.3617f)
            curveToRelative(-0.2639f, -0.2799f, -0.5675f, -0.5505f, -0.9065f, -0.8223f)
            curveToRelative(-0.6781f, -0.5435f, -1.4475f, -1.0751f, -2.3117f, -1.4129f)
            curveToRelative(-1.4086f, -0.5507f, -2.9231f, -0.8294f, -4.434f, -0.8434f)
            close()
            // Camera bump / ellipse
            moveTo(26.1412f, 1.3842f)
            arcToRelative(3.9059f, 1.8896f, 5.9333f, false, true, 1.0497f, 0.069f)
            arcToRelative(3.9059f, 1.8896f, 5.9333f, false, true, 3.4505f, 2.3638f)
            arcToRelative(3.9059f, 1.8896f, 5.9333f, false, true, -4.2844f, 1.3429f)
            arcToRelative(3.9059f, 1.8896f, 5.9333f, false, true, -3.4505f, -2.3638f)
            arcToRelative(3.9059f, 1.8896f, 5.9333f, false, true, 3.2346f, -1.4119f)
            close()
            // Inner cutout
            moveTo(21.536f, 4.8819f)
            curveToRelative(0.5177f, -0.009f, 0.9256f, 0.1089f, 0.9209f, 0.1062f)
            curveToRelative(1.491f, 0.8572f, 2.6395f, 1.3835f, 3.9218f, 1.4971f)
            curveToRelative(1.2823f, 0.1137f, 2.4303f, -0.1772f, 4.0179f, -0.5811f)
            curveToRelative(0.8848f, -0.2251f, 2.7918f, 0.4895f, 4.0868f, 1.6012f)
            curveToRelative(0.94f, 0.8069f, 1.4864f, 1.4666f, 1.7907f, 1.8341f)
            curveToRelative(0.4977f, 0.6011f, 1.0898f, 1.6033f, 1.0898f, 1.6033f)
            lineToRelative(0.0445f, 0.0772f)
            lineToRelative(0.0256f, 0.0348f)
            curveToRelative(-0.005f, -0.003f, -0.006f, 0.003f, 0.0387f, 0.0976f)
            curveToRelative(0.1031f, 0.2186f, 0.299f, 0.6627f, 0.6164f, 1.4643f)
            curveToRelative(0.3992f, 1.0083f, 0.8822f, 3.3361f, 0.8649f, 4.3506f)
            curveToRelative(-0.0324f, 1.8941f, -0.0906f, 17.001f, -0.3326f, 26.2796f)
            curveToRelative(-0.009f, 0.3522f, -0.1097f, 0.7291f, -0.2819f, 0.9619f)
            curveToRelative(-0.1723f, 0.2328f, -0.3886f, 0.4505f, -1.1839f, 0.5336f)
            curveToRelative(-0.2005f, 0.021f, -2.261f, 0.0734f, -4.7728f, 0.0817f)
            curveToRelative(-2.5118f, 0.008f, -5.6699f, -0.001f, -8.7617f, -0.0177f)
            curveToRelative(-6.1837f, -0.0332f, -12.1033f, -0.0941f, -12.1033f, -0.0941f)
            lineToRelative(-0.0465f, -0.0005f)
            lineToRelative(-0.0465f, 0.002f)
            curveToRelative(-0.9787f, 0.0475f, -1.4572f, -0.2589f, -1.8283f, -0.6525f)
            curveToRelative(-0.3711f, -0.3935f, -0.5342f, -1.002f, -0.5363f, -1.081f)
            lineToRelative(-0.002f, -0.0618f)
            curveToRelative(-0.006f, -0.2205f, -0.0136f, -2.1057f, -0.013f, -4.6254f)
            curveToRelative(0f, -2.5467f, 0.005f, -5.8139f, 0.0111f, -9.0379f)
            curveToRelative(0.0119f, -6.4479f, 0.0293f, -12.7226f, 0.0293f, -12.7226f)
            verticalLineToRelative(-0.009f)
            verticalLineToRelative(-0.009f)
            curveToRelative(0f, 0f, 0.0899f, -3.4118f, 1.2957f, -5.3949f)
            lineToRelative(0.002f, -0.004f)
            lineToRelative(0.002f, -0.004f)
            curveToRelative(0.7004f, -1.1684f, 2.488f, -3.2802f, 3.5116f, -3.934f)
            curveToRelative(1.1832f, -0.7557f, 2.9538f, -1.6308f, 4.7056f, -1.923f)
            curveToRelative(0.5439f, -0.0907f, 0.7923f, -0.1549f, 0.8272f, -0.1634f)
            curveToRelative(0.0349f, -0.009f, 0f, -0.005f, 0.4821f, -0.0616f)
            curveToRelative(0.5093f, -0.0596f, 1.1055f, -0.1378f, 1.6232f, -0.1465f)
            close()
            moveTo(31.4034f, 5.2676f)
            curveToRelative(0.0845f, 0.1018f, 0.0737f, 0.2615f, 0f, 0f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcEquilSuspendDeliveryPreview() {
    Icon(
        imageVector = IcEquilSuspendDelivery,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
