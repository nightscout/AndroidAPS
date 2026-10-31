package app.aaps.core.ui.compose.icons

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
 *
 * @see IcCgmInsertIconPreview
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
