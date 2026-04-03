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
 * Icon for CGM insert treatment type.
 * Represents continuous glucose monitor sensor insertion.
 *
 * replaces ic_cp_cgm_insert
 *
 * Bounding box: x: 1.2-22.6, y: 5.7-18.8 (viewport: 24x24, ~89% width)
 */
val IcCgmInsert: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCgmInsert",
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
            moveTo(22.395f, 7.073f)
            lineToRelative(-1.81f, -1.844f)
            lineTo(6.924f, 5.742f)
            curveToRelative(-1.555f, 0.021f, -2.648f, 0.927f, -2.99f, 1.754f)
            curveTo(2.66f, 8.461f, 1.273f, 10.1f, 1.209f, 10.763f)
            curveToRelative(-0.035f, 0.365f, 0.039f, 0.652f, 0.221f, 0.852f)
            curveToRelative(0.106f, 0.117f, 0.299f, 0.257f, 0.612f, 0.257f)
            horizontalLineToRelative(18.743f)
            curveToRelative(0.735f, 0f, 1.31f, -0.737f, 1.31f, -1.678f)
            curveToRelative(0f, -0.066f, -0.012f, -0.14f, -0.029f, -0.218f)
            curveToRelative(0.226f, -0.184f, 0.411f, -0.438f, 0.541f, -0.753f)
            curveTo(22.931f, 8.442f, 22.838f, 7.516f, 22.395f, 7.073f)
            close()

            moveTo(4.982f, 7.508f)
            curveTo(5.147f, 7.31f, 5.777f, 6.661f, 6.947f, 6.645f)
            lineToRelative(13.272f, -0.498f)
            lineToRelative(1.534f, 1.562f)
            curveToRelative(0.142f, 0.142f, 0.222f, 0.681f, 0.02f, 1.169f)
            curveToRelative(-0.149f, 0.36f, -0.383f, 0.542f, -0.696f, 0.542f)
            horizontalLineToRelative(-2.813f)
            lineToRelative(-2.437f, -0.582f)
            horizontalLineToRelative(-2.23f)
            curveToRelative(-0.469f, 0f, -0.714f, -0.154f, -0.729f, -0.459f)
            lineToRelative(-0.021f, -0.43f)
            lineToRelative(-8.115f, 0f)
            curveTo(4.756f, 7.854f, 4.827f, 7.695f, 4.982f, 7.508f)
            close()

            moveTo(20.785f, 10.968f)
            lineToRelative(-18.681f, 0f)
            curveToRelative(-0.002f, -0.026f, -0.002f, -0.061f, 0.002f, -0.106f)
            curveToRelative(0.063f, -0.283f, 0.9f, -1.393f, 1.92f, -2.278f)
            curveTo(4.177f, 8.75f, 4.402f, 8.848f, 4.677f, 8.853f)
            horizontalLineToRelative(7.373f)
            curveToRelative(0.205f, 0.558f, 0.764f, 0.889f, 1.547f, 0.889f)
            horizontalLineToRelative(2.123f)
            lineToRelative(2.437f, 0.582f)
            horizontalLineToRelative(2.919f)
            curveToRelative(0.036f, 0f, 0.072f, -0.002f, 0.108f, -0.004f)
            curveTo(21.146f, 10.724f, 20.939f, 10.968f, 20.785f, 10.968f)
            close()

            moveTo(15.113f, 15.365f)
            curveToRelative(-0.171f, -0.2f, -0.473f, -0.223f, -0.673f, -0.053f)
            lineToRelative(-2.276f, 1.945f)
            verticalLineToRelative(-4.179f)
            curveToRelative(0f, -0.264f, -0.214f, -0.478f, -0.478f, -0.478f)
            curveToRelative(-0.264f, 0f, -0.478f, 0.214f, -0.478f, 0.478f)
            verticalLineToRelative(4.179f)
            lineTo(8.93f, 15.312f)
            curveToRelative(-0.2f, -0.171f, -0.502f, -0.148f, -0.673f, 0.053f)
            curveToRelative(-0.172f, 0.201f, -0.148f, 0.502f, 0.053f, 0.673f)
            lineToRelative(3.065f, 2.618f)
            curveToRelative(0.024f, 0.02f, 0.051f, 0.03f, 0.077f, 0.045f)
            curveToRelative(0.018f, 0.01f, 0.034f, 0.023f, 0.053f, 0.031f)
            curveToRelative(0.058f, 0.024f, 0.119f, 0.039f, 0.18f, 0.039f)
            curveToRelative(0.062f, 0f, 0.122f, -0.015f, 0.18f, -0.039f)
            curveToRelative(0.019f, -0.008f, 0.035f, -0.021f, 0.053f, -0.031f)
            curveToRelative(0.026f, -0.015f, 0.054f, -0.025f, 0.077f, -0.045f)
            lineToRelative(3.065f, -2.618f)
            curveTo(15.26f, 15.866f, 15.284f, 15.565f, 15.113f, 15.365f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCgmInsertIconPreview() {
    Icon(
        imageVector = IcCgmInsert,
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
<g id="ic_cgm_insert">
	<g display="inline">
		<path fill="#67DFE8" d="M22.395,7.073l-1.81-1.844L6.924,5.742c-1.555,0.021-2.648,0.927-2.99,1.754
			C2.66,8.461,1.273,10.1,1.209,10.763c-0.035,0.365,0.039,0.652,0.221,0.852c0.106,0.117,0.299,0.257,0.612,0.257h18.743
			c0.735,0,1.31-0.737,1.31-1.678c0-0.066-0.012-0.14-0.029-0.218c0.226-0.184,0.411-0.438,0.541-0.753
			C22.931,8.442,22.838,7.516,22.395,7.073z M4.982,7.508C5.147,7.31,5.777,6.661,6.947,6.645l13.272-0.498l1.534,1.562
			c0.142,0.142,0.222,0.681,0.02,1.169c-0.149,0.36-0.383,0.542-0.696,0.542h-2.813l-2.437-0.582h-2.23
			c-0.469,0-0.714-0.154-0.729-0.459l-0.021-0.43l-8.115,0C4.756,7.854,4.827,7.695,4.982,7.508z M20.785,10.968l-18.681,0
			c-0.002-0.026-0.002-0.061,0.002-0.106c0.063-0.283,0.9-1.393,1.92-2.278C4.177,8.75,4.402,8.848,4.677,8.853h7.373
			c0.205,0.558,0.764,0.889,1.547,0.889h2.123l2.437,0.582h2.919c0.036,0,0.072-0.002,0.108-0.004
			C21.146,10.724,20.939,10.968,20.785,10.968z"/>
		<path fill="#67DFE8" d="M15.113,15.365c-0.171-0.2-0.473-0.223-0.673-0.053l-2.276,1.945v-4.179c0-0.264-0.214-0.478-0.478-0.478
			s-0.478,0.214-0.478,0.478v4.179L8.93,15.312c-0.2-0.171-0.502-0.148-0.673,0.053c-0.172,0.201-0.148,0.502,0.053,0.673
			l3.065,2.618c0.024,0.02,0.051,0.03,0.077,0.045c0.018,0.01,0.034,0.023,0.053,0.031c0.058,0.024,0.119,0.039,0.18,0.039
			c0.062,0,0.122-0.015,0.18-0.039c0.019-0.008,0.035-0.021,0.053-0.031c0.026-0.015,0.054-0.025,0.077-0.045l3.065-2.618
			C15.26,15.866,15.284,15.565,15.113,15.365z"/>
	</g>
</g>
</svg>
 */