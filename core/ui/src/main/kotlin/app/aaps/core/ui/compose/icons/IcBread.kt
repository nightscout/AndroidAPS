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
 * Icon for Bread.
 * Represents a loaf of bread with score marks.
 */
val IcBread: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcBread",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Loaf body
            moveTo(3.2f, 14f)
            quadTo(3.2f, 8.4f, 12f, 8.4f)
            quadTo(20.8f, 8.4f, 20.8f, 14f)
            lineTo(20.8f, 16f)
            quadTo(20.8f, 18f, 12f, 18f)
            quadTo(3.2f, 18f, 3.2f, 16f)
            close()
            // Score marks
            moveTo(7.2f, 10.4f)
            quadTo(8.8f, 9.2f, 10.4f, 10.8f)
            moveTo(10.8f, 10f)
            quadTo(12.4f, 8.8f, 14f, 10.4f)
            moveTo(14.4f, 10.8f)
            quadTo(16f, 9.6f, 17.6f, 11.2f)
            // Base seam
            moveTo(4f, 16f)
            quadTo(12f, 17.2f, 20f, 16f)
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcBreadIconPreview() {
    Icon(
        imageVector = IcBread,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
