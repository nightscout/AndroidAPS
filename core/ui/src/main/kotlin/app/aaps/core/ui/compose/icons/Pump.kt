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
 * Icon for insulin pump.
 * Represents the insulin pump device with display and buttons.
 *
 * Bounding box: x: 4-44, y: 1.5-46.5 (viewport: 48x48, includes stroke, ~83% width)
 */
val Pump: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pump",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f
    ).apply {
        // Main pump body
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(11.5f, 4f)
            lineTo(36.5f, 4f)
            arcTo(5f, 5f, 0f, false, true, 41.5f, 9f)
            lineTo(41.5f, 39f)
            arcTo(5f, 5f, 0f, false, true, 36.5f, 44f)
            lineTo(11.5f, 44f)
            arcTo(5f, 5f, 0f, false, true, 6.5f, 39f)
            lineTo(6.5f, 9f)
            arcTo(5f, 5f, 0f, false, true, 11.5f, 4f)
            close()
        }
        // Display screen
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(13.5f, 9f)
            lineTo(34.5f, 9f)
            arcTo(2f, 2f, 0f, false, true, 36.5f, 11f)
            lineTo(36.5f, 17f)
            arcTo(2f, 2f, 0f, false, true, 34.5f, 19f)
            lineTo(13.5f, 19f)
            arcTo(2f, 2f, 0f, false, true, 11.5f, 17f)
            lineTo(11.5f, 11f)
            arcTo(2f, 2f, 0f, false, true, 13.5f, 9f)
            close()
        }
        // Top center button
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(22.25f, 24f)
            lineTo(25.75f, 24f)
            arcTo(0.75f, 0.75f, 0f, false, true, 26.5f, 24.75f)
            lineTo(26.5f, 28.25f)
            arcTo(0.75f, 0.75f, 0f, false, true, 25.75f, 29f)
            lineTo(22.25f, 29f)
            arcTo(0.75f, 0.75f, 0f, false, true, 21.5f, 28.25f)
            lineTo(21.5f, 24.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 22.25f, 24f)
            close()
        }
        // Bottom center button
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(22.25f, 31.5f)
            lineTo(25.75f, 31.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 26.5f, 32.25f)
            lineTo(26.5f, 35.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 25.75f, 36.5f)
            lineTo(22.25f, 36.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 21.5f, 35.75f)
            lineTo(21.5f, 32.25f)
            arcTo(0.75f, 0.75f, 0f, false, true, 22.25f, 31.5f)
            close()
        }
        // Left button
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(17.25f, 27.75f)
            lineTo(20.75f, 27.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 21.5f, 28.5f)
            lineTo(21.5f, 32f)
            arcTo(0.75f, 0.75f, 0f, false, true, 20.75f, 32.75f)
            lineTo(17.25f, 32.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 16.5f, 32f)
            lineTo(16.5f, 28.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 17.25f, 27.75f)
            close()
        }
        // Right button
        path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(27.25f, 27.75f)
            lineTo(30.75f, 27.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 31.5f, 28.5f)
            lineTo(31.5f, 32f)
            arcTo(0.75f, 0.75f, 0f, false, true, 30.75f, 32.75f)
            lineTo(27.25f, 32.75f)
            arcTo(0.75f, 0.75f, 0f, false, true, 26.5f, 32f)
            lineTo(26.5f, 28.5f)
            arcTo(0.75f, 0.75f, 0f, false, true, 27.25f, 27.75f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun pumpPreview() {
    Icon(
        imageVector = Pump,
        contentDescription = null,
        tint = Color.Unspecified,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp)
    )
}