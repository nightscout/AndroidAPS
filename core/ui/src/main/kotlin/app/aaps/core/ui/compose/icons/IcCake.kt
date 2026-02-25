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
 * Icon for Cake.
 * Represents a cupcake with frosting, candle and sprinkles.
 */
val IcCake: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcCake",
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
            // Cupcake wrapper
            moveTo(6f, 11.6f)
            lineTo(7.2f, 18.8f)
            lineTo(16.8f, 18.8f)
            lineTo(18f, 11.6f)
            // Wrapper ridges
            moveTo(7.8f, 12f)
            lineTo(8.2f, 18.4f)
            moveTo(10f, 11.6f)
            lineTo(10f, 18.8f)
            moveTo(12.4f, 11.6f)
            lineTo(12.4f, 18.8f)
            moveTo(14.8f, 12f)
            lineTo(14.4f, 18.4f)
            // Frosting swirl
            moveTo(5.6f, 11.6f)
            quadTo(5.6f, 9.6f, 8f, 9.6f)
            quadTo(9.6f, 9.6f, 9.6f, 8.4f)
            quadTo(9.6f, 7.2f, 12f, 7.2f)
            quadTo(14.4f, 7.2f, 14.4f, 8.4f)
            quadTo(14.4f, 9.6f, 16f, 9.6f)
            quadTo(18.4f, 9.6f, 18.4f, 11.6f)
            // Candle
            moveTo(12f, 7.2f)
            lineTo(12f, 4.4f)
            // Flame
            moveTo(12f, 4.4f)
            quadTo(11f, 3.2f, 12f, 2f)
            quadTo(13f, 3.2f, 12f, 4.4f)
            // Sprinkles
            moveTo(7.6f, 10.4f)
            lineTo(8.4f, 10f)
            moveTo(10.4f, 8.8f)
            lineTo(10.8f, 8f)
            moveTo(13.6f, 8.8f)
            lineTo(13.2f, 8f)
            moveTo(15.6f, 10.4f)
            lineTo(16.4f, 10f)
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcCakeIconPreview() {
    Icon(
        imageVector = IcCake,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}
