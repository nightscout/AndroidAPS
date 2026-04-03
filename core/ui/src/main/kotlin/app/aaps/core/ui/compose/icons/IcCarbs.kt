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
 * Icon for Carbs treatment type.
 * Represents carbohydrate entries.
 *
 * replaces ic_cp_bolus_carbs
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% width)
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

@Preview(showBackground = true)
@Composable
private fun IcCarbsIconPreview() {
    Icon(
        imageVector = IcCarbs,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" width="24px"
	 height="24px" viewBox="0 0 24 24" enable-background="new 0 0 24 24" xml:space="preserve">
<g id="ic_carbs">
	<path display="inline" fill="none" d="M0,0h24v24H0V0z"/>
	<path display="inline" fill="#FEAF05" d="M18.885,15.904c-0.607,0.761-1.263,1.32-1.992,1.815
		c-0.691,0.469-1.542,0.712-2.199,0.855c-1.257,0.229-0.457,0.343,0.175,1.201c-2.279,3.255-6.121,3.472-9.326,1.811
		c-0.257-0.133-0.498-0.301-0.737-0.466C4.3,20.773,4.078,20.69,3.655,21.127c-0.342,0.354-1.148,1.13-1.558,1.532L1.2,21.687
		c0.44-0.4,0.948-0.897,1.312-1.239c0.795-0.747,0.614-0.837-0.02-1.745c-2.204-3.153-1.529-7.269,1.57-9.572
		c1.32,0.845,1.035,0.645,1.464-0.598c0.458-1.327,1.238-2.459,2.422-3.248c1.204,1.064,1.004,0.578,1.488-0.642
		c0.523-1.319,1.342-2.393,2.473-3.301c1.271,0.837,1.854,1.767,2.289,2.812c0.499,1.198,0.692,0.796,1.568-0.054
		c1.87-1.813,4.155-2.533,6.383-1.987c0.524,2.899-0.714,5.493-3.399,7.188c1.409,0.381,2.742,1.143,4.05,2.684
		c-0.802,1.175-2.217,2.302-3.644,2.541C17.114,14.868,18.264,14.946,18.885,15.904z M20.915,4.178
		c0.023-0.808-0.233-1.023-0.933-0.929c-2.274,0.305-4.411,2.347-4.791,4.577c-0.131,0.771,0.274,1.184,1.044,1.007
		c0.489-0.113,0.983-0.263,1.431-0.485C19.462,7.457,20.578,6.034,20.915,4.178z M13.739,7.363c0.02-1.277-0.422-2.3-1.034-3.261
		c-0.589-0.925-0.998-0.922-1.587-0.021c-1.36,2.081-0.807,4.5,0.277,6.276c0.347,0.568,0.814,0.573,1.222,0.02
		C13.297,9.457,13.764,8.442,13.739,7.363z M9.473,21.345c0.945,0,2.276-0.426,2.957-0.942c0.58-0.439,0.628-0.797,0.055-1.192
		c-2.128-1.467-4.303-1.786-6.553-0.218c-0.757,0.528-0.714,0.957,0.115,1.444C7.103,21.059,8.257,21.32,9.473,21.345z
		 M9.809,11.176C9.845,9.96,9.387,8.904,8.768,7.911C8.191,6.984,7.796,7.006,7.206,7.957c-1.137,1.835-1.041,4.403,0.23,6.16
		c0.441,0.61,0.821,0.649,1.292,0.055C9.42,13.299,9.834,12.308,9.809,11.176z M17.046,13.63c1.209-0.005,2.524-0.396,3.21-0.918
		c0.586-0.445,0.602-0.736,0.066-1.206c-1.711-1.5-4.815-1.597-6.59-0.207c-0.642,0.503-0.654,0.963,0.034,1.347
		C14.819,13.235,15.934,13.665,17.046,13.63z M2.523,14.762C2.48,15.764,2.75,16.802,3.34,17.746
		c0.558,0.893,1.039,0.903,1.632,0.066c1.288-1.819,1.179-4.583-0.247-6.285c-0.465-0.555-0.861-0.552-1.304,0.056
		C2.76,12.493,2.51,13.535,2.523,14.762z M12.957,14.157c-1.219-0.057-2.285,0.352-3.235,1.076c-0.475,0.362-0.495,0.76-0.009,1.162
		c1.558,1.287,4.921,1.398,6.579,0.208c0.671-0.481,0.688-0.881,0.009-1.327C15.287,14.609,14.202,14.127,12.957,14.157z"/>
</g>
</svg>
 */