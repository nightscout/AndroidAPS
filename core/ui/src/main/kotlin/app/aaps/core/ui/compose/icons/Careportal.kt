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
 * Icon for Careportal/Note treatment type.
 * Represents careportal entries and notes.
 * Based on ic_stylus_48dp.xml, scaled to 90% of viewport and centered.
 *
 * Viewport: 960x960
 * Drawing size: 90% of viewport (centered with 5% margin on all sides)
 */
val Careportal: ImageVector by lazy {
    ImageVector.Builder(
        name = "Careportal",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
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
            // First path: M167,840q-21,5 -36.5,-10.5T120,793l40,-191 198,198 -191,40Z
            // Scaled to 90% and translated by 48 (5% of 960)
            moveTo(198.3f, 804f)
            quadToRelative(-18.9f, 4.5f, -32.85f, -9.45f)
            reflectiveQuadTo(156f, 761.7f)
            lineToRelative(36f, -171.9f)
            lineToRelative(178.2f, 178.2f)
            lineToRelative(-171.9f, 36f)
            close()

            // Second path: M358,800L160,602l473,-473q17,-17 42,-17t42,17l114,114q17,17 17,42t-17,42L358,800Z
            moveTo(370.2f, 804f)
            lineTo(192f, 589.8f)
            lineToRelative(473.7f, -473.7f)
            quadToRelative(15.3f, -15.3f, 37.8f, -15.3f)
            reflectiveQuadToRelative(37.8f, 15.3f)
            lineToRelative(102.6f, 102.6f)
            quadToRelative(15.3f, 15.3f, 15.3f, 37.8f)
            reflectiveQuadToRelative(-15.3f, 37.8f)
            lineTo(370.2f, 804f)
            close()

            // Third path: M675,172L233,614l113,113 442,-442 -113,-113Z
            moveTo(655.5f, 202.8f)
            lineTo(257.7f, 600.6f)
            lineToRelative(101.7f, 101.7f)
            lineToRelative(397.8f, -397.8f)
            lineToRelative(-101.7f, -101.7f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun careportalPreview() {
    Icon(
        imageVector = Careportal,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp)
    )
}