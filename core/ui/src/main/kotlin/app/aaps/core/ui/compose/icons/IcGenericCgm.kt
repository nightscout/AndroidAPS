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
 * Icon for Generic CGM Plugin.
 * Represents a generic CGM integration.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcGenericCgm: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcGenericCgm",
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
            // Outer circle
            moveTo(12f, 1.2f)
            curveTo(6.035f, 1.2f, 1.2f, 6.035f, 1.2f, 12f)
            reflectiveCurveTo(6.035f, 22.8f, 12f, 22.8f)
            reflectiveCurveTo(22.8f, 17.965f, 22.8f, 12f)
            reflectiveCurveTo(17.965f, 1.2f, 12f, 1.2f)
            close()

            // First inner shape (left side)
            moveTo(7.098f, 14.67f)
            curveToRelative(-0.414f, 0.323f, -0.94f, 0.485f, -1.577f, 0.485f)
            curveToRelative(-0.788f, 0f, -1.436f, -0.277f, -1.943f, -0.831f)
            curveToRelative(-0.508f, -0.554f, -0.762f, -1.311f, -0.762f, -2.271f)
            curveToRelative(0f, -1.016f, 0.255f, -1.805f, 0.766f, -2.367f)
            reflectiveCurveToRelative(1.181f, -0.843f, 2.013f, -0.843f)
            curveToRelative(0.726f, 0f, 1.316f, 0.221f, 1.77f, 0.662f)
            curveToRelative(0.27f, 0.261f, 0.472f, 0.636f, 0.607f, 1.124f)
            lineToRelative(-1.187f, 0.292f)
            curveToRelative(-0.07f, -0.316f, -0.217f, -0.566f, -0.439f, -0.75f)
            reflectiveCurveTo(5.852f, 9.897f, 5.533f, 9.897f)
            curveToRelative(-0.44f, 0f, -0.797f, 0.163f, -1.071f, 0.487f)
            curveToRelative(-0.274f, 0.325f, -0.411f, 0.851f, -0.411f, 1.578f)
            curveToRelative(0f, 0.771f, 0.135f, 1.321f, 0.405f, 1.648f)
            reflectiveCurveToRelative(0.621f, 0.491f, 1.053f, 0.491f)
            curveToRelative(0.319f, 0f, 0.593f, -0.104f, 0.822f, -0.313f)
            curveToRelative(0.229f, -0.208f, 0.394f, -0.535f, 0.494f, -0.981f)
            lineToRelative(1.162f, 0.378f)
            curveToRelative(-0.124f, 0.477f, -0.42f, 0.972f, -0.835f, 1.295f)
            close()

            // Second inner shape (center)
            moveTo(14.361f, 14.21f)
            curveToRelative(-0.251f, 0.25f, -0.614f, 0.47f, -1.091f, 0.66f)
            curveToRelative(-0.477f, 0.189f, -0.959f, 0.285f, -1.448f, 0.285f)
            curveToRelative(-0.621f, 0f, -1.162f, -0.135f, -1.624f, -0.402f)
            curveToRelative(-0.461f, -0.268f, -0.809f, -0.65f, -1.041f, -1.148f)
            curveToRelative(-0.232f, -0.498f, -0.348f, -1.04f, -0.348f, -1.626f)
            curveToRelative(0f, -0.635f, 0.129f, -1.2f, 0.389f, -1.694f)
            curveToRelative(0.259f, -0.494f, 0.638f, -0.873f, 1.138f, -1.136f)
            curveToRelative(0.381f, -0.203f, 0.854f, -0.304f, 1.421f, -0.304f)
            curveToRelative(0.737f, 0f, 1.313f, 0.159f, 1.728f, 0.477f)
            curveToRelative(0.414f, 0.317f, 0.681f, 0.757f, 0.8f, 1.317f)
            lineToRelative(-1.191f, 0.229f)
            curveToRelative(-0.084f, -0.3f, -0.24f, -0.537f, -0.471f, -0.71f)
            curveToRelative(-0.231f, -0.173f, -0.52f, -0.26f, -0.865f, -0.26f)
            curveToRelative(-0.524f, 0f, -0.94f, 0.171f, -1.249f, 0.512f)
            reflectiveCurveToRelative(-0.464f, 0.848f, -0.464f, 1.52f)
            curveToRelative(0f, 0.725f, 0.157f, 1.268f, 0.47f, 1.63f)
            reflectiveCurveToRelative(0.724f, 0.543f, 1.231f, 0.543f)
            curveToRelative(0.251f, 0f, 0.503f, -0.051f, 0.755f, -0.152f)
            curveToRelative(0.253f, -0.101f, 0.469f, -0.224f, 0.649f, -0.368f)
            verticalLineToRelative(-0.773f)
            horizontalLineToRelative(-1.372f)
            verticalLineToRelative(-1.029f)
            horizontalLineToRelative(2.583f)
            verticalLineToRelative(2.033f)
            close()

            // Third inner shape (right side)
            moveTo(20.07f, 15.051f)
            verticalLineToRelative(-4.804f)
            lineToRelative(-1.178f, 4.804f)
            horizontalLineToRelative(-1.154f)
            lineToRelative(-1.174f, -4.804f)
            verticalLineToRelative(4.804f)
            horizontalLineToRelative(-1.114f)
            verticalLineTo(8.949f)
            horizontalLineToRelative(1.794f)
            lineToRelative(1.077f, 4.163f)
            lineToRelative(1.065f, -4.163f)
            horizontalLineToRelative(1.798f)
            verticalLineToRelative(6.103f)
            horizontalLineTo(20.07f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcGenericCgmIconPreview() {
    Icon(
        imageVector = IcGenericCgm,
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
<g id="Plugin_generic_CGM">
	<path fill="#FFFFFF" d="M12,1.2C6.035,1.2,1.2,6.035,1.2,12S6.035,22.8,12,22.8S22.8,17.965,22.8,12S17.965,1.2,12,1.2z
		 M7.098,14.67c-0.414,0.323-0.94,0.485-1.577,0.485c-0.788,0-1.436-0.277-1.943-0.831c-0.508-0.554-0.762-1.311-0.762-2.271
		c0-1.016,0.255-1.805,0.766-2.367s1.181-0.843,2.013-0.843c0.726,0,1.316,0.221,1.77,0.662c0.27,0.261,0.472,0.636,0.607,1.124
		l-1.187,0.292c-0.07-0.316-0.217-0.566-0.439-0.75S5.852,9.897,5.533,9.897c-0.44,0-0.797,0.163-1.071,0.487
		c-0.274,0.325-0.411,0.851-0.411,1.578c0,0.771,0.135,1.321,0.405,1.648s0.621,0.491,1.053,0.491c0.319,0,0.593-0.104,0.822-0.313
		c0.229-0.208,0.394-0.535,0.494-0.981l1.162,0.378C7.809,13.852,7.513,14.347,7.098,14.67z M14.361,14.21
		c-0.251,0.25-0.614,0.47-1.091,0.66c-0.477,0.189-0.959,0.285-1.448,0.285c-0.621,0-1.162-0.135-1.624-0.402
		c-0.461-0.268-0.809-0.65-1.041-1.148c-0.232-0.498-0.348-1.04-0.348-1.626c0-0.635,0.129-1.2,0.389-1.694
		c0.259-0.494,0.638-0.873,1.138-1.136c0.381-0.203,0.854-0.304,1.421-0.304c0.737,0,1.313,0.159,1.728,0.477
		c0.414,0.317,0.681,0.757,0.8,1.317l-1.191,0.229c-0.084-0.3-0.24-0.537-0.471-0.71c-0.231-0.173-0.52-0.26-0.865-0.26
		c-0.524,0-0.94,0.171-1.249,0.512s-0.464,0.848-0.464,1.52c0,0.725,0.157,1.268,0.47,1.63s0.724,0.543,1.231,0.543
		c0.251,0,0.503-0.051,0.755-0.152c0.253-0.101,0.469-0.224,0.649-0.368v-0.773h-1.372v-1.029h2.583V14.21z M20.07,15.051v-4.804
		l-1.178,4.804h-1.154l-1.174-4.804v4.804H15.45V8.949h1.794l1.077,4.163l1.065-4.163h1.798v6.103H20.07z"/>
</g>
</svg>
 */