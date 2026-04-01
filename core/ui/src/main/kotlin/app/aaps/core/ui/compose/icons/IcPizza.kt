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
 * Icon for Pizza.
 * Represents a pizza slice with pepperoni and olives.
 */
val IcPizza: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPizza",
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
            // Pizza slice outline
            moveTo(12f, 3.2f)
            lineTo(3.6f, 19.2f)
            quadTo(8f, 21.6f, 12f, 22f)
            quadTo(16f, 21.6f, 20.4f, 19.2f)
            close()
            // Crust arc
            moveTo(4.4f, 18.8f)
            quadTo(8f, 20.8f, 12f, 21.2f)
            quadTo(16f, 20.8f, 19.6f, 18.8f)
            // Pepperoni circles (r=1.2)
            moveTo(11.6f, 12f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.8f, 12f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 11.6f, 12f)
            moveTo(15.6f, 13.6f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.2f, 13.6f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.6f, 13.6f)
            moveTo(12.4f, 16.8f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10f, 16.8f)
            arcTo(1.2f, 1.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.4f, 16.8f)
            // Olive 1 (outer r=0.7, inner r=0.3)
            moveTo(9.1f, 15.2f)
            arcTo(0.7f, 0.7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.7f, 15.2f)
            arcTo(0.7f, 0.7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 9.1f, 15.2f)
            moveTo(8.7f, 15.2f)
            arcTo(0.3f, 0.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.1f, 15.2f)
            arcTo(0.3f, 0.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 8.7f, 15.2f)
            // Olive 2 (outer r=0.7, inner r=0.3)
            moveTo(13.9f, 9.6f)
            arcTo(0.7f, 0.7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.5f, 9.6f)
            arcTo(0.7f, 0.7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.9f, 9.6f)
            moveTo(13.5f, 9.6f)
            arcTo(0.3f, 0.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.9f, 9.6f)
            arcTo(0.3f, 0.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 13.5f, 9.6f)
            // Small leaf
            moveTo(15.6f, 17.2f)
            quadTo(16.4f, 16.4f, 17.2f, 17.2f)
            quadTo(16.4f, 18f, 15.6f, 17.2f)
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPizzaIconPreview() {
    Icon(
        imageVector = IcPizza,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
