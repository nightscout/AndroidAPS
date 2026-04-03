package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Calculator.
 * Represents calculation or bolus calculator.
 *
 * replaces ic_calculator
 *
 * Bounding box: x: 3.0-21.0, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcCalculator: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCalculator",
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
            moveTo(17.877f, 15.934f)
            horizontalLineToRelative(-4.495f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(4.495f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveTo(18.001f, 15.99f, 17.946f, 15.934f, 17.877f, 15.934f)
            close()

            moveTo(16.465f, 14.494f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineToRelative(-1.422f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.119f)
            horizontalLineToRelative(1.672f)
            verticalLineTo(14.494f)
            close()

            moveTo(14.794f, 18.988f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(1.422f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.119f)
            horizontalLineToRelative(-1.672f)
            verticalLineTo(18.988f)
            close()

            moveTo(20.311f, 1.2f)
            horizontalLineTo(3.689f)
            curveToRelative(-0.379f, 0f, -0.686f, 0.307f, -0.686f, 0.686f)
            verticalLineToRelative(20.227f)
            curveToRelative(0f, 0.379f, 0.307f, 0.687f, 0.686f, 0.687f)
            horizontalLineToRelative(16.622f)
            curveToRelative(0.379f, 0f, 0.687f, -0.307f, 0.687f, -0.687f)
            verticalLineTo(1.886f)
            curveTo(20.997f, 1.507f, 20.69f, 1.2f, 20.311f, 1.2f)
            close()

            moveTo(19.624f, 21.426f)
            horizontalLineTo(4.375f)
            verticalLineTo(6.792f)
            horizontalLineToRelative(15.248f)
            verticalLineTo(21.426f)
            close()

            moveTo(19.624f, 5.419f)
            horizontalLineTo(4.375f)
            verticalLineTo(2.573f)
            horizontalLineToRelative(15.248f)
            verticalLineTo(5.419f)
            close()

            moveTo(13.382f, 11.551f)
            horizontalLineToRelative(4.495f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineToRelative(-4.495f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveTo(13.257f, 11.495f, 13.313f, 11.551f, 13.382f, 11.551f)
            close()

            moveTo(6.226f, 11.551f)
            horizontalLineToRelative(1.412f)
            verticalLineToRelative(1.411f)
            curveToRelative(0f, 0.069f, 0.056f, 0.125f, 0.125f, 0.125f)
            horizontalLineToRelative(1.422f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.411f)
            horizontalLineToRelative(1.412f)
            curveToRelative(0.069f, 0f, 0.125f, -0.056f, 0.125f, -0.125f)
            verticalLineToRelative(-1.422f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineTo(9.31f)
            verticalLineTo(8.468f)
            curveToRelative(0f, -0.069f, -0.056f, -0.125f, -0.125f, -0.125f)
            horizontalLineTo(7.763f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineTo(9.88f)
            horizontalLineTo(6.226f)
            curveToRelative(-0.069f, 0f, -0.125f, 0.056f, -0.125f, 0.125f)
            verticalLineToRelative(1.422f)
            curveTo(6.102f, 11.495f, 6.158f, 11.551f, 6.226f, 11.551f)
            close()

            moveTo(9.656f, 16.741f)
            lineToRelative(0.998f, -0.998f)
            curveToRelative(0.024f, -0.024f, 0.037f, -0.055f, 0.037f, -0.088f)
            curveToRelative(0f, -0.033f, -0.013f, -0.064f, -0.037f, -0.088f)
            lineToRelative(-1.006f, -1.005f)
            curveToRelative(-0.048f, -0.049f, -0.128f, -0.049f, -0.176f, 0f)
            lineToRelative(-0.998f, 0.998f)
            lineToRelative(-0.998f, -0.998f)
            curveToRelative(-0.048f, -0.049f, -0.128f, -0.049f, -0.176f, 0f)
            lineToRelative(-1.005f, 1.005f)
            curveToRelative(-0.049f, 0.048f, -0.049f, 0.128f, 0f, 0.176f)
            lineToRelative(0.998f, 0.998f)
            lineToRelative(-0.998f, 0.998f)
            curveToRelative(-0.049f, 0.048f, -0.049f, 0.128f, 0f, 0.176f)
            lineToRelative(1.005f, 1.006f)
            curveToRelative(0.023f, 0.024f, 0.055f, 0.037f, 0.088f, 0.037f)
            curveToRelative(0.033f, 0f, 0.065f, -0.013f, 0.088f, -0.037f)
            lineToRelative(0.998f, -0.998f)
            lineToRelative(0.998f, 0.998f)
            curveToRelative(0.023f, 0.024f, 0.055f, 0.037f, 0.088f, 0.037f)
            curveToRelative(0.033f, 0f, 0.065f, -0.013f, 0.088f, -0.037f)
            lineToRelative(1.006f, -1.006f)
            curveToRelative(0.049f, -0.048f, 0.049f, -0.128f, 0f, -0.176f)
            lineTo(9.656f, 16.741f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCalculatorIconPreview() {
    Icon(
        imageVector = IcCalculator,
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
<g id="ic_calculator">
	<path display="inline" fill="none" d="M0,0h24v24H0V0z"/>
	<g display="inline">
		<path fill="#67E86A" d="M17.877,15.934h-4.495c-0.069,0-0.125,0.056-0.125,0.125v1.422c0,0.069,0.056,0.125,0.125,0.125h4.495
			c0.069,0,0.125-0.056,0.125-0.125v-1.422C18.001,15.99,17.946,15.934,17.877,15.934z"/>
		<g>
			<path fill="#67E86A" d="M16.465,14.494c0-0.069-0.056-0.125-0.125-0.125h-1.422c-0.069,0-0.125,0.056-0.125,0.125v1.119h1.672
				V14.494z"/>
			<path fill="#67E86A" d="M14.794,18.988c0,0.069,0.056,0.125,0.125,0.125h1.422c0.069,0,0.125-0.056,0.125-0.125v-1.119h-1.672
				V18.988z"/>
		</g>
		<path fill="#67E86A" d="M20.311,1.2H3.689c-0.379,0-0.686,0.307-0.686,0.686v20.227c0,0.379,0.307,0.687,0.686,0.687h16.622
			c0.379,0,0.687-0.307,0.687-0.687V1.886C20.997,1.507,20.69,1.2,20.311,1.2z M19.624,21.426H4.375V6.792h15.248V21.426z
			 M19.624,5.419H4.375V2.573h15.248V5.419z"/>
		<path fill="#67E86A" d="M13.382,11.551h4.495c0.069,0,0.125-0.056,0.125-0.125v-1.422c0-0.069-0.056-0.125-0.125-0.125h-4.495
			c-0.069,0-0.125,0.056-0.125,0.125v1.422C13.257,11.495,13.313,11.551,13.382,11.551z"/>
		<path fill="#67E86A" d="M6.226,11.551h1.412v1.411c0,0.069,0.056,0.125,0.125,0.125h1.422c0.069,0,0.125-0.056,0.125-0.125v-1.411
			h1.412c0.069,0,0.125-0.056,0.125-0.125v-1.422c0-0.069-0.056-0.125-0.125-0.125H9.31V8.468c0-0.069-0.056-0.125-0.125-0.125
			H7.763c-0.069,0-0.125,0.056-0.125,0.125V9.88H6.226c-0.069,0-0.125,0.056-0.125,0.125v1.422
			C6.102,11.495,6.158,11.551,6.226,11.551z"/>
		<path fill="#67E86A" d="M9.656,16.741l0.998-0.998c0.024-0.024,0.037-0.055,0.037-0.088s-0.013-0.064-0.037-0.088l-1.006-1.005
			c-0.048-0.049-0.128-0.049-0.176,0l-0.998,0.998l-0.998-0.998c-0.048-0.049-0.128-0.049-0.176,0l-1.005,1.005
			c-0.049,0.048-0.049,0.128,0,0.176l0.998,0.998l-0.998,0.998c-0.049,0.048-0.049,0.128,0,0.176l1.005,1.006
			c0.023,0.024,0.055,0.037,0.088,0.037c0.033,0,0.065-0.013,0.088-0.037l0.998-0.998l0.998,0.998
			c0.023,0.024,0.055,0.037,0.088,0.037s0.065-0.013,0.088-0.037l1.006-1.006c0.049-0.048,0.049-0.128,0-0.176L9.656,16.741z"/>
	</g>
</g>
</svg>
 */