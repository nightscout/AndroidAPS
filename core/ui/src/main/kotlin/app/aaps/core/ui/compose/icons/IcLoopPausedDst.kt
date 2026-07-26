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
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.ExcludeFromJacocoGeneratedReport

/**
 * Icon for a loop suspended by DST (daylight-saving time change).
 *
 * Base [IcLoopPaused] ring + pause bars, with the [IcHistory] clock glyph drawn as
 * a small badge in the top-left corner. The badge is a separate additive (ink) path
 * placed via a scaled/translated [group], so the base ring is untouched and there
 * is no even-odd inversion — the badge simply merges with the ring where they meet.
 *
 * The whole vector is tinted by a single color at the call site.
 */
val IcLoopPausedDst: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopPausedDst",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // --- Base "paused loop" (verbatim from IcLoopPaused) ---
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
            moveTo(22.8f, 9.19f)
            lineToRelative(-5.687f, -3.903f)
            lineToRelative(-1.306f, 6.578f)
            lineToRelative(2.068f, -1.728f)
            curveToRelative(0.014f, 0.055f, 0.03f, 0.109f, 0.042f, 0.165f)
            curveToRelative(0.114f, 0.503f, 0.18f, 1.025f, 0.18f, 1.563f)
            curveToRelative(0f, 3.888f, -3.152f, 7.039f, -7.039f, 7.039f)
            curveToRelative(-3.888f, 0f, -7.039f, -3.152f, -7.039f, -7.039f)
            curveToRelative(0f, -3.888f, 3.152f, -7.039f, 7.039f, -7.039f)
            curveToRelative(1.054f, 0f, 2.051f, 0.238f, 2.949f, 0.654f)
            curveToRelative(0.32f, 0.148f, 0.629f, 0.316f, 0.921f, 0.508f)
            lineToRelative(0.002f, -0.002f)
            lineToRelative(-0.346f, -1.755f)
            lineToRelative(1.845f, -0.529f)
            curveToRelative(-1.542f, -1.017f, -3.386f, -1.612f, -5.371f, -1.612f)
            curveToRelative(-5.399f, 0f, -9.775f, 4.376f, -9.775f, 9.775f)
            curveToRelative(0f, 5.399f, 4.376f, 9.775f, 9.775f, 9.775f)
            curveToRelative(5.399f, 0f, 9.775f, -4.376f, 9.775f, -9.775f)
            curveToRelative(0f, -0.747f, -0.091f, -1.471f, -0.25f, -2.17f)
            curveToRelative(-0.039f, -0.173f, -0.084f, -0.344f, -0.132f, -0.514f)
            lineTo(22.8f, 9.19f)
            lineTo(22.8f, 9.19f)
            close()

            moveTo(7.861f, 8.315f)
            horizontalLineToRelative(2.09f)
            verticalLineToRelative(6.978f)
            horizontalLineToRelative(-2.09f)
            verticalLineTo(8.315f)
            close()

            moveTo(12.041f, 8.315f)
            horizontalLineToRelative(2.09f)
            verticalLineToRelative(6.978f)
            horizontalLineToRelative(-2.09f)
            verticalLineTo(8.315f)
            close()
        }

        // --- Clock badge (top-left). IcHistory is 24x24; scale ~0.19 and
        //     translate so its center (~12,12) lands near (2.6, 2.6). ---
        group(scaleX = 0.19f, scaleY = 0.19f, translationX = 0.32f, translationY = 0.32f) {
            path(fill = SolidColor(Color.Black), fillAlpha = 1.0f) {
                moveTo(13.198f, 2.399f)
                curveToRelative(-5.107f, 0f, -9.283f, 4.011f, -9.573f, 9.047f)
                lineTo(2.529f, 10.35f)
                curveToRelative(-0.305f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
                curveToRelative(-0.304f, 0.305f, -0.304f, 0.797f, 0f, 1.101f)
                lineToRelative(2.397f, 2.396f)
                curveToRelative(0.152f, 0.151f, 0.352f, 0.228f, 0.551f, 0.228f)
                curveToRelative(0.199f, 0f, 0.399f, -0.076f, 0.551f, -0.228f)
                lineToRelative(2.396f, -2.396f)
                curveToRelative(0.304f, -0.304f, 0.304f, -0.797f, 0f, -1.101f)
                curveToRelative(-0.304f, -0.304f, -0.797f, -0.304f, -1.101f, 0f)
                lineToRelative(-1.036f, 1.036f)
                curveToRelative(0.316f, -4.149f, 3.785f, -7.431f, 8.013f, -7.431f)
                curveToRelative(4.436f, 0f, 8.045f, 3.609f, 8.045f, 8.045f)
                curveToRelative(0f, 4.436f, -3.609f, 8.045f, -8.045f, 8.045f)
                curveToRelative(-2.19f, 0f, -4.239f, -0.869f, -5.77f, -2.448f)
                curveToRelative(-0.3f, -0.308f, -0.793f, -0.315f, -1.101f, -0.017f)
                curveToRelative(-0.309f, 0.299f, -0.316f, 0.793f, -0.017f, 1.101f)
                curveToRelative(1.827f, 1.883f, 4.273f, 2.92f, 6.888f, 2.92f)
                curveToRelative(5.294f, 0f, 9.602f, -4.307f, 9.602f, -9.602f)
                reflectiveCurveTo(18.493f, 2.399f, 13.198f, 2.399f)
                close()

                moveTo(13.198f, 12.778f)
                horizontalLineToRelative(4.348f)
                curveToRelative(0.43f, 0f, 0.778f, -0.349f, 0.778f, -0.778f)
                curveToRelative(0f, -0.43f, -0.348f, -0.778f, -0.778f, -0.778f)
                horizontalLineToRelative(-3.57f)
                verticalLineTo(6.202f)
                curveToRelative(0f, -0.43f, -0.349f, -0.778f, -0.778f, -0.778f)
                reflectiveCurveToRelative(-0.778f, 0.348f, -0.778f, 0.777f)
                verticalLineTo(12f)
                curveTo(12.42f, 12.429f, 12.769f, 12.778f, 13.198f, 12.778f)
                close()
            }
        }
    }.build()
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun IcLoopPausedDstIconPreview() {
    Icon(
        imageVector = IcLoopPausedDst,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
