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
 * Icon for Equil Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 */
val IcPluginEquil: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginEquil",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(19.808f, 4.311f)
            horizontalLineTo(4.192f)
            curveTo(2.54f, 4.311f, 1.2f, 5.651f, 1.2f, 7.303f)
            verticalLineToRelative(9.393f)
            curveToRelative(0f, 1.652f, 1.34f, 2.992f, 2.992f, 2.992f)
            horizontalLineToRelative(15.616f)
            curveToRelative(1.652f, 0f, 2.992f, -1.34f, 2.992f, -2.992f)
            verticalLineTo(7.303f)
            curveTo(22.8f, 5.651f, 21.46f, 4.311f, 19.808f, 4.311f)
            close()
            moveTo(21.444f, 17.996f)
            curveToRelative(0f, 0.099f, -0.08f, 0.18f, -0.18f, 0.18f)
            horizontalLineTo(10.693f)
            horizontalLineTo(9.478f)
            curveToRelative(-0.04f, 0.077f, -0.119f, 0.13f, -0.212f, 0.13f)
            horizontalLineTo(7.576f)
            curveToRelative(-0.092f, 0f, -0.172f, -0.053f, -0.212f, -0.13f)
            horizontalLineTo(4.349f)
            curveToRelative(-0.793f, 0f, -1.436f, -0.643f, -1.436f, -1.436f)
            verticalLineTo(7.324f)
            curveToRelative(0f, -0.793f, 0.643f, -1.436f, 1.436f, -1.436f)
            horizontalLineToRelative(6.343f)
            horizontalLineToRelative(8.519f)
            verticalLineTo(5.628f)
            horizontalLineToRelative(1.257f)
            verticalLineToRelative(0.259f)
            horizontalLineToRelative(0.797f)
            curveToRelative(0.099f, 0f, 0.18f, 0.08f, 0.18f, 0.18f)
            verticalLineTo(17.996f)
            close()
        }

        // Next group (various shapes)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(21.264f, 5.888f)
            horizontalLineToRelative(-0.797f)
            verticalLineTo(5.628f)
            horizontalLineToRelative(-1.257f)
            verticalLineToRelative(0.259f)
            horizontalLineToRelative(-8.519f)
            horizontalLineTo(4.349f)
            curveToRelative(-0.793f, 0f, -1.436f, 0.643f, -1.436f, 1.436f)
            verticalLineToRelative(9.415f)
            curveToRelative(0f, 0.793f, 0.643f, 1.436f, 1.436f, 1.436f)
            horizontalLineToRelative(3.015f)
            curveToRelative(0.04f, 0.077f, 0.119f, 0.13f, 0.212f, 0.13f)
            horizontalLineToRelative(1.691f)
            curveToRelative(0.092f, 0f, 0.172f, -0.053f, 0.212f, -0.13f)
            horizontalLineToRelative(1.215f)
            horizontalLineToRelative(10.572f)
            curveToRelative(0.099f, 0f, 0.18f, -0.08f, 0.18f, -0.18f)
            verticalLineTo(6.067f)
            curveTo(21.444f, 5.968f, 21.364f, 5.888f, 21.264f, 5.888f)
            close()
            moveTo(9.311f, 12.398f)
            curveToRelative(0f, 0.041f, -0.033f, 0.075f, -0.075f, 0.075f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.033f, -0.075f, -0.075f)
            verticalLineToRelative(-0.733f)
            curveToRelative(0f, -0.041f, 0.033f, -0.075f, 0.075f, -0.075f)
            curveToRelative(0.041f, 0f, 0.075f, 0.033f, 0.075f, 0.075f)
            verticalLineTo(12.398f)
            close()
            moveTo(9.782f, 12.772f)
            curveToRelative(0f, 0.041f, -0.033f, 0.075f, -0.075f, 0.075f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.033f, -0.075f, -0.075f)
            verticalLineToRelative(-1.481f)
            curveToRelative(0f, -0.041f, 0.033f, -0.075f, 0.075f, -0.075f)
            curveToRelative(0.041f, 0f, 0.075f, 0.033f, 0.075f, 0.075f)
            verticalLineTo(12.772f)
            close()
            moveTo(10.254f, 13.176f)
            curveToRelative(0f, 0.05f, -0.033f, 0.09f, -0.075f, 0.09f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.04f, -0.075f, -0.09f)
            verticalLineToRelative(-2.289f)
            curveToRelative(0f, -0.05f, 0.033f, -0.09f, 0.075f, -0.09f)
            curveToRelative(0.041f, 0f, 0.075f, 0.04f, 0.075f, 0.09f)
            verticalLineTo(13.176f)
            close()
            moveTo(21.19f, 17.728f)
            curveToRelative(0f, 0.23f, -0.188f, 0.416f, -0.419f, 0.416f)
            horizontalLineToRelative(-9.709f)
            verticalLineToRelative(-0.076f)
            horizontalLineToRelative(-0.369f)
            verticalLineTo(5.995f)
            horizontalLineToRelative(0.369f)
            verticalLineTo(5.94f)
            horizontalLineToRelative(9.709f)
            curveToRelative(0.231f, 0f, 0.419f, 0.186f, 0.419f, 0.416f)
            verticalLineTo(17.728f)
            close()
        }

        // Group opacity 0.6 (three small paths)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(10.254f, 13.176f)
            curveToRelative(0f, 0.05f, -0.033f, 0.09f, -0.075f, 0.09f)
            lineToRelative(0f, 0f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.04f, -0.075f, -0.09f)
            verticalLineToRelative(-2.289f)
            curveToRelative(0f, -0.05f, 0.033f, -0.09f, 0.075f, -0.09f)
            lineToRelative(0f, 0f)
            curveToRelative(0.041f, 0f, 0.075f, 0.04f, 0.075f, 0.09f)
            verticalLineTo(13.176f)
            close()
            moveTo(9.782f, 12.772f)
            curveToRelative(0f, 0.041f, -0.033f, 0.075f, -0.075f, 0.075f)
            lineToRelative(0f, 0f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.033f, -0.075f, -0.075f)
            verticalLineToRelative(-1.481f)
            curveToRelative(0f, -0.041f, 0.033f, -0.075f, 0.075f, -0.075f)
            lineToRelative(0f, 0f)
            curveToRelative(0.041f, 0f, 0.075f, 0.033f, 0.075f, 0.075f)
            verticalLineTo(12.772f)
            close()
            moveTo(9.311f, 12.398f)
            curveToRelative(0f, 0.041f, -0.033f, 0.075f, -0.075f, 0.075f)
            lineToRelative(0f, 0f)
            curveToRelative(-0.041f, 0f, -0.075f, -0.033f, -0.075f, -0.075f)
            verticalLineToRelative(-0.733f)
            curveToRelative(0f, -0.041f, 0.033f, -0.075f, 0.075f, -0.075f)
            lineToRelative(0f, 0f)
            curveToRelative(0.041f, 0f, 0.075f, 0.033f, 0.075f, 0.075f)
            verticalLineTo(12.398f)
            close()
        }

        // Rectangle with opacity 0.5
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(10.693f, 5.995f)
            lineTo(11.062f, 5.995f)
            lineTo(11.062f, 18.068f)
            lineTo(10.693f, 18.068f)
            close()
        }

        // Path with opacity 0.5 (large inner detail)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.5f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(20.771f, 5.94f)
            horizontalLineToRelative(-7.66f)
            verticalLineToRelative(0.254f)
            horizontalLineToRelative(-1.945f)
            verticalLineTo(5.94f)
            horizontalLineToRelative(-0.105f)
            verticalLineToRelative(12.204f)
            horizontalLineToRelative(9.709f)
            curveToRelative(0.231f, 0f, 0.419f, -0.186f, 0.419f, -0.416f)
            verticalLineTo(6.356f)
            curveTo(21.19f, 6.126f, 21.002f, 5.94f, 20.771f, 5.94f)
            close()
            moveTo(13.306f, 6.351f)
            horizontalLineToRelative(4.892f)
            verticalLineToRelative(6.687f)
            horizontalLineToRelative(-4.892f)
            verticalLineTo(6.351f)
            close()
            moveTo(12.632f, 18.068f)
            horizontalLineToRelative(-1.466f)
            verticalLineTo(7.025f)
            verticalLineTo(6.299f)
            horizontalLineToRelative(1.997f)
            verticalLineToRelative(0.726f)
            horizontalLineToRelative(0.037f)
            verticalLineToRelative(5.924f)
            horizontalLineToRelative(-0.568f)
            verticalLineTo(18.068f)
            close()
            moveTo(21.055f, 13.173f)
            verticalLineToRelative(0.075f)
            verticalLineToRelative(4.683f)
            horizontalLineToRelative(-8.288f)
            verticalLineToRelative(-0.666f)
            curveToRelative(0.09f, -0.065f, 0.15f, -0.171f, 0.15f, -0.291f)
            verticalLineToRelative(-0.845f)
            horizontalLineToRelative(0.972f)
            verticalLineTo(15.38f)
            horizontalLineToRelative(-0.972f)
            verticalLineToRelative(-0.845f)
            curveToRelative(0f, -0.12f, -0.059f, -0.226f, -0.15f, -0.291f)
            verticalLineToRelative(-1.07f)
            horizontalLineToRelative(5.565f)
            verticalLineTo(6.955f)
            horizontalLineToRelative(-0.05f)
            verticalLineTo(6.247f)
            horizontalLineToRelative(1.436f)
            horizontalLineToRelative(1.147f)
            verticalLineToRelative(0.588f)
            horizontalLineToRelative(-1.147f)
            verticalLineToRelative(0.04f)
            horizontalLineToRelative(1.336f)
            verticalLineTo(13.173f)
            close()
        }

        // Path with opacity 0.7 (small vertical lines)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.7f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(12.632f, 7.025f)
            horizontalLineToRelative(-0.367f)
            horizontalLineToRelative(-1.1f)
            verticalLineToRelative(11.043f)
            horizontalLineToRelative(1.466f)
            verticalLineToRelative(-5.119f)
            horizontalLineToRelative(0.568f)
            verticalLineTo(7.025f)
            horizontalLineTo(12.632f)
            close()
            moveTo(12.266f, 15.2f)
            horizontalLineToRelative(-0.149f)
            verticalLineToRelative(0.312f)
            horizontalLineToRelative(-0.195f)
            verticalLineToRelative(-1.666f)
            horizontalLineToRelative(0.195f)
            verticalLineToRelative(0.312f)
            horizontalLineToRelative(0.149f)
            verticalLineTo(15.2f)
            close()
            moveTo(12.266f, 9.904f)
            horizontalLineToRelative(-0.149f)
            verticalLineToRelative(0.312f)
            horizontalLineToRelative(-0.195f)
            verticalLineTo(8.551f)
            horizontalLineToRelative(0.195f)
            verticalLineToRelative(0.312f)
            horizontalLineToRelative(0.149f)
            verticalLineTo(9.904f)
            close()
        }

        // Two polygons (small tick marks)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(12.266f, 8.862f)
            lineTo(12.117f, 8.862f)
            lineTo(12.117f, 8.551f)
            lineTo(11.922f, 8.551f)
            lineTo(11.922f, 10.216f)
            lineTo(12.117f, 10.216f)
            lineTo(12.117f, 9.904f)
            lineTo(12.266f, 9.904f)
            close()
            moveTo(12.266f, 14.158f)
            lineTo(12.117f, 14.158f)
            lineTo(12.117f, 13.847f)
            lineTo(11.922f, 13.847f)
            lineTo(11.922f, 15.512f)
            lineTo(12.117f, 15.512f)
            lineTo(12.117f, 15.2f)
            lineTo(12.266f, 15.2f)
            close()
        }

        // Polygon with opacity 0.3 (battery-like bars)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.3f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(20.292f, 13.637f)
            lineTo(20.292f, 13.936f)
            lineTo(19.813f, 13.936f)
            lineTo(19.813f, 13.637f)
            lineTo(19.439f, 13.637f)
            lineTo(19.439f, 13.936f)
            lineTo(18.961f, 13.936f)
            lineTo(18.961f, 13.637f)
            lineTo(18.706f, 13.637f)
            lineTo(18.706f, 17.871f)
            lineTo(18.961f, 17.871f)
            lineTo(18.961f, 17.572f)
            lineTo(19.439f, 17.572f)
            lineTo(19.439f, 17.871f)
            lineTo(19.813f, 17.871f)
            lineTo(19.813f, 17.572f)
            lineTo(20.292f, 17.572f)
            lineTo(20.292f, 17.871f)
            lineTo(20.546f, 17.871f)
            lineTo(20.546f, 17.572f)
            lineTo(20.546f, 13.936f)
            lineTo(20.546f, 13.637f)
            close()
        }

        // Path with opacity 0.65 (large detail)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.60f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(18.332f, 6.875f)
            verticalLineToRelative(0.379f)
            horizontalLineToRelative(2.533f)
            verticalLineToRelative(0.249f)
            horizontalLineToRelative(-2.533f)
            verticalLineToRelative(5.67f)
            horizontalLineToRelative(-5.565f)
            verticalLineToRelative(1.07f)
            curveToRelative(0.09f, 0.065f, 0.15f, 0.171f, 0.15f, 0.291f)
            verticalLineToRelative(0.845f)
            horizontalLineToRelative(0.972f)
            verticalLineToRelative(0.748f)
            horizontalLineToRelative(-0.972f)
            verticalLineToRelative(0.845f)
            curveToRelative(0f, 0.12f, -0.059f, 0.226f, -0.15f, 0.291f)
            verticalLineToRelative(0.666f)
            horizontalLineToRelative(8.288f)
            verticalLineToRelative(-4.683f)
            verticalLineToRelative(-0.075f)
            verticalLineTo(6.875f)
            horizontalLineTo(18.332f)
            close()
            moveTo(20.546f, 13.936f)
            verticalLineToRelative(3.635f)
            verticalLineToRelative(0.299f)
            horizontalLineToRelative(-0.254f)
            verticalLineToRelative(-0.299f)
            horizontalLineToRelative(-0.479f)
            verticalLineToRelative(0.299f)
            horizontalLineToRelative(-0.374f)
            verticalLineToRelative(-0.299f)
            horizontalLineToRelative(-0.479f)
            verticalLineToRelative(0.299f)
            horizontalLineToRelative(-0.254f)
            verticalLineToRelative(-4.234f)
            horizontalLineToRelative(0.254f)
            verticalLineToRelative(0.299f)
            horizontalLineToRelative(0.479f)
            verticalLineToRelative(-0.299f)
            horizontalLineToRelative(0.374f)
            verticalLineToRelative(0.299f)
            horizontalLineToRelative(0.479f)
            verticalLineToRelative(-0.299f)
            horizontalLineToRelative(0.254f)
            verticalLineTo(13.936f)
            close()
            moveTo(19.694f, 13.039f)
            curveToRelative(-0.587f, 0f, -1.062f, -0.476f, -1.062f, -1.062f)
            curveToRelative(0f, -0.587f, 0.476f, -1.062f, 1.062f, -1.062f)
            curveToRelative(0.587f, 0f, 1.062f, 0.476f, 1.062f, 1.062f)
            curveTo(20.756f, 12.563f, 20.28f, 13.039f, 19.694f, 13.039f)
            close()
            moveTo(20.866f, 9.319f)
            horizontalLineToRelative(-0.678f)
            verticalLineToRelative(1.117f)
            horizontalLineToRelative(-0.214f)
            verticalLineToRelative(-0.329f)
            horizontalLineToRelative(-0.658f)
            verticalLineToRelative(0.329f)
            horizontalLineTo(19.1f)
            verticalLineTo(9.319f)
            horizontalLineToRelative(-0.529f)
            verticalLineTo(8.032f)
            horizontalLineToRelative(2.294f)
            verticalLineTo(9.319f)
            close()
        }

        // Polygon with opacity 0.75 (top part)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.75f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(20.866f, 8.032f)
            lineTo(18.572f, 8.032f)
            lineTo(18.572f, 9.319f)
            lineTo(19.1f, 9.319f)
            lineTo(19.1f, 10.436f)
            lineTo(19.315f, 10.436f)
            lineTo(19.315f, 10.106f)
            lineTo(19.973f, 10.106f)
            lineTo(19.973f, 10.436f)
            lineTo(20.187f, 10.436f)
            lineTo(20.187f, 9.319f)
            lineTo(20.866f, 9.319f)
            close()
        }

        // Rectangle with opacity 0.75
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.75f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(18.282f, 7.254f)
            lineTo(20.865f, 7.254f)
            lineTo(20.865f, 7.503f)
            lineTo(18.282f, 7.503f)
            close()
        }

        // Various rectangles/polygons with opacity 0.8
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(19.719f, 6.247f)
            lineTo(18.282f, 6.247f)
            lineTo(18.282f, 6.955f)
            lineTo(19.719f, 6.955f)
            lineTo(19.719f, 6.835f)
            lineTo(20.866f, 6.835f)
            lineTo(20.866f, 6.247f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(13.306f, 6.351f)
            lineTo(18.198f, 6.351f)
            lineTo(18.198f, 13.038f)
            lineTo(13.306f, 13.038f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(11.166f, 6.299f)
            lineTo(13.163f, 6.299f)
            lineTo(13.163f, 7.025f)
            lineTo(11.166f, 7.025f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(11.166f, 5.94f)
            lineTo(13.111f, 5.94f)
            lineTo(13.111f, 6.194f)
            lineTo(11.166f, 6.194f)
            close()
        }

        // Circle with opacity 0.4
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.4f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(20.756f, 11.976f)
            arcToRelative(1.062f, 1.062f, 0f, false, true, -2.124f, 0f)
            arcToRelative(1.062f, 1.062f, 0f, false, true, 2.124f, 0f)
            close()
        }

        // Small arrow / pointer (full opacity)
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(21.031f, 13.82f)
            curveToRelative(-0.034f, 0.024f, -0.081f, 0.017f, -0.105f, -0.017f)
            lineToRelative(-1.287f, -1.775f)
            curveToRelative(-0.024f, -0.034f, -0.017f, -0.081f, 0.017f, -0.105f)
            lineToRelative(0f, 0f)
            curveToRelative(0.034f, -0.024f, 0.081f, -0.017f, 0.105f, 0.017f)
            lineToRelative(1.287f, 1.775f)
            curveTo(21.073f, 13.748f, 21.065f, 13.796f, 21.031f, 13.82f)
            lineTo(21.031f, 13.82f)
            close()
        }

        // Rectangle with opacity 0.6
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.6f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(13.306f, 13.95f)
            lineTo(17.816f, 13.95f)
            lineTo(17.816f, 14.679f)
            lineTo(13.306f, 14.679f)
            close()
        }

        // Polygon with opacity 0.8
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.8f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f
        ) {
            moveTo(17.815f, 13.95f)
            lineTo(13.306f, 13.95f)
            lineTo(13.09f, 13.402f)
            lineTo(18.04f, 13.402f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginEquilPreview() {
    Icon(
        imageVector = IcPluginEquil,
        contentDescription = "Equil Plugin Icon",
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg version="1.1" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" x="0px" y="0px" width="24px"
	 height="24px" viewBox="0 0 24 24" enable-background="new 0 0 24 24" xml:space="preserve">
<g id="Plugin_Equil">
	<g>
		<path opacity="0.7" fill="#FFFFFF" d="M19.808,4.311H4.192C2.54,4.311,1.2,5.651,1.2,7.303v9.393c0,1.652,1.34,2.992,2.992,2.992
			h15.616c1.652,0,2.992-1.34,2.992-2.992V7.303C22.8,5.651,21.46,4.311,19.808,4.311z M21.444,17.996c0,0.099-0.08,0.18-0.18,0.18
			H10.693H9.478c-0.04,0.077-0.119,0.13-0.212,0.13H7.576c-0.092,0-0.172-0.053-0.212-0.13H4.349c-0.793,0-1.436-0.643-1.436-1.436
			V7.324c0-0.793,0.643-1.436,1.436-1.436h6.343h8.519V5.628h1.257v0.259h0.797c0.099,0,0.18,0.08,0.18,0.18V17.996z"/>
		<g>
			<path fill="#FFFFFF" d="M21.264,5.888h-0.797V5.628h-1.257v0.259h-8.519H4.349c-0.793,0-1.436,0.643-1.436,1.436v9.415
				c0,0.793,0.643,1.436,1.436,1.436h3.015c0.04,0.077,0.119,0.13,0.212,0.13h1.691c0.092,0,0.172-0.053,0.212-0.13h1.215h10.572
				c0.099,0,0.18-0.08,0.18-0.18V6.067C21.444,5.968,21.364,5.888,21.264,5.888z M9.311,12.398c0,0.041-0.033,0.075-0.075,0.075
				c-0.041,0-0.075-0.033-0.075-0.075v-0.733c0-0.041,0.033-0.075,0.075-0.075c0.041,0,0.075,0.033,0.075,0.075V12.398z
				 M9.782,12.772c0,0.041-0.033,0.075-0.075,0.075c-0.041,0-0.075-0.033-0.075-0.075v-1.481c0-0.041,0.033-0.075,0.075-0.075
				c0.041,0,0.075,0.033,0.075,0.075V12.772z M10.254,13.176c0,0.05-0.033,0.09-0.075,0.09c-0.041,0-0.075-0.04-0.075-0.09v-2.289
				c0-0.05,0.033-0.09,0.075-0.09c0.041,0,0.075,0.04,0.075,0.09V13.176z M21.19,17.728c0,0.23-0.188,0.416-0.419,0.416h-9.709
				v-0.076h-0.369V5.995h0.369V5.94h9.709c0.231,0,0.419,0.186,0.419,0.416V17.728z"/>
			<g opacity="0.6">
				<path fill="#FFFFFF" d="M10.254,13.176c0,0.05-0.033,0.09-0.075,0.09l0,0c-0.041,0-0.075-0.04-0.075-0.09v-2.289
					c0-0.05,0.033-0.09,0.075-0.09l0,0c0.041,0,0.075,0.04,0.075,0.09V13.176z"/>
				<path fill="#FFFFFF" d="M9.782,12.772c0,0.041-0.033,0.075-0.075,0.075l0,0c-0.041,0-0.075-0.033-0.075-0.075v-1.481
					c0-0.041,0.033-0.075,0.075-0.075l0,0c0.041,0,0.075,0.033,0.075,0.075V12.772z"/>
				<path fill="#FFFFFF" d="M9.311,12.398c0,0.041-0.033,0.075-0.075,0.075l0,0c-0.041,0-0.075-0.033-0.075-0.075v-0.733
					c0-0.041,0.033-0.075,0.075-0.075l0,0c0.041,0,0.075,0.033,0.075,0.075V12.398z"/>
			</g>
		</g>
		<rect x="10.693" y="5.995" opacity="0.5" fill="#FFFFFF" width="0.369" height="12.073"/>
		<g>
			<path opacity="0.5" fill="#FFFFFF" d="M20.771,5.94h-7.66v0.254h-1.945V5.94h-0.105v12.204h9.709
				c0.231,0,0.419-0.186,0.419-0.416V6.356C21.19,6.126,21.002,5.94,20.771,5.94z M13.306,6.351h4.892v6.687h-4.892V6.351z
				 M12.632,18.068h-1.466V7.025V6.299h1.997v0.726h0.037v5.924h-0.568V18.068z M21.055,13.173v0.075v4.683h-8.288v-0.666
				c0.09-0.065,0.15-0.171,0.15-0.291v-0.845h0.972V15.38h-0.972v-0.845c0-0.12-0.059-0.226-0.15-0.291v-1.07h5.565V6.955h-0.05
				V6.247h1.436h1.147v0.588h-1.147v0.04h1.336V13.173z"/>
		</g>
		<path opacity="0.7" fill="#FFFFFF" d="M12.632,7.025h-0.367h-1.1v11.043h1.466v-5.119h0.568V7.025H12.632z M12.266,15.2h-0.149
			v0.312h-0.195v-1.666h0.195v0.312h0.149V15.2z M12.266,9.904h-0.149v0.312h-0.195V8.551h0.195v0.312h0.149V9.904z"/>
		<g>
			<polygon fill="#FFFFFF" points="12.266,8.862 12.117,8.862 12.117,8.551 11.922,8.551 11.922,10.216 12.117,10.216 12.117,9.904
				12.266,9.904 			"/>
			<polygon fill="#FFFFFF" points="12.266,14.158 12.117,14.158 12.117,13.847 11.922,13.847 11.922,15.512 12.117,15.512
				12.117,15.2 12.266,15.2 			"/>
		</g>
		<polygon opacity="0.3" fill="#FFFFFF" points="20.292,13.637 20.292,13.936 19.813,13.936 19.813,13.637 19.439,13.637
			19.439,13.936 18.961,13.936 18.961,13.637 18.706,13.637 18.706,17.871 18.961,17.871 18.961,17.572 19.439,17.572
			19.439,17.871 19.813,17.871 19.813,17.572 20.292,17.572 20.292,17.871 20.546,17.871 20.546,17.572 20.546,13.936
			20.546,13.637 		"/>
		<path opacity="0.65" fill="#FFFFFF" d="M18.332,6.875v0.379h2.533v0.249h-2.533v5.67h-5.565v1.07
			c0.09,0.065,0.15,0.171,0.15,0.291v0.845h0.972v0.748h-0.972v0.845c0,0.12-0.059,0.226-0.15,0.291v0.666h8.288v-4.683v-0.075
			V6.875H18.332z M20.546,13.936v3.635v0.299h-0.254v-0.299h-0.479v0.299h-0.374v-0.299h-0.479v0.299h-0.254v-4.234h0.254v0.299
			h0.479v-0.299h0.374v0.299h0.479v-0.299h0.254V13.936z M19.694,13.039c-0.587,0-1.062-0.476-1.062-1.062
			c0-0.587,0.476-1.062,1.062-1.062c0.587,0,1.062,0.476,1.062,1.062C20.756,12.563,20.28,13.039,19.694,13.039z M20.866,9.319
			h-0.678v1.117h-0.214v-0.329h-0.658v0.329H19.1V9.319h-0.529V8.032h2.294V9.319z"/>
		<g>
			<polygon opacity="0.75" fill="#FFFFFF" points="20.866,8.032 18.572,8.032 18.572,9.319 19.1,9.319 19.1,10.436 19.315,10.436
				19.315,10.106 19.973,10.106 19.973,10.436 20.187,10.436 20.187,9.319 20.866,9.319 			"/>
			<rect x="18.282" y="7.254" opacity="0.75" fill="#FFFFFF" width="2.583" height="0.249"/>
		</g>
		<g>
			<polygon opacity="0.8" fill="#FFFFFF" points="19.719,6.247 18.282,6.247 18.282,6.955 19.719,6.955 19.719,6.835 20.866,6.835
				20.866,6.247 			"/>
			<rect x="13.306" y="6.351" opacity="0.8" fill="#FFFFFF" width="4.892" height="6.687"/>
			<rect x="11.166" y="6.299" opacity="0.8" fill="#FFFFFF" width="1.997" height="0.726"/>
			<rect x="11.166" y="5.94" opacity="0.8" fill="#FFFFFF" width="1.945" height="0.254"/>
		</g>
		<circle opacity="0.4" fill="#FFFFFF" cx="19.694" cy="11.976" r="1.062"/>
		<path fill="#FFFFFF" d="M21.031,13.82c-0.034,0.024-0.081,0.017-0.105-0.017l-1.287-1.775c-0.024-0.034-0.017-0.081,0.017-0.105
			l0,0c0.034-0.024,0.081-0.017,0.105,0.017l1.287,1.775C21.073,13.748,21.065,13.796,21.031,13.82L21.031,13.82z"/>
		<rect x="13.306" y="13.95" opacity="0.6" fill="#FFFFFF" width="4.51" height="0.729"/>
		<polygon opacity="0.8" fill="#FFFFFF" points="17.815,13.95 13.306,13.95 13.09,13.402 18.04,13.402 		"/>
	</g>
</g>
</svg>
 */