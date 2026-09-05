package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Carbs treatment type.
 * Represents carbohydrate entries.
 *
 * replaces ic_cp_bolus_carbs
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% width)
 *
 * @see IcCarbsIconPreview
 */
val IcCarbs: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCarbs",
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
            moveTo(18.885f, 15.904f)
            curveToRelative(-0.607f, 0.761f, -1.263f, 1.32f, -1.992f, 1.815f)
            curveToRelative(-0.691f, 0.469f, -1.542f, 0.712f, -2.199f, 0.855f)
            curveToRelative(-1.257f, 0.229f, -0.457f, 0.343f, 0.175f, 1.201f)
            curveToRelative(-2.279f, 3.255f, -6.121f, 3.472f, -9.326f, 1.811f)
            curveToRelative(-0.257f, -0.133f, -0.498f, -0.301f, -0.737f, -0.466f)
            curveTo(4.3f, 20.773f, 4.078f, 20.69f, 3.655f, 21.127f)
            curveToRelative(-0.342f, 0.354f, -1.148f, 1.13f, -1.558f, 1.532f)
            lineTo(1.2f, 21.687f)
            curveToRelative(0.44f, -0.4f, 0.948f, -0.897f, 1.312f, -1.239f)
            curveToRelative(0.795f, -0.747f, 0.614f, -0.837f, -0.02f, -1.745f)
            curveToRelative(-2.204f, -3.153f, -1.529f, -7.269f, 1.57f, -9.572f)
            curveToRelative(1.32f, 0.845f, 1.035f, 0.645f, 1.464f, -0.598f)
            curveToRelative(0.458f, -1.327f, 1.238f, -2.459f, 2.422f, -3.248f)
            curveToRelative(1.204f, 1.064f, 1.004f, 0.578f, 1.488f, -0.642f)
            curveToRelative(0.523f, -1.319f, 1.342f, -2.393f, 2.473f, -3.301f)
            curveToRelative(1.271f, 0.837f, 1.854f, 1.767f, 2.289f, 2.812f)
            curveToRelative(0.499f, 1.198f, 0.692f, 0.796f, 1.568f, -0.054f)
            curveToRelative(1.87f, -1.813f, 4.155f, -2.533f, 6.383f, -1.987f)
            curveToRelative(0.524f, 2.899f, -0.714f, 5.493f, -3.399f, 7.188f)
            curveToRelative(1.409f, 0.381f, 2.742f, 1.143f, 4.05f, 2.684f)
            curveToRelative(-0.802f, 1.175f, -2.217f, 2.302f, -3.644f, 2.541f)
            curveTo(17.114f, 14.868f, 18.264f, 14.946f, 18.885f, 15.904f)
            close()

            moveTo(20.915f, 4.178f)
            curveToRelative(0.023f, -0.808f, -0.233f, -1.023f, -0.933f, -0.929f)
            curveToRelative(-2.274f, 0.305f, -4.411f, 2.347f, -4.791f, 4.577f)
            curveToRelative(-0.131f, 0.771f, 0.274f, 1.184f, 1.044f, 1.007f)
            curveToRelative(0.489f, -0.113f, 0.983f, -0.263f, 1.431f, -0.485f)
            curveTo(19.462f, 7.457f, 20.578f, 6.034f, 20.915f, 4.178f)
            close()

            moveTo(13.739f, 7.363f)
            curveToRelative(0.02f, -1.277f, -0.422f, -2.3f, -1.034f, -3.261f)
            curveToRelative(-0.589f, -0.925f, -0.998f, -0.922f, -1.587f, -0.021f)
            curveToRelative(-1.36f, 2.081f, -0.807f, 4.5f, 0.277f, 6.276f)
            curveToRelative(0.347f, 0.568f, 0.814f, 0.573f, 1.222f, 0.02f)
            curveTo(13.297f, 9.457f, 13.764f, 8.442f, 13.739f, 7.363f)
            close()

            moveTo(9.473f, 21.345f)
            curveToRelative(0.945f, 0f, 2.276f, -0.426f, 2.957f, -0.942f)
            curveToRelative(0.58f, -0.439f, 0.628f, -0.797f, 0.055f, -1.192f)
            curveToRelative(-2.128f, -1.467f, -4.303f, -1.786f, -6.553f, -0.218f)
            curveToRelative(-0.757f, 0.528f, -0.714f, 0.957f, 0.115f, 1.444f)
            curveTo(7.103f, 21.059f, 8.257f, 21.32f, 9.473f, 21.345f)
            close()

            moveTo(9.809f, 11.176f)
            curveToRelative(0.036f, -1.216f, -0.422f, -2.272f, -1.041f, -3.265f)
            curveToRelative(-0.577f, -0.927f, -0.972f, -0.905f, -1.562f, 0.046f)
            curveToRelative(-1.137f, 1.835f, -1.041f, 4.403f, 0.23f, 6.16f)
            curveToRelative(0.441f, 0.61f, 0.821f, 0.649f, 1.292f, 0.055f)
            curveTo(9.42f, 13.299f, 9.834f, 12.308f, 9.809f, 11.176f)
            close()

            moveTo(17.046f, 13.63f)
            curveToRelative(1.209f, -0.005f, 2.524f, -0.396f, 3.21f, -0.918f)
            curveToRelative(0.586f, -0.445f, 0.602f, -0.736f, 0.066f, -1.206f)
            curveToRelative(-1.711f, -1.5f, -4.815f, -1.597f, -6.59f, -0.207f)
            curveToRelative(-0.642f, 0.503f, -0.654f, 0.963f, 0.034f, 1.347f)
            curveTo(14.819f, 13.235f, 15.934f, 13.665f, 17.046f, 13.63f)
            close()

            moveTo(2.523f, 14.762f)
            curveToRelative(-0.043f, 1.002f, 0.227f, 2.04f, 0.817f, 2.984f)
            curveToRelative(0.558f, 0.893f, 1.039f, 0.903f, 1.632f, 0.066f)
            curveToRelative(1.288f, -1.819f, 1.179f, -4.583f, -0.247f, -6.285f)
            curveToRelative(-0.465f, -0.555f, -0.861f, -0.552f, -1.304f, 0.056f)
            curveTo(2.76f, 12.493f, 2.51f, 13.535f, 2.523f, 14.762f)
            close()

            moveTo(12.957f, 14.157f)
            curveToRelative(-1.219f, -0.057f, -2.285f, 0.352f, -3.235f, 1.076f)
            curveToRelative(-0.475f, 0.362f, -0.495f, 0.76f, -0.009f, 1.162f)
            curveToRelative(1.558f, 1.287f, 4.921f, 1.398f, 6.579f, 0.208f)
            curveToRelative(0.671f, -0.481f, 0.688f, -0.881f, 0.009f, -1.327f)
            curveTo(15.287f, 14.609f, 14.202f, 14.127f, 12.957f, 14.157f)
            close()
        }
    }.build()
}
