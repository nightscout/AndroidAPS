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
 * Icon for BG Delta (Greek Δ triangle outline).
 *
 * Replaces ic_auto_delta.
 *
 * Bounding box: x: 5.087-18.913, y: 4-20 (viewport: 24x24, ~67% height)
 */
val IcDelta: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcDelta",
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
            moveTo(12f, 4f)
            lineTo(5.087f, 20f)
            horizontalLineToRelative(13.826f)
            lineTo(12f, 4f)
            close()

            moveTo(11.375f, 8.236f)
            lineToRelative(4.614f, 10.678f)
            horizontalLineTo(6.761f)
            lineTo(11.375f, 8.236f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcDeltaIconPreview() {
    Icon(
        imageVector = IcDelta,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:pathData="M12,4L5.087,20h13.826L12,4zM11.375,8.236l4.614,10.678H6.761L11.375,8.236z"
      android:fillColor="#36FF00"/>
</vector>

 */
