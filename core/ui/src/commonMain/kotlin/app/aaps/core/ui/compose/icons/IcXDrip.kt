package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for xDrip.
 * Represents xDrip CGM integration.
 *
 * replaces ic_xdrip
 *
 * Bounding box: x: 4.0-20.0, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcXdripIconPreview
 */
val IcXDrip: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcXdrip",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFB92929)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.046f, 1.2f)
            curveToRelative(1.709f, 1.721f, 3.367f, 3.401f, 5.036f, 5.069f)
            curveToRelative(1.108f, 1.108f, 2.236f, 2.184f, 2.957f, 3.622f)
            curveToRelative(1.601f, 3.192f, 1.091f, 7.127f, -1.339f, 9.92f)
            curveToRelative(-2.272f, 2.611f, -6.202f, 3.666f, -9.472f, 2.543f)
            curveTo(2.791f, 20.143f, 0.95f, 12.334f, 5.736f, 7.479f)
            curveTo(7.786f, 5.399f, 9.875f, 3.358f, 12.046f, 1.2f)
            close()

            moveTo(11.965f, 4.442f)
            curveTo(10.344f, 6.054f, 8.812f, 7.569f, 7.29f, 9.093f)
            curveToRelative(-1.479f, 1.481f, -2.126f, 3.275f, -1.986f, 5.359f)
            curveToRelative(0.228f, 3.373f, 3.434f, 6.299f, 6.662f, 6.041f)
            curveTo(11.965f, 15.185f, 11.965f, 9.873f, 11.965f, 4.442f)
            close()
        }
    }.build()
}
