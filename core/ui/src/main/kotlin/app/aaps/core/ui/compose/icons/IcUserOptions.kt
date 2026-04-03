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
 * Icon for User Entry.
 *
 * replaces ic_user_options
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcUserOptions: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcUserEntry",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF6AE86D)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(13.892f, 13.296f)
            curveToRelative(-0.192f, -0.004f, -0.396f, 0.017f, -0.472f, -0.238f)
            curveToRelative(-0.064f, -0.217f, 0.003f, -0.372f, 0.171f, -0.512f)
            curveToRelative(0.879f, -0.732f, 1.037f, -1.724f, 0.995f, -2.858f)
            curveToRelative(0f, -0.207f, 0.018f, -0.487f, -0.003f, -0.764f)
            curveToRelative(-0.099f, -1.274f, -1.099f, -2.248f, -2.404f, -2.36f)
            curveToRelative(-1.183f, -0.102f, -2.393f, 0.679f, -2.606f, 1.881f)
            curveToRelative(-0.258f, 1.454f, -0.389f, 2.954f, 0.89f, 4.105f)
            curveToRelative(0.161f, 0.146f, 0.241f, 0.288f, 0.168f, 0.508f)
            curveToRelative(-0.083f, 0.248f, -0.281f, 0.239f, -0.476f, 0.237f)
            curveToRelative(-0.486f, -0.007f, -0.935f, 0.116f, -1.358f, 0.348f)
            curveToRelative(-1.362f, 0.749f, -1.46f, 1.965f, -0.231f, 2.884f)
            curveToRelative(1.818f, 1.363f, 5.527f, 1.177f, 7.199f, -0.36f)
            curveToRelative(0.659f, -0.606f, 0.711f, -1.297f, 0.118f, -1.971f)
            curveTo(15.36f, 13.604f, 14.686f, 13.311f, 13.892f, 13.296f)
            close()

            moveTo(14.201f, 16.043f)
            curveToRelative(-1.682f, 0.68f, -3.369f, 0.698f, -4.975f, -0.221f)
            curveToRelative(-0.81f, -0.463f, -0.767f, -0.928f, 0.064f, -1.366f)
            curveToRelative(0.347f, -0.183f, 0.724f, -0.299f, 1.121f, -0.273f)
            curveToRelative(0.487f, 0.034f, 0.89f, -0.066f, 1.071f, -0.579f)
            curveToRelative(0.196f, -0.558f, 0.428f, -1.152f, -0.147f, -1.608f)
            curveToRelative(-0.925f, -0.733f, -0.987f, -1.714f, -0.932f, -2.771f)
            curveToRelative(0.055f, -1.02f, 0.714f, -1.762f, 1.622f, -1.747f)
            curveToRelative(0.926f, 0.015f, 1.581f, 0.74f, 1.604f, 1.776f)
            curveToRelative(0.004f, 0.167f, 0f, 0.334f, 0f, 0.638f)
            curveToRelative(0.136f, 0.704f, -0.12f, 1.383f, -0.789f, 1.927f)
            curveToRelative(-0.553f, 0.449f, -0.539f, 1.033f, -0.329f, 1.661f)
            curveToRelative(0.21f, 0.627f, 0.668f, 0.759f, 1.246f, 0.708f)
            curveToRelative(0.055f, -0.004f, 0.112f, 0.006f, 0.165f, 0.02f)
            curveToRelative(0.544f, 0.13f, 1.23f, 0.234f, 1.327f, 0.838f)
            curveTo(15.349f, 15.66f, 14.662f, 15.857f, 14.201f, 16.043f)
            close()

            moveTo(21.948f, 10.127f)
            horizontalLineToRelative(-1.857f)
            curveToRelative(-0.21f, -0.907f, -0.568f, -1.754f, -1.048f, -2.521f)
            lineToRelative(1.317f, -1.317f)
            curveToRelative(0.334f, -0.334f, 0.334f, -0.876f, 0f, -1.209f)
            lineToRelative(-1.437f, -1.437f)
            curveToRelative(-0.334f, -0.334f, -0.876f, -0.334f, -1.209f, 0f)
            lineTo(16.396f, 4.96f)
            curveToRelative(-0.767f, -0.48f, -1.615f, -0.838f, -2.521f, -1.048f)
            verticalLineTo(2.054f)
            curveToRelative(0f, -0.473f, -0.383f, -0.855f, -0.855f, -0.855f)
            horizontalLineToRelative(-2.033f)
            curveToRelative(-0.473f, 0f, -0.855f, 0.383f, -0.855f, 0.855f)
            verticalLineToRelative(1.857f)
            curveToRelative(-0.907f, 0.21f, -1.754f, 0.568f, -2.521f, 1.048f)
            lineToRelative(-1.32f, -1.317f)
            curveToRelative(-0.334f, -0.334f, -0.876f, -0.334f, -1.209f, 0f)
            lineTo(3.644f, 5.081f)
            curveToRelative(-0.334f, 0.334f, -0.334f, 0.876f, 0f, 1.209f)
            lineTo(4.96f, 7.607f)
            curveToRelative(-0.48f, 0.767f, -0.838f, 1.615f, -1.048f, 2.521f)
            horizontalLineTo(2.055f)
            curveToRelative(-0.473f, 0f, -0.855f, 0.383f, -0.855f, 0.855f)
            verticalLineToRelative(2.033f)
            curveToRelative(0f, 0.473f, 0.383f, 0.855f, 0.855f, 0.855f)
            horizontalLineToRelative(1.857f)
            curveToRelative(0.21f, 0.907f, 0.568f, 1.754f, 1.048f, 2.521f)
            lineTo(3.644f, 17.71f)
            curveToRelative(-0.334f, 0.334f, -0.334f, 0.876f, 0f, 1.209f)
            lineToRelative(1.437f, 1.437f)
            curveToRelative(0.334f, 0.334f, 0.876f, 0.334f, 1.209f, 0f)
            lineToRelative(1.317f, -1.317f)
            curveToRelative(0.767f, 0.48f, 1.615f, 0.838f, 2.521f, 1.048f)
            verticalLineToRelative(1.859f)
            curveToRelative(0f, 0.473f, 0.383f, 0.855f, 0.855f, 0.855f)
            horizontalLineToRelative(2.033f)
            curveToRelative(0.473f, 0f, 0.855f, -0.383f, 0.855f, -0.855f)
            verticalLineToRelative(-1.857f)
            curveToRelative(0.907f, -0.21f, 1.755f, -0.568f, 2.521f, -1.048f)
            lineToRelative(1.317f, 1.317f)
            curveToRelative(0.334f, 0.334f, 0.876f, 0.334f, 1.209f, 0f)
            lineToRelative(1.437f, -1.437f)
            curveToRelative(0.334f, -0.334f, 0.334f, -0.876f, 0f, -1.209f)
            lineToRelative(-1.317f, -1.317f)
            curveToRelative(0.48f, -0.767f, 0.838f, -1.615f, 1.048f, -2.521f)
            horizontalLineToRelative(1.859f)
            curveToRelative(0.473f, 0f, 0.855f, -0.383f, 0.855f, -0.855f)
            verticalLineToRelative(-2.033f)
            curveTo(22.804f, 10.511f, 22.421f, 10.127f, 21.948f, 10.127f)
            close()

            moveTo(12.001f, 18.911f)
            curveToRelative(-3.811f, 0f, -6.911f, -3.1f, -6.911f, -6.911f)
            reflectiveCurveToRelative(3.1f, -6.909f, 6.911f, -6.909f)
            reflectiveCurveTo(18.912f, 8.189f, 18.912f, 12f)
            reflectiveCurveTo(15.812f, 18.911f, 12.001f, 18.911f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcUserEntryIconPreview() {
    Icon(
        imageVector = IcUserOptions,
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
<g id="ic_user_entry">
	<g display="inline">
		<path fill="#6AE86D" d="M13.892,13.296c-0.192-0.004-0.396,0.017-0.472-0.238c-0.064-0.217,0.003-0.372,0.171-0.512
			c0.879-0.732,1.037-1.724,0.995-2.858c0-0.207,0.018-0.487-0.003-0.764c-0.099-1.274-1.099-2.248-2.404-2.36
			c-1.183-0.102-2.393,0.679-2.606,1.881c-0.258,1.454-0.389,2.954,0.89,4.105c0.161,0.146,0.241,0.288,0.168,0.508
			c-0.083,0.248-0.281,0.239-0.476,0.237c-0.486-0.007-0.935,0.116-1.358,0.348c-1.362,0.749-1.46,1.965-0.231,2.884
			c1.818,1.363,5.527,1.177,7.199-0.36c0.659-0.606,0.711-1.297,0.118-1.971C15.36,13.604,14.686,13.311,13.892,13.296z
			 M14.201,16.043c-1.682,0.68-3.369,0.698-4.975-0.221c-0.81-0.463-0.767-0.928,0.064-1.366c0.347-0.183,0.724-0.299,1.121-0.273
			c0.487,0.034,0.89-0.066,1.071-0.579c0.196-0.558,0.428-1.152-0.147-1.608c-0.925-0.733-0.987-1.714-0.932-2.771
			c0.055-1.02,0.714-1.762,1.622-1.747c0.926,0.015,1.581,0.74,1.604,1.776c0.004,0.167,0,0.334,0,0.638
			c0.136,0.704-0.12,1.383-0.789,1.927c-0.553,0.449-0.539,1.033-0.329,1.661c0.21,0.627,0.668,0.759,1.246,0.708
			c0.055-0.004,0.112,0.006,0.165,0.02c0.544,0.13,1.23,0.234,1.327,0.838C15.349,15.66,14.662,15.857,14.201,16.043z"/>
		<path fill="#6AE86D" d="M21.948,10.127h-1.857c-0.21-0.907-0.568-1.754-1.048-2.521l1.317-1.317c0.334-0.334,0.334-0.876,0-1.209
			l-1.437-1.437c-0.334-0.334-0.876-0.334-1.209,0L16.396,4.96c-0.767-0.48-1.615-0.838-2.521-1.048V2.054
			c0-0.473-0.383-0.855-0.855-0.855h-2.033c-0.473,0-0.855,0.383-0.855,0.855v1.857c-0.907,0.21-1.754,0.568-2.521,1.048
			l-1.32-1.317c-0.334-0.334-0.876-0.334-1.209,0L3.644,5.081c-0.334,0.334-0.334,0.876,0,1.209L4.96,7.607
			c-0.48,0.767-0.838,1.615-1.048,2.521H2.055c-0.473,0-0.855,0.383-0.855,0.855v2.033c0,0.473,0.383,0.855,0.855,0.855h1.857
			c0.21,0.907,0.568,1.754,1.048,2.521L3.644,17.71c-0.334,0.334-0.334,0.876,0,1.209l1.437,1.437c0.334,0.334,0.876,0.334,1.209,0
			l1.317-1.317c0.767,0.48,1.615,0.838,2.521,1.048v1.859c0,0.473,0.383,0.855,0.855,0.855h2.033c0.473,0,0.855-0.383,0.855-0.855
			v-1.857c0.907-0.21,1.755-0.568,2.521-1.048l1.317,1.317c0.334,0.334,0.876,0.334,1.209,0l1.437-1.437
			c0.334-0.334,0.334-0.876,0-1.209l-1.317-1.317c0.48-0.767,0.838-1.615,1.048-2.521h1.859c0.473,0,0.855-0.383,0.855-0.855v-2.033
			C22.804,10.511,22.421,10.127,21.948,10.127z M12.001,18.911c-3.811,0-6.911-3.1-6.911-6.911s3.1-6.909,6.911-6.909
			S18.912,8.189,18.912,12S15.812,18.911,12.001,18.911z"/>
	</g>
</g>
</svg>
 */