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
 * Icon for a loop suspended by the pump.
 *
 * Base [IcLoopPaused] ring + pause bars, with the [IcPatchPump] glyph drawn as a
 * small badge in the top-left corner. The badge is a separate additive (ink) path
 * placed via a scaled/translated [group], so the base ring is untouched and there
 * is no even-odd inversion — the badge simply merges with the ring where they meet.
 *
 * The whole vector is tinted by a single color at the call site.
 */
val IcLoopPausedPump: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcLoopPausedPump",
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

        // --- Patch-pump badge (top-left). IcPatchPump is 80x80; scale ~0.075 and
        //     translate so its center (~40,40) lands near (2.6, 2.6). ---
        group(scaleX = 0.075f, scaleY = 0.075f, translationX = -0.4f, translationY = -0.4f) {
            path(fill = SolidColor(Color.Black), fillAlpha = 1.0f) {
                // Outer shape
                moveTo(18f, 54f)
                arcToRelative(3f, 3f, 0f, false, false, 3f, 3f)
                lineTo(45f, 57f)
                arcTo(19f, 19f, 0f, false, false, 62f, 40f)
                arcTo(19f, 19f, 0f, false, false, 45f, 24f)
                lineTo(21f, 24f)
                arcToRelative(3f, 3f, 0f, false, false, -3f, 3f)
                close()
                // Inner cutout (reverse winding to create outline)
                moveTo(67f, 40f)
                arcTo(22f, 22f, 0f, false, true, 45f, 62f)
                lineTo(19f, 62f)
                arcToRelative(6f, 6f, 0f, false, true, -6f, -6f)
                lineTo(13f, 25f)
                arcToRelative(6f, 6f, 0f, false, true, 6f, -6f)
                lineTo(45f, 19f)
                arcTo(22f, 22f, 0f, false, true, 67f, 40f)
            }
        }
    }.build()
}

@ExcludeFromJacocoGeneratedReport
@Preview(showBackground = true)
@Composable
private fun IcLoopPausedPumpIconPreview() {
    Icon(
        imageVector = IcLoopPausedPump,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
