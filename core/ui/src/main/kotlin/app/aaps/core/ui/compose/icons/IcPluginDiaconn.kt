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
 * Icon for Diaconn Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 */
val IcPluginDiaconn: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcPluginDiaconn",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Premier chemin (boîtier supérieur)
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
            moveTo(19.198f, 6.2f)
            horizontalLineToRelative(-0.633f)
            verticalLineTo(17.8f)
            horizontalLineToRelative(0.633f)
            curveToRelative(1.118f, 0f, 2.025f, -0.907f, 2.025f, -2.025f)
            verticalLineToRelative(-3.051f)
            curveToRelative(0f, -2.208f, 1.577f, -1.533f, 1.577f, -2.829f)
            verticalLineToRelative(-2.495f)
            curveTo(22.8f, 6.784f, 20.317f, 6.2f, 19.198f, 6.2f)
            close()
        }

        // Deuxième chemin (grand rectangle central)
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
            moveTo(3.225f, 6.2f)
            curveTo(2.107f, 6.2f, 1.2f, 7.107f, 1.2f, 8.225f)
            verticalLineToRelative(7.549f)
            curveToRelative(0f, 1.118f, 0.907f, 2.025f, 2.025f, 2.025f)
            horizontalLineToRelative(15.17f)
            verticalLineTo(6.2f)
            horizontalLineTo(3.225f)
            close()
            moveTo(17.195f, 14.4f)
            curveToRelative(0f, 0.559f, -0.453f, 1.012f, -1.013f, 1.012f)
            horizontalLineTo(4.502f)
            curveToRelative(-0.559f, 0f, -1.013f, -0.453f, -1.013f, -1.012f)
            verticalLineTo(9.546f)
            curveToRelative(0f, -0.559f, 0.453f, -1.012f, 1.013f, -1.012f)
            horizontalLineToRelative(11.68f)
            curveToRelative(0.559f, 0f, 1.013f, 0.453f, 1.013f, 1.012f)
            verticalLineTo(14.4f)
            close()
        }

        // Groupe avec des croix et des rectangles transformés
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
            moveTo(15.453f, 13.737f)
            curveToRelative(-0.233f, 0f, -0.422f, -0.19f, -0.422f, -0.422f)
            reflectiveCurveToRelative(0.19f, -0.422f, 0.422f, -0.422f)
            curveToRelative(0.233f, 0f, 0.422f, 0.19f, 0.422f, 0.422f)
            reflectiveCurveTo(15.686f, 13.737f, 15.453f, 13.737f)
            close()
            moveTo(15.453f, 13.013f)
            curveToRelative(-0.166f, 0f, -0.302f, 0.135f, -0.302f, 0.302f)
            reflectiveCurveToRelative(0.135f, 0.302f, 0.302f, 0.302f)
            reflectiveCurveToRelative(0.302f, -0.135f, 0.302f, -0.302f)
            reflectiveCurveTo(15.619f, 13.013f, 15.453f, 13.013f)
            close()
        }

        // Premier rectangle transformé (petit carré incliné)
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
            moveTo(15.653f, 10.350f)
            lineTo(15.739f, 10.435f)
            lineTo(15.259f, 10.914f)
            lineTo(15.173f, 10.829f)
            close()
        }

        // Deuxième rectangle transformé
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
            moveTo(15.261f, 10.356f)
            lineTo(15.740f, 10.829f)
            lineTo(15.654f, 10.914f)
            lineTo(15.175f, 10.441f)
            close()
        }

        // Polygone (petite flèche)
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
            moveTo(5.231f, 13.569f)
            lineTo(4.851f, 13.189f)
            lineTo(4.936f, 13.103f)
            lineTo(5.231f, 13.398f)
            lineTo(5.526f, 13.103f)
            lineTo(5.611f, 13.189f)
            close()
        }

        // Polygone (autre flèche)
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
            moveTo(4.936f, 10.843f)
            lineTo(4.851f, 10.758f)
            lineTo(5.231f, 10.377f)
            lineTo(5.611f, 10.758f)
            lineTo(5.526f, 10.843f)
            lineTo(5.231f, 10.548f)
            close()
        }

        // buttons
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            // Cercle
            moveTo(16.303f, 13.301f)
            arcToRelative(0.844f, 0.844f, 0f, true, true, -1.688f, 0f)
            arcToRelative(0.844f, 0.844f, 0f, true, true, 1.688f, 0f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(15.453f, 13.737f)
            curveToRelative(-0.233f, 0f, -0.422f, -0.19f, -0.422f, -0.422f)
            reflectiveCurveToRelative(0.19f, -0.422f, 0.422f, -0.422f)
            curveToRelative(0.233f, 0f, 0.422f, 0.19f, 0.422f, 0.422f)
            reflectiveCurveTo(15.686f, 13.737f, 15.453f, 13.737f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(5.231f, 9.787f)
            curveToRelative(-0.465f, 0f, -0.844f, 0.379f, -0.844f, 0.844f)
            curveToRelative(0f, 0.465f, 0.379f, 0.844f, 0.844f, 0.844f)
            curveToRelative(0.465f, 0f, 0.844f, -0.379f, 0.844f, -0.844f)
            curveTo(6.075f, 10.166f, 5.697f, 9.787f, 5.231f, 9.787f)
            close()
            moveTo(5.526f, 10.843f)
            lineTo(5.231f, 10.548f)
            lineTo(4.936f, 10.843f)
            lineTo(4.851f, 10.758f)
            lineTo(5.231f, 10.378f)
            lineTo(5.611f, 10.758f)
            lineTo(5.526f, 10.843f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(5.231f, 12.471f)
            curveToRelative(-0.465f, 0f, -0.844f, 0.379f, -0.844f, 0.844f)
            reflectiveCurveToRelative(0.379f, 0.844f, 0.844f, 0.844f)
            reflectiveCurveToRelative(0.844f, -0.379f, 0.844f, -0.844f)
            reflectiveCurveTo(5.697f, 12.471f, 5.231f, 12.471f)
            close()
            moveTo(5.231f, 13.569f)
            lineTo(4.851f, 13.189f)
            lineTo(4.936f, 13.104f)
            lineTo(5.231f, 13.399f)
            lineTo(5.526f, 13.104f)
            lineTo(5.611f, 13.189f)
            lineTo(5.231f, 13.569f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(15.453f, 9.787f)
            curveToRelative(-0.465f, 0f, -0.844f, 0.379f, -0.844f, 0.844f)
            curveToRelative(0f, 0.465f, 0.379f, 0.844f, 0.844f, 0.844f)
            curveToRelative(0.465f, 0f, 0.844f, -0.379f, 0.844f, -0.844f)
            curveTo(16.297f, 10.166f, 15.919f, 9.787f, 15.453f, 9.787f)
            close()
            moveTo(15.735f, 10.828f)
            lineTo(15.65f, 10.913f)
            lineToRelative(-0.196f, -0.196f)
            lineToRelative(-0.196f, 0.196f)
            lineToRelative(-0.085f, -0.085f)
            lineToRelative(0.196f, -0.196f)
            lineToRelative(-0.196f, -0.196f)
            lineToRelative(0.085f, -0.085f)
            lineToRelative(0.196f, 0.196f)
            lineToRelative(0.196f, -0.196f)
            lineToRelative(0.085f, 0.085f)
            lineToRelative(-0.196f, 0.196f)
            lineTo(15.735f, 10.828f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(16.182f, 8.533f)
            horizontalLineTo(4.502f)
            curveToRelative(-0.559f, 0f, -1.013f, 0.453f, -1.013f, 1.012f)
            verticalLineTo(14.4f)
            curveToRelative(0f, 0.559f, 0.453f, 1.012f, 1.013f, 1.012f)
            horizontalLineToRelative(11.68f)
            curveToRelative(0.559f, 0f, 1.013f, -0.453f, 1.013f, -1.012f)
            verticalLineTo(9.546f)
            curveTo(17.195f, 8.987f, 16.741f, 8.533f, 16.182f, 8.533f)
            close()
            moveTo(5.231f, 14.329f)
            curveToRelative(-0.559f, 0f, -1.014f, -0.455f, -1.014f, -1.014f)
            reflectiveCurveToRelative(0.455f, -1.014f, 1.014f, -1.014f)
            reflectiveCurveToRelative(1.014f, 0.455f, 1.014f, 1.014f)
            reflectiveCurveTo(5.79f, 14.329f, 5.231f, 14.329f)
            close()
            moveTo(5.231f, 11.645f)
            curveToRelative(-0.559f, 0f, -1.014f, -0.455f, -1.014f, -1.014f)
            reflectiveCurveToRelative(0.455f, -1.014f, 1.014f, -1.014f)
            reflectiveCurveToRelative(1.014f, 0.455f, 1.014f, 1.014f)
            reflectiveCurveTo(5.79f, 11.645f, 5.231f, 11.645f)
            close()
            moveTo(13.802f, 13.543f)
            curveToRelative(0f, 0.14f, -0.114f, 0.254f, -0.254f, 0.254f)
            horizontalLineTo(7.136f)
            curveToRelative(-0.14f, 0f, -0.254f, -0.114f, -0.254f, -0.254f)
            verticalLineToRelative(-3.14f)
            curveToRelative(0f, -0.14f, 0.114f, -0.254f, 0.254f, -0.254f)
            horizontalLineToRelative(6.412f)
            curveToRelative(0.14f, 0f, 0.254f, 0.114f, 0.254f, 0.254f)
            verticalLineTo(13.543f)
            close()
            moveTo(15.453f, 14.329f)
            curveToRelative(-0.559f, 0f, -1.014f, -0.455f, -1.014f, -1.014f)
            reflectiveCurveToRelative(0.455f, -1.014f, 1.014f, -1.014f)
            reflectiveCurveToRelative(1.014f, 0.455f, 1.014f, 1.014f)
            reflectiveCurveTo(16.012f, 14.329f, 15.453f, 14.329f)
            close()
            moveTo(15.453f, 11.645f)
            curveToRelative(-0.559f, 0f, -1.014f, -0.455f, -1.014f, -1.014f)
            reflectiveCurveToRelative(0.455f, -1.014f, 1.014f, -1.014f)
            reflectiveCurveToRelative(1.014f, 0.455f, 1.014f, 1.014f)
            reflectiveCurveTo(16.012f, 11.645f, 15.453f, 11.645f)
            close()
        }

        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 0.2f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(13.548f, 10.319f)
            horizontalLineTo(7.136f)
            curveToRelative(-0.046f, 0f, -0.084f, 0.038f, -0.084f, 0.084f)
            verticalLineToRelative(3.14f)
            curveToRelative(0f, 0.046f, 0.038f, 0.084f, 0.084f, 0.084f)
            horizontalLineToRelative(6.412f)
            curveToRelative(0.046f, 0f, 0.084f, -0.038f, 0.084f, -0.084f)
            verticalLineToRelative(-3.14f)
            curveTo(13.632f, 10.357f, 13.595f, 10.319f, 13.548f, 10.319f)
            close()
        }

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
            moveTo(11.048f, 11.629f)
            horizontalLineToRelative(-0.1f)
            verticalLineToRelative(0.181f)
            horizontalLineToRelative(0.1f)
            curveToRelative(0.031f, 0f, 0.055f, -0.008f, 0.072f, -0.024f)
            curveToRelative(0.017f, -0.016f, 0.026f, -0.038f, 0.026f, -0.066f)
            curveToRelative(0f, -0.028f, -0.008f, -0.051f, -0.024f, -0.067f)
            curveToRelative(-0.016f, -0.016f, -0.04f, -0.024f, -0.074f, -0.024f)
            close()
        }

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
            moveTo(12.144f, 12.637f)
            curveToRelative(0.035f, 0f, 0.061f, -0.011f, 0.08f, -0.034f)
            curveToRelative(0.019f, -0.022f, 0.028f, -0.052f, 0.028f, -0.089f)
            curveToRelative(0f, -0.036f, -0.009f, -0.065f, -0.029f, -0.087f)
            curveToRelative(-0.019f, -0.022f, -0.046f, -0.033f, -0.081f, -0.033f)
            curveToRelative(-0.034f, 0f, -0.061f, 0.01f, -0.08f, 0.032f)
            curveToRelative(-0.019f, 0.021f, -0.029f, 0.05f, -0.029f, 0.088f)
            curveToRelative(0f, 0.037f, 0.01f, 0.066f, 0.029f, 0.089f)
            curveToRelative(0.019f, 0.022f, 0.046f, 0.033f, 0.081f, 0.033f)
            close()
        }

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
            moveTo(10.38f, 11.672f)
            curveToRelative(-0.023f, 0.032f, -0.034f, 0.08f, -0.034f, 0.143f)
            verticalLineToRelative(0.029f)
            curveToRelative(0f, 0.062f, 0.011f, 0.109f, 0.034f, 0.143f)
            curveToRelative(0.023f, 0.034f, 0.055f, 0.051f, 0.098f, 0.051f)
            curveToRelative(0.042f, 0f, 0.074f, -0.016f, 0.096f, -0.049f)
            curveToRelative(0.022f, -0.033f, 0.034f, -0.08f, 0.034f, -0.143f)
            verticalLineToRelative(-0.029f)
            curveToRelative(0f, -0.063f, -0.011f, -0.111f, -0.034f, -0.144f)
            curveToRelative(-0.023f, -0.033f, -0.055f, -0.049f, -0.097f, -0.049f)
            curveToRelative(-0.042f, 0f, -0.074f, 0.016f, -0.097f, 0.049f)
            close()
        }

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
            moveTo(7.434f, 12.403f)
            lineTo(7.598f, 12.403f)
            lineTo(7.598f, 12.952f)
            lineTo(7.434f, 12.952f)
            close()
        }

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
            moveTo(12.143f, 12.785f)
            curveToRelative(-0.039f, 0f, -0.07f, 0.012f, -0.093f, 0.036f)
            curveToRelative(-0.023f, 0.024f, -0.035f, 0.056f, -0.035f, 0.097f)
            curveToRelative(0f, 0.039f, 0.011f, 0.071f, 0.034f, 0.095f)
            curveToRelative(0.023f, 0.024f, 0.055f, 0.036f, 0.095f, 0.036f)
            curveToRelative(0.04f, 0f, 0.071f, -0.012f, 0.094f, -0.035f)
            curveToRelative(0.023f, -0.023f, 0.034f, -0.055f, 0.034f, -0.096f)
            curveToRelative(0f, -0.04f, -0.012f, -0.073f, -0.035f, -0.097f)
            curveToRelative(-0.023f, -0.024f, -0.054f, -0.036f, -0.093f, -0.036f)
            close()
        }

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
            moveTo(10.716f, 13.049f)
            arcToRelative(0.144f, 0.144f, 0f, true, true, -0.288f, 0f)
            arcToRelative(0.144f, 0.144f, 0f, true, true, 0.288f, 0f)
            close()
        }

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
            moveTo(8.043f, 12.248f)
            verticalLineToRelative(0.089f)
            horizontalLineTo(7.922f)
            verticalLineToRelative(-0.089f)
            horizontalLineTo(7.718f)
            verticalLineToRelative(0.859f)
            horizontalLineToRelative(1.837f)
            lineToRelative(0.356f, -0.43f)
            lineToRelative(-0.356f, -0.43f)
            horizontalLineTo(8.043f)
            close()
            moveTo(9.544f, 13.068f)
            horizontalLineTo(8.321f)
            verticalLineToRelative(-0.781f)
            horizontalLineToRelative(1.224f)
            lineToRelative(0.326f, 0.39f)
            lineTo(9.544f, 13.068f)
            close()
        }

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
            moveTo(8.697f, 11.189f)
            curveToRelative(0.029f, 0f, 0.05f, -0.011f, 0.063f, -0.034f)
            curveToRelative(0.013f, -0.023f, 0.02f, -0.058f, 0.02f, -0.105f)
            verticalLineToRelative(-0.143f)
            curveToRelative(0f, -0.05f, -0.007f, -0.086f, -0.021f, -0.109f)
            curveToRelative(-0.014f, -0.023f, -0.035f, -0.034f, -0.064f, -0.034f)
            curveToRelative(-0.028f, 0f, -0.049f, 0.011f, -0.062f, 0.033f)
            curveToRelative(-0.013f, 0.022f, -0.021f, 0.056f, -0.021f, 0.102f)
            verticalLineToRelative(0.147f)
            curveToRelative(0f, 0.049f, 0.007f, 0.086f, 0.02f, 0.11f)
            curveToRelative(0.013f, 0.024f, 0.035f, 0.036f, 0.064f, 0.036f)
            close()
        }

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
            moveTo(13.548f, 10.319f)
            horizontalLineTo(7.136f)
            curveToRelative(-0.046f, 0f, -0.084f, 0.038f, -0.084f, 0.084f)
            verticalLineToRelative(3.14f)
            curveToRelative(0f, 0.046f, 0.038f, 0.084f, 0.084f, 0.084f)
            horizontalLineToRelative(6.412f)
            curveToRelative(0.046f, 0f, 0.084f, -0.038f, 0.084f, -0.084f)
            verticalLineToRelative(-3.14f)
            curveTo(13.632f, 10.357f, 13.595f, 10.319f, 13.548f, 10.319f)
            close()
            moveTo(11.072f, 10.807f)
            lineTo(11.247f, 10.632f)
            verticalLineToRelative(0.35f)
            verticalLineToRelative(0.35f)
            lineTo(11.072f, 11.157f)
            lineTo(10.897f, 10.982f)
            lineTo(11.072f, 10.807f)
            close()
            moveTo(10.701f, 11.66f)
            curveToRelative(0.021f, 0.045f, 0.032f, 0.097f, 0.032f, 0.156f)
            verticalLineToRelative(0.027f)
            curveToRelative(0f, 0.056f, -0.009f, 0.105f, -0.027f, 0.147f)
            curveToRelative(-0.018f, 0.042f, -0.044f, 0.075f, -0.076f, 0.1f)
            lineToRelative(0.1f, 0.079f)
            lineToRelative(-0.079f, 0.07f)
            lineToRelative(-0.128f, -0.103f)
            curveToRelative(-0.015f, 0.002f, -0.03f, 0.004f, -0.046f, 0.004f)
            curveToRelative(-0.05f, 0f, -0.095f, -0.012f, -0.134f, -0.036f)
            curveToRelative(-0.039f, -0.024f, -0.07f, -0.058f, -0.091f, -0.103f)
            curveToRelative(-0.022f, -0.045f, -0.032f, -0.096f, -0.033f, -0.154f)
            verticalLineToRelative(-0.03f)
            curveToRelative(0f, -0.059f, 0.011f, -0.112f, 0.032f, -0.157f)
            curveToRelative(0.021f, -0.045f, 0.052f, -0.08f, 0.091f, -0.104f)
            curveToRelative(0.039f, -0.024f, 0.084f, -0.036f, 0.134f, -0.036f)
            curveToRelative(0.05f, 0f, 0.095f, 0.012f, 0.134f, 0.036f)
            curveToRelative(0.039f, 0.024f, 0.069f, 0.058f, 0.091f, 0.104f)
            close()
            moveTo(10.208f, 10.763f)
            curveToRelative(0.018f, -0.03f, 0.042f, -0.054f, 0.074f, -0.071f)
            curveToRelative(0.032f, -0.017f, 0.068f, -0.026f, 0.109f, -0.026f)
            curveToRelative(0.062f, 0f, 0.11f, 0.015f, 0.145f, 0.045f)
            curveToRelative(0.034f, 0.03f, 0.052f, 0.072f, 0.052f, 0.126f)
            curveToRelative(0f, 0.03f, -0.008f, 0.06f, -0.023f, 0.091f)
            curveToRelative(-0.016f, 0.031f, -0.042f, 0.067f, -0.08f, 0.108f)
            lineToRelative(-0.137f, 0.145f)
            horizontalLineToRelative(0.259f)
            verticalLineToRelative(0.096f)
            horizontalLineToRelative(-0.413f)
            verticalLineToRelative(-0.082f)
            lineToRelative(0.195f, -0.208f)
            curveToRelative(0.027f, -0.029f, 0.047f, -0.055f, 0.059f, -0.077f)
            curveToRelative(0.013f, -0.022f, 0.019f, -0.043f, 0.019f, -0.062f)
            curveToRelative(0f, -0.027f, -0.007f, -0.048f, -0.02f, -0.063f)
            curveToRelative(-0.014f, -0.015f, -0.033f, -0.023f, -0.058f, -0.023f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.009f, -0.064f, 0.028f)
            curveToRelative(-0.016f, 0.019f, -0.023f, 0.043f, -0.023f, 0.073f)
            horizontalLineToRelative(-0.12f)
            curveToRelative(0f, -0.036f, 0.009f, -0.07f, 0.027f, -0.1f)
            close()
            moveTo(9.721f, 10.763f)
            curveToRelative(0.018f, -0.03f, 0.042f, -0.054f, 0.074f, -0.071f)
            curveToRelative(0.032f, -0.017f, 0.068f, -0.026f, 0.109f, -0.026f)
            curveToRelative(0.062f, 0f, 0.11f, 0.015f, 0.145f, 0.045f)
            curveToRelative(0.034f, 0.03f, 0.052f, 0.072f, 0.052f, 0.126f)
            curveToRelative(0f, 0.03f, -0.008f, 0.06f, -0.023f, 0.091f)
            curveToRelative(-0.016f, 0.031f, -0.042f, 0.067f, -0.08f, 0.108f)
            lineToRelative(-0.137f, 0.145f)
            horizontalLineToRelative(0.259f)
            verticalLineToRelative(0.096f)
            horizontalLineToRelative(-0.413f)
            verticalLineToRelative(-0.082f)
            lineToRelative(0.195f, -0.208f)
            curveToRelative(0.027f, -0.029f, 0.047f, -0.055f, 0.059f, -0.077f)
            curveToRelative(0.013f, -0.022f, 0.019f, -0.043f, 0.019f, -0.062f)
            curveToRelative(0f, -0.027f, -0.007f, -0.048f, -0.02f, -0.063f)
            curveToRelative(-0.014f, -0.015f, -0.033f, -0.023f, -0.058f, -0.023f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.009f, -0.064f, 0.028f)
            curveToRelative(-0.016f, 0.019f, -0.023f, 0.043f, -0.023f, 0.073f)
            horizontalLineToRelative(-0.12f)
            curveToRelative(0f, -0.036f, 0.009f, -0.07f, 0.027f, -0.1f)
            close()
            moveTo(9.817f, 11.942f)
            curveToRelative(0f, 0.067f, 0.04f, 0.1f, 0.119f, 0.1f)
            curveToRelative(0.03f, 0f, 0.053f, -0.006f, 0.069f, -0.018f)
            curveToRelative(0.017f, -0.012f, 0.025f, -0.029f, 0.025f, -0.05f)
            curveToRelative(0f, -0.024f, -0.008f, -0.042f, -0.025f, -0.054f)
            curveToRelative(-0.017f, -0.013f, -0.046f, -0.026f, -0.089f, -0.04f)
            curveToRelative(-0.043f, -0.014f, -0.077f, -0.028f, -0.102f, -0.041f)
            curveToRelative(-0.069f, -0.037f, -0.103f, -0.087f, -0.103f, -0.149f)
            curveToRelative(0f, -0.033f, 0.009f, -0.062f, 0.028f, -0.087f)
            curveToRelative(0.018f, -0.025f, 0.045f, -0.045f, 0.079f, -0.06f)
            curveToRelative(0.034f, -0.014f, 0.073f, -0.022f, 0.116f, -0.022f)
            curveToRelative(0.043f, 0f, 0.081f, 0.008f, 0.115f, 0.023f)
            curveToRelative(0.034f, 0.016f, 0.06f, 0.038f, 0.078f, 0.066f)
            curveToRelative(0.019f, 0.028f, 0.028f, 0.061f, 0.028f, 0.097f)
            horizontalLineToRelative(-0.124f)
            curveToRelative(0f, -0.028f, -0.009f, -0.049f, -0.026f, -0.064f)
            curveToRelative(-0.017f, -0.015f, -0.042f, -0.023f, -0.073f, -0.023f)
            curveToRelative(-0.03f, 0f, -0.054f, 0.006f, -0.071f, 0.019f)
            curveToRelative(-0.017f, 0.013f, -0.025f, 0.03f, -0.025f, 0.051f)
            curveToRelative(0f, 0.02f, 0.01f, 0.036f, 0.03f, 0.049f)
            curveToRelative(0.02f, 0.013f, 0.049f, 0.026f, 0.087f, 0.037f)
            curveToRelative(0.071f, 0.021f, 0.122f, 0.048f, 0.154f, 0.079f)
            curveToRelative(0.032f, 0.031f, 0.048f, 0.071f, 0.048f, 0.118f)
            curveToRelative(0f, 0.052f, -0.02f, 0.093f, -0.059f, 0.123f)
            curveToRelative(-0.039f, 0.03f, -0.093f, 0.045f, -0.159f, 0.045f)
            curveToRelative(-0.046f, 0f, -0.089f, -0.008f, -0.127f, -0.025f)
            curveToRelative(-0.038f, -0.017f, -0.067f, -0.04f, -0.087f, -0.07f)
            curveToRelative(-0.02f, -0.03f, -0.03f, -0.064f, -0.03f, -0.103f)
            horizontalLineToRelative(0.125f)
            close()
            moveTo(9.497f, 10.833f)
            curveToRelative(0.013f, -0.012f, 0.029f, -0.018f, 0.048f, -0.018f)
            curveToRelative(0.02f, 0f, 0.036f, 0.006f, 0.049f, 0.018f)
            curveToRelative(0.013f, 0.012f, 0.019f, 0.027f, 0.019f, 0.046f)
            curveToRelative(0f, 0.019f, -0.006f, 0.034f, -0.019f, 0.046f)
            curveToRelative(-0.013f, 0.012f, -0.029f, 0.018f, -0.049f, 0.018f)
            curveToRelative(-0.02f, 0f, -0.036f, -0.006f, -0.048f, -0.018f)
            curveToRelative(-0.013f, -0.012f, -0.019f, -0.027f, -0.019f, -0.046f)
            curveToRelative(0f, -0.019f, 0.007f, -0.035f, 0.019f, -0.047f)
            close()
            moveTo(9.497f, 11.172f)
            curveToRelative(0.013f, -0.012f, 0.029f, -0.018f, 0.048f, -0.018f)
            curveToRelative(0.02f, 0f, 0.036f, 0.006f, 0.049f, 0.018f)
            curveToRelative(0.013f, 0.012f, 0.019f, 0.027f, 0.019f, 0.046f)
            curveToRelative(0f, 0.019f, -0.006f, 0.034f, -0.019f, 0.046f)
            curveToRelative(-0.013f, 0.012f, -0.029f, 0.018f, -0.049f, 0.018f)
            curveToRelative(-0.02f, 0f, -0.036f, -0.006f, -0.048f, -0.018f)
            curveToRelative(-0.013f, -0.012f, -0.019f, -0.027f, -0.019f, -0.046f)
            curveToRelative(0f, -0.019f, 0.007f, -0.035f, 0.019f, -0.047f)
            close()
            moveTo(8.979f, 10.923f)
            curveToRelative(0f, -0.084f, 0.017f, -0.148f, 0.052f, -0.192f)
            curveToRelative(0.035f, -0.044f, 0.085f, -0.065f, 0.151f, -0.065f)
            curveToRelative(0.066f, 0f, 0.116f, 0.022f, 0.151f, 0.065f)
            curveToRelative(0.035f, 0.043f, 0.053f, 0.105f, 0.053f, 0.186f)
            verticalLineToRelative(0.111f)
            curveToRelative(0f, 0.083f, -0.017f, 0.147f, -0.052f, 0.191f)
            curveToRelative(-0.034f, 0.044f, -0.085f, 0.066f, -0.152f, 0.066f)
            curveToRelative(-0.066f, 0f, -0.116f, -0.022f, -0.151f, -0.065f)
            curveToRelative(-0.035f, -0.043f, -0.053f, -0.105f, -0.053f, -0.186f)
            verticalLineToRelative(-0.111f)
            close()
            moveTo(8.493f, 10.923f)
            curveToRelative(0f, -0.084f, 0.017f, -0.148f, 0.052f, -0.192f)
            curveToRelative(0.035f, -0.044f, 0.085f, -0.065f, 0.151f, -0.065f)
            curveToRelative(0.066f, 0f, 0.116f, 0.022f, 0.151f, 0.065f)
            curveToRelative(0.035f, 0.043f, 0.053f, 0.105f, 0.053f, 0.186f)
            verticalLineToRelative(0.111f)
            curveToRelative(0f, 0.083f, -0.017f, 0.147f, -0.052f, 0.191f)
            curveToRelative(-0.034f, 0.044f, -0.085f, 0.066f, -0.152f, 0.066f)
            curveToRelative(-0.066f, 0f, -0.116f, -0.022f, -0.151f, -0.065f)
            curveToRelative(-0.035f, -0.043f, -0.053f, -0.105f, -0.053f, -0.186f)
            verticalLineToRelative(-0.111f)
            close()
            moveTo(7.811f, 10.641f)
            horizontalLineToRelative(0.12f)
            verticalLineToRelative(0.237f)
            curveToRelative(0.032f, -0.038f, 0.072f, -0.057f, 0.12f, -0.057f)
            curveToRelative(0.097f, 0f, 0.146f, 0.056f, 0.148f, 0.169f)
            verticalLineToRelative(0.287f)
            horizontalLineToRelative(-0.12f)
            verticalLineToRelative(-0.284f)
            curveToRelative(0f, -0.026f, -0.005f, -0.045f, -0.017f, -0.057f)
            curveToRelative(-0.011f, -0.012f, -0.029f, -0.018f, -0.055f, -0.018f)
            curveToRelative(-0.035f, 0f, -0.06f, 0.013f, -0.076f, 0.041f)
            verticalLineToRelative(0.318f)
            horizontalLineToRelative(-0.12f)
            verticalLineTo(10.641f)
            close()
            moveTo(7.26f, 10.674f)
            horizontalLineToRelative(0.491f)
            verticalLineToRelative(0.101f)
            horizontalLineTo(7.566f)
            verticalLineToRelative(0.502f)
            horizontalLineTo(7.442f)
            verticalLineToRelative(-0.502f)
            horizontalLineTo(7.26f)
            verticalLineTo(10.674f)
            close()
            moveTo(9.612f, 13.228f)
            horizontalLineTo(7.597f)
            verticalLineToRelative(-0.155f)
            horizontalLineTo(7.434f)
            verticalLineToRelative(0.068f)
            horizontalLineTo(7.313f)
            verticalLineToRelative(-0.927f)
            horizontalLineToRelative(0.121f)
            verticalLineToRelative(0.069f)
            horizontalLineToRelative(0.164f)
            verticalLineToRelative(-0.155f)
            horizontalLineToRelative(2.014f)
            lineToRelative(0.406f, 0.49f)
            horizontalLineToRelative(0.186f)
            curveToRelative(0.062f, -0.056f, 0.185f, -0.156f, 0.27f, -0.156f)
            curveToRelative(0.119f, 0f, 0.216f, 0.097f, 0.216f, 0.216f)
            curveToRelative(0f, 0.119f, -0.097f, 0.216f, -0.216f, 0.216f)
            curveToRelative(-0.085f, 0f, -0.208f, -0.1f, -0.27f, -0.156f)
            horizontalLineToRelative(-0.186f)
            lineTo(9.612f, 13.228f)
            close()
            moveTo(10.716f, 13.409f)
            curveToRelative(-0.119f, 0f, -0.216f, -0.097f, -0.216f, -0.216f)
            reflectiveCurveToRelative(0.097f, -0.216f, 0.216f, -0.216f)
            reflectiveCurveToRelative(0.216f, 0.097f, 0.216f, 0.216f)
            reflectiveCurveTo(10.835f, 13.409f, 10.716f, 13.409f)
            close()
            moveTo(10.949f, 11.911f)
            verticalLineToRelative(0.221f)
            horizontalLineToRelative(-0.124f)
            verticalLineToRelative(-0.603f)
            horizontalLineToRelative(0.224f)
            curveToRelative(0.071f, 0f, 0.126f, 0.016f, 0.165f, 0.048f)
            curveToRelative(0.039f, 0.032f, 0.058f, 0.077f, 0.058f, 0.135f)
            curveToRelative(0f, 0.041f, -0.009f, 0.075f, -0.027f, 0.103f)
            curveToRelative(-0.018f, 0.027f, -0.045f, 0.049f, -0.081f, 0.066f)
            lineToRelative(0.13f, 0.246f)
            verticalLineToRelative(0.006f)
            horizontalLineToRelative(-0.133f)
            lineToRelative(-0.113f, -0.221f)
            horizontalLineTo(10.949f)
            close()
            moveTo(11.533f, 13.184f)
            horizontalLineToRelative(-0.184f)
            verticalLineToRelative(-0.708f)
            lineToRelative(-0.219f, 0.068f)
            verticalLineToRelative(-0.149f)
            lineToRelative(0.383f, -0.137f)
            horizontalLineToRelative(0.02f)
            verticalLineTo(13.184f)
            close()
            moveTo(11.534f, 10.982f)
            verticalLineToRelative(0.214f)
            lineToRelative(-0.112f, -0.107f)
            lineToRelative(-0.112f, -0.107f)
            lineToRelative(0.112f, -0.107f)
            lineToRelative(0.112f, -0.107f)
            verticalLineTo(10.982f)
            close()
            moveTo(12.372f, 13.126f)
            curveToRelative(-0.056f, 0.048f, -0.132f, 0.071f, -0.228f, 0.071f)
            reflectiveCurveToRelative(-0.172f, -0.024f, -0.229f, -0.072f)
            curveToRelative(-0.056f, -0.048f, -0.085f, -0.113f, -0.085f, -0.194f)
            curveToRelative(0f, -0.051f, 0.013f, -0.096f, 0.04f, -0.134f)
            curveToRelative(0.026f, -0.038f, 0.062f, -0.068f, 0.106f, -0.089f)
            curveToRelative(-0.039f, -0.021f, -0.07f, -0.048f, -0.092f, -0.083f)
            curveToRelative(-0.022f, -0.035f, -0.033f, -0.075f, -0.033f, -0.119f)
            curveToRelative(0f, -0.079f, 0.026f, -0.142f, 0.079f, -0.188f)
            curveToRelative(0.052f, -0.047f, 0.124f, -0.07f, 0.214f, -0.07f)
            curveToRelative(0.09f, 0f, 0.161f, 0.023f, 0.214f, 0.07f)
            curveToRelative(0.053f, 0.046f, 0.079f, 0.109f, 0.079f, 0.189f)
            curveToRelative(0f, 0.045f, -0.011f, 0.085f, -0.034f, 0.119f)
            curveToRelative(-0.022f, 0.035f, -0.053f, 0.062f, -0.093f, 0.083f)
            curveToRelative(0.045f, 0.022f, 0.081f, 0.051f, 0.107f, 0.089f)
            curveToRelative(0.026f, 0.038f, 0.039f, 0.082f, 0.039f, 0.134f)
            curveToRelative(0f, 0.081f, -0.028f, 0.146f, -0.084f, 0.194f)
            close()
            moveTo(13.209f, 12.362f)
            lineToRelative(-0.358f, 0.822f)
            horizontalLineToRelative(-0.194f)
            lineToRelative(0.358f, -0.777f)
            horizontalLineToRelative(-0.46f)
            verticalLineToRelative(-0.148f)
            horizontalLineToRelative(0.653f)
            verticalLineTo(12.362f)
            close()
            moveTo(13.42f, 11.211f)
            lineToRelative(-0.129f, 0.13f)
            horizontalLineToRelative(-1.372f)
            verticalLineToRelative(-0.718f)
            horizontalLineToRelative(1.372f)
            lineToRelative(0.129f, 0.13f)
            verticalLineTo(11.211f)
            close()
        }

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
            moveTo(11.991f, 10.695f)
            verticalLineToRelative(0.573f)
            horizontalLineToRelative(1.27f)
            lineToRelative(0.087f, -0.088f)
            verticalLineToRelative(-0.398f)
            lineToRelative(-0.087f, -0.088f)
            horizontalLineTo(11.991f)
            close()
            moveTo(12.276f, 11.248f)
            horizontalLineToRelative(-0.261f)
            verticalLineToRelative(-0.533f)
            horizontalLineToRelative(0.261f)
            verticalLineTo(11.248f)
            close()
            moveTo(12.573f, 11.248f)
            horizontalLineToRelative(-0.261f)
            verticalLineToRelative(-0.533f)
            horizontalLineToRelative(0.261f)
            verticalLineTo(11.248f)
            close()
            moveTo(12.87f, 11.248f)
            horizontalLineToRelative(-0.261f)
            verticalLineToRelative(-0.533f)
            horizontalLineToRelative(0.261f)
            verticalLineTo(11.248f)
            close()
        }

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
            moveTo(10.245f, 12.678f)
            curveToRelative(0.07f, 0.064f, 0.175f, 0.144f, 0.229f, 0.144f)
            curveToRelative(0.079f, 0f, 0.144f, -0.064f, 0.144f, -0.144f)
            curveToRelative(0f, -0.079f, -0.065f, -0.144f, -0.144f, -0.144f)
            curveToRelative(-0.079f, 0f, -0.184f, 0.08f, -0.254f, 0.144f)
            close()
        }

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
            moveTo(9.183f, 11.189f)
            curveToRelative(0.029f, 0f, 0.05f, -0.011f, 0.063f, -0.034f)
            curveToRelative(0.013f, -0.023f, 0.02f, -0.058f, 0.02f, -0.105f)
            verticalLineToRelative(-0.143f)
            curveToRelative(0f, -0.05f, -0.007f, -0.086f, -0.021f, -0.109f)
            curveToRelative(-0.014f, -0.023f, -0.035f, -0.034f, -0.064f, -0.034f)
            curveToRelative(-0.028f, 0f, -0.049f, 0.011f, -0.062f, 0.033f)
            curveToRelative(-0.013f, 0.022f, -0.021f, 0.056f, -0.021f, 0.102f)
            verticalLineToRelative(0.147f)
            curveToRelative(0f, 0.049f, 0.007f, 0.086f, 0.02f, 0.11f)
            curveToRelative(0.013f, 0.024f, 0.035f, 0.036f, 0.064f, 0.036f)
            close()
        }

        // Text in screen
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
            moveTo(7.751f, 10.775f)
            horizontalLineTo(7.566f)
            verticalLineToRelative(0.502f)
            horizontalLineTo(7.442f)
            verticalLineToRelative(-0.502f)
            horizontalLineTo(7.26f)
            verticalLineToRelative(-0.101f)
            horizontalLineToRelative(0.491f)
            verticalLineTo(10.775f)
            close()
            moveTo(7.931f, 10.878f)
            curveToRelative(0.032f, -0.038f, 0.072f, -0.057f, 0.12f, -0.057f)
            curveToRelative(0.097f, 0f, 0.146f, 0.056f, 0.148f, 0.169f)
            verticalLineToRelative(0.287f)
            horizontalLineToRelative(-0.12f)
            verticalLineToRelative(-0.284f)
            curveToRelative(0f, -0.026f, -0.005f, -0.045f, -0.017f, -0.057f)
            curveToRelative(-0.011f, -0.012f, -0.029f, -0.018f, -0.055f, -0.018f)
            curveToRelative(-0.035f, 0f, -0.06f, 0.013f, -0.076f, 0.041f)
            verticalLineToRelative(0.318f)
            horizontalLineToRelative(-0.12f)
            verticalLineToRelative(-0.636f)
            horizontalLineToRelative(0.12f)
            verticalLineTo(10.878f)
            close()
            moveTo(8.9f, 11.028f)
            curveToRelative(0f, 0.083f, -0.017f, 0.147f, -0.052f, 0.191f)
            curveToRelative(-0.034f, 0.044f, -0.085f, 0.066f, -0.152f, 0.066f)
            curveToRelative(-0.066f, 0f, -0.116f, -0.022f, -0.151f, -0.065f)
            curveToRelative(-0.035f, -0.043f, -0.053f, -0.105f, -0.053f, -0.186f)
            verticalLineToRelative(-0.111f)
            curveToRelative(0f, -0.084f, 0.017f, -0.148f, 0.052f, -0.192f)
            curveToRelative(0.035f, -0.044f, 0.085f, -0.065f, 0.151f, -0.065f)
            curveToRelative(0.066f, 0f, 0.116f, 0.022f, 0.151f, 0.065f)
            curveToRelative(0.035f, 0.043f, 0.053f, 0.105f, 0.053f, 0.186f)
            verticalLineTo(11.028f)
            close()
            moveTo(8.781f, 10.906f)
            curveToRelative(0f, -0.05f, -0.007f, -0.086f, -0.021f, -0.109f)
            curveToRelative(-0.014f, -0.023f, -0.035f, -0.034f, -0.064f, -0.034f)
            curveToRelative(-0.028f, 0f, -0.049f, 0.011f, -0.062f, 0.033f)
            curveToRelative(-0.013f, 0.022f, -0.021f, 0.056f, -0.021f, 0.102f)
            verticalLineToRelative(0.147f)
            curveToRelative(0f, 0.049f, 0.007f, 0.086f, 0.02f, 0.11f)
            curveToRelative(0.013f, 0.024f, 0.035f, 0.036f, 0.064f, 0.036f)
            curveToRelative(0.029f, 0f, 0.05f, -0.011f, 0.063f, -0.034f)
            curveToRelative(0.013f, -0.023f, 0.02f, -0.058f, 0.02f, -0.105f)
            verticalLineTo(10.906f)
            close()
            moveTo(9.387f, 11.028f)
            curveToRelative(0f, 0.083f, -0.017f, 0.147f, -0.052f, 0.191f)
            curveToRelative(-0.034f, 0.044f, -0.085f, 0.066f, -0.152f, 0.066f)
            curveToRelative(-0.066f, 0f, -0.116f, -0.022f, -0.151f, -0.065f)
            curveToRelative(-0.035f, -0.043f, -0.053f, -0.105f, -0.053f, -0.186f)
            verticalLineToRelative(-0.111f)
            curveToRelative(0f, -0.084f, 0.017f, -0.148f, 0.052f, -0.192f)
            curveToRelative(0.035f, -0.044f, 0.085f, -0.065f, 0.151f, -0.065f)
            curveToRelative(0.066f, 0f, 0.116f, 0.022f, 0.151f, 0.065f)
            curveToRelative(0.035f, 0.043f, 0.053f, 0.105f, 0.053f, 0.186f)
            verticalLineTo(11.028f)
            close()
            moveTo(9.267f, 10.906f)
            curveToRelative(0f, -0.05f, -0.007f, -0.086f, -0.021f, -0.109f)
            curveToRelative(-0.014f, -0.023f, -0.035f, -0.034f, -0.064f, -0.034f)
            curveToRelative(-0.028f, 0f, -0.049f, 0.011f, -0.062f, 0.033f)
            curveToRelative(-0.013f, 0.022f, -0.021f, 0.056f, -0.021f, 0.102f)
            verticalLineToRelative(0.147f)
            curveToRelative(0f, 0.049f, 0.007f, 0.086f, 0.02f, 0.11f)
            curveToRelative(0.013f, 0.024f, 0.035f, 0.036f, 0.064f, 0.036f)
            curveToRelative(0.029f, 0f, 0.05f, -0.011f, 0.063f, -0.034f)
            curveToRelative(0.013f, -0.023f, 0.02f, -0.058f, 0.02f, -0.105f)
            verticalLineTo(10.906f)
            close()
            moveTo(9.478f, 10.879f)
            curveToRelative(0f, -0.019f, 0.006f, -0.034f, 0.019f, -0.046f)
            curveToRelative(0.013f, -0.012f, 0.029f, -0.018f, 0.048f, -0.018f)
            curveToRelative(0.02f, 0f, 0.036f, 0.006f, 0.049f, 0.018f)
            curveToRelative(0.013f, 0.012f, 0.019f, 0.027f, 0.019f, 0.046f)
            curveToRelative(0f, 0.019f, -0.006f, 0.034f, -0.019f, 0.046f)
            curveToRelative(-0.013f, 0.012f, -0.029f, 0.018f, -0.049f, 0.018f)
            curveToRelative(-0.02f, 0f, -0.036f, -0.006f, -0.048f, -0.018f)
            curveToRelative(-0.013f, -0.012f, -0.019f, -0.027f, -0.019f, -0.046f)
            close()
            moveTo(9.478f, 11.218f)
            curveToRelative(0f, -0.019f, 0.006f, -0.034f, 0.019f, -0.046f)
            curveToRelative(0.013f, -0.012f, 0.029f, -0.018f, 0.048f, -0.018f)
            curveToRelative(0.02f, 0f, 0.036f, 0.006f, 0.049f, 0.018f)
            curveToRelative(0.013f, 0.012f, 0.019f, 0.027f, 0.019f, 0.046f)
            curveToRelative(0f, 0.019f, -0.006f, 0.034f, -0.019f, 0.046f)
            curveToRelative(-0.013f, 0.012f, -0.029f, 0.018f, -0.049f, 0.018f)
            curveToRelative(-0.02f, 0f, -0.036f, -0.006f, -0.048f, -0.018f)
            curveToRelative(-0.013f, -0.012f, -0.019f, -0.027f, -0.019f, -0.046f)
            close()
            moveTo(10.12f, 11.277f)
            horizontalLineTo(9.707f)
            verticalLineToRelative(-0.082f)
            lineToRelative(0.195f, -0.208f)
            curveToRelative(0.027f, -0.029f, 0.047f, -0.055f, 0.059f, -0.077f)
            curveToRelative(0.013f, -0.022f, 0.019f, -0.043f, 0.019f, -0.062f)
            curveToRelative(0f, -0.027f, -0.007f, -0.048f, -0.02f, -0.063f)
            curveToRelative(-0.014f, -0.015f, -0.033f, -0.023f, -0.058f, -0.023f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.009f, -0.064f, 0.028f)
            curveToRelative(-0.016f, 0.019f, -0.023f, 0.043f, -0.023f, 0.073f)
            horizontalLineToRelative(-0.12f)
            curveToRelative(0f, -0.037f, 0.009f, -0.07f, 0.026f, -0.101f)
            curveToRelative(0.018f, -0.03f, 0.042f, -0.054f, 0.074f, -0.071f)
            curveToRelative(0.032f, -0.017f, 0.068f, -0.026f, 0.109f, -0.026f)
            curveToRelative(0.062f, 0f, 0.11f, 0.015f, 0.145f, 0.045f)
            curveToRelative(0.034f, 0.03f, 0.052f, 0.072f, 0.052f, 0.126f)
            curveToRelative(0f, 0.03f, -0.008f, 0.06f, -0.023f, 0.091f)
            curveToRelative(-0.016f, 0.031f, -0.042f, 0.067f, -0.08f, 0.108f)
            lineToRelative(-0.137f, 0.145f)
            horizontalLineToRelative(0.259f)
            verticalLineTo(11.277f)
            close()
            moveTo(10.606f, 11.277f)
            horizontalLineToRelative(-0.413f)
            verticalLineToRelative(-0.082f)
            lineToRelative(0.195f, -0.208f)
            curveToRelative(0.027f, -0.029f, 0.047f, -0.055f, 0.059f, -0.077f)
            curveToRelative(0.013f, -0.022f, 0.019f, -0.043f, 0.019f, -0.062f)
            curveToRelative(0f, -0.027f, -0.007f, -0.048f, -0.02f, -0.063f)
            curveToRelative(-0.014f, -0.015f, -0.033f, -0.023f, -0.058f, -0.023f)
            curveToRelative(-0.027f, 0f, -0.048f, 0.009f, -0.064f, 0.028f)
            curveToRelative(-0.016f, 0.019f, -0.023f, 0.043f, -0.023f, 0.073f)
            horizontalLineToRelative(-0.12f)
            curveToRelative(0f, -0.037f, 0.009f, -0.07f, 0.026f, -0.101f)
            curveToRelative(0.018f, -0.03f, 0.042f, -0.054f, 0.074f, -0.071f)
            curveToRelative(0.032f, -0.017f, 0.068f, -0.026f, 0.109f, -0.026f)
            curveToRelative(0.062f, 0f, 0.11f, 0.015f, 0.145f, 0.045f)
            curveToRelative(0.034f, 0.03f, 0.052f, 0.072f, 0.052f, 0.126f)
            curveToRelative(0f, 0.03f, -0.008f, 0.06f, -0.023f, 0.091f)
            curveToRelative(-0.016f, 0.031f, -0.042f, 0.067f, -0.08f, 0.108f)
            lineToRelative(-0.137f, 0.145f)
            horizontalLineToRelative(0.259f)
            verticalLineTo(11.277f)
            close()
        }

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
            moveTo(10.03f, 11.973f)
            curveToRelative(0f, -0.024f, -0.008f, -0.042f, -0.025f, -0.054f)
            curveToRelative(-0.017f, -0.013f, -0.046f, -0.026f, -0.089f, -0.04f)
            curveToRelative(-0.043f, -0.014f, -0.077f, -0.028f, -0.102f, -0.041f)
            curveToRelative(-0.069f, -0.037f, -0.103f, -0.087f, -0.103f, -0.149f)
            curveToRelative(0f, -0.033f, 0.009f, -0.062f, 0.028f, -0.087f)
            curveToRelative(0.018f, -0.025f, 0.045f, -0.045f, 0.079f, -0.06f)
            curveToRelative(0.034f, -0.014f, 0.073f, -0.022f, 0.116f, -0.022f)
            curveToRelative(0.043f, 0f, 0.081f, 0.008f, 0.115f, 0.023f)
            curveToRelative(0.034f, 0.016f, 0.06f, 0.038f, 0.078f, 0.066f)
            curveToRelative(0.019f, 0.028f, 0.028f, 0.061f, 0.028f, 0.097f)
            horizontalLineToRelative(-0.124f)
            curveToRelative(0f, -0.028f, -0.009f, -0.049f, -0.026f, -0.064f)
            curveToRelative(-0.017f, -0.015f, -0.042f, -0.023f, -0.073f, -0.023f)
            curveToRelative(-0.03f, 0f, -0.054f, 0.006f, -0.071f, 0.019f)
            curveToRelative(-0.017f, 0.013f, -0.025f, 0.03f, -0.025f, 0.051f)
            curveToRelative(0f, 0.02f, 0.01f, 0.036f, 0.03f, 0.049f)
            curveToRelative(0.02f, 0.013f, 0.049f, 0.026f, 0.087f, 0.037f)
            curveToRelative(0.071f, 0.021f, 0.122f, 0.048f, 0.154f, 0.079f)
            curveToRelative(0.032f, 0.031f, 0.048f, 0.071f, 0.048f, 0.118f)
            curveToRelative(0f, 0.052f, -0.02f, 0.093f, -0.059f, 0.123f)
            curveToRelative(-0.039f, 0.03f, -0.093f, 0.045f, -0.159f, 0.045f)
            curveToRelative(-0.046f, 0f, -0.089f, -0.008f, -0.127f, -0.025f)
            curveToRelative(-0.038f, -0.017f, -0.067f, -0.04f, -0.087f, -0.07f)
            curveToRelative(-0.02f, -0.03f, -0.03f, -0.064f, -0.03f, -0.103f)
            horizontalLineToRelative(0.125f)
            curveToRelative(0f, 0.067f, 0.04f, 0.1f, 0.119f, 0.1f)
            curveToRelative(0.03f, 0f, 0.053f, -0.006f, 0.069f, -0.018f)
            curveToRelative(0.016f, -0.012f, 0.024f, -0.028f, 0.024f, -0.05f)
            close()
            moveTo(10.733f, 11.844f)
            curveToRelative(0f, 0.056f, -0.009f, 0.105f, -0.027f, 0.147f)
            curveToRelative(-0.018f, 0.042f, -0.044f, 0.075f, -0.076f, 0.1f)
            lineToRelative(0.1f, 0.079f)
            lineToRelative(-0.079f, 0.07f)
            lineToRelative(-0.128f, -0.103f)
            curveToRelative(-0.015f, 0.002f, -0.03f, 0.004f, -0.046f, 0.004f)
            curveToRelative(-0.05f, 0f, -0.095f, -0.012f, -0.134f, -0.036f)
            curveToRelative(-0.039f, -0.024f, -0.07f, -0.058f, -0.091f, -0.103f)
            curveToRelative(-0.022f, -0.045f, -0.032f, -0.096f, -0.033f, -0.154f)
            verticalLineToRelative(-0.03f)
            curveToRelative(0f, -0.059f, 0.011f, -0.112f, 0.032f, -0.157f)
            curveToRelative(0.021f, -0.045f, 0.052f, -0.08f, 0.091f, -0.104f)
            curveToRelative(0.039f, -0.024f, 0.084f, -0.036f, 0.134f, -0.036f)
            curveToRelative(0.05f, 0f, 0.095f, 0.012f, 0.134f, 0.036f)
            curveToRelative(0.039f, 0.024f, 0.069f, 0.059f, 0.091f, 0.104f)
            curveToRelative(0.021f, 0.045f, 0.032f, 0.097f, 0.032f, 0.156f)
            verticalLineTo(11.844f)
            close()
            moveTo(10.607f, 11.816f)
            curveToRelative(0f, -0.063f, -0.011f, -0.111f, -0.034f, -0.144f)
            curveToRelative(-0.023f, -0.033f, -0.055f, -0.049f, -0.097f, -0.049f)
            curveToRelative(-0.042f, 0f, -0.074f, 0.016f, -0.097f, 0.049f)
            curveToRelative(-0.023f, 0.032f, -0.034f, 0.08f, -0.034f, 0.143f)
            verticalLineToRelative(0.029f)
            curveToRelative(0f, 0.062f, 0.011f, 0.109f, 0.034f, 0.143f)
            curveToRelative(0.023f, 0.034f, 0.055f, 0.051f, 0.098f, 0.051f)
            curveToRelative(0.042f, 0f, 0.074f, -0.016f, 0.096f, -0.049f)
            curveToRelative(0.022f, -0.033f, 0.034f, -0.08f, 0.034f, -0.143f)
            verticalLineTo(11.816f)
            close()
            moveTo(11.048f, 11.911f)
            horizontalLineToRelative(-0.099f)
            verticalLineToRelative(0.221f)
            horizontalLineToRelative(-0.124f)
            verticalLineToRelative(-0.603f)
            horizontalLineToRelative(0.224f)
            curveToRelative(0.071f, 0f, 0.126f, 0.016f, 0.165f, 0.048f)
            curveToRelative(0.039f, 0.032f, 0.058f, 0.077f, 0.058f, 0.135f)
            curveToRelative(0f, 0.041f, -0.009f, 0.075f, -0.027f, 0.103f)
            curveToRelative(-0.018f, 0.027f, -0.045f, 0.049f, -0.081f, 0.066f)
            lineToRelative(0.13f, 0.246f)
            verticalLineToRelative(0.006f)
            horizontalLineToRelative(-0.133f)
            lineToRelative(-0.113f, -0.221f)
            horizontalLineTo(11.048f)
            close()
            moveTo(10.949f, 11.81f)
            horizontalLineToRelative(0.1f)
            curveToRelative(0.031f, 0f, 0.055f, -0.008f, 0.072f, -0.024f)
            curveToRelative(0.017f, -0.016f, 0.026f, -0.038f, 0.026f, -0.066f)
            curveToRelative(0f, -0.028f, -0.008f, -0.051f, -0.024f, -0.067f)
            curveToRelative(-0.016f, -0.016f, -0.041f, -0.024f, -0.074f, -0.024f)
            horizontalLineToRelative(-0.1f)
            verticalLineTo(11.81f)
            close()
        }

        // (Battery, Seringe)
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
            moveTo(10.896f, 10.982f)
            lineTo(11.072f, 10.807f)
            lineTo(11.247f, 10.631f)
            verticalLineToRelative(0.351f)
            verticalLineToRelative(0.351f)
            lineTo(11.072f, 11.157f)
            close()
            moveTo(11.31f, 10.982f)
            lineTo(11.422f, 10.875f)
            lineTo(11.534f, 10.768f)
            verticalLineToRelative(0.214f)
            verticalLineToRelative(0.214f)
            lineTo(11.422f, 11.089f)
            close()
        }

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
            moveTo(12.016f, 10.716f)
            lineTo(12.277f, 10.716f)
            lineTo(12.277f, 11.249f)
            lineTo(12.016f, 11.249f)
            close()
            moveTo(12.312f, 10.716f)
            lineTo(12.573f, 10.716f)
            lineTo(12.573f, 11.249f)
            lineTo(12.312f, 11.249f)
            close()
            moveTo(12.609f, 10.716f)
            lineTo(12.87f, 10.716f)
            lineTo(12.87f, 11.249f)
            lineTo(12.609f, 11.249f)
            close()
            moveTo(13.291f, 11.341f)
            horizontalLineToRelative(-1.372f)
            verticalLineToRelative(-0.718f)
            horizontalLineToRelative(1.372f)
            lineToRelative(0.129f, 0.13f)
            verticalLineToRelative(0.457f)
            lineTo(13.291f, 11.341f)
            close()
            moveTo(11.991f, 11.268f)
            horizontalLineToRelative(1.27f)
            lineToRelative(0.087f, -0.088f)
            verticalLineToRelative(-0.398f)
            lineToRelative(-0.087f, -0.088f)
            horizontalLineToRelative(-1.27f)
            verticalLineTo(11.268f)
            close()
        }

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
            moveTo(9.544f, 13.068f)
            lineTo(8.321f, 13.068f)
            lineTo(8.321f, 12.288f)
            lineTo(9.544f, 12.288f)
            lineTo(9.87f, 12.678f)
            close()
            moveTo(10.716f, 13.409f)
            curveToRelative(-0.119f, 0f, -0.216f, -0.097f, -0.216f, -0.216f)
            reflectiveCurveToRelative(0.097f, -0.216f, 0.216f, -0.216f)
            reflectiveCurveToRelative(0.216f, 0.097f, 0.216f, 0.216f)
            reflectiveCurveTo(10.835f, 13.409f, 10.716f, 13.409f)
            close()
            moveTo(10.716f, 13.049f)
            curveToRelative(-0.079f, 0f, -0.144f, 0.064f, -0.144f, 0.144f)
            reflectiveCurveToRelative(0.065f, 0.144f, 0.144f, 0.144f)
            reflectiveCurveToRelative(0.144f, -0.064f, 0.144f, -0.144f)
            reflectiveCurveTo(10.796f, 13.049f, 10.716f, 13.049f)
            close()
            moveTo(10.474f, 12.894f)
            curveToRelative(-0.111f, 0f, -0.287f, -0.171f, -0.307f, -0.191f)
            lineToRelative(-0.026f, -0.026f)
            lineToRelative(0.026f, -0.026f)
            curveToRelative(0.02f, -0.019f, 0.196f, -0.19f, 0.307f, -0.19f)
            curveToRelative(0.119f, 0f, 0.216f, 0.097f, 0.216f, 0.216f)
            curveToRelative(0f, 0.119f, -0.097f, 0.216f, -0.216f, 0.216f)
            close()
            moveTo(10.245f, 12.678f)
            curveToRelative(0.07f, 0.064f, 0.175f, 0.144f, 0.229f, 0.144f)
            curveToRelative(0.079f, 0f, 0.144f, -0.064f, 0.144f, -0.144f)
            curveToRelative(0f, -0.079f, -0.065f, -0.144f, -0.144f, -0.144f)
            curveToRelative(-0.079f, 0f, -0.184f, 0.08f, -0.254f, 0.144f)
            close()
            moveTo(9.989f, 12.618f)
            lineTo(10.245f, 12.618f)
            lineTo(10.245f, 12.739f)
            lineTo(9.989f, 12.739f)
            close()
            moveTo(7.313f, 12.214f)
            lineTo(7.434f, 12.214f)
            lineTo(7.434f, 13.141f)
            lineTo(7.313f, 13.141f)
            close()
            moveTo(7.374f, 12.952f)
            lineTo(7.66f, 12.952f)
            lineTo(7.66f, 13.073f)
            lineTo(7.374f, 13.073f)
            close()
            moveTo(7.374f, 12.283f)
            lineTo(7.66f, 12.283f)
            lineTo(7.66f, 12.404f)
            lineTo(7.374f, 12.404f)
            close()
            moveTo(7.922f, 12.188f)
            lineTo(8.043f, 12.188f)
            lineTo(8.043f, 12.337f)
            lineTo(7.922f, 12.337f)
            close()
            moveTo(9.612f, 13.228f)
            horizontalLineTo(7.597f)
            verticalLineToRelative(-1.1f)
            horizontalLineToRelative(2.014f)
            lineToRelative(0.456f, 0.55f)
            lineTo(9.612f, 13.228f)
            close()
            moveTo(7.718f, 13.108f)
            horizontalLineToRelative(1.837f)
            lineToRelative(0.356f, -0.43f)
            lineToRelative(-0.356f, -0.43f)
            horizontalLineTo(7.718f)
            verticalLineTo(13.108f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcPluginDiaconnPreview() {
    Icon(
        imageVector = IcPluginDiaconn,
        contentDescription = "Omnipod Plugin Icon",
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
<g id="Plugin_Diaconn">
	<g>
		<path fill="#FFFFFF" d="M19.198,6.2h-0.633V17.8h0.633c1.118,0,2.025-0.907,2.025-2.025c0,0,0-2.315,0-3.051
			c0-2.208,1.577-1.533,1.577-2.829c0-0.54,0-2.495,0-2.495C22.8,6.784,20.317,6.2,19.198,6.2z"/>
		<path fill="#FFFFFF" d="M3.225,6.2C2.107,6.2,1.2,7.107,1.2,8.225v7.549c0,1.118,0.907,2.025,2.025,2.025h15.17V6.2H3.225z
			 M17.195,14.4c0,0.559-0.453,1.012-1.013,1.012H4.502c-0.559,0-1.013-0.453-1.013-1.012V9.546c0-0.559,0.453-1.012,1.013-1.012
			h11.68c0.559,0,1.013,0.453,1.013,1.012V14.4z"/>
		<g>
			<g>
				<path fill="#FFFFFF" d="M15.453,13.737c-0.233,0-0.422-0.19-0.422-0.422s0.19-0.422,0.422-0.422c0.233,0,0.422,0.19,0.422,0.422
					S15.686,13.737,15.453,13.737z M15.453,13.013c-0.166,0-0.302,0.135-0.302,0.302s0.135,0.302,0.302,0.302
					c0.166,0,0.302-0.135,0.302-0.302S15.619,13.013,15.453,13.013z"/>
				<g>

						<rect x="15.393" y="10.294" transform="matrix(0.7078 0.7064 -0.7064 0.7078 12.0257 -7.8099)" fill="#FFFFFF" width="0.121" height="0.675"/>

						<rect x="15.116" y="10.571" transform="matrix(0.707 0.7072 -0.7072 0.707 12.0453 -7.8135)" fill="#FFFFFF" width="0.675" height="0.121"/>
				</g>
			</g>
			<g>
				<polygon fill="#FFFFFF" points="5.231,13.569 4.851,13.189 4.936,13.103 5.231,13.398 5.526,13.103 5.611,13.189 				"/>
				<polygon fill="#FFFFFF" points="4.936,10.843 4.851,10.758 5.231,10.377 5.611,10.758 5.526,10.843 5.231,10.548 				"/>
			</g>
		</g>
		<g>
			<circle opacity="0.2" fill="#FFFFFF" cx="15.453" cy="13.315" r="0.302"/>
			<path opacity="0.2" fill="#FFFFFF" d="M15.453,12.471c-0.465,0-0.844,0.379-0.844,0.844s0.379,0.844,0.844,0.844
				c0.465,0,0.844-0.379,0.844-0.844S15.919,12.471,15.453,12.471z M15.453,13.737c-0.233,0-0.422-0.19-0.422-0.422
				s0.19-0.422,0.422-0.422c0.233,0,0.422,0.19,0.422,0.422S15.686,13.737,15.453,13.737z"/>
			<path opacity="0.2" fill="#FFFFFF" d="M5.231,9.787c-0.465,0-0.844,0.379-0.844,0.844c0,0.465,0.379,0.844,0.844,0.844
				c0.465,0,0.844-0.379,0.844-0.844C6.075,10.166,5.697,9.787,5.231,9.787z M5.526,10.843l-0.295-0.295l-0.295,0.295l-0.085-0.085
				l0.38-0.38l0.38,0.38L5.526,10.843z"/>
			<path opacity="0.2" fill="#FFFFFF" d="M5.231,12.471c-0.465,0-0.844,0.379-0.844,0.844s0.379,0.844,0.844,0.844
				c0.465,0,0.844-0.379,0.844-0.844S5.697,12.471,5.231,12.471z M5.231,13.569l-0.38-0.38l0.085-0.085l0.295,0.295l0.295-0.295
				l0.085,0.085L5.231,13.569z"/>
			<path opacity="0.2" fill="#FFFFFF" d="M15.453,9.787c-0.465,0-0.844,0.379-0.844,0.844c0,0.465,0.379,0.844,0.844,0.844
				c0.465,0,0.844-0.379,0.844-0.844C16.297,10.166,15.919,9.787,15.453,9.787z M15.735,10.828l-0.085,0.085l-0.196-0.196
				l-0.196,0.196l-0.085-0.085l0.196-0.196l-0.196-0.196l0.085-0.085l0.196,0.196l0.196-0.196l0.085,0.085l-0.196,0.196
				L15.735,10.828z"/>
		</g>
		<g>
			<path opacity="0.2" fill="#FFFFFF" d="M16.182,8.533H4.502c-0.559,0-1.013,0.453-1.013,1.012V14.4
				c0,0.559,0.453,1.012,1.013,1.012h11.68c0.559,0,1.013-0.453,1.013-1.012V9.546C17.195,8.987,16.741,8.533,16.182,8.533z
				 M5.231,14.329c-0.559,0-1.014-0.455-1.014-1.014c0-0.559,0.455-1.014,1.014-1.014s1.014,0.455,1.014,1.014
				C6.245,13.874,5.79,14.329,5.231,14.329z M5.231,11.645c-0.559,0-1.014-0.455-1.014-1.014s0.455-1.014,1.014-1.014
				s1.014,0.455,1.014,1.014S5.79,11.645,5.231,11.645z M13.802,13.543c0,0.14-0.114,0.254-0.254,0.254H7.136
				c-0.14,0-0.254-0.114-0.254-0.254v-3.14c0-0.14,0.114-0.254,0.254-0.254h6.412c0.14,0,0.254,0.114,0.254,0.254V13.543z
				 M15.453,14.329c-0.559,0-1.014-0.455-1.014-1.014c0-0.559,0.455-1.014,1.014-1.014c0.559,0,1.014,0.455,1.014,1.014
				C16.467,13.874,16.012,14.329,15.453,14.329z M15.453,11.645c-0.559,0-1.014-0.455-1.014-1.014s0.455-1.014,1.014-1.014
				c0.559,0,1.014,0.455,1.014,1.014S16.012,11.645,15.453,11.645z"/>
			<path opacity="0.2" fill="#FFFFFF" d="M13.548,10.319H7.136c-0.046,0-0.084,0.038-0.084,0.084v3.14
				c0,0.046,0.038,0.084,0.084,0.084h6.412c0.046,0,0.084-0.038,0.084-0.084v-3.14C13.632,10.357,13.595,10.319,13.548,10.319z"/>
		</g>
		<g>
			<path fill="#7F8188" d="M11.048,11.629h-0.1v0.181h0.1c0.031,0,0.055-0.008,0.072-0.024c0.017-0.016,0.026-0.038,0.026-0.066
				c0-0.028-0.008-0.051-0.024-0.067C11.106,11.637,11.082,11.629,11.048,11.629z"/>
			<path fill="#7F8188" d="M12.144,12.637c0.035,0,0.061-0.011,0.08-0.034c0.019-0.022,0.028-0.052,0.028-0.089
				c0-0.036-0.009-0.065-0.029-0.087c-0.019-0.022-0.046-0.033-0.081-0.033c-0.034,0-0.061,0.01-0.08,0.032
				c-0.019,0.021-0.029,0.05-0.029,0.088c0,0.037,0.01,0.066,0.029,0.089C12.082,12.626,12.109,12.637,12.144,12.637z"/>
			<path fill="#7F8188" d="M10.38,11.672c-0.023,0.032-0.034,0.08-0.034,0.143v0.029c0,0.062,0.011,0.109,0.034,0.143
				c0.023,0.034,0.055,0.051,0.098,0.051c0.042,0,0.074-0.016,0.096-0.049c0.022-0.033,0.034-0.08,0.034-0.143v-0.029
				c0-0.063-0.011-0.111-0.034-0.144c-0.023-0.033-0.055-0.049-0.097-0.049C10.435,11.623,10.403,11.639,10.38,11.672z"/>
			<rect x="7.434" y="12.403" fill="#7F8188" width="0.164" height="0.549"/>
			<path fill="#7F8188" d="M12.143,12.785c-0.039,0-0.07,0.012-0.093,0.036c-0.023,0.024-0.035,0.056-0.035,0.097
				c0,0.039,0.011,0.071,0.034,0.095c0.023,0.024,0.055,0.036,0.095,0.036c0.04,0,0.071-0.012,0.094-0.035
				c0.023-0.023,0.034-0.055,0.034-0.096c0-0.04-0.012-0.073-0.035-0.097C12.213,12.797,12.182,12.785,12.143,12.785z"/>
			<circle fill="#7F8188" cx="10.716" cy="13.193" r="0.144"/>
			<path fill="#7F8188" d="M8.043,12.248v0.089H7.922v-0.089H7.718v0.859h1.837l0.356-0.43l-0.356-0.43H8.043z M9.544,13.068H8.321
				v-0.781h1.224l0.326,0.39L9.544,13.068z"/>
			<path fill="#7F8188" d="M8.697,11.189c0.029,0,0.05-0.011,0.063-0.034c0.013-0.023,0.02-0.058,0.02-0.105v-0.143
				c0-0.05-0.007-0.086-0.021-0.109c-0.014-0.023-0.035-0.034-0.064-0.034c-0.028,0-0.049,0.011-0.062,0.033
				c-0.013,0.022-0.021,0.056-0.021,0.102v0.147c0,0.049,0.007,0.086,0.02,0.11C8.646,11.177,8.668,11.189,8.697,11.189z"/>
			<path fill="#7F8188" d="M13.548,10.319H7.136c-0.046,0-0.084,0.038-0.084,0.084v3.14c0,0.046,0.038,0.084,0.084,0.084h6.412
				c0.046,0,0.084-0.038,0.084-0.084v-3.14C13.632,10.357,13.595,10.319,13.548,10.319z M11.072,10.807l0.175-0.175v0.35v0.35
				l-0.175-0.175l-0.175-0.175L11.072,10.807z M10.701,11.66c0.021,0.045,0.032,0.097,0.032,0.156v0.027
				c0,0.056-0.009,0.105-0.027,0.147c-0.018,0.042-0.044,0.075-0.076,0.1l0.1,0.079l-0.079,0.07l-0.128-0.103
				c-0.015,0.002-0.03,0.004-0.046,0.004c-0.05,0-0.095-0.012-0.134-0.036s-0.07-0.058-0.091-0.103
				c-0.022-0.045-0.032-0.096-0.033-0.154v-0.03c0-0.059,0.011-0.112,0.032-0.157c0.021-0.045,0.052-0.08,0.091-0.104
				c0.039-0.024,0.084-0.036,0.134-0.036c0.05,0,0.095,0.012,0.134,0.036C10.65,11.581,10.68,11.615,10.701,11.66z M10.208,10.763
				c0.018-0.03,0.042-0.054,0.074-0.071c0.032-0.017,0.068-0.026,0.109-0.026c0.062,0,0.11,0.015,0.145,0.045
				c0.034,0.03,0.052,0.072,0.052,0.126c0,0.03-0.008,0.06-0.023,0.091c-0.016,0.031-0.042,0.067-0.08,0.108l-0.137,0.145h0.259
				v0.096h-0.413v-0.082l0.195-0.208c0.027-0.029,0.047-0.055,0.059-0.077c0.013-0.022,0.019-0.043,0.019-0.062
				c0-0.027-0.007-0.048-0.02-0.063c-0.014-0.015-0.033-0.023-0.058-0.023c-0.027,0-0.048,0.009-0.064,0.028
				c-0.016,0.019-0.023,0.043-0.023,0.073h-0.12C10.181,10.827,10.19,10.793,10.208,10.763z M9.721,10.763
				c0.018-0.03,0.042-0.054,0.074-0.071c0.032-0.017,0.068-0.026,0.109-0.026c0.062,0,0.11,0.015,0.145,0.045
				c0.034,0.03,0.052,0.072,0.052,0.126c0,0.03-0.008,0.06-0.023,0.091c-0.016,0.031-0.042,0.067-0.08,0.108L9.861,11.18h0.259
				v0.096H9.707v-0.082l0.195-0.208c0.027-0.029,0.047-0.055,0.059-0.077c0.013-0.022,0.019-0.043,0.019-0.062
				c0-0.027-0.007-0.048-0.02-0.063c-0.014-0.015-0.033-0.023-0.058-0.023c-0.027,0-0.048,0.009-0.064,0.028
				c-0.016,0.019-0.023,0.043-0.023,0.073h-0.12C9.695,10.827,9.704,10.793,9.721,10.763z M9.817,11.942c0,0.067,0.04,0.1,0.119,0.1
				c0.03,0,0.053-0.006,0.069-0.018c0.017-0.012,0.025-0.029,0.025-0.05c0-0.024-0.008-0.042-0.025-0.054
				c-0.017-0.013-0.046-0.026-0.089-0.04c-0.043-0.014-0.077-0.028-0.102-0.041c-0.069-0.037-0.103-0.087-0.103-0.149
				c0-0.033,0.009-0.062,0.028-0.087c0.018-0.025,0.045-0.045,0.079-0.06c0.034-0.014,0.073-0.022,0.116-0.022
				c0.043,0,0.081,0.008,0.115,0.023c0.034,0.016,0.06,0.038,0.078,0.066c0.019,0.028,0.028,0.061,0.028,0.097h-0.124
				c0-0.028-0.009-0.049-0.026-0.064c-0.017-0.015-0.042-0.023-0.073-0.023c-0.03,0-0.054,0.006-0.071,0.019
				c-0.017,0.013-0.025,0.03-0.025,0.051c0,0.02,0.01,0.036,0.03,0.049c0.02,0.013,0.049,0.026,0.087,0.037
				c0.071,0.021,0.122,0.048,0.154,0.079c0.032,0.031,0.048,0.071,0.048,0.118c0,0.052-0.02,0.093-0.059,0.123
				c-0.039,0.03-0.093,0.045-0.159,0.045c-0.046,0-0.089-0.008-0.127-0.025c-0.038-0.017-0.067-0.04-0.087-0.07
				c-0.02-0.03-0.03-0.064-0.03-0.103H9.817z M9.497,10.833c0.013-0.012,0.029-0.018,0.048-0.018c0.02,0,0.036,0.006,0.049,0.018
				c0.013,0.012,0.019,0.027,0.019,0.046c0,0.019-0.006,0.034-0.019,0.046c-0.013,0.012-0.029,0.018-0.049,0.018
				c-0.02,0-0.036-0.006-0.048-0.018c-0.013-0.012-0.019-0.027-0.019-0.046C9.478,10.86,9.485,10.844,9.497,10.833z M9.497,11.172
				c0.013-0.012,0.029-0.018,0.048-0.018c0.02,0,0.036,0.006,0.049,0.018c0.013,0.012,0.019,0.027,0.019,0.046
				c0,0.019-0.006,0.034-0.019,0.046c-0.013,0.012-0.029,0.018-0.049,0.018c-0.02,0-0.036-0.006-0.048-0.018
				c-0.013-0.012-0.019-0.027-0.019-0.046C9.478,11.199,9.485,11.184,9.497,11.172z M8.979,10.923c0-0.084,0.017-0.148,0.052-0.192
				c0.035-0.044,0.085-0.065,0.151-0.065c0.066,0,0.116,0.022,0.151,0.065s0.053,0.105,0.053,0.186v0.111
				c0,0.083-0.017,0.147-0.052,0.191c-0.034,0.044-0.085,0.066-0.152,0.066c-0.066,0-0.116-0.022-0.151-0.065
				c-0.035-0.043-0.053-0.105-0.053-0.186V10.923z M8.493,10.923c0-0.084,0.017-0.148,0.052-0.192
				c0.035-0.044,0.085-0.065,0.151-0.065c0.066,0,0.116,0.022,0.151,0.065c0.035,0.043,0.053,0.105,0.053,0.186v0.111
				c0,0.083-0.017,0.147-0.052,0.191c-0.034,0.044-0.085,0.066-0.152,0.066c-0.066,0-0.116-0.022-0.151-0.065
				c-0.035-0.043-0.053-0.105-0.053-0.186V10.923z M7.811,10.641h0.12v0.237c0.032-0.038,0.072-0.057,0.12-0.057
				c0.097,0,0.146,0.056,0.148,0.169v0.287h-0.12v-0.284c0-0.026-0.005-0.045-0.017-0.057c-0.011-0.012-0.029-0.018-0.055-0.018
				c-0.035,0-0.06,0.013-0.076,0.041v0.318h-0.12V10.641z M7.26,10.674h0.491v0.101H7.566v0.502H7.442v-0.502H7.26V10.674z
				 M9.612,13.228H7.597v-0.155H7.434v0.068H7.313v-0.927h0.121v0.069h0.164v-0.155h2.014l0.406,0.49h0.186
				c0.062-0.056,0.185-0.156,0.27-0.156c0.119,0,0.216,0.097,0.216,0.216c0,0.119-0.097,0.216-0.216,0.216
				c-0.085,0-0.208-0.1-0.27-0.156h-0.186L9.612,13.228z M10.716,13.409c-0.119,0-0.216-0.097-0.216-0.216s0.097-0.216,0.216-0.216
				c0.119,0,0.216,0.097,0.216,0.216S10.835,13.409,10.716,13.409z M10.949,11.911v0.221h-0.124v-0.603h0.224
				c0.071,0,0.126,0.016,0.165,0.048c0.039,0.032,0.058,0.077,0.058,0.135c0,0.041-0.009,0.075-0.027,0.103
				c-0.018,0.027-0.045,0.049-0.081,0.066l0.13,0.246v0.006h-0.133l-0.113-0.221H10.949z M11.533,13.184h-0.184v-0.708l-0.219,0.068
				v-0.149l0.383-0.137h0.02V13.184z M11.534,10.982v0.214l-0.112-0.107l-0.112-0.107l0.112-0.107l0.112-0.107V10.982z
				 M12.372,13.126c-0.056,0.048-0.132,0.071-0.228,0.071s-0.172-0.024-0.229-0.072c-0.056-0.048-0.085-0.113-0.085-0.194
				c0-0.051,0.013-0.096,0.04-0.134c0.026-0.038,0.062-0.068,0.106-0.089c-0.039-0.021-0.07-0.048-0.092-0.083
				c-0.022-0.035-0.033-0.075-0.033-0.119c0-0.079,0.026-0.142,0.079-0.188c0.052-0.047,0.124-0.07,0.214-0.07
				c0.09,0,0.161,0.023,0.214,0.07c0.053,0.046,0.079,0.109,0.079,0.189c0,0.045-0.011,0.085-0.034,0.119
				c-0.022,0.035-0.053,0.062-0.093,0.083c0.045,0.022,0.081,0.051,0.107,0.089c0.026,0.038,0.039,0.082,0.039,0.134
				C12.456,13.013,12.428,13.078,12.372,13.126z M13.209,12.362l-0.358,0.822h-0.194l0.358-0.777h-0.46v-0.148h0.653V12.362z
				 M13.42,11.211l-0.129,0.13h-1.372v-0.718h1.372l0.129,0.13V11.211z"/>
			<path fill="#7F8188" d="M11.991,10.695v0.573h1.27l0.087-0.088v-0.398l-0.087-0.088H11.991z M12.276,11.248h-0.261v-0.533h0.261
				V11.248z M12.573,11.248h-0.261v-0.533h0.261V11.248z M12.87,11.248h-0.261v-0.533h0.261V11.248z"/>
			<path fill="#7F8188" d="M10.245,12.678c0.07,0.064,0.175,0.144,0.229,0.144c0.079,0,0.144-0.064,0.144-0.144
				c0-0.079-0.065-0.144-0.144-0.144C10.42,12.534,10.315,12.614,10.245,12.678L10.245,12.678z"/>
			<path fill="#7F8188" d="M9.183,11.189c0.029,0,0.05-0.011,0.063-0.034c0.013-0.023,0.02-0.058,0.02-0.105v-0.143
				c0-0.05-0.007-0.086-0.021-0.109c-0.014-0.023-0.035-0.034-0.064-0.034c-0.028,0-0.049,0.011-0.062,0.033S9.1,10.85,9.099,10.896
				v0.147c0,0.049,0.007,0.086,0.02,0.11C9.132,11.177,9.154,11.189,9.183,11.189z"/>
		</g>
		<g id="Screen_2_" opacity="0.8">
			<g>
				<g>
					<path fill="#FFFFFF" d="M7.751,10.775H7.566v0.502H7.442v-0.502H7.26v-0.101h0.491V10.775z"/>
					<path fill="#FFFFFF" d="M7.931,10.878c0.032-0.038,0.072-0.057,0.12-0.057c0.097,0,0.146,0.056,0.148,0.169v0.287h-0.12v-0.284
						c0-0.026-0.005-0.045-0.017-0.057c-0.011-0.012-0.029-0.018-0.055-0.018c-0.035,0-0.06,0.013-0.076,0.041v0.318h-0.12v-0.636
						h0.12V10.878z"/>
					<path fill="#FFFFFF" d="M8.9,11.028c0,0.083-0.017,0.147-0.052,0.191c-0.034,0.044-0.085,0.066-0.152,0.066
						c-0.066,0-0.116-0.022-0.151-0.065c-0.035-0.043-0.053-0.105-0.053-0.186v-0.111c0-0.084,0.017-0.148,0.052-0.192
						c0.035-0.044,0.085-0.065,0.151-0.065c0.066,0,0.116,0.022,0.151,0.065c0.035,0.043,0.053,0.105,0.053,0.186V11.028z
						 M8.781,10.906c0-0.05-0.007-0.086-0.021-0.109c-0.014-0.023-0.035-0.034-0.064-0.034c-0.028,0-0.049,0.011-0.062,0.033
						c-0.013,0.022-0.021,0.056-0.021,0.102v0.147c0,0.049,0.007,0.086,0.02,0.11c0.013,0.024,0.035,0.036,0.064,0.036
						c0.029,0,0.05-0.011,0.063-0.034c0.013-0.023,0.02-0.058,0.02-0.105V10.906z"/>
					<path fill="#FFFFFF" d="M9.387,11.028c0,0.083-0.017,0.147-0.052,0.191c-0.034,0.044-0.085,0.066-0.152,0.066
						c-0.066,0-0.116-0.022-0.151-0.065c-0.035-0.043-0.053-0.105-0.053-0.186v-0.111c0-0.084,0.017-0.148,0.052-0.192
						c0.035-0.044,0.085-0.065,0.151-0.065c0.066,0,0.116,0.022,0.151,0.065s0.053,0.105,0.053,0.186V11.028z M9.267,10.906
						c0-0.05-0.007-0.086-0.021-0.109c-0.014-0.023-0.035-0.034-0.064-0.034c-0.028,0-0.049,0.011-0.062,0.033
						S9.1,10.85,9.099,10.896v0.147c0,0.049,0.007,0.086,0.02,0.11c0.013,0.024,0.035,0.036,0.064,0.036
						c0.029,0,0.05-0.011,0.063-0.034c0.013-0.023,0.02-0.058,0.02-0.105V10.906z"/>
					<path fill="#FFFFFF" d="M9.478,10.879c0-0.019,0.006-0.034,0.019-0.046c0.013-0.012,0.029-0.018,0.048-0.018
						c0.02,0,0.036,0.006,0.049,0.018c0.013,0.012,0.019,0.027,0.019,0.046c0,0.019-0.006,0.034-0.019,0.046
						c-0.013,0.012-0.029,0.018-0.049,0.018c-0.02,0-0.036-0.006-0.048-0.018C9.485,10.913,9.478,10.898,9.478,10.879z
						 M9.478,11.218c0-0.019,0.006-0.034,0.019-0.046c0.013-0.012,0.029-0.018,0.048-0.018c0.02,0,0.036,0.006,0.049,0.018
						c0.013,0.012,0.019,0.027,0.019,0.046c0,0.019-0.006,0.034-0.019,0.046c-0.013,0.012-0.029,0.018-0.049,0.018
						c-0.02,0-0.036-0.006-0.048-0.018C9.485,11.252,9.478,11.237,9.478,11.218z"/>
					<path fill="#FFFFFF" d="M10.12,11.277H9.707v-0.082l0.195-0.208c0.027-0.029,0.047-0.055,0.059-0.077
						c0.013-0.022,0.019-0.043,0.019-0.062c0-0.027-0.007-0.048-0.02-0.063c-0.014-0.015-0.033-0.023-0.058-0.023
						c-0.027,0-0.048,0.009-0.064,0.028c-0.016,0.019-0.023,0.043-0.023,0.073h-0.12c0-0.037,0.009-0.07,0.026-0.101
						c0.018-0.03,0.042-0.054,0.074-0.071c0.032-0.017,0.068-0.026,0.109-0.026c0.062,0,0.11,0.015,0.145,0.045
						c0.034,0.03,0.052,0.072,0.052,0.126c0,0.03-0.008,0.06-0.023,0.091c-0.016,0.031-0.042,0.067-0.08,0.108L9.861,11.18h0.259
						V11.277z"/>
					<path fill="#FFFFFF" d="M10.606,11.277h-0.413v-0.082l0.195-0.208c0.027-0.029,0.047-0.055,0.059-0.077
						c0.013-0.022,0.019-0.043,0.019-0.062c0-0.027-0.007-0.048-0.02-0.063c-0.014-0.015-0.033-0.023-0.058-0.023
						c-0.027,0-0.048,0.009-0.064,0.028c-0.016,0.019-0.023,0.043-0.023,0.073h-0.12c0-0.037,0.009-0.07,0.026-0.101
						c0.018-0.03,0.042-0.054,0.074-0.071c0.032-0.017,0.068-0.026,0.109-0.026c0.062,0,0.11,0.015,0.145,0.045
						c0.034,0.03,0.052,0.072,0.052,0.126c0,0.03-0.008,0.06-0.023,0.091c-0.016,0.031-0.042,0.067-0.08,0.108l-0.137,0.145h0.259
						V11.277z"/>
				</g>
				<g>
					<path fill="#FFFFFF" d="M10.03,11.973c0-0.024-0.008-0.042-0.025-0.054c-0.017-0.013-0.046-0.026-0.089-0.04
						c-0.043-0.014-0.077-0.028-0.102-0.041c-0.069-0.037-0.103-0.087-0.103-0.149c0-0.033,0.009-0.062,0.028-0.087
						c0.018-0.025,0.045-0.045,0.079-0.06c0.034-0.014,0.073-0.022,0.116-0.022c0.043,0,0.081,0.008,0.115,0.023
						c0.034,0.016,0.06,0.038,0.078,0.066c0.019,0.028,0.028,0.061,0.028,0.097h-0.124c0-0.028-0.009-0.049-0.026-0.064
						c-0.017-0.015-0.042-0.023-0.073-0.023c-0.03,0-0.054,0.006-0.071,0.019c-0.017,0.013-0.025,0.03-0.025,0.051
						c0,0.02,0.01,0.036,0.03,0.049c0.02,0.013,0.049,0.026,0.087,0.037c0.071,0.021,0.122,0.048,0.154,0.079
						c0.032,0.031,0.048,0.071,0.048,0.118c0,0.052-0.02,0.093-0.059,0.123c-0.039,0.03-0.093,0.045-0.159,0.045
						c-0.046,0-0.089-0.008-0.127-0.025c-0.038-0.017-0.067-0.04-0.087-0.07c-0.02-0.03-0.03-0.064-0.03-0.103h0.125
						c0,0.067,0.04,0.1,0.119,0.1c0.03,0,0.053-0.006,0.069-0.018C10.022,12.011,10.03,11.995,10.03,11.973z"/>
					<path fill="#FFFFFF" d="M10.733,11.844c0,0.056-0.009,0.105-0.027,0.147c-0.018,0.042-0.044,0.075-0.076,0.1l0.1,0.079
						l-0.079,0.07l-0.128-0.103c-0.015,0.002-0.03,0.004-0.046,0.004c-0.05,0-0.095-0.012-0.134-0.036s-0.07-0.058-0.091-0.103
						c-0.022-0.045-0.032-0.096-0.033-0.154v-0.03c0-0.059,0.011-0.112,0.032-0.157c0.021-0.045,0.052-0.08,0.091-0.104
						c0.039-0.024,0.084-0.036,0.134-0.036c0.05,0,0.095,0.012,0.134,0.036c0.039,0.024,0.069,0.059,0.091,0.104
						c0.021,0.045,0.032,0.097,0.032,0.156V11.844z M10.607,11.816c0-0.063-0.011-0.111-0.034-0.144
						c-0.023-0.033-0.055-0.049-0.097-0.049c-0.042,0-0.074,0.016-0.097,0.049c-0.023,0.032-0.034,0.08-0.034,0.143v0.029
						c0,0.062,0.011,0.109,0.034,0.143c0.023,0.034,0.055,0.051,0.098,0.051c0.042,0,0.074-0.016,0.096-0.049
						c0.022-0.033,0.034-0.08,0.034-0.143V11.816z"/>
					<path fill="#FFFFFF" d="M11.048,11.911h-0.099v0.221h-0.124v-0.603h0.224c0.071,0,0.126,0.016,0.165,0.048
						c0.039,0.032,0.058,0.077,0.058,0.135c0,0.041-0.009,0.075-0.027,0.103c-0.018,0.027-0.045,0.049-0.081,0.066l0.13,0.246v0.006
						h-0.133L11.048,11.911z M10.949,11.81h0.1c0.031,0,0.055-0.008,0.072-0.024c0.017-0.016,0.026-0.038,0.026-0.066
						c0-0.028-0.008-0.051-0.024-0.067c-0.016-0.016-0.041-0.024-0.074-0.024h-0.1V11.81z"/>
				</g>
				<g id="_x31_87_2_">
					<path fill="#FFFFFF" d="M11.533,13.184h-0.184v-0.708l-0.219,0.068v-0.149l0.383-0.137h0.02V13.184z"/>
					<path fill="#FFFFFF" d="M12.436,12.505c0,0.045-0.011,0.085-0.034,0.119c-0.022,0.035-0.053,0.062-0.093,0.083
						c0.045,0.022,0.081,0.051,0.107,0.089c0.026,0.038,0.039,0.082,0.039,0.134c0,0.082-0.028,0.147-0.084,0.195
						c-0.056,0.048-0.132,0.071-0.228,0.071s-0.172-0.024-0.229-0.072c-0.056-0.048-0.085-0.113-0.085-0.194
						c0-0.051,0.013-0.096,0.04-0.134c0.026-0.038,0.062-0.068,0.106-0.089c-0.039-0.021-0.07-0.048-0.092-0.083
						c-0.022-0.035-0.033-0.075-0.033-0.119c0-0.079,0.026-0.142,0.079-0.188c0.052-0.047,0.124-0.07,0.214-0.07
						c0.09,0,0.161,0.023,0.214,0.07C12.41,12.363,12.436,12.426,12.436,12.505z M12.272,12.918c0-0.04-0.012-0.073-0.035-0.097
						c-0.023-0.024-0.055-0.036-0.094-0.036c-0.039,0-0.07,0.012-0.093,0.036c-0.023,0.024-0.035,0.056-0.035,0.097
						c0,0.039,0.011,0.071,0.034,0.095c0.023,0.024,0.055,0.036,0.095,0.036c0.04,0,0.071-0.012,0.094-0.035
						C12.26,12.991,12.272,12.959,12.272,12.918z M12.252,12.514c0-0.036-0.009-0.065-0.029-0.087
						c-0.019-0.022-0.046-0.033-0.081-0.033c-0.034,0-0.061,0.01-0.08,0.032c-0.019,0.021-0.029,0.05-0.029,0.088
						c0,0.037,0.01,0.066,0.029,0.089c0.019,0.022,0.046,0.034,0.081,0.034s0.061-0.011,0.08-0.034
						C12.243,12.581,12.252,12.551,12.252,12.514z"/>
					<path fill="#FFFFFF" d="M13.209,12.362l-0.358,0.822h-0.194l0.358-0.777h-0.46v-0.148h0.653V12.362z"/>
				</g>
			</g>
			<g>
				<polygon fill="#FFFFFF" points="10.896,10.982 11.072,10.807 11.247,10.631 11.247,10.982 11.247,11.332 11.072,11.157 				"/>
				<polygon fill="#FFFFFF" points="11.31,10.982 11.422,10.875 11.534,10.768 11.534,10.982 11.534,11.196 11.422,11.089 				"/>
			</g>
			<g id="Battery_1_">
				<rect x="12.016" y="10.716" fill="#FFFFFF" width="0.261" height="0.533"/>
				<rect x="12.312" y="10.716" fill="#FFFFFF" width="0.261" height="0.533"/>
				<rect x="12.609" y="10.716" fill="#FFFFFF" width="0.261" height="0.533"/>
				<path fill="#FFFFFF" d="M13.291,11.341h-1.372v-0.718h1.372l0.129,0.13v0.457L13.291,11.341z M11.991,11.268h1.27l0.087-0.088
					v-0.398l-0.087-0.088h-1.27V11.268z"/>
			</g>
			<g id="Seringe_1_">
				<polygon fill="#FFFFFF" points="9.544,13.068 8.321,13.068 8.321,12.288 9.544,12.288 9.87,12.678 				"/>
				<path fill="#FFFFFF" d="M10.716,13.409c-0.119,0-0.216-0.097-0.216-0.216s0.097-0.216,0.216-0.216
					c0.119,0,0.216,0.097,0.216,0.216S10.835,13.409,10.716,13.409z M10.716,13.049c-0.079,0-0.144,0.064-0.144,0.144
					s0.065,0.144,0.144,0.144c0.079,0,0.144-0.064,0.144-0.144S10.796,13.049,10.716,13.049z"/>
				<path fill="#FFFFFF" d="M10.474,12.894c-0.111,0-0.287-0.171-0.307-0.191l-0.026-0.026l0.026-0.026
					c0.02-0.019,0.196-0.19,0.307-0.19c0.119,0,0.216,0.097,0.216,0.216C10.69,12.797,10.593,12.894,10.474,12.894z M10.245,12.678
					c0.07,0.064,0.175,0.144,0.229,0.144c0.079,0,0.144-0.064,0.144-0.144c0-0.079-0.065-0.144-0.144-0.144
					C10.42,12.534,10.315,12.614,10.245,12.678z"/>
				<rect x="9.989" y="12.618" fill="#FFFFFF" width="0.256" height="0.121"/>
				<rect x="7.313" y="12.214" fill="#FFFFFF" width="0.121" height="0.927"/>
				<g>
					<rect x="7.374" y="12.952" fill="#FFFFFF" width="0.286" height="0.121"/>
					<rect x="7.374" y="12.283" fill="#FFFFFF" width="0.286" height="0.121"/>
				</g>
				<rect x="7.922" y="12.188" fill="#FFFFFF" width="0.121" height="0.149"/>
				<path fill="#FFFFFF" d="M9.612,13.228H7.597v-1.1h2.014l0.456,0.55L9.612,13.228z M7.718,13.108h1.837l0.356-0.43l-0.356-0.43
					H7.718V13.108z"/>
			</g>
		</g>
	</g>
</g>
</svg>
 */