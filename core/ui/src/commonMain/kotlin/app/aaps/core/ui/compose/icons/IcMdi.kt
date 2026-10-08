package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Syringe / insulin pen icon. Used for Sources.MDI (Multiple Daily Injections) — the label
 * applied to user-entered treatments via PumpType.USER.
 *
 * replaces ic_ict
 *
 * Viewport: 64x64 (plunger + barrel + tick marks + needle)
 *
 * @see IcMdiIconPreview
 */
val IcMdi: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcMdi",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 64f,
        viewportHeight = 64f
    ).apply {
        // Plunger head
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
            moveTo(6.171f, 24.3397f)
            curveTo(3.8502f, 26.496f, 3.5322f, 29.698f, 5.4601f, 31.4917f)
            curveTo(7.3872f, 33.2847f, 10.8312f, 32.9912f, 13.152f, 30.835f)
            lineTo(6.171f, 24.3397f)
            close()
            moveTo(6.1447f, 24.3153f)
            lineTo(13.1257f, 30.8105f)
            lineTo(32.4135f, 12.8898f)
            lineTo(25.4325f, 6.3946f)
            lineTo(6.1447f, 24.3153f)
            close()
        }
        // Small detail dot
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
            moveTo(27.8111f, 21.5928f)
            curveTo(28.4437f, 21.5932f, 28.9565f, 21.048f, 28.9569f, 20.3756f)
            curveTo(28.9573f, 19.7033f, 28.4444f, 19.1566f, 27.8126f, 19.1562f)
            curveTo(27.18f, 19.1558f, 26.6664f, 19.701f, 26.666f, 20.3742f)
            lineTo(27.8118f, 20.3749f)
            lineTo(27.8111f, 21.5928f)
            close()
        }
        // Outline band along the body
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
            moveTo(28.3077f, 20.9977f)
            curveTo(28.4591f, 20.8544f, 28.4651f, 20.615f, 28.3218f, 20.4636f)
            lineTo(27.8025f, 19.9148f)
            curveTo(27.6593f, 19.7634f, 27.4206f, 19.7567f, 27.2692f, 19.9f)
            lineTo(13.0015f, 33.3152f)
            curveTo(12.8501f, 33.4584f, 13.1194f, 33.5198f, 13.2627f, 33.6712f)
            lineTo(13.7819f, 34.22f)
            curveTo(13.9252f, 34.3715f, 14.1646f, 34.3788f, 14.316f, 34.2355f)
            lineTo(28.3077f, 20.9977f)
            close()
        }
        // Small rhombus near base
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
            moveTo(12.8796f, 30.7117f)
            lineToRelative(1.4542f, 1.4634f)
            lineToRelative(-1.2991f, 1.2909f)
            lineToRelative(-1.4542f, -1.4634f)
            close()
        }
        // Tip wedge (needle)
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
            moveTo(11.2926f, 57.0326f)
            lineTo(15.0819f, 53.5125f)
            lineTo(15.523f, 53.923f)
            lineTo(11.7337f, 57.4431f)
            lineTo(10.9249f, 57.7851f)
            lineTo(11.2926f, 57.0326f)
            close()
        }
        // Outline rectangle near base (stroke only, width 1.15)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.15f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(16.3239f, 48.4068f)
            lineToRelative(4.6153f, 4.2949f)
            lineToRelative(-3.3673f, 3.1281f)
            lineToRelative(-4.6153f, -4.2949f)
            close()
        }
        // Long outline parallelogram along the shaft (stroke only, width 0.68)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeAlpha = 1.0f,
            strokeLineWidth = 0.68f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(32.6362f, 32.3306f)
            lineToRelative(5.8109f, 5.4075f)
            lineToRelative(-16.8087f, 15.6146f)
            lineToRelative(-5.8109f, -5.4075f)
            close()
        }
        // Tick mark 1
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
            moveTo(30.0742f, 35.3171f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Tick mark 2
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
            moveTo(19.738f, 44.8949f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Tick mark 3
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
            moveTo(21.8556f, 42.9278f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Tick mark 4
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
            moveTo(23.955f, 40.9775f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Tick mark 5
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
            moveTo(26.0246f, 39.0549f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Tick mark 6
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
            moveTo(28.0977f, 37.1291f)
            lineToRelative(2.3081f, 2.1478f)
            lineToRelative(-0.4207f, 0.3908f)
            lineToRelative(-2.3081f, -2.1478f)
            close()
        }
        // Plunger top cap (filled paddle)
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
            moveTo(50.4865f, 14.7282f)
            curveTo(53.0833f, 12.3159f, 56.7436f, 11.8079f, 58.6621f, 13.5932f)
            curveTo(60.5806f, 15.3785f, 60.0311f, 18.7813f, 57.4343f, 21.1936f)
            lineTo(38.5619f, 38.7252f)
            lineTo(31.6141f, 32.2598f)
            lineTo(50.4865f, 14.7282f)
            close()
        }
    }.build()
}
