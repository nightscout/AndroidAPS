package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Icon for CareLevo Pump Plugin.
 *
 * Single-color design using NonZero winding to create holes:
 *   CW outer body  → filled border frame
 *   CCW inner panel → transparent window
 *   CCW connector dots (in border) → transparent holes
 *   CW  lens circle  → ring fill
 *   CCW lens center  → transparent center of ring
 *   CW  text bars    → filled bars inside window
 *
 * Works correctly with any tint color (no multi-color tricks).
 */
val IcPluginCarelevo: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginCarelevo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {

        // ── Combined body + all winding-based cutouts/fills ───────────────────
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            // ── 1. Outer device body (CW) ─ winding = +1 ──────────────────────
            moveTo(5.9f, 4.5f)
            lineTo(18.1f, 4.5f)
            curveTo(20.5f, 4.5f, 22.5f, 6.5f, 22.5f, 8.9f)
            lineTo(22.5f, 15.1f)
            curveTo(22.5f, 17.5f, 20.5f, 19.5f, 18.1f, 19.5f)
            lineTo(5.9f, 19.5f)
            curveTo(3.5f, 19.5f, 1.5f, 17.5f, 1.5f, 15.1f)
            lineTo(1.5f, 8.9f)
            curveTo(1.5f, 6.5f, 3.5f, 4.5f, 5.9f, 4.5f)
            close()

            // ── 2. Inner panel (CCW) ─ cancels to 0 → transparent window ──────
            //      Start TL, go DOWN first (CCW in screen-Y-down coords)
            moveTo(5.8f, 6.5f)
            curveTo(4.58f, 6.5f, 3.6f, 7.48f, 3.6f, 8.7f)
            lineTo(3.6f, 15.3f)
            curveTo(3.6f, 16.52f, 4.58f, 17.5f, 5.8f, 17.5f)
            lineTo(18.2f, 17.5f)
            curveTo(19.42f, 17.5f, 20.4f, 16.52f, 20.4f, 15.3f)
            lineTo(20.4f, 8.7f)
            curveTo(20.4f, 7.48f, 19.42f, 6.5f, 18.2f, 6.5f)
            close()

            // ── 3. Connector dot holes (CCW, r=0.4) ─ transparent holes ───────
            //      isPositiveArc=false → CCW arc → creates hole
            // Top connector  (center 6.9, 5.8)
            moveTo(7.3f, 5.8f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -0.8f, dy1 = 0f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0.8f, dy1 = 0f)
            // Bottom connector (center 6.9, 18.3)
            moveTo(7.3f, 18.3f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -0.8f, dy1 = 0f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0.8f, dy1 = 0f)
            // Right connector  (center 21.5, 12.0)
            moveTo(21.9f, 12.0f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -0.8f, dy1 = 0f)
            arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0.8f, dy1 = 0f)

            // ── 4. CARELEVO text bars (CW rects) ─ +1 → fills inside window ───
            //      Go RIGHT first from TL (CW in screen-Y-down coords)
            moveTo(13.2f, 10.7f)
            lineTo(19.5f, 10.7f)
            lineTo(19.5f, 11.2f)
            lineTo(13.2f, 11.2f)
            close()
            moveTo(13.2f, 11.75f)
            lineTo(18.3f, 11.75f)
            lineTo(18.3f, 12.25f)
            lineTo(13.2f, 12.25f)
            close()
            moveTo(13.2f, 12.8f)
            lineTo(19.5f, 12.8f)
            lineTo(19.5f, 13.3f)
            lineTo(13.2f, 13.3f)
            close()
        }

        // ── Lens arc – 300° open arc, gap on right (C보다 조금 더 길게) ──────────
        // Center (10.1, 12.0), r = 1.75
        // Start: 30° above right  → (11.62, 11.13)
        // End  : 30° below right  → (11.62, 12.88)
        // Long CCW arc (300°) going up → left → down
        path(
            fill = SolidColor(Color.Transparent),
            fillAlpha = 0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 0.85f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f
        ) {
            moveTo(11.62f, 11.13f)
            arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 0f, dy1 = 1.75f)
        }

    }.build()
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun IcPluginCarelevoPreview() {
    Icon(
        imageVector = IcPluginCarelevo,
        contentDescription = null,
        modifier = Modifier
            .padding(8.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
