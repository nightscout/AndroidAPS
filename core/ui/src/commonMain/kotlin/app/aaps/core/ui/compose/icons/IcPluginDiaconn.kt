package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Diaconn Pump Plugin.
 *
 * Bounding box: (viewport: 24x24, ~90% width)
 *
 * @see IcPluginDiaconnPreview
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
