package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcAaruy: ImageVector
    get() {
        if (_AaruyPumpBind != null) {
            return _AaruyPumpBind!!
        }
        _AaruyPumpBind = ImageVector.Builder(
            name = "AaruyPumpBind",
            defaultWidth = 319.71.dp,
            defaultHeight = 412.72.dp,
            viewportWidth = 319.71f,
            viewportHeight = 412.72f
        ).apply {
            addAaruyPaths1()
            addAaruyPaths2()
            addAaruyPaths3()
            addAaruyPaths4()
            addAaruyPaths5()
            addAaruyPaths6()
            addAaruyPaths7()
            addAaruyPaths8()
            addAaruyPaths9()
            addAaruyPaths10()
            addAaruyPaths11()
            addAaruyPaths12()
            addAaruyPaths13()
            addAaruyPaths14()
        }.build()

        return _AaruyPumpBind!!
    }

private fun ImageVector.Builder.addAaruyPaths1() {
    path(
        fill = SolidColor(Color.White),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.64f, 169.27f)
        curveToRelative(3.51f, 0.34f, 6.6f, 1.35f, 9.17f, 4f)
        arcToRelative(21f, 21f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.7f, 9f)
        curveToRelative(1.74f, 6.1f, 2.29f, 14f, 2.54f, 20.25f)
        reflectiveCurveToRelative(0.29f, 14.11f, 0.3f, 20.46f)
        quadToRelative(0f, 10.23f, 0f, 20.46f)
        horizontalLineTo(269.9f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.42f, 1.42f)
        verticalLineToRelative(0.56f)
        horizontalLineToRelative(27f)
        arcToRelative(21.25f, 21.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.26f, 21.26f)
        verticalLineToRelative(39.13f)
        arcToRelative(45.34f, 45.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 43.91f)
        verticalLineToRelative(34.6f)
        arcToRelative(28.34f, 28.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, -28.34f, 28.34f)
        horizontalLineTo(206.66f)
        arcToRelative(28.34f, 28.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, -28.35f, -28.34f)
        verticalLineToRelative(-34.6f)
        arcToRelative(45.34f, 45.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -43.91f)
        verticalLineTo(186.59f)
        arcToRelative(21.25f, 21.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.26f, -21.26f)
        horizontalLineToRelative(19.89f)
        arcToRelative(21.21f, 21.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.13f, 1.62f)
        horizontalLineToRelative(6.56f)
        arcToRelative(6.74f, 6.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.8f, 1f)
        curveToRelative(0.56f, 0.4f, 1.16f, 0.86f, 1.69f, 1.28f)
        close()
    }
    path(
        fill = SolidColor(Color(0xFFFFFEF5)),
        stroke = SolidColor(Color(0xFF030000)),
        strokeLineWidth = 0.2f,
        strokeLineJoin = StrokeJoin.Bevel
    ) {
        moveTo(184.55f, 253.08f)
        verticalLineToRelative(-1.49f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, -0.38f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, -0.12f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0f)
        horizontalLineTo(187f)
        arcToRelative(3.28f, 3.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, -0.61f)
        quadToRelative(0.48f, -1f, 1f, -2f)
        lineToRelative(1.85f, -3.9f)
        horizontalLineToRelative(0f)
        lineToRelative(0.7f, -1.48f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, -0.25f)
        verticalLineTo(182f)
        curveToRelative(0f, -1.94f, 0f, -3.87f, 0f, -5.81f)
        arcToRelative(4.83f, 4.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.83f, -4.83f)
        horizontalLineToRelative(7.23f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.88f, -0.14f)
        lineToRelative(1.14f, -0.37f)
        lineToRelative(0.14f, 0f)
        lineToRelative(0.38f, -0.12f)
        lineToRelative(0.25f, -0.08f)
        lineToRelative(0.26f, -0.07f)
        curveToRelative(0.34f, -0.11f, 0.66f, -0.2f, 1f, -0.27f)
        lineToRelative(0.64f, -0.16f)
        arcToRelative(4.54f, 4.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.31f, -3.12f)
        horizontalLineToRelative(22.39f)
        arcToRelative(6.74f, 6.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.8f, 1f)
        curveToRelative(0.56f, 0.4f, 1.16f, 0.86f, 1.69f, 1.28f)
        lineToRelative(0f, 4.28f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.91f, 0.63f)
        arcToRelative(22.94f, 22.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 1.25f)
        verticalLineToRelative(4f)
        arcToRelative(7.84f, 7.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.23f, 1.07f)
        arcToRelative(9.33f, 9.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 1.4f)
        verticalLineToRelative(63.52f)
        lineToRelative(3.94f, 3.65f)
        lineToRelative(2.07f, 1.91f)
        horizontalLineToRelative(0.45f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.12f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, 0.38f)
        verticalLineToRelative(0.4f)
        lineToRelative(0.64f, 0.6f)
        horizontalLineToRelative(1.16f)
        lineToRelative(0.07f, 0f)
        horizontalLineToRelative(1.81f)
        lineToRelative(0.07f, 0f)
        horizontalLineToRelative(1.57f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.56f)
        verticalLineToRelative(-3f)
        lineToRelative(0f, -0.08f)
        verticalLineToRelative(-4.21f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.42f, -1.42f)
        horizontalLineTo(269.9f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.42f, 1.42f)
        verticalLineTo(249f)
        lineToRelative(0f, 0.08f)
        verticalLineToRelative(2.45f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 0.57f)
        horizontalLineToRelative(2.3f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(0.56f, 0f)
        horizontalLineTo(303.6f)
        arcToRelative(4.93f, 4.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.55f, 0f)
        arcToRelative(4.68f, 4.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.54f, 0.08f)
        arcToRelative(5.23f, 5.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.32f)
        lineToRelative(0.5f, 0.23f)
        lineToRelative(0.46f, 0.28f)
        arcToRelative(6.3f, 6.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.84f, 0.69f)
        curveToRelative(0.13f, 0.13f, 0.25f, 0.27f, 0.37f, 0.41f)
        reflectiveCurveToRelative(0.22f, 0.29f, 0.32f, 0.44f)
        arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.51f, 1f)
        arcToRelative(5.34f, 5.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.32f, 1f)
        arcToRelative(5.65f, 5.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 1.08f)
        verticalLineToRelative(86f)
        curveToRelative(0f, 0.32f, 0f, 0.63f, 0f, 0.93f)
        reflectiveCurveToRelative(0f, 0.59f, 0f, 0.85f)
        curveToRelative(0f, 0.46f, 0f, 0.94f, 0f, 1.46f)
        reflectiveCurveToRelative(0f, 1f, 0f, 1.58f)
        curveToRelative(0f, 0.84f, 0f, 1.69f, 0f, 2.54f)
        verticalLineToRelative(11.34f)
        curveToRelative(0f, 0.85f, 0f, 1.7f, 0f, 2.53f)
        curveToRelative(0f, 0.55f, 0f, 1.07f, 0f, 1.59f)
        reflectiveCurveToRelative(0f, 1f, 0f, 1.44f)
        verticalLineToRelative(0f)
        curveToRelative(0f, 0.26f, 0f, 0.54f, 0f, 0.84f)
        reflectiveCurveToRelative(0f, 0.62f, 0f, 0.93f)
        verticalLineToRelative(23.82f)
        arcToRelative(8.79f, 8.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.18f, 1.77f)
        arcToRelative(9.23f, 9.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.51f, 1.7f)
        arcToRelative(9.48f, 9.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.84f, 1.57f)
        arcToRelative(9.29f, 9.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.5f, 2.5f)
        arcToRelative(9.48f, 9.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.57f, 0.84f)
        arcToRelative(9.23f, 9.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.7f, 0.51f)
        arcToRelative(8.86f, 8.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.77f, 0.18f)
        horizontalLineToRelative(-2.65f)
        curveToRelative(-3.36f, 0f, -6.71f, 0f, -10f, 0f)
        lineToRelative(-14.13f, 0f)
        horizontalLineToRelative(-3.73f)
        lineToRelative(-1.53f, 0f)
        lineToRelative(-0.84f, 0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        horizontalLineTo(264.9f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.57f)
        verticalLineToRelative(1f)
        lineToRelative(0f, 0.08f)
        verticalLineToRelative(4.21f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.42f, 1.42f)
        horizontalLineTo(242.29f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.42f, -1.42f)
        verticalLineToRelative(-4.21f)
        lineToRelative(0f, -0.08f)
        verticalLineToRelative(-1f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.57f)
        horizontalLineTo(238f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(-0.06f)
        lineToRelative(-0.95f, 0f)
        lineToRelative(-1.54f, 0f)
        lineToRelative(-1.76f, 0f)
        horizontalLineTo(231.9f)
        lineToRelative(-14.83f, 0f)
        curveToRelative(-3.31f, 0f, -6.66f, 0f, -10f, 0f)
        horizontalLineTo(204.4f)
        arcToRelative(8.69f, 8.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.76f, -0.18f)
        arcToRelative(9.11f, 9.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.71f, -0.51f)
        arcToRelative(9.38f, 9.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.56f, -0.84f)
        arcToRelative(8.84f, 8.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.38f, -1.13f)
        lineToRelative(-0.08f, -0.08f)
        arcToRelative(5.68f, 5.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, -4.86f, -5.61f)
        verticalLineTo(289.7f)
        lineToRelative(-2.48f, -4.82f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.29f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.68f, -0.75f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.07f, -0.06f)
        arcToRelative(4.47f, 4.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.45f, -0.42f)
        arcToRelative(6.1f, 6.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.41f, -0.46f)
        arcToRelative(6.65f, 6.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.66f, -1f)
        arcToRelative(5.12f, 5.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.25f, -0.53f)
        curveToRelative(-0.08f, -0.18f, -0.15f, -0.36f, -0.22f, -0.54f)
        arcToRelative(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.3f, -1.07f)
        curveToRelative(-0.08f, -0.35f, -0.14f, -0.7f, -0.19f, -1.06f)
        reflectiveCurveToRelative(-0.09f, -0.69f, -0.11f, -1f)
        reflectiveCurveToRelative(-0.05f, -0.69f, -0.05f, -1f)
        arcToRelative(2.26f, 2.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.26f)
        verticalLineToRelative(-1.75f)
        curveToRelative(0f, -0.65f, 0f, -1.31f, 0f, -2f)
        curveToRelative(0f, -1.31f, 0f, -2.63f, 0f, -3.94f)
        curveToRelative(0f, -2.63f, 0f, -5.25f, 0f, -7.88f)
        verticalLineToRelative(-0.68f)
        horizontalLineToRelative(-0.21f)
        arcToRelative(2.84f, 2.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.84f, -2.84f)
        verticalLineToRelative(-1.56f)
        arcToRelative(0.43f, 0.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.24f, -0.38f)
        verticalLineTo(253.6f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.15f)
        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.05f, -0.13f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.1f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.08f)
        close()
    }
    path(
        fill = SolidColor(Color(0xFFF5FBFE)),
        stroke = SolidColor(Color(0xFF231815)),
        strokeLineWidth = 0.2f
    ) {
        moveTo(234.72f, 175.48f)
        verticalLineToRelative(75.57f)
        horizontalLineToRelative(6.8f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.12f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, 0.38f)
        verticalLineToRelative(1.69f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.08f)
        curveToRelative(0f, 0.06f, 0f, 0.14f, 0f, 0.26f)
        reflectiveCurveToRelative(0f, 0.3f, 0f, 0.49f)
        lineToRelative(-0.08f, 0f)
        lineToRelative(-0.22f, -0.11f)
        arcToRelative(1.05f, 1.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.24f, -0.06f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.26f, 0f)
        horizontalLineToRelative(-1.92f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.56f)
        curveToRelative(0f, 0.25f, 0f, 0.49f, 0f, 0.74f)
        horizontalLineTo(238f)
        lineToRelative(-0.73f, 0f)
        lineToRelative(-49.48f, 0f)
        lineToRelative(-2.57f, 0f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.57f, -0.54f)
        curveToRelative(0f, -0.43f, -0.05f, -0.87f, -0.1f, -1.3f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-1.7f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, -0.38f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, -0.12f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0f)
        horizontalLineToRelative(6f)
        verticalLineTo(181.19f)
        arcToRelative(2.83f, 2.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.75f, -2.62f)
        lineToRelative(13.88f, -5.75f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.52f, -1.06f)
        verticalLineToRelative(-0.28f)
        arcToRelative(4.54f, 4.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.54f, -4.53f)
        horizontalLineToRelative(22.39f)
        arcToRelative(6.74f, 6.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.8f, 1f)
        curveToRelative(0.56f, 0.4f, 1.16f, 0.86f, 1.69f, 1.28f)
        lineToRelative(0f, 4.28f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.91f, 0.63f)
        arcTo(22.94f, 22.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 234.72f, 175.48f)
        close()
    }
    path(
        fill = SolidColor(Color(0xFFF5FBFE)),
        stroke = SolidColor(Color(0xFF231815)),
        strokeLineWidth = 0.2f
    ) {
        moveTo(237.67f, 173.55f)
        arcToRelative(10.71f, 10.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.49f, 1.39f)
        arcToRelative(9.5f, 9.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.17f, 3.35f)
        arcToRelative(28.4f, 28.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 8.87f)
        arcToRelative(127.43f, 127.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.61f, 18.69f)
        curveToRelative(0.17f, 5.92f, 0.18f, 12.87f, 0.19f, 18.79f)
        verticalLineToRelative(83.22f)
        arcToRelative(27.71f, 27.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 3.28f)
        arcToRelative(0.79f, 0.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, 0.37f)
        verticalLineToRelative(0.09f)
        lineToRelative(0f, 3.78f)
        arcToRelative(13.07f, 13.07f, 0f, isMoreThanHalf = true, isPositiveArc = false, 8.56f, 0f)
        lineToRelative(0f, -3.78f)
        verticalLineToRelative(-0.09f)
        arcToRelative(0.79f, 0.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, -0.37f)
        arcToRelative(27.71f, 27.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, -3.28f)
        verticalLineTo(243.43f)
        quadToRelative(0f, -10.23f, 0f, -20.47f)
        curveToRelative(0f, -6.36f, 0f, -14.11f, -0.3f, -20.46f)
        reflectiveCurveToRelative(-0.8f, -14.16f, -2.54f, -20.26f)
        arcToRelative(20.83f, 20.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4.69f, -9f)
        curveToRelative(-2.57f, -2.64f, -5.66f, -3.65f, -9.18f, -4f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.38f, 363.53f)
        arcToRelative(2.13f, 2.13f, 0f, isMoreThanHalf = true, isPositiveArc = false, 2.15f, 2.09f)
        arcTo(2.13f, 2.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, 270.38f, 363.53f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.9f, 329.15f)
        arcToRelative(1.39f, 1.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.4f, -1.29f)
        verticalLineToRelative(-0.32f)
        curveToRelative(0f, -0.65f, 0.88f, -1.43f, 1.47f, -1.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(257.43f, 326.38f)
        curveToRelative(0.59f, -0.31f, 1.52f, 0.5f, 1.52f, 1.16f)
        arcToRelative(1.33f, 1.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.15f, 0.84f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.25f, 0.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.98f, 311.6f)
        lineTo(256.47f, 311.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(194.29f, 177.83f)
        arcToRelative(5.27f, 5.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, 0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.92f, 250.44f)
        lineTo(189.62f, 250.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(203.42f, 172.46f)
        lineToRelative(1.74f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266f, 249.72f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.61f, 2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.92f, 244.85f)
        lineTo(268.46f, 244.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 244.85f)
        verticalLineToRelative(-1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 243.43f)
        lineTo(254.35f, 243.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 246.15f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 245.59f)
        lineTo(263.7f, 245.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.7f, 245.59f)
        lineTo(254.35f, 245.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.56f, 249.74f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.23f, 249.17f)
        curveToRelative(-0.11f, 0f, -0.16f, 0.17f, -0.18f, 0.24f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, 0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.39f, 251.87f)
        lineToRelative(0.11f, 0.26f)
        lineToRelative(0.14f, 0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.39f, 251.87f)
        arcToRelative(18.87f, 18.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5.72f, 0.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.67f, 252.75f)
        arcToRelative(18.8f, 18.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5.32f, -0.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.38f, 253f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, -0.21f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.32f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.38f, 253f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.64f, 252.38f)
        lineToRelative(0.2f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.84f, 252.2f)
        lineToRelative(-0.23f, -0.17f)
        lineToRelative(-0.22f, -0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266f, 249.72f)
        lineToRelative(0.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 249.74f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 249.18f)
        horizontalLineToRelative(-1.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.56f, 249.74f)
        horizontalLineToRelative(1.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 253f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 252.35f)
        lineTo(266.38f, 252.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.38f, 252.95f)
        lineTo(268.45f, 252.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 246.21f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 245.65f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(-2.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.88f, 243.42f)
        horizontalLineToRelative(-1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 245.65f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 246.15f)
        horizontalLineToRelative(2f)
        lineToRelative(0.21f, 0f)
        horizontalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.6f, 246.21f)
        reflectiveCurveToRelative(0f, 0f, 0.05f, 0f)
        horizontalLineToRelative(1.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 246.23f)
        lineTo(254.35f, 246.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.23f, 249.17f)
        quadToRelative(-3.15f, 0f, -6.56f, 0f)
        lineToRelative(-5.32f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.56f, 249.18f)
        horizontalLineToRelative(-0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.72f, 249.12f)
        horizontalLineToRelative(-0.56f)
        lineToRelative(-0.57f, 0f)
        horizontalLineToRelative(-1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.6f, 249.06f)
        lineTo(250.1f, 249.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.61f, 249.12f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 249.68f)
        horizontalLineToRelative(0.07f)
        lineToRelative(0.19f, 0f)
        horizontalLineToRelative(1.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.59f, 252.76f)
        arcToRelative(1.21f, 1.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, 0.19f)
        lineToRelative(0.22f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.84f, 252.2f)
        arcToRelative(6.6f, 6.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.72f, -2.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.38f, 252.35f)
        arcToRelative(1.27f, 1.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, -0.08f)
    }
}

private fun ImageVector.Builder.addAaruyPaths2() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 249.74f)
        lineTo(268.45f, 252.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.29f, 249.68f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.55f, 252.5f)
        arcToRelative(4.06f, 4.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.1f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 249.06f)
        lineTo(270.72f, 249.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.29f, 249.68f)
        lineTo(271.3f, 249.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 245.66f)
        lineTo(271.3f, 245.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 246.21f)
        lineTo(270.73f, 246.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 244.85f)
        horizontalLineToRelative(2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.9f, 243.4f)
        lineTo(269.88f, 243.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 244.84f)
        lineTo(271.32f, 244.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.01f, 244.82f)
        lineTo(248.03f, 244.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.45f, 243.42f)
        lineTo(249.43f, 243.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 244.84f)
        horizontalLineToRelative(2.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.6f, 246.23f)
        lineTo(248.6f, 246.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.03f, 245.65f)
        lineTo(248.03f, 245.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 245.66f)
        lineTo(271.3f, 249.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 246.23f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 245.66f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.03f, 249.63f)
        lineTo(248.03f, 245.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.6f, 249.06f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 249.63f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.03f, 249.63f)
        lineTo(248.04f, 249.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.61f, 249.12f)
        lineTo(248.6f, 249.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 251.9f)
        horizontalLineToRelative(1.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.04f, 249.68f)
        lineTo(248.04f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.86f, 251.9f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, 0.29f)
        arcToRelative(0.43f, 0.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.16f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.12f, 253.06f)
        lineToRelative(0.18f, 0f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.17f, -0.06f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, -0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.49f, 251.9f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.09f, 0.13f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.05f, 0.16f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.49f, 251.9f)
        horizontalLineToRelative(1.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.29f, 251.9f)
        lineTo(271.29f, 249.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255f)
        arcToRelative(0.47f, 0.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.17f, 0f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.17f, -0.1f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.2f, -0.3f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.65f, 254.13f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.22f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.1f, 0.19f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.28f, 0.26f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, 0.15f)
        arcToRelative(1.49f, 1.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.36f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.74f, 254.38f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244f, 254.36f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244f, 254.37f)
        horizontalLineToRelative(0.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 254.65f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -0.13f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.35f, 251.9f)
        lineTo(271.29f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.35f, 251.56f)
        lineTo(271.35f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.04f, 251.9f)
        lineTo(247.98f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.74f, 252.64f)
        lineTo(244.74f, 254.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.74f, 254.38f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.77f, 254.38f)
        lineTo(245.77f, 252.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.96f, 254.36f)
        lineTo(243.96f, 252.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.89f, 252.6f)
        lineTo(243.89f, 254.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.85f, 254.42f)
        curveToRelative(0f, -0.37f, 0f, -0.75f, 0f, -1.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.84f, 253.3f)
        lineTo(245.84f, 252.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.41f, 252.46f)
        arcToRelative(0.81f, 0.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.86f, 251.9f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.32f, 0.25f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, 0.15f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.89f, 252.75f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.94f, 252.46f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, -0.31f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.32f, -0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270f, 252.72f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.1f)
        reflectiveCurveToRelative(0f, -0.09f, 0f, -0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270f, 252.75f)
        horizontalLineToRelative(0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.5f, 252.75f)
        arcToRelative(0.69f, 0.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.65f, 254.13f)
        verticalLineToRelative(-0.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.65f, 253.3f)
        lineTo(270.73f, 253.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.73f, 253.3f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255f)
        lineTo(274.78f, 254.97f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 253.3f)
        horizontalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.4f, 253f)
        horizontalLineToRelative(0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 253f)
        arcToRelative(4.89f, 4.89f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.54f, 0f)
        horizontalLineToRelative(0.12f)
        lineToRelative(0.11f, 0f)
        lineToRelative(0.22f, 0f)
        horizontalLineToRelative(0.09f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0.14f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.78f, 252.72f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.4f, 252.95f)
        lineTo(242.09f, 252.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 254.65f)
        verticalLineToRelative(0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.2f, 255f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 254.3f)
        lineTo(242.21f, 254.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.32f, 254.87f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 254.3f)
        horizontalLineToRelative(1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.89f, 254.31f)
        lineTo(243.96f, 254.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.32f, 254.87f)
        lineTo(243.42f, 254.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.42f, 254.94f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.19f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.42f, 254.94f)
        lineTo(244.63f, 254.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.87f, 251f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.87f, 251.01f)
        lineTo(236.17f, 251.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.17f, 251f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.17f, 250.44f)
        lineTo(234.87f, 250.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.7f, 252.72f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.65f, -0.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.77f, 254.38f)
        lineTo(245.85f, 254.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.2f, 254.95f)
        lineTo(245.29f, 254.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.82f, 254.62f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.89f, 253.3f)
        lineTo(248.91f, 252.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.7f, 252.72f)
        lineTo(270.73f, 253.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255.19f)
        verticalLineTo(255f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 254.93f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.17f, 251f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.26f, 0f)
    }
}

private fun ImageVector.Builder.addAaruyPaths3() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.87f, 250.44f)
        horizontalLineToRelative(-0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.29f, 254.99f)
        lineTo(242.59f, 254.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 254.94f)
        horizontalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 254.93f)
        lineTo(304.41f, 254.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 254.97f)
        lineTo(274.78f, 254.96f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.75f, 176.16f)
        arcToRelative(4.81f, 4.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.81f, -4.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(205.16f, 171.9f)
        lineToRelative(0.64f, -0.28f)
        lineToRelative(0.64f, -0.26f)
        lineToRelative(0.81f, -0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.29f, 255f)
        verticalLineToRelative(0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.82f, 254.62f)
        lineToRelative(-0.07f, 0.17f)
        arcToRelative(0.24f, 0.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, 0.12f)
        reflectiveCurveToRelative(0f, 0.05f, 0.06f, 0.07f)
        lineToRelative(0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.29f, 255f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.53f, -0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(304.41f, 254.36f)
        lineTo(277.05f, 254.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 254.36f)
        lineTo(277.05f, 253.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(304.41f, 254.93f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(304.41f, 254.93f)
        arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.87f, 0.17f)
        arcToRelative(2.32f, 2.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.74f, 0.47f)
        arcToRelative(2.22f, 2.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0.33f)
        arcToRelative(2.08f, 2.08f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0.38f)
        arcToRelative(2.3f, 2.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.14f, 0.42f)
        arcToRelative(1.92f, 1.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 257.6f)
        arcToRelative(4.41f, 4.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.33f, -1.68f)
        arcToRelative(4.28f, 4.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.41f, -0.76f)
        curveToRelative(-0.08f, -0.12f, -0.17f, -0.24f, -0.26f, -0.35f)
        lineToRelative(-0.29f, -0.33f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.67f, -0.54f)
        arcToRelative(4.09f, 4.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.59f, -0.67f)
        arcToRelative(4.14f, 4.14f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.87f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(303.6f, 253.19f)
        lineTo(277.05f, 253.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 257.14f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, -1.07f)
        arcToRelative(2.82f, 2.82f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.27f, -0.48f)
        arcToRelative(2.36f, 2.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.36f, -0.42f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.91f, -0.6f)
        arcToRelative(2.46f, 2.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.53f, -0.15f)
        arcToRelative(2.57f, 2.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.55f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 343.62f)
        lineTo(307.25f, 257.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 343.62f)
        lineTo(308.02f, 343.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308.02f, 343.62f)
        lineTo(308.02f, 257.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.92f, 251.01f)
        lineTo(189.62f, 251.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.86f, 176.16f)
        arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.7f, -3.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(194.29f, 177.83f)
        lineToRelative(0.05f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.92f, 250.44f)
        verticalLineTo(251f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189.62f, 250.44f)
        verticalLineTo(251f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(303.6f, 252.05f)
        arcToRelative(5.68f, 5.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.09f, 0.11f)
        arcToRelative(6.25f, 6.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.32f)
        arcToRelative(5.84f, 5.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.95f, 0.52f)
        arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.84f, 0.69f)
        curveToRelative(0.13f, 0.13f, 0.25f, 0.26f, 0.37f, 0.4f)
        reflectiveCurveToRelative(0.22f, 0.29f, 0.32f, 0.44f)
        arcToRelative(5.84f, 5.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.52f, 0.95f)
        arcToRelative(6.56f, 6.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.32f, 1f)
        curveToRelative(0f, 0.18f, 0.06f, 0.36f, 0.08f, 0.54f)
        arcToRelative(4.89f, 4.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.75f, 399.99f)
        lineTo(264.11f, 399.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.11f, 400f)
        arcToRelative(1.08f, 1.08f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, 0.3f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, 0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.33f, 400.63f)
        lineTo(264.33f, 403.04f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.83f, 400.63f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, -0.34f)
        arcToRelative(0.87f, 0.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, -0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.05f, 399.99f)
        lineTo(238.39f, 399.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.39f, 400f)
        lineToRelative(0f, 0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.39f, 400.83f)
        verticalLineToRelative(-0.39f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.49f, 400.22f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.28f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.41f, 400.84f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.61f, 400.81f)
        curveToRelative(0f, -0.09f, 0f, -0.19f, 0f, -0.29f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.15f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.07f)
        reflectiveCurveToRelative(0f, -0.05f, 0f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.99f, 400.75f)
        lineTo(238f, 400.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.75f)
        reflectiveCurveToRelative(0f, -0.07f, 0f, -0.11f)
        arcToRelative(0.42f, 0.42f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.1f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, -0.09f)
        lineToRelative(-0.07f, -0.08f)
        lineToRelative(-0.08f, -0.06f)
        lineToRelative(-0.1f, -0.05f)
        arcToRelative(0.48f, 0.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.2f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.79f, 400.84f)
        lineTo(231.79f, 401.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.49f, 400.8f)
        horizontalLineToRelative(9.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.06f, 401.41f)
        lineTo(207.06f, 400.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.88f, 401.43f)
        lineToRelative(-9.82f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.88f, 400.81f)
        lineTo(216.88f, 401.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.49f, 400.24f)
        horizontalLineToRelative(-0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.06f, 400.23f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.06f, 400.8f)
        horizontalLineToRelative(0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.06f, 400.23f)
        arcToRelative(19.15f, 19.15f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.92f, -0.23f)
        lineToRelative(-0.67f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(204.4f, 401.41f)
        lineTo(207.06f, 401.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193f, 284.85f)
        lineToRelative(0.1f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.58f, 284.85f)
        lineTo(192.95f, 284.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(297.44f, 401.41f)
        lineTo(300.09f, 401.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308.02f, 393.48f)
        lineTo(308.02f, 369.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308.02f, 369.66f)
        lineTo(307.25f, 369.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 369.66f)
        lineTo(307.25f, 394.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 394.59f)
        arcToRelative(6.24f, 6.24f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, 1.3f)
        arcToRelative(6.37f, 6.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, 1.23f)
        arcToRelative(6.45f, 6.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.31f, 0.58f)
        arcToRelative(6.3f, 6.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.37f, 0.53f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, 0.5f)
        curveToRelative(-0.15f, 0.16f, -0.31f, 0.31f, -0.47f, 0.45f)
        arcToRelative(6.58f, 6.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.07f, 0.74f)
        arcToRelative(6.65f, 6.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.2f, 0.51f)
        arcToRelative(8.06f, 8.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.54f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(300.31f, 400.8f)
        lineTo(297.44f, 400.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.04f, 346.01f)
        lineTo(308.01f, 346.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 346f)
        curveToRelative(0f, -0.8f, 0f, -1.59f, 0f, -2.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.04f, 367.26f)
        lineTo(308.01f, 367.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 369.66f)
        curveToRelative(0f, -0.8f, 0f, -1.6f, 0f, -2.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 394.59f)
        horizontalLineToRelative(-0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(300.31f, 400.24f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(300.31f, 400.24f)
        lineTo(297.44f, 400.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.61f, 400.81f)
        horizontalLineToRelative(9.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.41f, 400.84f)
        lineToRelative(14.2f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.61f, 401.43f)
        lineToRelative(-14.2f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.61f, 401.43f)
        lineTo(287.61f, 400.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.88f, 401.43f)
        lineToRelative(14.91f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.79f, 400.84f)
        lineToRelative(-14.91f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.78f)
        curveToRelative(-0.53f, 0.06f, -5.69f, 0.06f, -6.21f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(297.44f, 401.41f)
        lineToRelative(-9.83f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(300.09f, 401.41f)
        arcToRelative(7.93f, 7.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.93f, -7.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 393.48f)
        horizontalLineToRelative(1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 367.26f)
        curveToRelative(0f, -0.4f, 0f, -0.83f, 0f, -1.28f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(214f, 400.22f)
        verticalLineToRelative(-0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 369.66f)
        horizontalLineToRelative(-0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307.25f, 343.62f)
        curveToRelative(0f, 0.43f, 0f, 0.85f, -0.05f, 1.25f)
        curveToRelative(0f, 0.21f, 0f, 0.41f, -0.07f, 0.6f)
        reflectiveCurveToRelative(-0.06f, 0.37f, -0.09f, 0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307f, 346f)
        curveToRelative(-0.06f, 0.2f, -0.11f, 0.41f, -0.17f, 0.63f)
        verticalLineToRelative(0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307f, 346f)
        lineToRelative(-0.18f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(307f, 367.26f)
        curveToRelative(0f, 0.18f, 0.07f, 0.36f, 0.09f, 0.55f)
        reflectiveCurveToRelative(0.05f, 0.39f, 0.07f, 0.59f)
        curveToRelative(0f, 0.41f, 0.05f, 0.83f, 0.05f, 1.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.39f, 400.27f)
        horizontalLineToRelative(-5.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.52f, 400.25f)
        lineToRelative(-14.13f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(297.44f, 400.24f)
        horizontalLineToRelative(-9.92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.63f, 400.23f)
        lineTo(274.65f, 400.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.14f, 400.23f)
        lineTo(274.63f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.12f, 400.22f)
        lineTo(272.14f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.42f, 400.22f)
        horizontalLineToRelative(-5.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.31f, 400.23f)
        lineTo(232.33f, 400.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229.81f, 400.23f)
        lineTo(232.31f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229.79f, 400.22f)
        lineTo(229.81f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.65f, 400.22f)
        reflectiveCurveToRelative(0f, -0.08f, 0f, -0.09f)
        verticalLineTo(400f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.45f, 400.43f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.85f, 0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.45f, 400.43f)
        lineTo(263.45f, 400.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.33f, 400.22f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.09f)
        verticalLineToRelative(-0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.11f, 400f)
        lineToRelative(-0.11f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.75f, 400f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.77f, 400f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.08f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.58f, 284.85f)
        arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.27f, -0.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193f, 284.85f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 402.48f)
        verticalLineToRelative(-0.06f)
    }
}

private fun ImageVector.Builder.addAaruyPaths4() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.12f)
        curveToRelative(0f, 0.18f, 0f, 0.33f, 0f, 0.44f)
        reflectiveCurveToRelative(0f, 0.15f, 0f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.78f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.43f, 400.22f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.12f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 400.78f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.39f, 400f)
        lineTo(238f, 400f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.66f, 402.14f)
        arcToRelative(4.64f, 4.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.65f, -0.18f)
        lineToRelative(0.37f, -0.08f)
        lineToRelative(0.19f, 0f)
        horizontalLineToRelative(0.19f)
        curveToRelative(0.33f, -0.06f, 0.68f, -0.1f, 1f, -0.15f)
        lineToRelative(1.1f, -0.1f)
        lineToRelative(1.1f, -0.06f)
        horizontalLineToRelative(1.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.41f, 400.84f)
        lineTo(273.41f, 401.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.21f, 400.5f)
        lineToRelative(0.63f, 0.05f)
        lineToRelative(0.64f, 0f)
        lineToRelative(0.41f, 0f)
        horizontalLineToRelative(0.43f)
        lineToRelative(0.91f, 0.05f)
        lineToRelative(0.51f, 0f)
        lineToRelative(0.51f, 0f)
        lineToRelative(1.07f, 0.06f)
        horizontalLineToRelative(0.55f)
        lineToRelative(0.54f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.21f, 400.5f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.49f, 400.22f)
        horizontalLineToRelative(-0.06f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.15f, 400.43f)
        arcToRelative(0.27f, 0.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.27f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.42f, 400.17f)
        curveToRelative(0f, -0.05f, 0f, -0.15f, 0f, -0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.14f, 400f)
        horizontalLineToRelative(-0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.77f, 400f)
        horizontalLineToRelative(0.33f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.76f, 401f)
        lineToRelative(-0.21f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.61f, 400.8f)
        lineToRelative(-0.21f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.86f, 401.31f)
        arcToRelative(18.77f, 18.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.72f, -0.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(252.58f, 400.43f)
        arcToRelative(18.82f, 18.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.72f, 0.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.55f, 400.8f)
        arcToRelative(2.1f, 2.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, 0.26f)
        lineToRelative(-0.12f, 0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.86f, 401.31f)
        lineToRelative(-0.12f, -0.25f)
        lineToRelative(-0.13f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.24f, 403.46f)
        arcToRelative(6.25f, 6.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.62f, -2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.3f, 401.31f)
        arcToRelative(6.25f, 6.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.62f, 2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.92f, 403.46f)
        lineToRelative(0.55f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.3f, 401.31f)
        lineToRelative(0.22f, -0.16f)
        lineToRelative(0.24f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 400.23f)
        lineTo(259.29f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.29f, 400.83f)
        lineTo(261.41f, 400.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 400.83f)
        lineTo(245.87f, 400.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.87f, 400.23f)
        lineTo(243.74f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.4f, 401f)
        lineToRelative(0.23f, 0.17f)
        lineToRelative(0.23f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.68f, 403.44f)
        lineToRelative(0.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.29f, 400.23f)
        curveToRelative(-0.38f, -0.13f, -0.76f, -0.25f, -1.15f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262f, 400.14f)
        arcToRelative(2.11f, 2.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.62f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.13f, 400.14f)
        lineTo(243.13f, 399.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 400.23f)
        arcToRelative(2.1f, 2.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.61f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 403.44f)
        verticalLineTo(404f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 404f)
        horizontalLineToRelative(1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.68f, 404f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.68f, 403.44f)
        horizontalLineToRelative(-1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246f, 404f)
        curveToRelative(0.1f, 0f, 0.16f, -0.17f, 0.18f, -0.24f)
        arcToRelative(1.06f, 1.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.92f, 403.46f)
        quadToRelative(-6.35f, 0.07f, -12.68f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.47f, 404f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.14f, 404f)
        curveToRelative(-0.1f, 0f, -0.16f, -0.17f, -0.18f, -0.24f)
        arcToRelative(1.06f, 1.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 404f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 403.44f)
        horizontalLineToRelative(-1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.47f, 404f)
        horizontalLineToRelative(1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.55f, 407.59f)
        lineTo(256.61f, 407.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.61f, 407.59f)
        lineTo(261.43f, 407.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 407.59f)
        verticalLineTo(407f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 407.03f)
        lineTo(243.73f, 407.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 407f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 407.59f)
        lineTo(248.55f, 407.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(255.84f, 408.32f)
        lineTo(249.32f, 408.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.61f, 407.59f)
        arcToRelative(5.32f, 5.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.77f, 0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.32f, 408.32f)
        arcToRelative(4.85f, 4.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.77f, -0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 409.74f)
        lineTo(261.43f, 409.74f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 408.32f)
        lineTo(255.84f, 408.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.32f, 408.32f)
        lineTo(243.73f, 408.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.29f, 409.78f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 407.59f)
        lineTo(261.43f, 408.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 407.59f)
        horizontalLineToRelative(1.42f)
        lineToRelative(0.7f, 0f)
        horizontalLineToRelative(0.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 407.54f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.25f, 403.5f)
        horizontalLineToRelative(-2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.47f, 403.44f)
        arcToRelative(6.77f, 6.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.71f, -2.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 400.83f)
        lineTo(261.41f, 403.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.76f, 401f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, -0.08f)
        lineToRelative(0.14f, 0f)
        arcToRelative(1.33f, 1.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 400.83f)
        arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.28f, -0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.88f, 401.28f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.25f, 401.28f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.8f, -0.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.4f, 400.43f)
        horizontalLineToRelative(-0.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 400.62f)
        arcToRelative(3.68f, 3.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.27f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.4f, 401f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.72f, 2.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.87f, 400.83f)
        arcToRelative(1.33f, 1.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0f)
        lineToRelative(0.13f, 0f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 403.44f)
        lineTo(243.74f, 400.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.28f, 401.28f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.28f, 400.43f)
        horizontalLineToRelative(-0.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 403.44f)
        horizontalLineToRelative(-2.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.14f, 404f)
        horizontalLineToRelative(0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.68f, 404f)
        horizontalLineToRelative(0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.7f, 404.12f)
        lineTo(241.46f, 404.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.69f, 407f)
        horizontalLineToRelative(-0.06f)
        lineToRelative(-0.22f, 0f)
        horizontalLineToRelative(-2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.46f, 407f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.56f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 408.32f)
        lineTo(243.73f, 407.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.9f, 408.34f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 409.74f)
        horizontalLineToRelative(1.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.31f, 409.75f)
        horizontalLineToRelative(1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.87f, 408.36f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 408.34f)
        lineTo(264.29f, 408.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.87f, 409.78f)
        lineTo(262.84f, 409.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 408.34f)
        horizontalLineToRelative(-2.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 408.34f)
        lineTo(264.26f, 407.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.7f, 406.95f)
        lineTo(263.69f, 406.97f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 407.54f)
        lineTo(264.27f, 407.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.68f, 404.07f)
        lineTo(263.7f, 404.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.25f, 401.28f)
        horizontalLineToRelative(-1.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.85f, 401.28f)
        lineTo(240.91f, 401.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.28f, 401.28f)
        horizontalLineToRelative(-1.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.91f, 401.28f)
        lineTo(240.91f, 403.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.46f, 404.12f)
        lineTo(241.48f, 404.07f)
    }
}
private fun ImageVector.Builder.addAaruyPaths5() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.91f, 403.5f)
        lineTo(240.89f, 403.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.27f, 407.52f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.46f, 407f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.89f, 407.52f)
        lineTo(240.9f, 407.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.46f, 406.97f)
        lineTo(241.46f, 406.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.31f, 409.75f)
        lineTo(242.29f, 409.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.9f, 408.34f)
        lineTo(240.87f, 408.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.9f, 408.34f)
        lineTo(240.9f, 407.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.83f, 400.63f)
        curveToRelative(0f, 0.11f, 0f, 0.22f, 0f, 0.33f)
        verticalLineToRelative(0.24f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.3f, 401.28f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(-0.06f)
        reflectiveCurveToRelative(0f, -0.05f, 0f, -0.08f)
        verticalLineTo(401f)
        curveToRelative(0f, -0.11f, 0f, -0.22f, 0f, -0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.3f, 401.28f)
        lineTo(264.25f, 401.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 254.05f)
        lineToRelative(0f, 0.2f)
        lineToRelative(0f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.92f, 251f)
        lineToRelative(0.15f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.17f, 282.73f)
        arcToRelative(6.89f, 6.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.94f, -4.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193f, 284.85f)
        lineToRelative(-0.38f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189.62f, 250.44f)
        arcToRelative(1.17f, 1.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.07f, 0.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.06f, 176.16f)
        horizontalLineToRelative(0.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.86f, 177.83f)
        verticalLineToRelative(-1.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.34f, 249.17f)
        horizontalLineToRelative(-0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.45f, 249.17f)
        horizontalLineToRelative(-0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.32f, 244.82f)
        lineToRelative(-0.01f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.01f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.39f, 252.59f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, 0.1f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.3f, 402.61f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.9f, 404f)
        horizontalLineTo(246f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.79f, 404f)
        horizontalLineToRelative(0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.37f, 404f)
        horizontalLineToRelative(0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.25f, 404f)
        horizontalLineToRelative(0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.87f, 409.78f)
        lineTo(242.29f, 409.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.9f, 243.42f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248f, 244.82f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.63f, 254.9f)
        lineToRelative(0.21f, -0.07f)
        lineToRelative(0.26f, -0.07f)
        lineToRelative(0.27f, 0f)
        lineToRelative(0.26f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.91f, 252.75f)
        horizontalLineToRelative(0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 254.41f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 254.39f)
        lineTo(274.22f, 254.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(205f, 170.77f)
        lineToRelative(0.37f, -0.11f)
        lineToRelative(0.51f, -0.16f)
        curveToRelative(0.33f, -0.1f, 0.64f, -0.18f, 0.93f, -0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 252.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.89f, 404.15f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.7f, 400.43f)
        verticalLineToRelative(-0.28f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.87f, 409.75f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 408.36f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.26f, 408.36f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.45f, 400.44f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.56f, 409.6f)
        lineToRelative(-0.05f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.88f, 401.24f)
        arcToRelative(2.45f, 2.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.27f)
        arcToRelative(2.54f, 2.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.26f)
        curveToRelative(0f, -0.07f, 0f, -0.13f, 0f, -0.17f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.28f, 401.24f)
        arcToRelative(2.45f, 2.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.27f)
        arcToRelative(2.54f, 2.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.26f)
        arcToRelative(0.72f, 0.72f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.17f)
        reflectiveCurveToRelative(0f, -0.07f, 0f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(195.56f, 172.46f)
        lineTo(203.42f, 172.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.81f, 252.49f)
        arcToRelative(1.27f, 1.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, 0.31f)
        lineToRelative(0.09f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.81f, 252.49f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, 0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.12f, 253.06f)
        lineTo(269.12f, 253.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 253f)
        arcToRelative(2.19f, 2.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.67f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.76f, 252.76f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.21f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.55f, 252.5f)
        arcToRelative(3.28f, 3.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.2f, 0.31f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.23f, 0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.41f, 252.46f)
        arcToRelative(0.92f, 0.92f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, 0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.94f, 252.46f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.18f, 0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244f, 254.37f)
        arcToRelative(0.7f, 0.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.16f)
        lineToRelative(-0.05f, 0.12f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, 0.14f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.17f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.83f, 252.75f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.85f, -0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.89f, 252.75f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.85f, -0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.65f, 253.3f)
        lineTo(273.65f, 252.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 254.36f)
        horizontalLineToRelative(-2.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255f)
        horizontalLineToRelative(2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(303.6f, 252.05f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 257.6f)
        horizontalLineToRelative(1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 252.05f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.83f, 403.04f)
        lineTo(240.83f, 400.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.41f, 400.12f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.27f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.15f)
        verticalLineToRelative(0.15f)
        horizontalLineToRelative(0f)
        verticalLineTo(401f)
        curveToRelative(0f, 0.07f, 0f, 0.12f, 0f, 0.17f)
        verticalLineToRelative(0.1f)
        horizontalLineToRelative(0f)
        curveToRelative(0f, 0.11f, 0f, 0.21f, 0f, 0.31f)
        verticalLineToRelative(0.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.41f, 401.47f)
        curveToRelative(0f, 0.15f, 0f, 0.3f, 0f, 0.44f)
        reflectiveCurveToRelative(0f, 0.27f, 0f, 0.37f)
        reflectiveCurveToRelative(0f, 0.19f, 0f, 0.25f)
        reflectiveCurveToRelative(0f, 0.08f, 0f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.42f, 402.57f)
        curveToRelative(0.05f, 0f, 0.07f, -0.09f, 0.09f, -0.12f)
        lineToRelative(0f, -0.14f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.29f)
        arcToRelative(5.82f, 5.82f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.79f, 401.47f)
        horizontalLineToRelative(1.08f)
        lineToRelative(1.09f, 0.07f)
        lineToRelative(0.28f, 0f)
        lineToRelative(0.14f, 0f)
        horizontalLineToRelative(0.12f)
        lineToRelative(0.48f, 0.05f)
        lineToRelative(0.94f, 0.14f)
        lineToRelative(0.23f, 0f)
        horizontalLineToRelative(0.09f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.35f, 0.07f)
        curveToRelative(0.24f, 0.06f, 0.46f, 0.12f, 0.66f, 0.19f)
        lineToRelative(0.07f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0.18f, 0.08f)
        lineToRelative(0.18f, 0.1f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238f, 402.53f)
        lineTo(238f, 400.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.58f, 284.85f)
        arcToRelative(2.29f, 2.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.51f, 0.15f)
        curveToRelative(-0.13f, 0f, -0.4f, 0f, -0.49f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217f, 400.25f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.05f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.07f)
        lineToRelative(0f, 0.15f)
        curveToRelative(0f, 0.1f, 0f, 0.2f, 0f, 0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.06f, 402.55f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(300.09f, 402.55f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.16f, 369.66f)
        horizontalLineTo(308f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.11f, 351f)
        horizontalLineToRelative(-0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.12f, 348.44f)
        curveToRelative(0f, 0.05f, -0.07f, 0.08f, -0.11f, 0.1f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(2.84f, 2.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.31f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217f, 400.25f)
        horizontalLineToRelative(-9.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.85f, 401.28f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.85f, -0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.15f, 402.51f)
        lineTo(267.15f, 400.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.15f, 402.51f)
        curveToRelative(0f, -0.09f, 0.1f, -0.17f, 0.21f, -0.23f)
        lineToRelative(0.16f, -0.08f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.63f, 402.47f)
        curveToRelative(0f, -0.51f, 0f, -0.73f, 0f, -1.24f)
        reflectiveCurveToRelative(0.09f, -0.82f, 0.09f, -1.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.14f, 400f)
        verticalLineToRelative(2.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.9f, 402.61f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.08f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.25f)
        curveToRelative(0f, -0.1f, 0f, -0.23f, 0f, -0.37f)
        reflectiveCurveToRelative(0f, -0.28f, 0f, -0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(252.58f, 400.43f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.29f, 400.83f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.55f, 400.8f)
        arcToRelative(1.22f, 1.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.32f, -0.36f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.42f, -0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 400.83f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.74f, 400.23f)
        verticalLineToRelative(0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.87f, 400.83f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.87f, 400.23f)
        arcToRelative(1.83f, 1.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.42f, 0.21f)
        arcToRelative(1.22f, 1.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.32f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.43f, 409.74f)
        verticalLineToRelative(-1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 408.32f)
        verticalLineToRelative(1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.68f, 404.07f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.69f, 400.62f)
        lineToRelative(-0.14f, -0.15f)
        lineToRelative(-0.17f, -0.13f)
        arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.35f, -0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.13f, 400.14f)
        arcToRelative(2.16f, 2.16f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.36f, 0.2f)
        lineToRelative(-0.16f, 0.13f)
        lineToRelative(-0.14f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.91f, 403.5f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 407f)
        horizontalLineToRelative(-2f)
        lineToRelative(-0.21f, 0f)
        horizontalLineToRelative(-0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.46f, 406.95f)
        lineTo(263.7f, 406.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.9f, 407.54f)
        horizontalLineToRelative(0.7f)
        lineToRelative(0.71f, 0f)
        horizontalLineToRelative(1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.27f, 403.55f)
        lineTo(264.25f, 403.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.25f, 403.5f)
        lineTo(264.25f, 401.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(264.27f, 403.55f)
        lineTo(264.27f, 407.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.7f, 404.12f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.89f, 403.55f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.73f, 408.32f)
        horizontalLineTo(240.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(261.41f, 404f)
        horizontalLineToRelative(1.14f)
        lineToRelative(0.57f, 0f)
        horizontalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246f, 404f)
        quadToRelative(3.14f, 0f, 6.56f, 0f)
        curveToRelative(2.28f, 0f, 4.46f, 0f, 6.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.48f, 404.07f)
        horizontalLineTo(242f)
        lineToRelative(0.57f, 0f)
        horizontalLineToRelative(1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.39f, 252.59f)
        horizontalLineToRelative(0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217.07f, 402.57f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.12f, 364.84f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.62f, 255.3f)
        lineTo(187.62f, 255.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.4f, 273.52f)
        arcToRelative(13.54f, 13.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, 1.68f)
        arcToRelative(12f, 12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.33f, 1.67f)
        arcToRelative(13.2f, 13.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.27f, 3.12f)
        arcToRelative(14.46f, 14.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.92f, 1.43f)
        arcToRelative(13.6f, 13.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.1f, 1.29f)
        arcToRelative(14.1f, 14.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.59f, 2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 257.28f)
        lineTo(187.57f, 255.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.4f, 257.57f)
        lineTo(236.4f, 273.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.38f, 260.12f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        reflectiveCurveToRelative(0f, -0.09f, 0f, -0.16f)
        reflectiveCurveToRelative(0f, -0.16f, 0f, -0.26f)
        reflectiveCurveToRelative(0f, -0.22f, 0f, -0.35f)
        reflectiveCurveToRelative(0f, -0.28f, 0f, -0.43f)
        lineToRelative(0f, -0.49f)
        curveToRelative(0f, -0.17f, 0f, -0.35f, 0f, -0.53f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(186.81f, 255.72f)
        lineTo(186.81f, 257.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(186.81f, 255.3f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(305.85f, 350.97f)
        lineTo(305.85f, 362.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(305.85f, 362.31f)
        curveToRelative(0f, 0.65f, 0f, 1.29f, 0f, 1.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(305.88f, 349f)
        curveToRelative(0f, 0.64f, -0.05f, 1.29f, 0f, 1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.41f, 261.54f)
        lineTo(238.6f, 261.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.41f, 261.54f)
        lineTo(238.41f, 257.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.4f, 257.57f)
        lineTo(238.41f, 257.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.59f, 255.72f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.64f, 255.3f)
        lineTo(238.41f, 255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.71f, 255.87f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.06f, -0.24f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.18f)
        curveToRelative(0f, -0.1f, -0.08f, -0.15f, -0.13f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.83f, 350.97f)
        lineTo(306.83f, 362.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 378.32f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.63f, 254.93f)
        lineTo(184.63f, 254.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184f, 257.28f)
        horizontalLineToRelative(2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184f, 255.72f)
        horizontalLineToRelative(2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 378.32f)
        horizontalLineToRelative(0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.09f, 255.3f)
        arcToRelative(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.29f, 0.1f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, 0.14f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 378.32f)
        lineTo(230.08f, 378.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.81f, 394.2f)
        lineTo(306.86f, 394.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 378.32f)
        lineTo(306.81f, 378.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 378.32f)
        lineTo(296.89f, 394.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 378.32f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.41f, 363.53f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, 0.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.38f, 260.12f)
        lineTo(187.38f, 260.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.4f, 255.3f)
        horizontalLineToRelative(2.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(230.08f, 284.86f)
        lineTo(230.08f, 378.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(233.91f, 169.08f)
        curveToRelative(0f, -0.27f, 0.05f, -0.52f, 0.07f, -0.76f)
        curveToRelative(0f, -0.41f, 0.09f, -0.75f, 0.12f, -1f)
        reflectiveCurveToRelative(0.05f, -0.37f, 0.05f, -0.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 261.54f)
        lineTo(306.85f, 261.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.6f, 261.54f)
        lineToRelative(0.05f, -3f)
        curveToRelative(0f, -0.35f, 0f, -0.7f, 0f, -1.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.21f, 257.53f)
        curveToRelative(0f, 0.28f, 0f, 0.56f, 0f, 0.84f)
        lineToRelative(-0.12f, 2.9f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.67f, 257.53f)
        horizontalLineToRelative(7.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.63f, 260.67f)
        lineToRelative(0.06f, -1.2f)
        curveToRelative(0f, -0.65f, 0.07f, -1.29f, 0.1f, -1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.81f, 257.53f)
        curveToRelative(0f, 0.62f, 0f, 1.25f, 0f, 1.88f)
        reflectiveCurveToRelative(0f, 1.25f, 0f, 1.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.81f, 257.53f)
        horizontalLineToRelative(9.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.05f, 261.3f)
        curveToRelative(1.09f, 0f, 2.44f, 0f, 4f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(239.27f, 255.35f)
        lineToRelative(-0.39f, 0f)
        lineToRelative(-0.13f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.79f, 257.53f)
        horizontalLineToRelative(23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.77f, 255.3f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.28f)
        curveToRelative(0f, 0.09f, 0f, 0.18f, 0f, 0.28f)
        verticalLineToRelative(1.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.05f, 261.3f)
        quadToRelative(-2.49f, 0f, -7.45f, 0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.08f, 261.54f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        verticalLineToRelative(-0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.62f, 261.54f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-0.21f)
        curveToRelative(0f, -0.07f, 0f, -0.14f, 0f, -0.22f)
        verticalLineToRelative(-0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.8f, 261.29f)
        curveToRelative(-1.72f, 0f, -3.64f, 0f, -5.79f, -0.1f)
        horizontalLineToRelative(-0.51f)
        lineToRelative(-0.57f, 0f)
        lineToRelative(-1.33f, -0.05f)
        lineToRelative(-3.38f, -0.13f)
        curveToRelative(-2.57f, -0.1f, -4.83f, -0.18f, -6.76f, -0.23f)
        reflectiveCurveToRelative(-3.55f, -0.08f, -4.83f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.37f, 253.32f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.37f, 253.87f)
        horizontalLineToRelative(1.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.86f, 253.87f)
        horizontalLineTo(247f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247f, 253.32f)
        verticalLineToRelative(0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.32f, 255.3f)
        horizontalLineToRelative(-0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.72f, 254.44f)
        horizontalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.06f, 255.3f)
        curveToRelative(-0.93f, 0f, -1.86f, 0f, -2.79f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(239.29f, 254.44f)
        lineTo(241.21f, 254.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(298.94f, 261.53f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(294.61f, 261.53f)
        horizontalLineToRelative(4.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(294.6f, 261.53f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.59f, 254.54f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.77f, 261.54f)
        horizontalLineTo(266.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.08f, 261.54f)
        horizontalLineTo(238.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.06f, 255.27f)
        lineTo(242.06f, 255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246f, 329.45f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.49f, -0.08f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.45f, -0.21f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.38f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 328.8f)
        arcToRelative(1.49f, 1.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, -0.49f)
        arcToRelative(1.63f, 1.63f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.1f, -0.59f)
        curveToRelative(0f, -0.1f, 0f, -0.2f, 0f, -0.3f)
        arcToRelative(2.29f, 2.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.28f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, -0.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.85f, 329.22f)
        arcToRelative(2.36f, 2.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.4f, -0.69f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, -0.39f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.42f)
        arcToRelative(2.73f, 2.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.41f)
        curveToRelative(0f, -0.14f, 0.07f, -0.27f, 0.11f, -0.4f)
        arcToRelative(2.44f, 2.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, -0.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.82f, 328.8f)
        arcToRelative(1.63f, 1.63f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.84f, 0.57f)
        arcToRelative(1.93f, 1.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.48f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.82f, 326.65f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0.49f)
        arcToRelative(1.87f, 1.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, 0.59f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.3f)
        arcToRelative(1.28f, 1.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0.28f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(260.59f, 326.23f)
        arcToRelative(2.15f, 2.15f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, 0.69f)
        arcToRelative(1.74f, 1.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 0.39f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.42f)
        arcToRelative(2.42f, 2.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.15f, 0.81f)
        arcToRelative(2.22f, 2.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.4f, 0.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.31f, 311.6f)
        horizontalLineToRelative(0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(260.59f, 326.23f)
        arcToRelative(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, -5.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.82f, 320.45f)
        arcToRelative(8.52f, 8.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, 5.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.89f, 319.65f)
        curveToRelative(-0.07f, 0.25f, -0.16f, 0.52f, -0.26f, 0.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.82f, 320.45f)
        curveToRelative(-0.1f, -0.28f, -0.19f, -0.55f, -0.26f, -0.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.57f, 317.92f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, -0.2f)
        lineToRelative(0.06f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.12f, 320f)
        curveToRelative(0.1f, -0.31f, 0.18f, -0.61f, 0.25f, -0.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.07f, 319.13f)
        curveToRelative(0.07f, 0.28f, 0.16f, 0.58f, 0.25f, 0.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.32f, 320f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.5f, 0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.52f, 317.66f)
        lineToRelative(0.07f, 0.06f)
        arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.28f, 0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.77f, 261.54f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(-0.1f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.77f, 261.54f)
        lineTo(289.78f, 261.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 257.53f)
        horizontalLineToRelative(-3.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 261.54f)
        horizontalLineToRelative(-4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.29f, 311.6f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 326.65f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.83f, -0.57f)
        arcTo(2.09f, 2.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 246f, 326f)
        arcToRelative(1.66f, 1.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.49f, 0.07f)
        arcToRelative(1.92f, 1.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.46f, 0.21f)
        arcToRelative(1.72f, 1.72f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, 0.36f)
        arcToRelative(1.49f, 1.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0.49f)
        arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 0.59f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.3f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.29f)
        arcToRelative(1.23f, 1.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, 0.26f)
        arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.15f, 0.23f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.4f, 0.37f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.47f, 0.21f)
        arcToRelative(1.66f, 1.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.49f, 0.07f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(244.63f, 328.8f)
        curveToRelative(0f, -0.05f, -0.18f, 0f, -0.23f, 0f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.2f, 0.11f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.35f, 0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.85f, 326.23f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.54f, -0.49f)
        arcToRelative(2.33f, 2.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.64f, -0.3f)
        arcToRelative(2.05f, 2.05f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, -0.07f)
        lineToRelative(0.35f, 0f)
        arcToRelative(2.37f, 2.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.69f, 0.11f)
        arcToRelative(2.42f, 2.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.65f, 0.31f)
        arcToRelative(3.11f, 3.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, 0.23f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, 0.28f)
        arcToRelative(2.44f, 2.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.37f, 0.67f)
        arcToRelative(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 1.56f)
        arcToRelative(1.58f, 1.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, 0.35f)
        arcToRelative(3.45f, 3.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.2f, 0.32f)
        arcToRelative(2.26f, 2.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.25f, 0.28f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.29f, 0.24f)
        arcToRelative(2.29f, 2.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.65f, 0.31f)
        arcToRelative(2.37f, 2.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.69f, 0.11f)
        arcToRelative(2.3f, 2.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.69f, -0.1f)
        arcToRelative(2.19f, 2.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.33f, -0.12f)
        arcToRelative(1.92f, 1.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.31f, -0.18f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.54f, -0.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.85f, 326.23f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, 0.3f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, 0.11f)
        reflectiveCurveToRelative(0.18f, 0.06f, 0.23f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.82f, 326.65f)
        curveToRelative(0.05f, 0.05f, 0.17f, 0f, 0.22f, 0f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.21f, -0.11f)
        arcToRelative(1.38f, 1.38f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.34f, -0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(260.59f, 329.22f)
        arcToRelative(2.09f, 2.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.54f, 0.49f)
        arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.63f, 0.3f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.35f, 0.07f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.34f, 0f)
        arcToRelative(2.41f, 2.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.34f, -0.42f)
        lineToRelative(-0.29f, -0.23f)
        arcToRelative(3.33f, 3.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.25f, -0.28f)
        arcToRelative(2.6f, 2.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.36f, -0.68f)
        arcToRelative(2.44f, 2.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.13f, -0.77f)
        arcToRelative(2.28f, 2.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, -0.78f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.15f, -0.35f)
        arcToRelative(1.77f, 1.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, -0.32f)
        arcToRelative(1.72f, 1.72f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, -0.28f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.29f, -0.24f)
        arcToRelative(2.37f, 2.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -0.32f)
        lineToRelative(0.33f, 0.12f)
        arcToRelative(2.48f, 2.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, 0.18f)
        arcToRelative(2.22f, 2.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.53f, 0.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(258.5f, 329.45f)
        horizontalLineToRelative(0f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.49f, -0.07f)
        arcToRelative(1.92f, 1.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.46f, -0.21f)
        lineToRelative(-0.22f, -0.16f)
        arcToRelative(2.12f, 2.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.47f, -0.69f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.1f, -0.59f)
        arcToRelative(2.19f, 2.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -0.59f)
        lineToRelative(0.12f, -0.26f)
        lineToRelative(0.16f, -0.24f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, -0.36f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.47f, -0.21f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.49f, -0.07f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.48f, 0.08f)
        arcToRelative(1.85f, 1.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.45f, 0.21f)
        arcToRelative(1.66f, 1.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.39f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(259.82f, 328.8f)
        curveToRelative(0.05f, -0.05f, 0.17f, 0f, 0.22f, 0f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0.11f)
        arcToRelative(1.38f, 1.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, 0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(243.85f, 329.22f)
        arcToRelative(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.74f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.63f, 320.45f)
        curveToRelative(-0.11f, 0.28f, -0.22f, 0.56f, -0.35f, 0.85f)
        reflectiveCurveToRelative(-0.28f, 0.57f, -0.43f, 0.86f)
        curveToRelative(-0.31f, 0.59f, -0.67f, 1.17f, -1f, 1.76f)
        reflectiveCurveToRelative(-0.8f, 1.19f, -1.22f, 1.79f)
        lineToRelative(-0.65f, 0.9f)
        lineToRelative(-0.33f, 0.45f)
        lineToRelative(-0.17f, 0.22f)
        lineToRelative(-0.09f, 0.11f)
        lineToRelative(0f, 0.05f)
        lineToRelative(-0.06f, 0f)
        arcToRelative(0.1f, 0.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.09f, -0.11f)
        lineToRelative(-0.17f, -0.22f)
        lineToRelative(-0.34f, -0.45f)
        lineToRelative(-0.65f, -0.91f)
        curveToRelative(-0.42f, -0.6f, -0.84f, -1.2f, -1.22f, -1.79f)
        reflectiveCurveToRelative(-0.73f, -1.18f, -1f, -1.76f)
        curveToRelative(-0.16f, -0.29f, -0.3f, -0.58f, -0.43f, -0.87f)
        arcToRelative(8.77f, 8.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.36f, -0.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.23f, 253.39f)
        lineTo(185.23f, 253.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.23f, 253.32f)
        horizontalLineToRelative(-0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.55f, 251.61f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.67f, 253.31f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.35f, 253.32f)
        horizontalLineToRelative(0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.09f, 253.32f)
        arcToRelative(1.48f, 1.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, 0.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(195.35f, 252.61f)
        quadToRelative(10.35f, 0.09f, 20.71f, 0.06f)
        curveToRelative(2.34f, 0f, 18.38f, 0f, 20.71f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.77f, 252.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.27f, 252.49f)
        lineTo(188.98f, 252.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189f, 252.49f)
        curveToRelative(1f, 0.07f, 5.4f, 0.11f, 6.37f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.65f, 254.58f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 254.62f)
        curveToRelative(0f, 0.08f, 0f, 0.18f, 0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.13f, 251.06f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.08f, 251.06f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.51f, 251.06f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 251.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(233.09f, 178.05f)
        arcToRelative(3.42f, 3.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.84f, 171.2f)
        arcToRelative(1.93f, 1.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.14f, 176.35f)
        horizontalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.49f, 171.45f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.49f, 171.48f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.49f, 171.48f)
        horizontalLineToRelative(0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.85f, 171.44f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.85f, 171.48f)
        arcToRelative(2.61f, 2.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, 0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.53f, 172f)
        arcToRelative(4.91f, 4.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.9f, 172f)
        arcToRelative(0.13f, 0.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
        arcToRelative(0.27f, 0.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, 0.07f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.19f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.53f, 172f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.16f, 0.48f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.17f, 0.18f)
        arcToRelative(0.63f, 0.63f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, 0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217.21f, 172.2f)
        arcToRelative(6.23f, 6.23f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.15f, 0.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(228.76f, 176.88f)
        lineToRelative(-0.95f, -0.39f)
        lineToRelative(-0.46f, -0.18f)
        lineToRelative(-0.45f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229f, 176.35f)
        lineToRelative(-0.5f, -0.33f)
        lineToRelative(-0.48f, -0.33f)
        lineToRelative(-0.46f, -0.3f)
        lineToRelative(-0.42f, -0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.14f, 178.72f)
        arcToRelative(5.09f, 5.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, 0.6f)
        arcToRelative(4.37f, 4.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, 0.62f)
        arcToRelative(5.84f, 5.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.14f, 1.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.7f, 181.19f)
        arcToRelative(1.88f, 1.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -0.53f)
        arcToRelative(2.52f, 2.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.53f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, -0.52f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.41f, -0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.22f, 253.39f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242f, 253.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242f, 253.24f)
        arcToRelative(3.45f, 3.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.55f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.42f, 253.32f)
        lineTo(241.42f, 253.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 253.91f)
        lineTo(187.57f, 254.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.97f, 253.24f)
        lineTo(241.97f, 254.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.77f, 252.49f)
        lineTo(234.52f, 252.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.69f, 253.93f)
        curveToRelative(-3.78f, 0.13f, -17.9f, 0.16f, -21.68f, 0.16f)
        quadToRelative(-10.84f, 0f, -21.69f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.22f, 255.15f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.11f, 0.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
        verticalLineToRelative(-0.22f)
        arcToRelative(2.53f, 2.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(235.15f, 255.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.42f, 255.2f)
        verticalLineToRelative(0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.48f, 255.19f)
        lineToRelative(0.06f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.72f, 254.61f)
        lineTo(238.22f, 254.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.15f, 167f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.09f, 251.62f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.98f, 254.11f)
        lineTo(241.98f, 253.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.07f, 175.12f)
        curveToRelative(0f, -0.17f, 0f, -0.38f, -0.07f, -0.63f)
        reflectiveCurveToRelative(-0.09f, -0.87f, -0.12f, -1.39f)
        reflectiveCurveToRelative(0f, -0.95f, 0f, -1.45f)
        verticalLineToRelative(-0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.85f, 261.54f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.56f, 251.62f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        quadToRelative(0f, 0.12f, 0f, 0.36f)
        arcToRelative(4.56f, 4.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.55f)
        curveToRelative(0f, 0.11f, 0f, 0.21f, 0f, 0.32f)
        reflectiveCurveToRelative(0f, 0.22f, 0f, 0.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.66f, 254.58f)
        horizontalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.22f, 254.58f)
        lineTo(185.22f, 253.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242f, 253.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.67f, 253.31f)
        lineTo(184.66f, 254.58f)
    }
}
private fun ImageVector.Builder.addAaruyPaths6() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 253.91f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.3f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.09f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(212.89f, 171.48f)
        horizontalLineToRelative(3.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.79f, 255.19f)
        lineToRelative(-0.08f, 0f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.07f, 175.12f)
        arcToRelative(1.37f, 1.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.23f)
        curveToRelative(0f, 0.09f, 0f, 0.19f, 0f, 0.29f)
        reflectiveCurveToRelative(0f, 0.23f, 0f, 0.35f)
        reflectiveCurveToRelative(0f, 0.24f, 0f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.55f, 253.17f)
        reflectiveCurveToRelative(0f, 0.09f, 0.12f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.86f, 179.22f)
        verticalLineToRelative(-1.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.07f, 250.43f)
        horizontalLineToRelative(-0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(200.79f, 175.27f)
        lineTo(199f, 176f)
        quadToRelative(-2.35f, 0.92f, -4.71f, 1.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 244.85f)
        lineTo(262.92f, 244.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 246.15f)
        lineTo(268.46f, 246.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 249.71f)
        curveToRelative(3.88f, 0f, 7.77f, 0f, 11.65f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(265.22f, 253.32f)
        curveToRelative(0.39f, -0.11f, 0.78f, -0.24f, 1.16f, -0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 243.43f)
        horizontalLineToRelative(-0.63f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 246.23f)
        lineTo(248.6f, 246.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 249.17f)
        horizontalLineToRelative(-1.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 249.06f)
        lineTo(270.73f, 249.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 252.42f)
        lineToRelative(-0.29f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.48f, 255f)
        lineTo(274.78f, 255f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.7f, 179.49f)
        lineToRelative(0f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 246.54f)
        lineTo(239.6f, 251.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 254.66f)
        verticalLineToRelative(-0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 251f)
        horizontalLineToRelative(0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.89f, 253.31f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.85f, 254.42f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(273.49f, 255.19f)
        lineTo(274.78f, 255.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.22f, 251.05f)
        lineToRelative(-0.08f, -0.13f)
        lineToRelative(-0.15f, -0.17f)
        arcToRelative(1.14f, 1.14f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.38f, -0.23f)
        arcToRelative(1.36f, 1.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.44f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.56f, 254.86f)
        horizontalLineToRelative(0.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.67f, 257.26f)
        lineTo(238.41f, 257.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 343.62f)
        horizontalLineToRelative(0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.75f, 257.14f)
        horizontalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.07f, 251f)
        horizontalLineToRelative(-0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193.05f, 287.72f)
        lineTo(191.58f, 284.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(201.7f, 399.87f)
        lineToRelative(0.75f, 0.28f)
        curveToRelative(0.25f, 0.08f, 0.5f, 0.15f, 0.76f, 0.21f)
        lineToRelative(0.76f, 0.17f)
        arcToRelative(18.68f, 18.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.09f, 0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.31f, 396.63f)
        lineToRelative(-0.12f, 0.28f)
        curveToRelative(-0.09f, 0.18f, -0.19f, 0.36f, -0.29f, 0.53f)
        arcToRelative(5.36f, 5.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.34f, 0.49f)
        quadToRelative(-0.18f, 0.22f, -0.39f, 0.45f)
        arcToRelative(5.76f, 5.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.44f, 0.41f)
        arcToRelative(5.43f, 5.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, 0.66f)
        arcToRelative(5.92f, 5.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.11f, 0.46f)
        arcToRelative(7.63f, 7.63f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.33f, 0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(199.7f, 399.87f)
        arcToRelative(7.94f, 7.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.7f, 1.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 347.34f)
        verticalLineToRelative(0f)
        curveToRelative(0f, -0.45f, 0f, -0.87f, 0f, -1.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 366.58f)
        curveToRelative(0.05f, 0.23f, 0.11f, 0.46f, 0.18f, 0.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.86f, 367.31f)
        lineToRelative(0.18f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.94f, 399.87f)
        verticalLineToRelative(0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.14f, 399.87f)
        lineTo(272.14f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.63f, 399.87f)
        lineTo(274.63f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.48f, 399.87f)
        curveToRelative(0f, 0.09f, 0f, 0.19f, 0f, 0.28f)
        verticalLineToRelative(0.27f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.57f, 399.87f)
        curveToRelative(0f, 0.21f, -0.09f, 0.33f, -0.14f, 0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229.81f, 399.87f)
        lineTo(229.81f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.31f, 399.87f)
        lineTo(232.31f, 400.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229.75f, 399.87f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.16f, 399.87f)
        lineToRelative(-0.11f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.35f, 399.87f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.14f, 399.87f)
        verticalLineToRelative(0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.03f, 399.87f)
        lineTo(262.03f, 400.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247f, 399.87f)
        curveToRelative(-0.39f, 0.11f, -0.77f, 0.23f, -1.15f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 251f)
        lineToRelative(0.15f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.15f, 251.05f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.7f, 179.44f)
        lineToRelative(0f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(287.42f, 402.57f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189.73f, 283.84f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 254.39f)
        curveToRelative(0f, -0.11f, -0.16f, -0.18f, -0.25f, -0.21f)
        arcToRelative(1.13f, 1.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.32f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.08f, 251.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.84f, 400.27f)
        lineToRelative(-14.86f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(250.1f, 245.59f)
        horizontalLineTo(248f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.14f, 345.39f)
        curveToRelative(0f, 0.25f, -0.25f, 0.42f, -0.44f, 0.5f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.69f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.16f, 343.61f)
        lineTo(309.16f, 343.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.11f, 362.31f)
        lineTo(309.11f, 362.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.14f, 345.39f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.7f, 245.59f)
        arcToRelative(5.44f, 5.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.78f, -0.74f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.46f, 244.85f)
        lineTo(268.46f, 245.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 244.84f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.42f, -1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.45f, 243.42f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.42f, 1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(268.45f, 249.74f)
        horizontalLineToRelative(2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(249.39f, 252.72f)
        lineTo(249.39f, 253.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(269.97f, 253.32f)
        lineTo(269.97f, 252.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(271.3f, 245.65f)
        lineTo(271.3f, 244.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.03f, 244.84f)
        lineTo(248.03f, 245.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 252.59f)
        verticalLineTo(253f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(270.44f, 252.75f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.85f, -0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 182f)
        horizontalLineToRelative(0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.72f, 245.54f)
        lineToRelative(0.2f, 0f)
        arcToRelative(0.26f, 0.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.78f, 255f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.56f, -0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 252.18f)
        lineTo(274.22f, 254.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(277.05f, 253.19f)
        lineToRelative(-0.53f, 0f)
        lineToRelative(-0.27f, 0f)
        lineToRelative(-0.14f, 0f)
        horizontalLineTo(276f)
        arcToRelative(3.57f, 3.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.47f, -0.11f)
        lineToRelative(-0.23f, -0.07f)
        lineToRelative(-0.11f, 0f)
        lineToRelative(-0.06f, 0f)
        horizontalLineToRelative(-0.05f)
        lineToRelative(-0.34f, -0.16f)
        lineToRelative(-0.15f, -0.09f)
        lineToRelative(-0.07f, -0.05f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.69f, 0.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, -0.15f)
        arcToRelative(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, -0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(195.56f, 172.46f)
        verticalLineToRelative(-1.12f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308f, 343.62f)
        horizontalLineToRelative(1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.43f, 400.22f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.38f, 0.16f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(231.85f, 400.27f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.07f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.07f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.14f)
        arcToRelative(2.81f, 2.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217.07f, 402.57f)
        reflectiveCurveToRelative(-0.07f, -0.09f, -0.08f, -0.11f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.15f)
        arcToRelative(2.54f, 2.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
        arcToRelative(5.83f, 5.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.14f, 367.89f)
        curveToRelative(0f, -0.26f, -0.25f, -0.42f, -0.44f, -0.5f)
        arcToRelative(1.66f, 1.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.69f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(309.12f, 364.84f)
        reflectiveCurveToRelative(-0.07f, -0.09f, -0.11f, -0.1f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, -0.05f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(308.56f, 362.31f)
        horizontalLineToRelative(0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(267.15f, 400.43f)
        verticalLineToRelative(-0.18f)
        curveToRelative(0f, -0.05f, 0f, -0.1f, 0f, -0.15f)
        verticalLineToRelative(-0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.84f, 409.75f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.42f, -1.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.88f, 400.45f)
        lineTo(262.88f, 399.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(262.69f, 400.62f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.15f, 0.31f)
        arcToRelative(1.39f, 1.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(263.45f, 400.44f)
        horizontalLineToRelative(-0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.76f, 400.43f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.85f, 0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.28f, 399.87f)
        lineTo(242.28f, 400.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 400.62f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.15f, 0.31f)
        arcToRelative(1.39f, 1.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.9f, 408.36f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.41f, 1.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(240.89f, 407.52f)
        lineTo(240.89f, 403.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.73f, 176.16f)
        horizontalLineToRelative(0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.23f, 250.86f)
        curveToRelative(0.89f, -2.11f, 1.86f, -4.18f, 2.84f, -6.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 252.59f)
        horizontalLineToRelative(0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.72f, 252.59f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.21f, 253.6f)
        horizontalLineToRelative(0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(230.08f, 284.86f)
        horizontalLineToRelative(-37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.38f, 273.52f)
        horizontalLineToRelative(49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 255.87f)
        horizontalLineToRelative(49.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.71f, 255.87f)
        arcToRelative(3.83f, 3.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.23f, 0.76f)
        curveToRelative(0f, 0.14f, 0f, 0.3f, -0.06f, 0.45f)
        arcToRelative(3.93f, 3.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(186.81f, 257.28f)
        verticalLineToRelative(2.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(186.81f, 257.28f)
        lineTo(187.57f, 257.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 378.32f)
        lineTo(296.89f, 261.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(305.88f, 364.24f)
        arcToRelative(16.65f, 16.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.22f, 1.66f)
        curveToRelative(0.09f, 0.54f, 0.19f, 1f, 0.3f, 1.48f)
        curveToRelative(0.05f, 0.23f, 0.1f, 0.44f, 0.15f, 0.65f)
        reflectiveCurveToRelative(0.09f, 0.38f, 0.13f, 0.55f)
        reflectiveCurveToRelative(0.07f, 0.33f, 0.09f, 0.47f)
        arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.81f, 343.84f)
        arcToRelative(1.91f, 1.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.39f)
        curveToRelative(0f, 0.14f, 0f, 0.29f, -0.09f, 0.46f)
        reflectiveCurveToRelative(-0.08f, 0.36f, -0.13f, 0.56f)
        reflectiveCurveToRelative(-0.1f, 0.42f, -0.15f, 0.64f)
        curveToRelative(-0.11f, 0.45f, -0.21f, 0.95f, -0.3f, 1.48f)
        reflectiveCurveToRelative(-0.17f, 1.09f, -0.22f, 1.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193.05f, 378.32f)
        horizontalLineToRelative(37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(186.81f, 255.3f)
        lineTo(188.09f, 255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.59f, 255.72f)
        lineTo(186.81f, 255.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.41f, 257.57f)
        verticalLineTo(255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.09f, 255.3f)
        horizontalLineToRelative(0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.42f, 255.3f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.45f, 255.3f)
        horizontalLineToRelative(48.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.44f, 255.2f)
        verticalLineToRelative(0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 394.2f)
        horizontalLineToRelative(9.92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.81f, 378.32f)
        horizontalLineToRelative(-9.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(230.08f, 378.32f)
        lineTo(230.08f, 394.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(230.08f, 394.2f)
        lineTo(296.89f, 394.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(193.05f, 394.2f)
        horizontalLineToRelative(37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(230.08f, 378.32f)
        horizontalLineToRelative(-37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(296.89f, 394.2f)
        verticalLineToRelative(5.66f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(226.41f, 399.87f)
        horizontalLineToRelative(0.19f)
        lineToRelative(0.18f, 0f)
        arcToRelative(2.14f, 2.14f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, -0.06f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.67f, -0.27f)
        arcToRelative(3.68f, 3.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.62f, -0.46f)
        curveToRelative(0.09f, -0.09f, 0.19f, -0.19f, 0.28f, -0.3f)
        lineToRelative(0.27f, -0.35f)
        arcToRelative(5.55f, 5.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.46f, -0.84f)
        curveToRelative(0.07f, -0.16f, 0.13f, -0.32f, 0.19f, -0.49f)
        reflectiveCurveToRelative(0.11f, -0.34f, 0.16f, -0.52f)
        reflectiveCurveToRelative(0.09f, -0.37f, 0.13f, -0.56f)
        arcToRelative(5.81f, 5.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.09f, -0.58f)
        arcToRelative(9.71f, 9.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -1.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(233.83f, 171.44f)
        verticalLineTo(171f)
        curveToRelative(0f, -0.15f, 0f, -0.29f, 0f, -0.43f)
        reflectiveCurveToRelative(0f, -0.55f, 0f, -0.82f)
        reflectiveCurveToRelative(0f, -0.47f, 0f, -0.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(299.35f, 261.53f)
        horizontalLineToRelative(7.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.81f, 261.54f)
        horizontalLineToRelative(-7.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(298.94f, 261.54f)
        horizontalLineToRelative(-9.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.62f, 261.54f)
        horizontalLineTo(254.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.46f, 255.3f)
        horizontalLineToRelative(3.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 255.3f)
        horizontalLineToRelative(12.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.67f, 257.53f)
        curveToRelative(0f, -0.15f, 0f, -0.3f, 0f, -0.44f)
        verticalLineToRelative(-0.42f)
        curveToRelative(0f, -0.14f, 0f, -0.26f, 0f, -0.39f)
        verticalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(298.94f, 261.54f)
        lineToRelative(-1.46f, -0.06f)
        lineToRelative(-1.38f, -0.05f)
        lineToRelative(-2.48f, -0.08f)
        curveToRelative(-1.53f, 0f, -2.8f, -0.06f, -3.82f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(298.87f, 257.53f)
        curveToRelative(0f, 0.29f, 0f, 0.59f, 0f, 0.89f)
        lineToRelative(0.06f, 3.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 261.08f)
        lineToRelative(0.32f, 0f)
        lineToRelative(0.81f, 0f)
        lineToRelative(0.42f, 0f)
        lineToRelative(0.42f, 0f)
        quadToRelative(3.45f, -0.17f, 6f, -0.24f)
        curveToRelative(1.71f, -0.05f, 3.14f, -0.08f, 4.28f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.79f, 257.53f)
        horizontalLineTo(254.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.21f, 257.53f)
        quadToRelative(0f, -0.57f, 0f, -1.14f)
        curveToRelative(0f, -0.19f, 0f, -0.38f, 0.06f, -0.56f)
        arcToRelative(2.33f, 2.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.28f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, -0.19f)
        reflectiveCurveToRelative(0f, -0.06f, 0.05f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(298.87f, 257.53f)
        arcToRelative(9.18f, 9.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -1.17f)
        arcToRelative(5.4f, 5.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, -0.58f)
        arcToRelative(1.52f, 1.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.28f)
        arcToRelative(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.15f)
        arcToRelative(0.14f, 0.14f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(289.6f, 255.3f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.06f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.2f)
        arcToRelative(2.51f, 2.51f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.27f)
        curveToRelative(0f, 0.19f, 0f, 0.38f, 0f, 0.57f)
        curveToRelative(0f, 0.37f, 0f, 0.75f, 0f, 1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.79f, 257.53f)
        horizontalLineToRelative(-7.92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.95f, 253.87f)
        lineTo(250.1f, 253.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(254.35f, 253.87f)
        lineTo(272.37f, 253.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(246.95f, 255.3f)
        lineTo(246.95f, 253.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(272.37f, 253.87f)
        lineTo(272.37f, 255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(239.29f, 253.88f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.21f, 254.44f)
        verticalLineToRelative(-0.54f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(239.27f, 255.35f)
        lineTo(239.29f, 254.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.21f, 254.44f)
        arcToRelative(0.81f, 0.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, 0.06f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, 0.05f)
        lineToRelative(0.13f, 0.1f)
        lineToRelative(0.06f, 0.07f)
        lineToRelative(0f, 0.06f)
        arcToRelative(1.09f, 1.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, 0.15f)
        arcToRelative(0.82f, 0.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.07f, 0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.37f, 319.13f)
        arcToRelative(5.9f, 5.9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, -0.66f)
        arcToRelative(3.87f, 3.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.92f, 318f)
        arcToRelative(5.42f, 5.42f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.15f, 1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.57f, 317.92f)
        arcToRelative(10.85f, 10.85f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9.3f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.56f, 319.65f)
        arcToRelative(3.73f, 3.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.15f, -0.94f)
        arcToRelative(1.84f, 1.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.41f)
        arcToRelative(1.68f, 1.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, -0.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.87f, 317.92f)
        arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0.38f)
        arcToRelative(1.84f, 1.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.41f)
        arcToRelative(3.73f, 3.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.15f, 0.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.52f, 317.51f)
        arcToRelative(11.08f, 11.08f, 0f, isMoreThanHalf = true, isPositiveArc = true, -8.6f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(248.32f, 320f)
        arcToRelative(18.31f, 18.31f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.7f, 1.85f)
        quadToRelative(0.42f, 0.92f, 0.93f, 1.86f)
        curveToRelative(0.34f, 0.63f, 0.7f, 1.27f, 1.08f, 1.9f)
        curveToRelative(0.19f, 0.32f, 0.38f, 0.64f, 0.58f, 0.95f)
        reflectiveCurveToRelative(0.19f, 0.32f, 0.29f, 0.48f)
        lineToRelative(0.15f, 0.24f)
        lineToRelative(0.09f, 0.11f)
        lineToRelative(0.05f, 0f)
        horizontalLineToRelative(0.06f)
        arcToRelative(0.1f, 0.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, 0f)
        lineToRelative(0f, -0.06f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.11f)
        lineToRelative(0.15f, -0.24f)
        curveToRelative(0.09f, -0.16f, 0.19f, -0.31f, 0.29f, -0.47f)
        lineToRelative(0.57f, -1f)
        curveToRelative(0.37f, -0.63f, 0.74f, -1.26f, 1.07f, -1.88f)
        reflectiveCurveToRelative(0.65f, -1.25f, 0.92f, -1.86f)
        curveToRelative(0.14f, -0.31f, 0.27f, -0.62f, 0.38f, -0.93f)
        reflectiveCurveToRelative(0.22f, -0.6f, 0.31f, -0.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.63f, 320.45f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.51f, -0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(237.69f, 253.93f)
        arcToRelative(1.36f, 1.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.49f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(185.23f, 253.32f)
        verticalLineToRelative(-0.44f)
        curveToRelative(0f, -0.14f, 0f, -0.29f, 0f, -0.42f)
        reflectiveCurveToRelative(0f, -0.52f, 0f, -0.73f)
        reflectiveCurveToRelative(0f, -0.38f, 0f, -0.5f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.13f)
        arcToRelative(0.06f, 0.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.41f, 253.32f)
        lineToRelative(0.38f, 0f)
        lineToRelative(0.18f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.09f, 253.22f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.22f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.23f)
        curveToRelative(0f, 0.15f, 0f, 0.3f, 0f, 0.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.35f, 253.32f)
        lineTo(238.09f, 253.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 254.62f)
        lineTo(185.22f, 254.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(194.32f, 254f)
        curveToRelative(-1f, 0f, -5.72f, -0.05f, -6.75f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.23f, 254.91f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.22f, 254.62f)
        lineTo(238.21f, 253.91f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.66f, 254.58f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, 0.36f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.43f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.23f, 254.91f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.21f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0f)
        lineToRelative(-0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.7f, 253.32f)
        lineTo(185.23f, 253.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.7f, 253.32f)
        arcToRelative(1.45f, 1.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.52f, -0.6f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, -0.17f)
        arcToRelative(1.34f, 1.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.27f, 252.49f)
        curveToRelative(0f, -0.21f, 0f, -0.4f, 0f, -0.58f)
        reflectiveCurveToRelative(0f, -0.33f, -0.06f, -0.46f)
        reflectiveCurveToRelative(0f, -0.22f, 0f, -0.29f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.7f, 251.06f)
        arcToRelative(0f, 0f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0f)
        arcToRelative(0.19f, 0.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.08f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.29f)
        arcToRelative(10.21f, 10.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, 1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.7f, 181.19f)
        horizontalLineTo(212.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(207.22f, 171.48f)
        horizontalLineToRelative(5.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.14f, 178.72f)
        lineTo(234.14f, 176.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.14f, 176.35f)
        lineTo(228.98f, 176.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(227.12f, 175.12f)
        lineTo(234.07f, 175.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.81f, 252.49f)
        arcToRelative(1.39f, 1.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, 0.06f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, 0.17f)
        arcToRelative(1.48f, 1.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.3f, 0.26f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.08f, 251.62f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.38f, 0.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.1f)
        reflectiveCurveToRelative(0f, 0.08f, 0f, 0.14f)
        reflectiveCurveToRelative(0f, 0.13f, 0f, 0.2f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.24f)
        curveToRelative(0f, 0.09f, 0f, 0.18f, 0f, 0.28f)
        reflectiveCurveToRelative(0f, 0.41f, 0f, 0.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(241.41f, 253.32f)
        curveToRelative(0f, -0.3f, 0f, -0.6f, 0f, -0.87f)
        reflectiveCurveToRelative(0f, -0.26f, 0f, -0.39f)
        reflectiveCurveToRelative(0f, -0.23f, 0f, -0.34f)
        arcToRelative(2.3f, 2.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.28f)
        curveToRelative(0f, -0.08f, 0f, -0.15f, 0f, -0.21f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.13f)
        arcToRelative(0.06f, 0.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.07f, 181.19f)
        horizontalLineToRelative(21.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.87f, 178.57f)
        horizontalLineTo(192.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217.06f, 172.82f)
        horizontalLineTo(206.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(216.48f, 171.23f)
        curveToRelative(0f, -0.31f, -0.06f, -0.61f, -0.11f, -0.9f)
        reflectiveCurveToRelative(-0.1f, -0.59f, -0.16f, -0.86f)
        arcToRelative(7.12f, 7.12f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.21f, -0.77f)
        arcToRelative(4.39f, 4.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.27f, -0.65f)
        arcToRelative(1.75f, 1.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, -0.27f)
        curveToRelative(0f, -0.09f, -0.11f, -0.16f, -0.16f, -0.24f)
        arcToRelative(2.46f, 2.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.38f, -0.35f)
        arcToRelative(1.49f, 1.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.45f, -0.19f)
        lineToRelative(-0.26f, 0f)
        horizontalLineTo(214f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(214f, 167f)
        horizontalLineToRelative(0.33f)
        lineToRelative(0.29f, 0f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.52f, 0.19f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.44f, 0.34f)
        arcToRelative(3.41f, 3.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.37f, 0.5f)
        arcToRelative(4.24f, 4.24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.3f, 0.64f)
        arcToRelative(5.64f, 5.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, 0.76f)
        curveToRelative(0.07f, 0.28f, 0.14f, 0.57f, 0.19f, 0.86f)
        reflectiveCurveToRelative(0.09f, 0.6f, 0.12f, 0.91f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(217.21f, 172.2f)
        curveToRelative(0.86f, 0.2f, 1.71f, 0.42f, 2.56f, 0.66f)
        reflectiveCurveToRelative(1.68f, 0.49f, 2.52f, 0.75f)
        lineToRelative(2.47f, 0.76f)
        lineToRelative(1.2f, 0.38f)
        lineToRelative(1.16f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(226.9f, 176.14f)
        lineToRelative(-1.13f, -0.41f)
        lineToRelative(-1.18f, -0.43f)
        lineToRelative(-2.46f, -0.87f)
        curveToRelative(-0.83f, -0.29f, -1.66f, -0.57f, -2.51f, -0.84f)
        reflectiveCurveToRelative(-1.69f, -0.54f, -2.56f, -0.77f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(229f, 176.35f)
        lineToRelative(4.11f, 1.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.87f, 178.57f)
        lineToRelative(-1f, -0.43f)
        lineToRelative(-3.07f, -1.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(232.87f, 178.57f)
        curveToRelative(0.08f, -0.17f, 0.15f, -0.35f, 0.22f, -0.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(233.75f, 179.13f)
        arcToRelative(2.81f, 2.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.88f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.21f, 255.34f)
        arcToRelative(0.43f, 0.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.24f, 0.38f)
        verticalLineToRelative(1.56f)
        arcToRelative(2.84f, 2.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.84f, 2.84f)
        horizontalLineToRelative(0.57f)
        verticalLineToRelative(13.4f)
        arcToRelative(14.17f, 14.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.67f, 11.34f)
        verticalLineTo(394.2f)
        arcToRelative(5.67f, 5.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.67f, 5.67f)
        horizontalLineTo(301.19f)
        arcToRelative(5.67f, 5.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.67f, -5.67f)
        verticalLineTo(366.46f)
        curveToRelative(0f, -0.17f, 0f, -0.33f, 0f, -0.5f)
        lineToRelative(1.44f, 0f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.28f, -0.29f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-18f)
        horizontalLineToRelative(0f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.28f, -0.28f)
        lineToRelative(-1.44f, 0f)
        curveToRelative(0f, -0.17f, 0f, -0.33f, 0f, -0.5f)
        verticalLineTo(261.54f)
        curveToRelative(0f, -0.28f, 0f, -0.55f, 0f, -0.82f)
        lineToRelative(0f, -2.19f)
        curveToRelative(0f, -0.33f, 0f, -0.67f, 0f, -1f)
        arcToRelative(2.26f, 2.26f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.26f, -2.23f)
        horizontalLineToRelative(-31f)
        lineToRelative(0f, -1.43f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.56f, -0.55f)
        horizontalLineTo(246.42f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.56f, 0.55f)
        lineToRelative(0f, 1.43f)
        horizontalLineToRelative(-3.2f)
        verticalLineToRelative(0f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.2f, -0.68f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.42f, -0.46f)
        lineToRelative(-0.08f, 0f)
        lineToRelative(-0.22f, -0.11f)
        arcToRelative(1.05f, 1.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.24f, -0.06f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.26f, 0f)
        horizontalLineToRelative(-1.92f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.56f)
        curveToRelative(0f, 0.25f, 0f, 0.49f, 0f, 0.74f)
        horizontalLineTo(238f)
        lineToRelative(-0.73f, 0f)
        lineToRelative(-49.48f, 0f)
        lineToRelative(-2.57f, 0f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.57f, -0.54f)
        curveToRelative(0f, -0.43f, -0.05f, -0.87f, -0.1f, -1.3f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-0.21f)
        lineToRelative(-0.11f, 0.06f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, 0.08f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, 0.1f)
        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, 0.13f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.15f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(236.75f, 174.23f)
        arcToRelative(21.21f, 21.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 12.36f)
        verticalLineToRelative(58.79f)
        horizontalLineTo(248f)
        verticalLineToRelative(-0.56f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.42f, -1.42f)
        horizontalLineToRelative(0.67f)
        verticalLineTo(224.62f)
        curveToRelative(0f, -5.92f, 0f, -12.87f, -0.19f, -18.78f)
        arcToRelative(127.31f, 127.31f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.61f, -18.69f)
        arcToRelative(28.49f, 28.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, -8.87f)
        arcToRelative(9.48f, 9.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3.17f, -3.34f)
        arcToRelative(10.75f, 10.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4.49f, -1.39f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(256.53f, 317.99f)
        lineTo(256.5f, 315.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(247.92f, 317.99f)
        lineTo(247.94f, 315.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.63f, 255.3f)
        lineTo(242.32f, 255.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(245.83f, 255.3f)
        horizontalLineToRelative(0.63f)
    }
}
private fun ImageVector.Builder.addAaruyPaths7() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(266.77f, 255.3f)
        horizontalLineToRelative(6.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.84f, 347.32f)
        curveToRelative(0f, 1.21f, 0f, 2.43f, 0f, 3.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(306.84f, 366f)
        curveToRelative(0f, -1.22f, 0f, -2.43f, 0f, -3.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(190.17f, 282.73f)
        curveToRelative(0.4f, 0.38f, 1f, 0.84f, 1.14f, 1.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.4f, 255.3f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.19f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(188.23f, 250.86f)
        lineToRelative(-0.53f, -0.21f)
        lineToRelative(-0.26f, -0.11f)
        lineToRelative(-0.13f, 0f)
        lineToRelative(-0.11f, 0f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(191.07f, 243f)
        reflectiveCurveToRelative(-0.33f, 0f, -0.38f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(234.07f, 175.12f)
        curveToRelative(0.37f, 0f, 0.65f, 0.08f, 0.65f, 0.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189.62f, 251f)
        arcToRelative(1.24f, 1.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.33f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(189.62f, 251f)
        lineToRelative(0.21f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(184.39f, 255.3f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, -0.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(238.71f, 255.3f)
        lineTo(238.71f, 255.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(187.57f, 254.62f)
        lineTo(238.22f, 254.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(242.47f, 252.95f)
        lineTo(242.47f, 254.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(274.22f, 252.18f)
        lineTo(274.22f, 252.11f)
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(282.09f, 272.22f)
        arcToRelative(3.25f, 3.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.61f, 2f)
        arcToRelative(2.21f, 2.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.79f, 0.83f)
        arcToRelative(2.61f, 2.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -0.87f)
        lineToRelative(0.09f, 0.88f)
        horizontalLineToRelative(1.28f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        curveToRelative(0f, -0.16f, 0f, -0.32f, 0f, -0.47f)
        curveToRelative(0f, -0.63f, 0f, -1.26f, 0f, -1.89f)
        lineToRelative(0f, -3.77f)
        horizontalLineToRelative(-1.43f)
        lineToRelative(0f, 3.21f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, 1.63f)
        arcToRelative(1.28f, 1.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.15f, -0.59f)
        arcToRelative(2.28f, 2.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, -1.38f)
        curveToRelative(0f, -1f, 0f, -1.91f, 0f, -2.87f)
        horizontalLineToRelative(-1.44f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(276.87f, 275.06f)
        horizontalLineToRelative(1.44f)
        verticalLineToRelative(-3.25f)
        arcToRelative(1.35f, 1.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.46f, -1.48f)
        arcToRelative(1.47f, 1.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.39f)
        lineToRelative(0.32f, -0.63f)
        curveToRelative(0.11f, -0.2f, 0.23f, -0.41f, 0.33f, -0.62f)
        arcToRelative(2.24f, 2.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.5f, -0.56f)
        arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.65f, 0.83f)
        curveToRelative(0f, -0.28f, -0.07f, -0.56f, -0.1f, -0.83f)
        horizontalLineToRelative(-1.34f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(292.3f, 271.65f)
        curveToRelative(-0.2f, 0.54f, -0.41f, 1.07f, -0.62f, 1.61f)
        lineToRelative(-0.54f, -1.65f)
        curveToRelative(-0.28f, -0.89f, -0.57f, -1.78f, -0.85f, -2.67f)
        horizontalLineTo(288.6f)
        quadToRelative(1.14f, 2.94f, 2.27f, 5.88f)
        lineToRelative(-1.11f, 2.52f)
        horizontalLineToRelative(1.58f)
        lineToRelative(2.17f, -5f)
        curveToRelative(0.37f, -0.83f, 0.73f, -1.66f, 1.09f, -2.49f)
        lineToRelative(0.33f, -0.76f)
        lineTo(295f, 269f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(-1.6f)
        curveTo(293f, 269.85f, 292.67f, 270.75f, 292.3f, 271.65f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(275.7f, 271.23f)
        verticalLineToRelative(-2.31f)
        horizontalLineToRelative(-1.38f)
        curveToRelative(0f, 0.31f, 0f, 0.63f, -0.05f, 0.95f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.86f, -1f)
        arcToRelative(3.08f, 3.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 6.15f)
        arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.88f, -1f)
        lineToRelative(0.07f, 1f)
        horizontalLineToRelative(1.36f)
        curveTo(275.71f, 273.79f, 275.71f, 272.51f, 275.7f, 271.23f)
        close()
        moveTo(273.77f, 273.23f)
        arcToRelative(1.62f, 1.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.21f, 0.47f)
        arcToRelative(1.67f, 1.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.73f, -1.77f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.71f, -1.76f)
        arcToRelative(1.67f, 1.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.22f, 0.48f)
        arcToRelative(1.87f, 1.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.49f, 1.29f)
        arcTo(1.89f, 1.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 273.77f, 273.26f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(268.52f, 268.9f)
        horizontalLineToRelative(-1.38f)
        curveToRelative(0f, 0.32f, 0f, 0.64f, 0f, 0.95f)
        arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.87f, -0.95f)
        arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, 3.05f)
        arcToRelative(2.85f, 2.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 3.1f)
        arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.88f, -1f)
        curveToRelative(0f, 0.34f, 0.05f, 0.68f, 0.08f, 1f)
        horizontalLineToRelative(1.35f)
        curveTo(268.55f, 273f, 268.53f, 271f, 268.52f, 268.9f)
        close()
        moveTo(266.61f, 273.24f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.21f, 0.48f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.74f, -1.78f)
        arcToRelative(1.73f, 1.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.92f, -1.28f)
        arcToRelative(1.86f, 1.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.51f, 1.28f)
        arcTo(1.9f, 1.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 266.61f, 273.24f)
        close()
    }
    path(
        fill = SolidColor(Color(0xFFFFFEF5)),
        stroke = SolidColor(Color(0xFF030000)),
        strokeLineWidth = 0.2f,
        strokeLineJoin = StrokeJoin.Bevel
    ) {
        moveTo(38.53f, 170.69f)
        lineToRelative(0.9f, 0.3f)
        lineToRelative(1.17f, 0.38f)
        lineToRelative(1.15f, 0.39f)
        lineToRelative(4.63f, 1.54f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.42f)
        arcToRelative(6.41f, 6.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.94f, 0.58f)
        arcToRelative(7.06f, 7.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.83f, 0.71f)
        curveToRelative(0.13f, 0.13f, 0.25f, 0.27f, 0.37f, 0.41f)
        reflectiveCurveToRelative(0.23f, 0.28f, 0.34f, 0.43f)
        arcToRelative(8.11f, 8.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.57f, 0.94f)
        curveToRelative(0.08f, 0.16f, 0.15f, 0.33f, 0.22f, 0.49f)
        arcToRelative(5.44f, 5.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.19f, 0.52f)
        arcToRelative(7.46f, 7.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, 2.16f)
        verticalLineToRelative(63.89f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.24f, 0.56f)
        lineToRelative(6.27f, 6.05f)
        lineToRelative(0.17f, 0f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.12f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, 0.33f)
        lineToRelative(1.1f, 1.07f)
        horizontalLineToRelative(3.34f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.42f, -1.42f)
        verticalLineToRelative(-6.38f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.42f, -1.41f)
        horizontalLineTo(86.1f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.41f, 1.41f)
        verticalLineTo(249f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(1.16f)
        arcToRelative(1.67f, 1.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.11f, 0.48f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.3f, 0.46f)
        arcToRelative(1.44f, 1.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.46f, 0.31f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.55f, 0.11f)
        horizontalLineToRelative(2.21f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0.21f, 0f)
        horizontalLineToRelative(0.13f)
        lineToRelative(0.41f, 0f)
        horizontalLineToRelative(28.16f)
        arcToRelative(5.65f, 5.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.1f)
        arcToRelative(4.43f, 4.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.49f, 0.13f)
        arcToRelative(3.06f, 3.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.48f, 0.17f)
        arcToRelative(3.82f, 3.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.46f, 0.21f)
        arcToRelative(3.87f, 3.87f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.43f, 0.27f)
        arcToRelative(4.43f, 4.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.79f, 0.64f)
        curveToRelative(0.12f, 0.12f, 0.23f, 0.24f, 0.34f, 0.37f)
        reflectiveCurveToRelative(0.21f, 0.27f, 0.3f, 0.41f)
        arcToRelative(5.48f, 5.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.48f, 0.89f)
        curveToRelative(0.06f, 0.16f, 0.12f, 0.32f, 0.17f, 0.48f)
        reflectiveCurveToRelative(0.09f, 0.33f, 0.13f, 0.49f)
        arcToRelative(6.64f, 6.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, 1f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        verticalLineTo(343f)
        curveToRelative(0f, 0.31f, 0f, 0.62f, 0f, 0.92f)
        reflectiveCurveToRelative(0f, 0.59f, 0f, 0.85f)
        curveToRelative(0f, 0.46f, 0f, 0.95f, 0f, 1.46f)
        reflectiveCurveToRelative(0f, 1f, 0f, 1.59f)
        curveToRelative(0f, 0.83f, 0f, 1.68f, 0f, 2.53f)
        verticalLineToRelative(11.34f)
        curveToRelative(0f, 0.85f, 0f, 1.7f, 0f, 2.53f)
        curveToRelative(0f, 0.54f, 0f, 1.07f, 0f, 1.59f)
        reflectiveCurveToRelative(0f, 1f, 0f, 1.46f)
        curveToRelative(0f, 0.26f, 0f, 0.54f, 0f, 0.84f)
        reflectiveCurveToRelative(0f, 0.62f, 0f, 0.93f)
        verticalLineToRelative(24.39f)
        arcToRelative(8.86f, 8.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.18f, 1.77f)
        arcToRelative(9.23f, 9.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.51f, 1.7f)
        arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.84f, 1.56f)
        arcToRelative(8.84f, 8.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.13f, 1.38f)
        arcToRelative(9.25f, 9.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.37f, 1.13f)
        arcToRelative(9.48f, 9.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.57f, 0.84f)
        arcToRelative(9.23f, 9.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.7f, 0.51f)
        arcToRelative(8.86f, 8.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.77f, 0.18f)
        horizontalLineTo(80f)
        lineToRelative(-0.17f, 0f)
        horizontalLineTo(57.58f)
        lineToRelative(-0.16f, 0f)
        horizontalLineTo(20.53f)
        arcToRelative(8.69f, 8.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.76f, -0.18f)
        arcToRelative(9.11f, 9.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.71f, -0.51f)
        arcTo(9.38f, 9.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.5f, 401f)
        arcToRelative(8.84f, 8.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.38f, -1.13f)
        arcToRelative(10.21f, 10.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.69f, -0.77f)
        arcToRelative(5.68f, 5.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, -4.25f, -5.49f)
        verticalLineTo(289.12f)
        lineToRelative(-2f, -4f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        lineTo(7.05f, 285f)
        lineToRelative(-0.12f, -0.18f)
        lineToRelative(-0.28f, -0.38f)
        curveToRelative(-0.21f, -0.27f, -0.4f, -0.55f, -0.59f, -0.85f)
        reflectiveCurveToRelative(-0.19f, -0.3f, -0.28f, -0.46f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.25f, -0.47f)
        curveToRelative(-0.26f, -0.51f, -0.52f, -1f, -0.77f, -1.51f)
        reflectiveCurveToRelative(-0.5f, -1f, -0.73f, -1.58f)
        curveToRelative(-0.11f, -0.28f, -0.22f, -0.56f, -0.32f, -0.84f)
        reflectiveCurveToRelative(-0.19f, -0.57f, -0.28f, -0.87f)
        arcToRelative(9.4f, 9.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.2f, -0.92f)
        arcToRelative(6.39f, 6.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, -1f)
        verticalLineTo(259.53f)
        horizontalLineTo(2.94f)
        arcTo(2.84f, 2.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, 256.69f)
        verticalLineToRelative(-1.55f)
        arcToRelative(0.42f, 0.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.24f, -0.38f)
        curveToRelative(0f, -0.59f, 0f, -1.17f, 0f, -1.75f)
        arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.15f)
        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.13f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.1f)
        lineToRelative(0.08f, -0.08f)
        lineToRelative(0.11f, -0.06f)
        verticalLineTo(251f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, -0.39f)
        arcTo(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 250.5f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0f)
        horizontalLineTo(3.11f)
        curveToRelative(0f, -0.05f, 0f, -0.1f, 0f, -0.15f)
        reflectiveCurveToRelative(0f, -0.2f, 0.05f, -0.3f)
        arcToRelative(2.47f, 2.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -0.29f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.14f, -0.29f)
        arcToRelative(1.09f, 1.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -0.15f)
        arcToRelative(5.12f, 5.12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.27f, -0.52f)
        curveToRelative(0.55f, -1.23f, 1.12f, -2.43f, 1.71f, -3.64f)
        curveToRelative(0.44f, -0.91f, 0.89f, -1.82f, 1.34f, -2.73f)
        lineToRelative(0f, 0f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.15f)
        verticalLineTo(175.57f)
        arcToRelative(4.83f, 4.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.83f, -4.83f)
        horizontalLineToRelative(7.23f)
        arcToRelative(3.05f, 3.05f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.6f, -0.06f)
    }
    path(
        fill = SolidColor(Color.White),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.8f, 58.55f)
        arcTo(57.4f, 57.4f, 0f, isMoreThanHalf = true, isPositiveArc = false, 24.16f, 94.07f)
        arcTo(57.4f, 57.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 125.51f, 63.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22.26f, 166.38f)
        horizontalLineTo(20.63f)
    }
    path(
        fill = SolidColor(Color(0xFFF5FBFE)),
        stroke = SolidColor(Color(0xFF231815)),
        strokeLineWidth = 0.2f
    ) {
        moveTo(0.68f, 251f)
        verticalLineToRelative(0f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, -0.39f)
        arcTo(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 250.5f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0f)
        horizontalLineTo(4.69f)
        lineToRelative(0.09f, -0.14f)
        arcToRelative(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.15f, -0.17f)
        arcToRelative(1.14f, 1.14f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.38f, -0.23f)
        arcToRelative(1.32f, 1.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.44f, -0.08f)
        horizontalLineTo(7.2f)
        verticalLineTo(180.6f)
        arcTo(2.83f, 2.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 178f)
        lineToRelative(7.13f, -3f)
        arcToRelative(2.87f, 2.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.4f, -0.18f)
        horizontalLineToRelative(0f)
        arcToRelative(5.65f, 5.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.86f, -3.44f)
        arcToRelative(4.65f, 4.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.16f, -0.72f)
        lineToRelative(0f, -0.09f)
        lineToRelative(0f, -0.27f)
        curveToRelative(0f, -0.09f, 0f, -0.18f, 0f, -0.27f)
        reflectiveCurveToRelative(0f, -0.39f, 0f, -0.22f)
        verticalLineToRelative(-3.45f)
        horizontalLineToRelative(-5f)
        lineToRelative(-0.09f, 0f)
        lineToRelative(-0.18f, -0.1f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, -0.34f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.11f, -0.67f)
        horizontalLineToRelative(0f)
        lineToRelative(0.21f, -12.19f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.16f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.14f, -0.24f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.19f, -0.12f)
        arcToRelative(0.31f, 0.31f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0f)
        horizontalLineToRelative(0.12f)
        verticalLineToRelative(-0.64f)
        arcTo(4.26f, 4.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19f, 147.67f)
        horizontalLineToRelative(6.22f)
        lineToRelative(1.61f, -3.26f)
        arcToRelative(14.07f, 14.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, -1.61f)
        curveToRelative(0.09f, -0.87f, 0.21f, -1.73f, 0.38f, -2.58f)
        arcToRelative(19.23f, 19.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.72f, -2.57f)
        arcToRelative(15.48f, 15.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.14f, -2.44f)
        arcTo(13.13f, 13.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 30.93f, 133f)
        arcToRelative(11.75f, 11.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.75f, -3.15f)
        arcToRelative(13.12f, 13.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, -0.66f)
        arcToRelative(14.35f, 14.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.34f, 0.09f)
        arcToRelative(16.7f, 16.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.55f, 0.92f)
        arcTo(20.9f, 20.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 49.26f, 132f)
        arcToRelative(24.48f, 24.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.66f, 2.83f)
        arcToRelative(30f, 30f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.55f, 3.92f)
        arcToRelative(37.94f, 37.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.28f, 5.11f)
        arcToRelative(48.46f, 48.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.88f, 6.41f)
        quadToRelative(2.61f, 7.14f, 5.43f, 14.18f)
        curveToRelative(1.88f, 4.71f, 3.81f, 9.38f, 5.83f, 14f)
        reflectiveCurveToRelative(4.13f, 9.25f, 6.36f, 13.8f)
        reflectiveCurveToRelative(4.57f, 9f, 7.1f, 13.42f)
        arcToRelative(147.37f, 147.37f, 0f, isMoreThanHalf = false, isPositiveArc = false, 8.16f, 12.8f)
        curveToRelative(1.48f, 2.06f, 3f, 4.06f, 4.67f, 6f)
        arcToRelative(63.12f, 63.12f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.23f, 5.5f)
        arcToRelative(38.3f, 38.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 4.63f)
        arcToRelative(20.19f, 20.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7f, 2.77f)
        arcToRelative(13f, 13f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.43f, -0.84f)
        arcToRelative(18.21f, 18.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6.09f, -4.47f)
        lineToRelative(0.33f, -0.37f)
        arcToRelative(28.27f, 28.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.1f, -2.64f)
        curveToRelative(0.71f, -1f, 1.36f, -2f, 2f, -3.09f)
        reflectiveCurveToRelative(1.27f, -2.31f, 1.84f, -3.51f)
        reflectiveCurveToRelative(1.16f, -2.58f, 1.68f, -3.9f)
        reflectiveCurveToRelative(1.06f, -2.82f, 1.53f, -4.26f)
        reflectiveCurveToRelative(0.94f, -3.05f, 1.35f, -4.59f)
        curveToRelative(0.88f, -3.3f, 1.59f, -6.66f, 2.17f, -10f)
        curveToRelative(0.62f, -3.63f, 1.08f, -7.29f, 1.41f, -11f)
        quadToRelative(0.53f, -5.81f, 0.62f, -11.65f)
        curveToRelative(0f, -2.27f, 0f, -4.53f, 0f, -6.8f)
        lineToRelative(4.27f, 1.43f)
        curveToRelative(0f, 1.81f, 0f, 3.63f, 0f, 5.44f)
        curveToRelative(-0.07f, 4f, -0.27f, 8f, -0.63f, 12f)
        quadToRelative(-0.51f, 5.67f, -1.46f, 11.3f)
        curveToRelative(-0.6f, 3.5f, -1.34f, 7f, -2.25f, 10.41f)
        curveToRelative(-0.43f, 1.61f, -0.9f, 3.22f, -1.42f, 4.8f)
        reflectiveCurveToRelative(-1f, 3f, -1.61f, 4.51f)
        reflectiveCurveToRelative(-1.15f, 2.8f, -1.8f, 4.17f)
        reflectiveCurveToRelative(-1.28f, 2.57f, -2f, 3.81f)
        reflectiveCurveToRelative(-1.41f, 2.32f, -2.19f, 3.43f)
        arcToRelative(36.38f, 36.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.4f, 3f)
        lineToRelative(-0.39f, 0.43f)
        arcToRelative(29.6f, 29.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.21f, 2.13f)
        arcTo(22f, 22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 130f, 239.2f)
        curveToRelative(-0.48f, 0.3f, -1f, 0.57f, -1.49f, 0.83f)
        reflectiveCurveToRelative(-1f, 0.48f, -1.56f, 0.69f)
        reflectiveCurveToRelative(-1.06f, 0.38f, -1.6f, 0.53f)
        arcToRelative(15.93f, 15.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.66f, 0.38f)
        arcToRelative(17.73f, 17.73f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3.42f, 0.24f)
        arcToRelative(15.24f, 15.24f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.78f, -0.14f)
        arcToRelative(17.29f, 17.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.75f, -0.3f)
        curveToRelative(-0.62f, -0.14f, -1.23f, -0.31f, -1.83f, -0.5f)
        reflectiveCurveToRelative(-1.19f, -0.41f, -1.78f, -0.65f)
        arcToRelative(28.47f, 28.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3.66f, -1.84f)
        quadToRelative(-1f, -0.58f, -1.92f, -1.23f)
        curveToRelative(-0.61f, -0.42f, -1.22f, -0.86f, -1.81f, -1.32f)
        reflectiveCurveToRelative(-1.31f, -1f, -2f, -1.6f)
        reflectiveCurveToRelative(-1.25f, -1.1f, -1.85f, -1.68f)
        curveToRelative(-1.35f, -1.28f, -2.64f, -2.63f, -3.87f, -4f)
        curveToRelative(-0.69f, -0.77f, -1.37f, -1.56f, -2f, -2.36f)
        reflectiveCurveToRelative(-1.3f, -1.61f, -1.93f, -2.43f)
        reflectiveCurveTo(92.74f, 222f, 92.07f, 221f)
        reflectiveCurveToRelative(-1.33f, -1.87f, -2f, -2.82f)
        lineTo(89f, 216.61f)
        lineTo(88f, 215f)
        curveToRelative(-0.69f, -1.08f, -1.36f, -2.16f, -2f, -3.24f)
        lineTo(84.89f, 210f)
        lineToRelative(-1.06f, -1.82f)
        curveToRelative(-0.71f, -1.22f, -1.39f, -2.44f, -2.07f, -3.67f)
        curveToRelative(-0.37f, -0.68f, -0.74f, -1.36f, -1.1f, -2f)
        reflectiveCurveToRelative(-0.73f, -1.36f, -1.08f, -2f)
        quadToRelative(-1.08f, -2.05f, -2.11f, -4.12f)
        curveToRelative(-0.38f, -0.75f, -0.75f, -1.51f, -1.12f, -2.27f)
        lineToRelative(-1.11f, -2.28f)
        quadToRelative(-1.08f, -2.28f, -2.15f, -4.57f)
        lineTo(72f, 184.64f)
        lineToRelative(-1.12f, -2.52f)
        quadToRelative(-1.11f, -2.52f, -2.19f, -5.05f)
        lineToRelative(-1.32f, -3.14f)
        curveToRelative(-0.44f, -1.05f, -0.87f, -2.1f, -1.3f, -3.15f)
        quadToRelative(-1.29f, -3.15f, -2.55f, -6.31f)
        quadToRelative(-2.49f, -6.33f, -4.84f, -12.69f)
        arcTo(43.28f, 43.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 56f, 145.92f)
        arcToRelative(32.88f, 32.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.92f, -4.55f)
        arcTo(25.8f, 25.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 50f, 138f)
        arcToRelative(20.29f, 20.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, -2.35f)
        arcToRelative(16.08f, 16.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.9f, -1.46f)
        arcToRelative(12.76f, 12.76f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.65f, -0.69f)
        arcToRelative(10.17f, 10.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.34f, -0.07f)
        arcToRelative(8.7f, 8.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 0.44f)
        arcToRelative(7.47f, 7.47f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.67f, 0.84f)
        arcToRelative(7.64f, 7.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.37f, 1.17f)
        arcTo(8.76f, 8.76f, 0f, isMoreThanHalf = false, isPositiveArc = false, 33f, 137.36f)
        arcToRelative(10.71f, 10.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.81f, 1.75f)
        arcToRelative(12.43f, 12.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.55f, 2f)
        arcToRelative(17.36f, 17.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, 2.13f)
        quadToRelative(-0.06f, 0.63f, -0.09f, 1.26f)
        lineToRelative(1.59f, 3.21f)
        horizontalLineTo(39f)
        arcToRelative(4.25f, 4.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.25f, 4.18f)
        curveToRelative(0f, 0.21f, 0f, 0.43f, 0f, 0.64f)
        horizontalLineToRelative(0.16f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.12f)
        arcTo(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 44f, 153f)
        quadToRelative(0.11f, 6.11f, 0.22f, 12.2f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.12f, 0.67f)
        horizontalLineToRelative(0f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, 0.34f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.18f, 0.1f)
        lineToRelative(-0.09f, 0f)
        horizontalLineTo(38.46f)
        verticalLineToRelative(3.67f)
        arcToRelative(2.45f, 2.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.27f)
        lineToRelative(0f, 0.27f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.1f)
        arcToRelative(4.29f, 4.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.16f, 0.71f)
        arcToRelative(5.69f, 5.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.72f, 1.54f)
        arcTo(5.61f, 5.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 42f, 175f)
        lineToRelative(7.14f, 3f)
        arcToRelative(2.83f, 2.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.75f, 2.62f)
        verticalLineToRelative(69.86f)
        horizontalLineTo(57.7f)
        lineToRelative(0.17f, 0f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.12f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.17f, 0.33f)
        verticalLineToRelative(1.74f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.08f)
        curveToRelative(0f, 0.06f, 0f, 0.14f, 0f, 0.26f)
        reflectiveCurveToRelative(0f, 0.3f, 0f, 0.5f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.22f, -0.11f)
        lineToRelative(-0.24f, -0.06f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.26f, 0f)
        horizontalLineTo(55.42f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, 0.56f)
        verticalLineToRelative(0.74f)
        horizontalLineToRelative(-0.71f)
        lineToRelative(-0.73f, 0f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.13f, 0f)
        horizontalLineTo(4.78f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, 0f)
        lineToRelative(-0.72f, 0f)
        lineToRelative(-2.57f, 0f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.41f, -0.17f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, -0.4f)
        curveToRelative(0f, -0.41f, -0.05f, -0.74f, -0.07f, -1f)
        lineToRelative(0f, -0.36f)
        verticalLineTo(251f)
        close()
    }
    path(
        fill = SolidColor(Color(0xFFF5FBFE)),
        stroke = SolidColor(Color(0xFF231815)),
        strokeLineWidth = 0.2f
    ) {
        moveTo(108.54f, 61.8f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.39f, 0.1f)
        horizontalLineToRelative(-4.6f)
        arcToRelative(36.58f, 36.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -18.76f, 27f)
        curveToRelative(-0.66f, 0.34f, -0.78f, 1.33f, -1.45f, 1.63f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.54f, 0.13f)
        horizontalLineToRelative(0f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.3f, 0f)
        lineToRelative(-0.14f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.09f, 0f)
        lineTo(82f, 90.44f)
        lineToRelative(-0.12f, -0.09f)
        lineToRelative(-0.12f, -0.09f)
        lineToRelative(-0.12f, -0.09f)
        lineToRelative(-0.12f, -0.09f)
        lineTo(81.43f, 90f)
        lineToRelative(-0.09f, 0f)
        lineToRelative(-0.18f, -0.07f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.35f, 0f)
        horizontalLineToRelative(-0.25f)
        arcToRelative(1.67f, 1.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, 0.08f)
        arcToRelative(1.11f, 1.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.27f, 0.13f)
        arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.2f, 0.15f)
        arcToRelative(1.38f, 1.38f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.23f, 0.26f)
        arcToRelative(1.62f, 1.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, 0.18f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        reflectiveCurveToRelative(0f, 0.1f, 0f, 0.14f)
        lineToRelative(0f, 0.15f)
        lineToRelative(0f, 0.15f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.15f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.08f, 0.12f)
        arcToRelative(1.51f, 1.51f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.21f, 0.23f)
        lineToRelative(0f, 0f)
        arcToRelative(1.47f, 1.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.48f, 0.25f)
        arcToRelative(1.29f, 1.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.55f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.15f, 0f)
        lineToRelative(-0.14f, -0.05f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, -0.06f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.08f, -0.06f)
        lineToRelative(-0.12f, -0.09f)
        lineTo(76.94f, 92f)
        lineToRelative(-0.11f, -0.11f)
        lineToRelative(-0.1f, -0.11f)
        lineToRelative(-0.11f, -0.1f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, -0.1f)
        lineToRelative(-0.08f, -0.06f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.1f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.34f, -0.1f)
        lineToRelative(-0.25f, 0f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, 0f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.29f, 0.09f)
        arcToRelative(0.94f, 0.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, 0.12f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.27f, 0.23f)
        arcToRelative(0.79f, 0.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.12f, 0.15f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, 0.13f)
        reflectiveCurveToRelative(0f, 0.1f, 0f, 0.14f)
        arcToRelative(0.88f, 0.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.15f)
        lineToRelative(-0.06f, 0.14f)
        lineToRelative(-0.06f, 0.14f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0.13f)
        arcToRelative(0.35f, 0.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.08f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.1f, 0.11f)
        arcToRelative(0.88f, 0.88f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, 0.19f)
        lineToRelative(0f, 0f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.52f, 0.18f)
        arcToRelative(2.55f, 2.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.42f, -0.51f)
        arcToRelative(2.35f, 2.35f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.23f, -0.08f)
        horizontalLineToRelative(-0.07f)
        arcToRelative(1.26f, 1.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0.05f)
        quadToRelative(-1.12f, 0f, -2.25f, 0f)
        arcTo(35.42f, 35.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 33f, 59.47f)
        curveToRelative(0f, -0.17f, 0f, -0.34f, 0f, -0.53f)
        reflectiveCurveToRelative(0f, -0.44f, 0f, -0.68f)
        verticalLineToRelative(-0.17f)
        curveToRelative(0f, -0.19f, 0f, -0.39f, 0f, -0.59f)
        reflectiveCurveToRelative(0f, -0.2f, 0f, -0.3f)
        verticalLineToRelative(-0.47f)
        curveToRelative(0f, -0.24f, 0f, -0.46f, 0f, -0.67f)
        arcToRelative(4.41f, 4.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.52f)
        arcToRelative(35.44f, 35.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 34.8f, -33.47f)
        curveToRelative(0.75f, 0f, 1.5f, 0f, 2.25f, 0f)
        arcToRelative(1.17f, 1.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.27f, 0f)
        horizontalLineToRelative(0.08f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.14f, 0f)
        arcToRelative(3.79f, 3.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.48f, -0.29f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, -0.27f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.52f, 0.18f)
        lineToRelative(0f, 0f)
        arcToRelative(1.12f, 1.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.24f, 0.19f)
        lineToRelative(0.1f, 0.11f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0.08f)
        arcToRelative(0.91f, 0.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.07f, 0.14f)
        lineToRelative(0.06f, 0.14f)
        lineToRelative(0.06f, 0.14f)
        arcToRelative(1.21f, 1.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.14f)
        lineToRelative(0f, 0.14f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, 0.13f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.12f, 0.16f)
        arcToRelative(1.44f, 1.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.27f, 0.22f)
        lineToRelative(0.22f, 0.13f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, 0.09f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.3f, 0f)
        arcToRelative(1.07f, 1.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, 0f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.34f, -0.1f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.17f, -0.1f)
        arcToRelative(0.21f, 0.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -0.06f)
        lineToRelative(0.11f, -0.09f)
        lineToRelative(0.11f, -0.11f)
        lineToRelative(0.1f, -0.11f)
        lineToRelative(0.11f, -0.11f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, -0.1f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, -0.1f)
        lineToRelative(0.08f, -0.05f)
        lineToRelative(0f, 0f)
        lineToRelative(0.14f, -0.06f)
        lineToRelative(0.14f, -0.05f)
        lineToRelative(0.15f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.55f, 0f)
        arcToRelative(1.32f, 1.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.48f, 0.26f)
        lineToRelative(0f, 0f)
        arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, 0.22f)
        lineToRelative(0.08f, 0.13f)
        lineToRelative(0f, 0f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        arcToRelative(1.950166E12f, 1.950166E12f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, 0.3f)
        lineToRelative(0f, 0.14f)
        lineToRelative(0f, 0.15f)
        curveToRelative(0f, 0.05f, 0f, 0.1f, 0f, 0.14f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.1f, 0.18f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.23f, 0.26f)
        lineTo(80f, 25f)
        lineToRelative(0.27f, 0.13f)
        arcToRelative(1.63f, 1.63f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.3f, 0.07f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, 0f)
        arcToRelative(1.06f, 1.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, -0.05f)
        arcToRelative(0.67f, 0.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, -0.07f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.13f, -0.08f)
        lineToRelative(0.12f, -0.09f)
        lineToRelative(0.12f, -0.1f)
        lineToRelative(0.12f, -0.09f)
        lineToRelative(0.12f, -0.09f)
        lineToRelative(0.13f, -0.07f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.14f, -0.05f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.3f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(1.25f, 1.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.54f, 0.12f)
        lineToRelative(0.42f, 0.36f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.33f, 0.45f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.7f, 0.83f)
        arcToRelative(35.81f, 35.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.76f, 27f)
        horizontalLineToRelative(4.6f)
        arcToRelative(0.86f, 0.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.47f, 0.14f)
        arcToRelative(30.2f, 30.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.32f, 2.14f)
        arcToRelative(34.3f, 34.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.14f, 0.7f)
        arcToRelative(24.89f, 24.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.72f, 2.46f)
        arcTo(25.94f, 25.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 134.23f, 66f)
        arcToRelative(45f, 45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.45f, 9.92f)
        arcToRelative(83.71f, 83.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.51f, 10.78f)
        curveToRelative(1f, 3.67f, 1.74f, 7.37f, 2.4f, 11.1f)
        curveToRelative(1.31f, 7.34f, 2.23f, 15.08f, 2.9f, 22.5f)
        reflectiveCurveToRelative(1.12f, 15.1f, 1.49f, 22.66f)
        lineToRelative(-4.33f, -1.45f)
        curveToRelative(-0.67f, -13.4f, -1.69f, -27.89f, -3.93f, -41.17f)
        curveToRelative(-0.57f, -3.4f, -1.24f, -6.78f, -2f, -10.14f)
        arcToRelative(92.26f, 92.26f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.89f, -9.92f)
        arcToRelative(50.12f, 50.12f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4.35f, -9.37f)
        arcToRelative(25f, 25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -6.93f, -7.58f)
        arcTo(20.46f, 20.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 119f, 60.44f)
        arcToRelative(29.24f, 29.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -7.08f, -0.8f)
        arcTo(28.82f, 28.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 108.54f, 61.8f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(101.88f, 61.9f)
        lineTo(103.55f, 61.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.35f, 22.15f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.53f, 0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.36f, 54.11f)
        arcTo(2.7f, 2.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 33f, 55.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33f, 59.47f)
        arcToRelative(2.68f, 2.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.33f, 1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 92.29f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.54f, 0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.59f, 40.46f)
        arcToRelative(10.34f, 10.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.76f, 3.11f)
        arcToRelative(9.3f, 9.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, 3.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 34.18f)
        lineTo(58.63f, 34.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 37.01f)
        lineTo(56.5f, 37.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 38.86f)
        lineTo(58.63f, 32.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 38.86f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.43f, 0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 82.81f)
        lineTo(58.63f, 76.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 76.14f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.43f, -0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 77.99f)
        lineTo(58.63f, 77.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 80.83f)
        lineTo(58.51f, 80.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(149.98f, 142.98f)
        lineTo(163.54f, 147.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(129.52f, 136.16f)
        lineTo(145.65f, 141.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(151.21f, 171.74f)
        lineTo(163.54f, 175.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(129.52f, 164.51f)
        lineTo(146.94f, 170.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.65f, 165.91f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.15f, 166.32f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.26f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.09f, 165.91f)
        horizontalLineToRelative(0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 250.41f)
        lineTo(7.2f, 249.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.58f, 250.47f)
        lineTo(4.69f, 250.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.84f, 254.59f)
        lineTo(54.84f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.1f, 252.65f)
        lineTo(58.1f, 253.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.53f, 254.71f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.19f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 250.6f)
        lineTo(64.08f, 250.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(91.08f, 251.57f)
        lineToRelative(0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 284.27f)
        lineTo(46.21f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(26.81f, 144.39f)
        lineTo(31.21f, 144.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.79f, 88.9f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.07f, -0.15f)
        lineToRelative(-0.07f, -0.11f)
        lineToRelative(-0.07f, -0.15f)
        lineToRelative(0f, -0.09f)
        lineToRelative(-0.05f, -0.1f)
        lineToRelative(0f, -0.1f)
        lineToRelative(-0.06f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.6f, 92.25f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.12f, 90f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.28f, 80.83f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.07f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.09f)
        arcToRelative(0.91f, 0.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        curveToRelative(0f, 0.11f, 0f, 0.22f, 0f, 0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 81.5f)
        arcToRelative(4.13f, 4.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.82f, -1f)
        arcToRelative(4.24f, 4.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.4f, -2.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.39f, 29.91f)
        curveToRelative(0f, 0.9f, 0f, 1.81f, 0f, 2.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 37.16f)
        arcToRelative(4.52f, 4.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.59f, 37.8f)
        curveToRelative(0f, 0.36f, 0f, 0.61f, 0f, 0.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 76.44f)
        curveToRelative(0f, 0.25f, 0f, 0.51f, 0f, 0.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 77.86f)
        arcToRelative(5.23f, 5.23f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 81.5f)
        horizontalLineToRelative(-0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.4f, 82.4f)
        curveToRelative(0f, 0.88f, 0f, 1.79f, 0f, 2.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 77.99f)
        lineTo(75.49f, 80.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.05f, 82.98f)
        lineTo(56.5f, 82.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 82.3f)
        lineTo(55.65f, 81.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 82.98f)
        lineTo(57.05f, 82.98f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 80.83f)
        lineTo(58.51f, 82.98f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 82.3f)
        lineTo(56.5f, 77.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 77.99f)
        lineTo(55.65f, 78.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.65f, 78.84f)
        lineTo(55.65f, 81.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.65f, 33.77f)
        lineTo(56.5f, 32.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 32.71f)
        lineTo(57.05f, 32.02f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.65f, 33.77f)
        lineTo(55.65f, 36.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.65f, 36.16f)
        lineTo(56.5f, 37.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.5f, 37.01f)
        lineTo(56.5f, 32.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 37.01f)
        lineTo(68.41f, 37.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 32.02f)
        lineTo(58.51f, 34.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.05f, 32.02f)
        lineTo(58.51f, 32.02f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 92.29f)
        verticalLineTo(85.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 30f)
        verticalLineTo(22.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.39f, 29.91f)
        horizontalLineToRelative(-0.5f)
        reflectiveCurveToRelative(-0.07f, 0f, -0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(73.08f, 21.55f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.28f, 81.44f)
        curveToRelative(-3.68f, 0.06f, -7.35f, 0.12f, -11f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.06f, 81.49f)
        curveToRelative(-0.55f, 0f, -1.39f, 0f, -1.78f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 38.56f)
        curveToRelative(0.13f, 6.31f, 0.3f, 12.63f, 0.3f, 18.94f)
        reflectiveCurveToRelative(-0.18f, 12.63f, -0.3f, 18.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 33.5f)
        arcToRelative(4.2f, 4.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.83f, 1f)
        arcToRelative(4.13f, 4.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.39f, 2.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.28f, 34.18f)
        horizontalLineToRelative(-2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.26f, 33.42f)
        curveToRelative(3.67f, 0f, 7.34f, 0.08f, 11f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.25f, 34.18f)
        horizontalLineTo(75.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.24f, 34.18f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.24f, 34.18f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(-0.07f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.4f, 32.6f)
        arcToRelative(0.87f, 0.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.86f, 0.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.4f, 32.6f)
        arcToRelative(1.63f, 1.63f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, 0f)
        lineToRelative(-0.15f, 0f)
        reflectiveCurveToRelative(-0.12f, 0f, -0.12f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.26f, 81.58f)
        arcToRelative(0.88f, 0.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.86f, 0.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.27f, 33.57f)
        lineToRelative(1.79f, -0.06f)
        horizontalLineToRelative(0.29f)
    }
}
private fun ImageVector.Builder.addAaruyPaths8() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(101.88f, 61.9f)
        arcToRelative(3.75f, 3.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.89f, -0.13f)
        arcTo(20.3f, 20.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 98.91f, 61f)
        arcToRelative(18.74f, 18.74f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, -0.65f)
        arcToRelative(6.48f, 6.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1f, -0.17f)
        lineToRelative(-0.75f, -0.11f)
        arcToRelative(6.25f, 6.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.66f, -0.56f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.85f, -0.63f)
        arcToRelative(2.14f, 2.14f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, -0.61f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.17f, -0.56f)
        arcToRelative(1.91f, 1.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.63f)
        arcToRelative(2.39f, 2.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, -0.58f)
        arcTo(2.71f, 2.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 93f, 55.8f)
        arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.84f, -0.49f)
        arcToRelative(7.23f, 7.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.13f, -0.52f)
        arcToRelative(8.26f, 8.26f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1f, -0.18f)
        arcToRelative(10.9f, 10.9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.15f, -0.34f)
        curveToRelative(0.84f, -0.29f, 1.62f, -0.62f, 2.31f, -0.85f)
        arcToRelative(4.89f, 4.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.51f, -0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.6f, 81f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(73.08f, 21.55f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.79f, 26.11f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.49f, 34.18f)
        horizontalLineTo(71.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.23f, 80.83f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(103.55f, 53.11f)
        lineTo(103.55f, 53.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.6f, 92.25f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.12f, 90f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 26.94f)
        lineToRelative(0.06f, -0.1f)
        lineToRelative(0f, -0.1f)
        lineToRelative(0.05f, -0.1f)
        lineToRelative(0f, -0.08f)
        lineToRelative(0.07f, -0.15f)
        lineToRelative(0.07f, -0.12f)
        lineToRelative(0.07f, -0.14f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 82.25f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 76.44f)
        horizontalLineTo(88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 77.14f)
        verticalLineToRelative(-0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 76.41f)
        verticalLineToRelative(-0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.59f, 37.8f)
        horizontalLineTo(88f)
        reflectiveCurveToRelative(-0.07f, 0f, -0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 82.24f)
        curveToRelative(0f, 0.06f, 0.09f, 0.09f, 0.12f, 0.1f)
        lineToRelative(0.15f, 0f)
        lineToRelative(0.31f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.99f, 260.95f)
        lineTo(122.98f, 260.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.73f, 261f)
        curveToRelative(0f, -1f, 0f, -2f, 0.05f, -3f)
        lineToRelative(0f, -1.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.34f, 256.94f)
        curveToRelative(0f, 0.28f, 0f, 0.56f, 0f, 0.84f)
        lineToRelative(-0.12f, 2.9f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.8f, 256.94f)
        horizontalLineToRelative(7.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.76f, 260.08f)
        lineToRelative(0.06f, -1.2f)
        curveToRelative(0f, -0.65f, 0.07f, -1.29f, 0.1f, -1.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.94f, 256.94f)
        curveToRelative(0f, 0.63f, 0f, 1.25f, 0f, 1.88f)
        reflectiveCurveToRelative(0f, 1.25f, 0f, 1.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.94f, 256.94f)
        horizontalLineTo(115f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.4f, 254.76f)
        arcToRelative(1.59f, 1.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.39f, 0f)
        lineToRelative(-0.13f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.92f, 256.94f)
        horizontalLineToRelative(23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.9f, 254.71f)
        horizontalLineToRelative(0f)
        verticalLineTo(255f)
        curveToRelative(0f, 0.09f, 0f, 0.18f, 0f, 0.28f)
        verticalLineToRelative(1.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.73f, 254.71f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.06f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.2f)
        arcToRelative(2.36f, 2.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.28f)
        curveToRelative(0f, 0.18f, 0f, 0.37f, 0f, 0.56f)
        curveToRelative(0f, 0.38f, 0f, 0.75f, 0f, 1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.18f, 260.72f)
        curveToRelative(-1.66f, 0f, -4.14f, 0.07f, -7.45f, 0.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.2f, 261f)
        verticalLineToRelative(-0.06f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        verticalLineToRelative(-0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.75f, 261f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-0.21f)
        curveToRelative(0f, -0.07f, 0f, -0.14f, 0f, -0.22f)
        verticalLineToRelative(-0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.93f, 260.7f)
        curveToRelative(-1.72f, 0f, -3.64f, 0f, -5.79f, -0.1f)
        horizontalLineToRelative(-0.51f)
        lineToRelative(-0.57f, 0f)
        lineToRelative(-1.33f, -0.05f)
        lineToRelative(-3.38f, -0.13f)
        curveToRelative(-2.57f, -0.1f, -4.83f, -0.18f, -6.76f, -0.23f)
        reflectiveCurveToRelative(-3.55f, -0.08f, -4.83f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.5f, 252.73f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.5f, 253.28f)
        horizontalLineToRelative(1.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89f, 252.73f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.56f, 0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.5f, 252.73f)
        lineTo(86.86f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.55f, 252.73f)
        horizontalLineToRelative(-0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.28f)
        horizontalLineToRelative(1.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.08f, 252.73f)
        verticalLineToRelative(0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.99f, 253.28f)
        lineTo(61.98f, 253.74f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.08f, 254.71f)
        lineTo(63.08f, 253.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.59f, 253.28f)
        lineTo(89.61f, 254.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.08f, 252.73f)
        horizontalLineToRelative(-0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89f, 252.73f)
        horizontalLineTo(88.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.76f, 254.69f)
        horizontalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.45f, 254.71f)
        horizontalLineToRelative(-0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.19f, 254.69f)
        horizontalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.19f, 254.71f)
        curveToRelative(-0.93f, 0f, -1.86f, 0f, -2.79f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115.07f, 260.94f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(110.74f, 260.94f)
        horizontalLineTo(115f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.91f, 261f)
        horizontalLineToRelative(4.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.72f, 254f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.9f, 261f)
        horizontalLineTo(82.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.2f, 261f)
        horizontalLineTo(54.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.62f, 254.71f)
        horizontalLineToRelative(16.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.73f, 254.71f)
        horizontalLineToRelative(4.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(114.54f, 254.71f)
        horizontalLineToRelative(6.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 250.46f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.83f, 250.47f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.07f, 175.07f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.82f, 253.34f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.49f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.36f, 252.8f)
        lineTo(1.36f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.36f, 252.73f)
        horizontalLineTo(0.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.36f, 252.73f)
        verticalLineToRelative(-0.44f)
        curveToRelative(0f, -0.14f, 0f, -0.28f, 0f, -0.42f)
        reflectiveCurveToRelative(0f, -0.52f, 0f, -0.73f)
        reflectiveCurveToRelative(0f, -0.38f, 0f, -0.5f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.13f)
        arcToRelative(0.06f, 0.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.82f, 252.73f)
        arcToRelative(4.51f, 4.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.53f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.8f, 252.73f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.54f, 252.73f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 252.73f)
        horizontalLineToRelative(0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.34f, 253.01f)
        lineTo(57.48f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11.48f, 252f)
        quadToRelative(10.35f, 0.09f, 20.71f, 0.06f)
        curveToRelative(2.34f, 0f, 18.38f, 0f, 20.71f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.9f, 251.9f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.4f, 251.9f)
        lineTo(5.57f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.78f, 254f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.45f, 253.44f)
        lineTo(10.45f, 254.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.57f, 254.3f)
        horizontalLineToRelative(0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.82f, 253.34f)
        curveToRelative(-3.78f, 0.13f, -17.9f, 0.16f, -21.68f, 0.16f)
        quadToRelative(-10.84f, 0f, -21.69f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 254f)
        curveToRelative(0f, 0.08f, 0f, 0.18f, 0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.89f, 253.18f)
        lineToRelative(-0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.83f, 253.23f)
        verticalLineToRelative(0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.82f, 254.3f)
        verticalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.82f, 254.3f)
        lineToRelative(0.54f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.36f, 254.32f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.35f, 254.03f)
        lineTo(54.34f, 253.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.55f, 254.58f)
        lineToRelative(0.58f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.21f, 250.47f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.1f)
        reflectiveCurveToRelative(0f, 0.17f, 0f, 0.3f)
        reflectiveCurveToRelative(0f, 0.28f, 0.06f, 0.45f)
        reflectiveCurveToRelative(0f, 0.37f, 0f, 0.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.21f, 250.47f)
        lineTo(5.58f, 250.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.26f, 250.47f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.64f, 250.47f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.45f, 253.44f)
        arcToRelative(1.74f, 1.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, -0.92f)
        arcToRelative(0.94f, 0.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.78f, -0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.94f, 251.9f)
        arcToRelative(1.39f, 1.39f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, 0.06f)
        lineToRelative(0.15f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.34f, 253.32f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.65f, 254.56f)
        curveToRelative(-0.14f, 0f, -0.2f, -0.17f, -0.2f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 250.46f)
        lineTo(7.2f, 250.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.35f, 252.8f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.1f, 252.65f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.55f, 252.73f)
        lineTo(57.55f, 253.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.46f, 254.58f)
        horizontalLineToRelative(0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.16f, 254.66f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42f, 175f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.9f, 251.9f)
        lineTo(50.65f, 251.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.48f, 254.3f)
        verticalLineToRelative(0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.35f, 254.56f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.09f, 0.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
        verticalLineToRelative(-0.21f)
        curveToRelative(0f, -0.1f, 0f, -0.19f, 0f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.65f, 251.9f)
        arcToRelative(10.21f, 10.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -1f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
        arcToRelative(0.19f, 0.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.08f)
        arcToRelative(0f, 0f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.21f, 251f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        reflectiveCurveToRelative(0f, 0.09f, 0f, 0.14f)
        reflectiveCurveToRelative(0f, 0.13f, 0f, 0.2f)
        arcToRelative(1.94f, 1.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.24f)
        curveToRelative(0f, 0.09f, 0f, 0.19f, 0f, 0.28f)
        reflectiveCurveToRelative(0f, 0.41f, 0f, 0.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.68f, 252.72f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.85f, 254.02f)
        lineTo(54.35f, 254.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.35f, 338.42f)
        arcToRelative(11.32f, 11.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.23f, -0.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.56f, 336.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.35f, 315.53f)
        arcToRelative(12.06f, 12.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.47f, 259.53f)
        lineToRelative(-0.36f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.75f, 254.71f)
        lineTo(3.75f, 254.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.53f, 272.93f)
        arcToRelative(13.29f, 13.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, 1.68f)
        arcToRelative(11.74f, 11.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.33f, 1.67f)
        arcToRelative(13.2f, 13.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.27f, 3.12f)
        arcToRelative(16.55f, 16.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.92f, 1.43f)
        arcToRelative(15.11f, 15.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.1f, 1.3f)
        arcToRelative(14.46f, 14.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.59f, 2.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 256.69f)
        lineTo(3.7f, 255.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.53f, 256.98f)
        lineTo(52.53f, 272.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.51f, 259.53f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        reflectiveCurveToRelative(0f, -0.09f, 0f, -0.16f)
        reflectiveCurveToRelative(0f, -0.16f, 0f, -0.26f)
        reflectiveCurveToRelative(0f, -0.22f, 0f, -0.35f)
        reflectiveCurveToRelative(0f, -0.28f, 0f, -0.43f)
        lineToRelative(0f, -0.49f)
        curveToRelative(0f, -0.17f, 0f, -0.35f, 0f, -0.53f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(2.94f, 255.14f)
        lineTo(2.94f, 256.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(2.94f, 254.71f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 284.27f)
        lineTo(9.18f, 284.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(121.98f, 350.38f)
        lineTo(121.98f, 361.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122f, 361.72f)
        curveToRelative(0f, 0.65f, 0f, 1.3f, 0f, 1.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122f, 348.45f)
        curveToRelative(0f, 0.63f, -0.05f, 1.28f, 0f, 1.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.91f, 361.72f)
        lineTo(122.91f, 350.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.91f, 350.38f)
        horizontalLineTo(122f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.91f, 361.72f)
        horizontalLineTo(122f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.54f, 260.95f)
        lineTo(54.73f, 260.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.54f, 260.95f)
        lineTo(54.54f, 256.98f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.53f, 256.98f)
        lineTo(54.54f, 256.98f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 377.73f)
        lineTo(113.02f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.74f, 315.69f)
        lineToRelative(-0.19f, 0f)
        lineToRelative(-0.38f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.53f, 315.63f)
        lineToRelative(-0.38f, 0f)
        lineToRelative(-0.18f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.22f, 323.23f)
        curveToRelative(-0.08f, -0.22f, -0.17f, -0.44f, -0.25f, -0.62f)
        lineToRelative(-0.11f, -0.24f)
        lineToRelative(0f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.22f, 331f)
        arcToRelative(11.28f, 11.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -7.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.9f, 322.27f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0.07f)
        lineToRelative(-0.11f, 0.24f)
        quadToRelative(-0.06f, 0.13f, -0.12f, 0.3f)
        curveToRelative(0f, 0.1f, -0.09f, 0.21f, -0.13f, 0.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 331f)
        curveToRelative(0.08f, 0.22f, 0.17f, 0.44f, 0.25f, 0.62f)
        lineToRelative(0.11f, 0.24f)
        lineToRelative(0.06f, 0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 323.23f)
        arcToRelative(11.4f, 11.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 7.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.22f, 254.71f)
        arcToRelative(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.29f, 0.1f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, 0.14f)
        arcToRelative(0.84f, 0.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.72f, 255.14f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.77f, 254.71f)
        lineTo(54.54f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.84f, 255.28f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.06f, -0.24f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.18f)
        curveToRelative(0f, -0.1f, -0.08f, -0.15f, -0.13f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.94f, 315.75f)
        lineToRelative(-0.2f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67f, 315.69f)
        lineToRelative(-0.2f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.51f, 316f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.2f, 316f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.15f, 322.32f)
        lineTo(58.15f, 322.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.9f, 332f)
        lineToRelative(0.25f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.56f, 331.95f)
        lineTo(78.56f, 331.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.56f, 332f)
        lineToRelative(0.25f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.81f, 322.27f)
        horizontalLineToRelative(-0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.51f, 316f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.56f, 332f)
        arcToRelative(11.29f, 11.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3.41f, 4.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.56f, 336.14f)
        arcToRelative(10.85f, 10.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.25f, -1.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.17f, 315.63f)
        arcToRelative(11.22f, 11.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.64f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.96f, 350.38f)
        lineTo(122.96f, 361.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.56f, 336.15f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.1f, 336.18f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.1f, 255.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 284.27f)
        lineToRelative(-0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.14f, 336.15f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.77f, 315.77f)
        horizontalLineTo(66.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 377.73f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.76f, 254.34f)
        lineTo(0.76f, 253.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.55f, 254.71f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 322.28f)
        horizontalLineTo(57.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.72f, 322.29f)
        lineToRelative(-0.16f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.75f, 322.28f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.91f, 333.3f)
        arcToRelative(9.94f, 9.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.76f, -1.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.33f, 254.76f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.1f, 256.69f)
        horizontalLineTo(2.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.1f, 255.14f)
        horizontalLineTo(2.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.53f, 254.71f)
        horizontalLineTo(2.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.74f, 315.69f)
        lineTo(69.74f, 316.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.97f, 315.69f)
        lineTo(66.97f, 316.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.53f, 315.63f)
        lineTo(67.53f, 316.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.81f, 332f)
        lineTo(78.81f, 330.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.22f, 331.04f)
        lineTo(79.22f, 329.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.9f, 322.27f)
        lineTo(57.9f, 323.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 331.04f)
        lineTo(57.48f, 329.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.01f, 315.78f)
        lineTo(70.01f, 316.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(111.94f, 55.38f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.2f, 316f)
        lineTo(70.2f, 316f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.2f, 316f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.37f, 316.26f)
        lineTo(66.37f, 316.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.09f, 316.32f)
        lineTo(66.09f, 316.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.62f, 316.32f)
        lineTo(70.62f, 316.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.85f, 318.2f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(74.93f, 318.25f)
        lineToRelative(-0.12f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.18f, 333.31f)
        lineTo(58.91f, 333.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.15f, 331.39f)
        lineTo(58.15f, 331.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.56f, 322.88f)
        lineTo(78.56f, 322.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.17f, 316.11f)
        lineTo(69.17f, 315.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.81f, 323.54f)
        lineTo(78.81f, 322.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.22f, 325.12f)
        lineTo(79.22f, 323.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 325.12f)
        lineTo(57.48f, 323.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.7f, 316.2f)
        lineTo(66.7f, 315.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.51f, 316.01f)
        lineTo(66.51f, 316f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.51f, 316.24f)
        lineTo(66.51f, 316.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.62f, 316.08f)
        lineToRelative(-0.28f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.34f, 316.03f)
        lineTo(70.34f, 316.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.15f, 322.32f)
        lineToRelative(-0.19f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.51f, 272.93f)
        lineTo(3.51f, 259.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.62f, 150.31f)
        reflectiveCurveToRelative(-0.06f, 0f, -0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 361.72f)
        lineTo(124.41f, 350.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 361.72f)
        lineTo(124.69f, 350.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 365.06f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 347f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.28f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 361.72f)
        horizontalLineToRelative(-0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 350.38f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.54f, 365.06f)
        moveToRelative(-2.13f, 0f)
        arcToRelative(2.13f, 2.13f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.26f, 0f)
        arcToRelative(2.13f, 2.13f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4.26f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.85f, 399.28f)
        arcToRelative(5.7f, 5.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.42f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.02f, 377.73f)
        lineTo(46.21f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 393.61f)
        lineTo(122.99f, 393.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.99f, 393.61f)
        lineTo(122.99f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.99f, 377.73f)
        lineTo(122.94f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.02f, 377.73f)
        lineTo(113.02f, 393.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 377.73f)
        lineTo(122.94f, 393.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113f, 399.28f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 377.73f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.54f, 399.28f)
        lineTo(45.88f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.87f, 57.5f)
        arcToRelative(17.54f, 17.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 35.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.94f, 57.5f)
        arcTo(17.45f, 17.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 84f, 49.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.25f, 58.64f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0.3f, -2f)
        arcTo(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 69.25f, 58.64f)
        close()
    }
}
private fun ImageVector.Builder.addAaruyPaths9() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.75f, 59.33f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0.48f, -3.17f)
        arcTo(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 69.75f, 59.33f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 144.39f)
        horizontalLineToRelative(0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.55f, 254.62f)
        verticalLineToRelative(0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.61f, 254.6f)
        lineToRelative(0.06f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.69f, 251f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        curveToRelative(0f, 0.08f, 0f, 0.21f, 0f, 0.36f)
        arcToRelative(4.56f, 4.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.55f)
        curveToRelative(0f, 0.11f, 0f, 0.21f, 0f, 0.32f)
        reflectiveCurveToRelative(0f, 0.22f, 0f, 0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.79f, 254f)
        horizontalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.35f, 253.99f)
        lineTo(1.35f, 252.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.8f, 252.73f)
        lineTo(0.79f, 253.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.22f, 251f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.92f, 254.6f)
        arcToRelative(0.12f, 0.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, -0.05f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.68f, 252.58f)
        reflectiveCurveToRelative(0f, 0.09f, 0.12f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.49f, 34.18f)
        lineTo(69.82f, 37.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 85.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.49f, 34.18f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.3f, 80.83f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 77.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 261f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.61f, 31.82f)
        reflectiveCurveToRelative(0f, -0.05f, 0f, -0.05f)
        lineToRelative(0.06f, -0.05f)
        lineToRelative(0.12f, -0.09f)
        arcToRelative(2.49f, 2.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.24f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.13f, 30.83f)
        lineToRelative(1.75f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.88f, 30.86f)
        horizontalLineTo(58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 31.53f)
        horizontalLineToRelative(-0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.88f, 30.86f)
        arcToRelative(28.57f, 28.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, -2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 29.84f)
        verticalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.52f, 74.65f)
        arcTo(34.44f, 34.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, 67.83f, 92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.09f, 60.64f)
        arcTo(33.94f, 33.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 35.58f, 68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.58f, 47f)
        arcToRelative(34f, 34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.49f, 7.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 23.05f)
        arcToRelative(34.45f, 34.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, -29.31f, 17.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 84.21f)
        arcToRelative(28.46f, 28.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9.78f, 1.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.88f, 84.15f)
        lineToRelative(-1.7f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.88f, 84.15f)
        lineToRelative(0.18f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 22.29f)
        arcTo(35.25f, 35.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 37.59f, 40.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.36f, 60.89f)
        arcToRelative(34.92f, 34.92f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.47f, 7.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.83f, 46.9f)
        arcToRelative(34.69f, 34.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.47f, 7.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.59f, 74.55f)
        arcTo(35.23f, 35.23f, 0f, isMoreThanHalf = false, isPositiveArc = false, 67.83f, 92.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 23.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.09f, 60.64f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.52f, 74.65f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.58f, 47f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.51f, 40.35f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 75.36f)
        verticalLineToRelative(0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 39.14f)
        verticalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 75.87f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.5f, 75.87f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 29.89f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 23.55f)
        verticalLineToRelative(-0.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.85f, 36.87f)
        lineTo(53.95f, 36.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54f, 36.87f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.27f, 2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.95f, 33.89f)
        lineTo(53.86f, 33.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.95f, 33.89f)
        lineTo(53.95f, 36.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 31.53f)
        lineTo(56.22f, 31.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 31.62f)
        arcTo(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 54f, 33.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 31.62f)
        lineTo(58.06f, 31.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.92f, 31.53f)
        horizontalLineToRelative(-1.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 31.62f)
        lineTo(56.22f, 31.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 31.53f)
        arcToRelative(0.65f, 0.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, 0.08f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.24f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 32.19f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 23.05f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.58f, 0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.91f, 54.72f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 83.47f)
        lineTo(58.06f, 83.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.61f, 83.18f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, 0.21f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.31f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.63f, 82.81f)
        lineTo(58.72f, 82.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 83.38f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, -0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 83.38f)
        lineTo(56.22f, 83.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 83.47f)
        horizontalLineToRelative(1.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 83.38f)
        lineTo(58.06f, 83.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.86f, 81.11f)
        lineTo(53.95f, 81.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54f, 81.11f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.27f, 2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.95f, 78.13f)
        lineTo(53.85f, 78.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.85f, 78.13f)
        verticalLineToRelative(2.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.85f, 80.61f)
        verticalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.95f, 78.13f)
        lineTo(53.95f, 81.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 86f)
        verticalLineToRelative(-0.92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 92.15f)
        verticalLineToRelative(-0.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.85f, 78.13f)
        arcToRelative(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, -0.75f)
        arcToRelative(2.76f, 2.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, -0.68f)
        arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.26f, -0.29f)
        arcToRelative(2.61f, 2.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.27f, -0.22f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0.16f, -0.09f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 75.87f)
        arcTo(2.26f, 2.26f, 0f, isMoreThanHalf = false, isPositiveArc = false, 54f, 78.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.21f, 78.24f)
        horizontalLineToRelative(0.33f)
        lineToRelative(0.17f, 0f)
        lineToRelative(0.08f, 0f)
        reflectiveCurveToRelative(0.06f, 0f, 0.06f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.5f, 75.87f)
        reflectiveCurveToRelative(-0.08f, 0f, -0.12f, -0.1f)
        arcToRelative(0.68f, 0.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.27f, 75.51f)
        arcToRelative(2.88f, 2.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.82f, 0.36f)
        arcToRelative(2.64f, 2.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.1f, 1.43f)
        arcToRelative(2.68f, 2.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, 0.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.18f, 76.71f)
        arcToRelative(0.65f, 0.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.27f, -0.08f)
        curveToRelative(-0.07f, 0f, -0.17f, -0.1f, -0.17f, -0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 77.28f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.22f, 0.28f)
        arcToRelative(0.65f, 0.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.36f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 85.16f)
        horizontalLineToRelative(0.5f)
        reflectiveCurveToRelative(0.07f, 0f, 0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 85.16f)
        verticalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.26f, 80.65f)
        horizontalLineToRelative(0.52f)
        reflectiveCurveToRelative(0.07f, 0f, 0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.26f, 80.65f)
        curveToRelative(0f, 0.18f, 0f, 0.35f, 0f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.25f, 81.18f)
        horizontalLineToRelative(0.46f)
        lineToRelative(0.08f, 0f)
        reflectiveCurveToRelative(0.06f, 0f, 0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.3f, 81.84f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.3f, 81.84f)
        lineToRelative(0.32f, -0.08f)
        lineToRelative(0.16f, -0.05f)
        lineToRelative(0.07f, 0f)
        reflectiveCurveToRelative(0.07f, 0f, 0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.91f, 81.62f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.92f, 83.47f)
        horizontalLineToRelative(0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 84.21f)
        lineToRelative(0f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.07f, 83.54f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, -0.17f)
        lineToRelative(-0.12f, -0.09f)
        lineToRelative(-0.06f, 0f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 29f)
        reflectiveCurveToRelative(0f, 0f, -0.06f, 0f)
        lineToRelative(-0.07f, 0f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(1.51f, 1.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.74f, 38.57f)
        curveToRelative(0f, -0.1f, 0.1f, -0.17f, 0.17f, -0.2f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.27f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.3f, 33.16f)
        lineToRelative(0.32f, 0.08f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, 0.06f)
        lineToRelative(0.07f, 0f)
        reflectiveCurveToRelative(0.07f, 0f, 0.06f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.86f, 33.89f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.14f, 33.26f)
        lineToRelative(0.16f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.14f, 33.26f)
        arcToRelative(4.08f, 4.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.11f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.25f, 33.83f)
        curveToRelative(0f, 0.17f, 0f, 0.35f, 0f, 0.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.21f, 36.77f)
        arcToRelative(2.81f, 2.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.57f, 1.75f)
        arcToRelative(2.59f, 2.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.67f, 0.61f)
        arcToRelative(2.88f, 2.88f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.82f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.27f, 39.49f)
        arcToRelative(0.71f, 0.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.21f)
        arcToRelative(0.38f, 0.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.11f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.51f, 39.14f)
        arcTo(3.32f, 3.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, 55f, 38.9f)
        arcToRelative(2.71f, 2.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.34f, -0.26f)
        lineToRelative(-0.21f, -0.21f)
        lineToRelative(-0.1f, -0.12f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, -0.08f)
        lineTo(54.13f, 38f)
        arcToRelative(2.32f, 2.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, -0.36f)
        arcToRelative(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, -0.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 37.37f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, 0.07f)
        arcToRelative(0.35f, 0.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 39.14f)
        arcToRelative(1.53f, 1.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.59f, -0.13f)
        arcToRelative(1.45f, 1.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.5f, -0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 39.14f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.17f, 0.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.26f, 32.12f)
        arcToRelative(1.13f, 1.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.19f, -0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.26f, 34.35f)
        curveToRelative(0f, 0.81f, 0f, 1.62f, 0f, 2.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.21f, 78.24f)
        curveToRelative(0f, 0.8f, 0f, 1.6f, 0f, 2.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.07f, 83.54f)
        arcToRelative(1.11f, 1.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.19f, -0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.86f, 75.72f)
        reflectiveCurveToRelative(0f, 0.14f, 0f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.74f, 57.5f)
        horizontalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.27f, 39.49f)
        lineTo(58f, 39.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.18f, 75.87f)
        curveToRelative(-0.06f, 0f, -0.1f, -0.08f, -0.12f, -0.12f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 75.58f)
        lineToRelative(-2.75f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.74f, 38.57f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.08f, 54.36f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.56f, 68f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 86f)
        reflectiveCurveToRelative(0f, 0f, -0.06f, 0f)
        lineToRelative(-0.07f, 0f)
        lineToRelative(-0.14f, 0f)
        horizontalLineToRelative(-0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 92f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.2f, 60.19f)
        lineToRelative(0f, -0.06f)
        arcToRelative(2.52f, 2.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, -0.37f)
        lineToRelative(0.19f, -0.29f)
        arcToRelative(3.59f, 3.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.17f, -0.34f)
        arcToRelative(3.17f, 3.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.16f, -0.37f)
        curveToRelative(0f, -0.13f, 0.09f, -0.26f, 0.13f, -0.4f)
        reflectiveCurveToRelative(0.06f, -0.28f, 0.08f, -0.43f)
        arcToRelative(2.64f, 2.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.29f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.14f)
        curveToRelative(0f, -0.08f, 0f, -0.15f, 0f, -0.22f)
        reflectiveCurveToRelative(0f, -0.15f, 0f, -0.22f)
        reflectiveCurveToRelative(-0.05f, -0.29f, -0.08f, -0.42f)
        reflectiveCurveToRelative(-0.08f, -0.28f, -0.13f, -0.41f)
        reflectiveCurveToRelative(-0.1f, -0.25f, -0.16f, -0.37f)
        reflectiveCurveToRelative(-0.11f, -0.22f, -0.17f, -0.33f)
        lineToRelative(-0.19f, -0.29f)
        arcToRelative(3.19f, 3.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.29f, -0.37f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.6f, 41.06f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 76.28f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.8f, 75.87f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.2f, 39.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 37.72f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.32f, 0.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34f, 60.33f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.51f, 39.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 29.89f)
        verticalLineTo(29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.21f, 83.47f)
        arcToRelative(2.31f, 2.31f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.77f, -0.13f)
        arcToRelative(2.35f, 2.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.72f, -0.41f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0f)
        lineToRelative(-0.12f, -0.12f)
        arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.22f, -0.29f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.35f, -0.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.86f, 33.89f)
        arcToRelative(2.56f, 2.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.18f, 54.8f)
        lineToRelative(-0.13f, -0.11f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.2f, 54.82f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.66f, 41.08f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.73f, 46.73f)
        lineTo(35.73f, 46.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.73f, 68.27f)
        lineTo(35.73f, 68.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.07f, 31.46f)
        arcToRelative(27.88f, 27.88f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.77f, -1.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(39.76f, 57.5f)
        arcTo(28.64f, 28.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 53.3f, 81.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 92f)
        curveToRelative(0f, -1.13f, 0f, -3.33f, 0f, -5.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 28.86f)
        curveToRelative(0f, -2.48f, 0f, -4.68f, 0f, -5.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.09f, 54.37f)
        arcTo(4.13f, 4.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 35f, 55.61f)
        arcToRelative(4.43f, 4.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.89f, 5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.58f, 68f)
        arcToRelative(6.49f, 6.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.64f, 1f)
        arcTo(6.94f, 6.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 39f, 70.81f)
        arcToRelative(2.93f, 2.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.43f, 3.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.52f, 40.35f)
        arcToRelative(2.09f, 2.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.89f, 1.39f)
        arcTo(3.37f, 3.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 39f, 44.18f)
        arcTo(8.2f, 8.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 35.58f, 47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.14f, 33.26f)
        arcTo(28.64f, 28.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 39.76f, 57.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 23f)
        verticalLineToRelative(-0.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.59f, 40.46f)
        arcToRelative(0.72f, 0.72f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.92f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.08f, 60.65f)
        curveToRelative(-0.31f, 0.29f, -0.57f, 0.38f, -0.72f, 0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.83f, 68.11f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.17f)
        arcToRelative(0.26f, 0.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.15f, -0.07f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.23f, 0f)
        lineToRelative(0.27f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.36f, 54.11f)
        curveToRelative(0.15f, -0.14f, 0.41f, 0f, 0.72f, 0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.56f, 47f)
        curveToRelative(-0.42f, 0.16f, -0.68f, 0.13f, -0.73f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 92.71f)
        verticalLineTo(92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.51f, 74.66f)
        arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.92f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.36f, 54.11f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.55f, 0.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.09f, 60.64f)
        arcToRelative(0.47f, 0.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.36f, 60.89f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.55f, -0.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.08f, 73.75f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.49f, 0.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.36f, 46.54f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.53f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.08f, 41.25f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.49f, -0.79f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.67f, 41.09f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 29f)
        verticalLineTo(23.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 22.85f)
        verticalLineToRelative(-0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 22.29f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.58f, 0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.91f, 60.28f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, -0.27f)
        arcToRelative(2.7f, 2.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, -0.55f)
        curveToRelative(0f, -0.24f, -0.05f, -0.53f, -0.06f, -0.86f)
        reflectiveCurveToRelative(0f, -0.7f, 0f, -1.1f)
        reflectiveCurveToRelative(0f, -0.76f, 0f, -1.1f)
        reflectiveCurveToRelative(0f, -0.44f, 0f, -0.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 92.71f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.58f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.08f, 41.25f)
        arcToRelative(0.92f, 0.92f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.44f, -0.18f)
        horizontalLineToRelative(0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.36f, 46.54f)
        arcToRelative(2.07f, 2.07f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, -0.43f)
        arcToRelative(5.27f, 5.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.19f, -0.64f)
        curveToRelative(0.08f, -0.24f, 0.19f, -0.51f, 0.31f, -0.8f)
        reflectiveCurveToRelative(0.28f, -0.59f, 0.45f, -0.89f)
        verticalLineToRelative(-0.06f)
        lineToRelative(0f, 0f)
        lineToRelative(0.06f, -0.1f)
        lineToRelative(0.11f, -0.2f)
        lineToRelative(0.23f, -0.4f)
        curveToRelative(0.14f, -0.26f, 0.29f, -0.5f, 0.43f, -0.72f)
        arcToRelative(3.88f, 3.88f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.83f, -1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(33.61f, 55.78f)
        lineToRelative(0f, -0.23f)
        arcToRelative(2.64f, 2.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, -0.55f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 91.45f)
        verticalLineTo(86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 92.36f)
        verticalLineToRelative(-0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.36f, 68.47f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.21f, -0.19f)
        horizontalLineToRelative(0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 85.16f)
        arcToRelative(27.6f, 27.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -8.77f, -1.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.26f, 80.65f)
        arcTo(27.66f, 27.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, 40.74f, 57.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.74f, 57.5f)
        arcTo(27.67f, 27.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, 53.26f, 34.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.18f, 76.71f)
        arcToRelative(1.11f, 1.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.36f, -0.77f)
        lineToRelative(-0.09f, -0.08f)
        lineToRelative(-0.09f, -0.07f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.19f, -0.12f)
        arcToRelative(1.07f, 1.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.43f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.86f, 75.72f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.82f, 0.16f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.69f, 0.48f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0.07f)
        lineToRelative(-0.1f, 0.14f)
        arcToRelative(1.87f, 1.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, 0.31f)
        arcToRelative(1.73f, 1.73f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.13f, 0.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 39.42f)
        arcToRelative(1.08f, 1.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.8f, -0.36f)
        arcTo(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 58.9f, 39f)
        lineToRelative(0.07f, -0.09f)
        arcToRelative(0.72f, 0.72f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.11f, -0.19f)
        arcToRelative(1.07f, 1.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.1f, -0.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 37.37f)
        arcToRelative(1.81f, 1.81f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.14f, 0.69f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, 0.16f)
        lineToRelative(0.09f, 0.15f)
        arcToRelative(1.93f, 1.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.22f, 0.28f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, 0.06f)
        lineToRelative(0.16f, 0.12f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, 0.19f)
        horizontalLineToRelative(0f)
        lineToRelative(0.06f, 0f)
        lineToRelative(0.1f, 0f)
        lineToRelative(0.22f, 0.06f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.43f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.6f, 73.94f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.82f, 68.33f)
        arcToRelative(0.47f, 0.47f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.6f, 41.06f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 77.99f)
        lineTo(69.82f, 77.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 31f)
        lineTo(69.82f, 30.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 39.14f)
        verticalLineToRelative(-0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.24f, 66.29f)
        lineTo(53.31f, 66.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.2f, 39.13f)
        lineTo(58.2f, 39.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.08f, 22.11f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.09f, 22.11f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.64f, 46.73f)
        horizontalLineToRelative(-0.07f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.21f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.6f, 73.94f)
        horizontalLineToRelative(-0.14f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.38f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(102.59f, 53.11f)
        arcTo(34.45f, 34.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 84.35f, 26.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 88.06f)
        arcTo(34.45f, 34.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 102.59f, 61.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.39f, 85.09f)
        verticalLineToRelative(1.55f)
        curveToRelative(0f, 0.51f, 0f, 1.08f, 0f, 1.48f)
        reflectiveCurveToRelative(0f, 0.81f, 0f, 1.23f)
        reflectiveCurveToRelative(0f, 0.92f, 0f, 1.24f)
        curveToRelative(0f, 1.28f, 0f, 2.13f, 0f, 2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.43f, 22.15f)
        lineToRelative(0f, 1.94f)
        curveToRelative(0f, 0.64f, 0f, 1.29f, 0f, 1.94f)
        curveToRelative(0f, 1.29f, 0f, 2.59f, 0f, 3.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.35f, 33.5f)
        curveToRelative(0f, 0.12f, 0f, 0.23f, 0f, 0.35f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.08f)
        reflectiveCurveToRelative(0f, 0.08f, 0f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(101.88f, 53.11f)
        lineTo(103.55f, 53.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.85f, 253.85f)
        horizontalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 253.32f)
        lineTo(3.7f, 253.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 253.32f)
        lineTo(3.7f, 254.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.63f, 254.61f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 253.32f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.3f)
        arcToRelative(1.61f, 1.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.09f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.31f, 253.26f)
        reflectiveCurveToRelative(0f, -0.07f, 0f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.31f, 335.05f)
        lineTo(60.31f, 334.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.81f, 332f)
        verticalLineToRelative(0f)
        lineToRelative(0f, -0.08f)
        lineToRelative(0.11f, -0.24f)
        curveToRelative(0f, -0.09f, 0.09f, -0.19f, 0.13f, -0.29f)
        lineToRelative(0.12f, -0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.9f, 330.73f)
        lineTo(57.9f, 332f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.2f, 316f)
        lineTo(70.2f, 316.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(78.56f, 322.32f)
        arcToRelative(11.32f, 11.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, -7.94f, -6.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.31f, 335.05f)
        arcToRelative(11.19f, 11.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.4f, -1.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60f, 46.07f)
        arcToRelative(14.18f, 14.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.8f, 22.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(76.81f, 68.93f)
        arcTo(14.18f, 14.18f, 0f, isMoreThanHalf = true, isPositiveArc = true, 60f, 46.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.83f, 68.11f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.53f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 39.64f)
        verticalLineTo(57.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 57.5f)
        verticalLineTo(75.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.74f, 38.57f)
        curveToRelative(0f, -1f, 0f, -2f, 0f, -3.08f)
        reflectiveCurveToRelative(0f, -2.16f, 0f, -3.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 32.19f)
        lineTo(58.63f, 32.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.61f, 31.82f)
        arcToRelative(0.7f, 0.7f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 82.81f)
        curveToRelative(0f, -1.13f, 0f, -2.23f, 0f, -3.29f)
        reflectiveCurveToRelative(0f, -2.09f, 0f, -3.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.08f, 73.75f)
        arcToRelative(3.47f, 3.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.86f, -1f)
        curveToRelative(-0.29f, -0.45f, -0.57f, -1f, -0.86f, -1.53f)
        verticalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, -0.07f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, -0.13f)
        lineToRelative(-0.12f, -0.26f)
        curveToRelative(-0.08f, -0.17f, -0.16f, -0.33f, -0.23f, -0.49f)
        reflectiveCurveToRelative(-0.24f, -0.58f, -0.32f, -0.82f)
        arcToRelative(3.33f, 3.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.18f, -0.58f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 32.19f)
        reflectiveCurveToRelative(0.05f, 0f, 0.06f, 0f)
        horizontalLineToRelative(0.07f)
        lineToRelative(0.14f, 0f)
        horizontalLineToRelative(0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 29.84f)
        curveToRelative(0f, 1.29f, 0f, 2.59f, 0f, 3.87f)
        verticalLineToRelative(3.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.18f, 38.3f)
        curveToRelative(0f, -1f, 0f, -2f, 0f, -3f)
        reflectiveCurveToRelative(0f, -2.1f, 0f, -3.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.26f, 82.88f)
        curveToRelative(0f, -1.07f, 0f, -2.13f, 0f, -3.17f)
        reflectiveCurveToRelative(0f, -2f, 0f, -3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 77.63f)
        verticalLineToRelative(3.66f)
        curveToRelative(0f, 1.28f, 0f, 2.59f, 0f, 3.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.18f, 75.87f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.6f, 73.94f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.66f, 73.92f)
        lineToRelative(0.13f, -0.11f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.16f, -0.22f)
        lineToRelative(0f, 0f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, -0.09f)
        reflectiveCurveToRelative(0f, -0.1f, 0.08f, -0.16f)
        arcToRelative(3.59f, 3.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.12f, -0.39f)
        lineToRelative(0f, -0.22f)
        arcToRelative(1.77f, 1.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.23f)
        arcToRelative(2.09f, 2.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.25f)
        curveToRelative(0f, -0.08f, 0f, -0.16f, 0f, -0.25f)
        arcToRelative(2.33f, 2.33f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -0.25f)
        arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.25f)
        arcTo(3.8f, 3.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 38.8f, 71f)
        arcToRelative(5.66f, 5.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.34f, -0.47f)
        curveToRelative(-0.12f, -0.15f, -0.24f, -0.29f, -0.37f, -0.43f)
        reflectiveCurveToRelative(-0.27f, -0.27f, -0.41f, -0.4f)
        lineToRelative(-0.4f, -0.36f)
        lineTo(36.87f, 69f)
        lineToRelative(-0.17f, -0.12f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, -0.15f)
        arcToRelative(2.84f, 2.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.35f, -0.22f)
        arcToRelative(1.62f, 1.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.29f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.84f, 46.67f)
        horizontalLineToRelative(0f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, -0.15f)
        arcToRelative(2.91f, 2.91f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.36f, -0.22f)
        arcToRelative(2.43f, 2.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.28f, -0.2f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, 0f)
        curveToRelative(0.13f, -0.1f, 0.27f, -0.21f, 0.4f, -0.33f)
        arcToRelative(4.59f, 4.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.41f, -0.36f)
        arcToRelative(4.89f, 4.89f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.4f, -0.39f)
        curveToRelative(0.13f, -0.14f, 0.26f, -0.28f, 0.38f, -0.43f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 38.8f, 44f)
        curveToRelative(0.05f, -0.08f, 0.1f, -0.16f, 0.14f, -0.24f)
        arcToRelative(1.87f, 1.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.12f, -0.25f)
        arcToRelative(2.77f, 2.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.1f, -0.26f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.06f, -0.25f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.25f)
        arcToRelative(1.93f, 1.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.24f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.23f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.22f)
        arcToRelative(3.08f, 3.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, -0.39f)
        lineTo(39f, 41.49f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.08f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, -0.21f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.12f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.62f, 41.06f)
        lineTo(38.62f, 41.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.8f, 75.87f)
        arcToRelative(1.41f, 1.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.39f, 1.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(120.54f, 254.34f)
        arcToRelative(2.36f, 2.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.48f, 0f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.38f, 0.12f)
        horizontalLineToRelative(0.06f)
        lineToRelative(0.05f, 0f)
        lineToRelative(0.1f, 0f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.19f, 0.11f)
        arcToRelative(1.84f, 1.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, 0.27f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.08f, 0.08f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0.18f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0.38f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, 0.2f)
        lineToRelative(0.06f, 0.22f)
        lineToRelative(0f, 0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.87f, 252.69f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.31f, 252.83f)
        lineToRelative(-0.44f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.86f, 252.71f)
        lineToRelative(0.46f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.78f, 253.1f)
        lineToRelative(0.3f, 0f)
        lineToRelative(0.15f, 0f)
        reflectiveCurveToRelative(0.12f, 0f, 0.12f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90f, 252.56f)
        arcToRelative(0.45f, 0.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, 0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90f, 252.56f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.78f, 252.49f)
        lineTo(88.93f, 252.49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.93f, 252.49f)
        verticalLineToRelative(0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.93f, 252.69f)
        lineTo(89.87f, 252.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.92f, 399.63f)
        lineTo(30.16f, 399.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(30.16f, 399.63f)
        verticalLineToRelative(-0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 399.63f)
        lineTo(90.78f, 399.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.82f, 252f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.61f)
        lineToRelative(-0.16f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.75f, 254.53f)
        lineTo(89.62f, 254.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.38f, 254.36f)
        lineTo(61.38f, 254.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.77f, 254.25f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, -0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.39f, 254.4f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.37f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.76f, 254.26f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.39f, 254.4f)
        lineToRelative(0.22f, 0.18f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.06f, 0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 253.82f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, 0.25f)
        arcToRelative(0.41f, 0.41f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, 0.11f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.57f, 254.28f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, 0.08f)
        arcToRelative(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.21f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.41f)
        lineTo(90.91f, 254.37f)
    }
}
private fun ImageVector.Builder.addAaruyPaths10() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 251.31f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(8f, 175.57f)
        verticalLineToRelative(3.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.55f, 249.11f)
        curveToRelative(-0.1f, 0.2f, -0.2f, 0.4f, -0.31f, 0.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.53f, 282.15f)
        lineTo(6.91f, 282.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.55f, 284.26f)
        lineTo(9.08f, 284.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.76f, 400.15f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(0.39f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.43f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.5f, 402.15f)
        curveToRelative(0f, -0.67f, 0.07f, -1.34f, 0.11f, -2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.61f, 400.15f)
        lineTo(79.81f, 400.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.81f, 400.15f)
        curveToRelative(0f, 0.66f, 0.07f, 1.33f, 0.11f, 2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.76f, 401.35f)
        lineTo(80.66f, 400.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.23f, 401.39f)
        verticalLineToRelative(1.13f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.19f, 402.53f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.22f, 402.53f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.22f, 401.39f)
        lineTo(82.19f, 401.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.19f, 401.39f)
        lineToRelative(-0.59f, 0f)
        lineToRelative(-0.42f, 0f)
        horizontalLineToRelative(-0.31f)
        curveToRelative(-0.07f, 0f, -0.11f, 0f, -0.11f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.09f, 400.21f)
        lineTo(82.19f, 401.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.09f, 400.21f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.33f, 399.65f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.09f, 400.21f)
        lineTo(116.43f, 400.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.43f, 400.21f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.43f, 399.65f)
        lineTo(82.09f, 399.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.23f, 401.39f)
        lineTo(55.33f, 400.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.65f, 401.35f)
        reflectiveCurveToRelative(0f, 0f, -0.1f, 0f)
        horizontalLineToRelative(-0.5f)
        lineToRelative(-0.23f, 0f)
        lineToRelative(-0.59f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.33f, 400.21f)
        horizontalLineToRelative(1.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.23f, 401.39f)
        lineTo(20.53f, 401.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.53f, 401.39f)
        verticalLineToRelative(1.13f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.53f, 401.39f)
        arcToRelative(7.94f, 7.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5.39f, -2.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.63f, 400.21f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.33f, 399.65f)
        lineTo(23.63f, 399.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.63f, 400.21f)
        lineTo(55.33f, 400.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.29f, 393.46f)
        horizontalLineToRelative(-1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 393.46f)
        arcToRelative(7.93f, 7.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, -7.93f, 7.93f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.14f, 345.42f)
        lineTo(123.17f, 345.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.17f, 366.67f)
        lineTo(124.14f, 366.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 369.07f)
        lineTo(124.15f, 369.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 393.46f)
        lineTo(124.15f, 369.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 369.07f)
        lineTo(123.38f, 394f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 394f)
        arcToRelative(6.24f, 6.24f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, 1.3f)
        arcToRelative(6.37f, 6.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, 1.23f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.1f, 1.61f)
        curveToRelative(-0.15f, 0.16f, -0.31f, 0.31f, -0.47f, 0.45f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.07f, 0.74f)
        arcToRelative(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.2f, 0.52f)
        arcToRelative(8.37f, 8.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.55f, 0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 394f)
        horizontalLineTo(123f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.43f, 399.65f)
        arcToRelative(10.86f, 10.86f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.1f, -0.06f)
        lineToRelative(0.6f, -0.1f)
        lineToRelative(0.32f, -0.07f)
        lineToRelative(0.16f, -0.05f)
        lineToRelative(0.08f, 0f)
        horizontalLineToRelative(0.08f)
        arcToRelative(6.94f, 6.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1f, -0.43f)
        curveToRelative(0.18f, -0.09f, 0.35f, -0.2f, 0.53f, -0.31f)
        arcToRelative(2.48f, 2.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.26f, -0.19f)
        lineToRelative(0.13f, -0.1f)
        lineToRelative(0.07f, -0.05f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(5.27f, 5.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.8f, -0.83f)
        arcToRelative(4.54f, 4.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.36f, -0.5f)
        lineToRelative(0.15f, -0.27f)
        reflectiveCurveToRelative(0f, -0.09f, 0.08f, -0.14f)
        lineToRelative(0f, -0.07f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.12f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.94f, 399.64f)
        lineTo(48.44f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.63f, 399.65f)
        curveToRelative(2.17f, 0f, 4.35f, 0f, 6.53f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.09f, 399.65f)
        horizontalLineTo(80.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 399.63f)
        curveToRelative(1.12f, 0f, 2.24f, 0f, 3.36f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.27f, 399.64f)
        lineTo(90.76f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(48.46f, 399.63f)
        lineTo(56.81f, 399.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.63f, 399.65f)
        curveToRelative(-0.51f, 0f, -1f, 0f, -1.52f, 0f)
        reflectiveCurveToRelative(-1f, -0.08f, -1.52f, -0.15f)
        lineToRelative(-0.19f, 0f)
        lineToRelative(-0.19f, 0f)
        lineToRelative(-0.38f, -0.07f)
        lineToRelative(-0.23f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.62f, 400.21f)
        lineTo(23.63f, 400.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.19f, 400.21f)
        horizontalLineToRelative(0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 343.03f)
        lineTo(123.38f, 343.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.29f, 369.07f)
        horizontalLineToRelative(-1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 369.07f)
        verticalLineToRelative(-1.23f)
        curveToRelative(0f, -0.41f, 0f, -0.8f, 0f, -1.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.14f, 366.67f)
        curveToRelative(0f, -0.27f, 0f, -0.51f, 0f, -0.73f)
        verticalLineToRelative(-0.52f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.24f, 350.38f)
        horizontalLineToRelative(-0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.14f, 345.42f)
        curveToRelative(0f, -0.36f, 0f, -0.75f, 0f, -1.16f)
        verticalLineTo(343f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.29f, 343f)
        horizontalLineToRelative(-1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 343f)
        curveToRelative(0f, 0.45f, 0f, 0.89f, 0f, 1.29f)
        arcToRelative(9.64f, 9.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, 1.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 343f)
        horizontalLineTo(123f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.17f, 345.42f)
        curveToRelative(-0.06f, 0.21f, -0.11f, 0.41f, -0.17f, 0.63f)
        verticalLineToRelative(0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.17f, 345.42f)
        lineToRelative(-0.18f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.17f, 366.67f)
        arcToRelative(10.18f, 10.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, 1.11f)
        curveToRelative(0f, 0.4f, 0f, 0.84f, 0f, 1.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 369.07f)
        horizontalLineTo(123f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 260f)
        horizontalLineTo(123f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 343.03f)
        lineTo(123.38f, 260.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 256.56f)
        arcToRelative(2.87f, 2.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, -0.77f)
        arcToRelative(2.35f, 2.35f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, -0.37f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.19f, -0.35f)
        arcToRelative(2.85f, 2.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.51f, -0.59f)
        arcToRelative(3.26f, 3.26f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.66f, -0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 343.03f)
        lineTo(124.15f, 256.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.15f, 256.63f)
        arcToRelative(3.53f, 3.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, -0.79f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.6f, -1.44f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.5f, -0.62f)
        arcToRelative(4.92f, 4.92f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.61f, -0.5f)
        arcToRelative(3.91f, 3.91f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.7f, -0.37f)
        arcToRelative(3.83f, 3.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.75f, -0.23f)
        arcToRelative(3.53f, 3.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.79f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(120.12f, 252.6f)
        lineTo(113.25f, 252.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.25f, 252.6f)
        lineTo(93.18f, 252.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 252.6f)
        lineTo(93.18f, 253.77f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 253.77f)
        lineTo(120.54f, 253.77f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(121.77f, 254.05f)
        lineToRelative(-0.15f, -0.07f)
        arcToRelative(3.21f, 3.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.53f, -0.15f)
        arcToRelative(2.57f, 2.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.55f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.29f, 256.63f)
        horizontalLineToRelative(-1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.08f, 284.26f)
        lineToRelative(0.1f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51f, 250.42f)
        horizontalLineToRelative(-0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(81.52f, 251.28f)
        curveToRelative(0.09f, 0.21f, 0.18f, 0.38f, 0.25f, 0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(81.77f, 251.79f)
        arcToRelative(1.08f, 1.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.32f, 0.36f)
        horizontalLineToRelative(0f)
        lineToRelative(0.05f, 0f)
        lineToRelative(0.1f, 0.06f)
        lineToRelative(0.23f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.51f, 252.36f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(81.77f, 251.79f)
        lineToRelative(0.2f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.13f, 249.13f)
        arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.61f, 2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82f, 251.61f)
        lineToRelative(-0.45f, -0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.69f, 249.15f)
        lineToRelative(-0.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.07f, 251.28f)
        arcToRelative(6.23f, 6.23f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.61f, -2.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 251.79f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, -0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(81.52f, 251.28f)
        arcToRelative(19.11f, 19.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, -11.45f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.08f, 252.36f)
        curveToRelative(0.38f, 0.13f, 0.77f, 0.26f, 1.16f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.51f, 252.36f)
        lineTo(84.68f, 252.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 251.76f)
        lineTo(82.51f, 251.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 249.15f)
        verticalLineToRelative(2.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.69f, 249.15f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82f, 251.61f)
        arcToRelative(6.6f, 6.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.72f, -2.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.51f, 251.76f)
        arcToRelative(1.27f, 1.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79f, 244.22f)
        lineTo(84.68f, 244.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 244.22f)
        verticalLineToRelative(-1.4f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 242.81f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(1.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 244.22f)
        lineTo(79f, 244.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 245f)
        lineTo(79.83f, 245f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 244.22f)
        lineTo(84.68f, 245f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.83f, 245f)
        arcToRelative(5.56f, 5.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.83f, -0.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 245.57f)
        lineTo(84.68f, 245.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 245.57f)
        verticalLineTo(245f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.83f, 245f)
        lineTo(66.91f, 245f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 245f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 249.15f)
        verticalLineToRelative(-0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 248.59f)
        horizontalLineToRelative(-2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.69f, 248.59f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.36f, 248.58f)
        curveToRelative(-0.11f, 0f, -0.16f, 0.17f, -0.18f, 0.24f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, 0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.24f, 248.58f)
        curveToRelative(0.1f, 0f, 0.15f, 0.17f, 0.17f, 0.24f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.46f, 249.13f)
        quadToRelative(3.17f, 0f, 6.34f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.8f, 249.09f)
        quadToRelative(3.17f, 0f, 6.33f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.8f, 249.09f)
        verticalLineToRelative(-0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.9f, 248.59f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.9f, 248.59f)
        horizontalLineToRelative(-2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 248.59f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 249.15f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.07f, 251.28f)
        lineToRelative(-0.45f, 0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.9f, 249.15f)
        lineToRelative(0.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.62f, 251.61f)
        lineToRelative(0.2f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.08f, 251.76f)
        verticalLineToRelative(0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 251.79f)
        arcToRelative(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.13f, 0.19f)
        lineToRelative(-0.09f, 0.09f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(1.37f, 1.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.42f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 252.36f)
        lineTo(69.08f, 252.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.08f, 251.76f)
        lineTo(66.91f, 251.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(110.35f, 254.67f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 244.22f)
        horizontalLineToRelative(2.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 245f)
        lineTo(66.91f, 244.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 245.06f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 245.64f)
        lineTo(64.93f, 245.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87f, 245.64f)
        horizontalLineToRelative(-0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.95f, 245.62f)
        lineTo(86.95f, 245.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 245.57f)
        horizontalLineToRelative(1.13f)
        lineToRelative(0.57f, 0f)
        horizontalLineTo(87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.64f, 245.62f)
        horizontalLineToRelative(0.57f)
        lineToRelative(0.57f, 0f)
        horizontalLineToRelative(1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.64f, 245.64f)
        lineTo(64.64f, 245.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 245.64f)
        horizontalLineToRelative(-0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 248.47f)
        lineTo(64.93f, 248.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 245.64f)
        lineTo(86.66f, 248.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 248.47f)
        lineTo(64.93f, 245.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 248.47f)
        horizontalLineTo(87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.64f, 248.47f)
        horizontalLineToRelative(0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.24f, 248.58f)
        lineTo(69.12f, 248.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.36f, 248.58f)
        quadToRelative(-3.15f, 0f, -6.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.69f, 248.59f)
        lineTo(82.58f, 248.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(75.8f, 248.55f)
        quadToRelative(-3.42f, 0f, -6.56f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.57f, 248.54f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.49f, 0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 248.59f)
        horizontalLineTo(64.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.62f, 251.61f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, 0.08f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(1.27f, 1.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.9f, 249.15f)
        arcToRelative(6.6f, 6.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.72f, 2.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 251.76f)
        verticalLineToRelative(-2.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 251.76f)
        arcToRelative(3.44f, 3.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.78f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.13f, 251.31f)
        lineToRelative(-0.29f, 0.27f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, 0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.13f, 251.84f)
        horizontalLineToRelative(-0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.13f, 251.84f)
        lineTo(66.13f, 251.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.55f, 252f)
        arcToRelative(0.44f, 0.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.1f)
        arcToRelative(0.11f, 0.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.62f, 251.84f)
        arcToRelative(0.37f, 0.37f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 251.31f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(111f, 254.42f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.42f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.42f)
        lineTo(111f, 254.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.61f)
        verticalLineToRelative(-0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.79f)
        verticalLineToRelative(-0.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.34f, 253.18f)
        verticalLineToRelative(0.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.34f, 253.8f)
        horizontalLineTo(62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.98f, 253.73f)
        lineTo(59.99f, 253.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60f, 253.17f)
        arcToRelative(1.12f, 1.12f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.34f, -0.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 245.09f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, 0.36f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, 0.13f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 249f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.18f)
        arcToRelative(0.81f, 0.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -0.18f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, -0.13f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.57f, 248.54f)
        lineTo(64.64f, 248.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 249.09f)
        lineToRelative(0.08f, 0f)
        horizontalLineToRelative(0.21f)
        lineToRelative(0.54f, 0f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.7f, 252f)
        curveToRelative(0.25f, 0f, 0.43f, 0.25f, 0.51f, 0.45f)
        arcToRelative(1.72f, 1.72f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60f, 253.17f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(1.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.42f, 254.29f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.56f, -0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.57f, 253.71f)
        verticalLineToRelative(0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.42f, 254.31f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.56f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.42f, 254.29f)
        lineTo(59.42f, 254.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.34f, 253.8f)
        horizontalLineTo(60f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61f, 254.35f)
        curveToRelative(0.12f, 0f, 0.2f, -0.12f, 0.25f, -0.22f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, -0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.65f, 252.34f)
        arcToRelative(0.24f, 0.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.06f)
        horizontalLineToRelative(-0.22f)
        lineToRelative(-0.09f, 0f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, 0.06f)
        horizontalLineTo(59f)
        lineToRelative(-0.06f, 0f)
        lineToRelative(-0.09f, 0.07f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.18f, 0.17f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        arcToRelative(0.21f, 0.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.06f)
        lineToRelative(-0.07f, 0.11f)
        verticalLineTo(253f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        arcToRelative(0.16f, 0.16f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.65f, 252.34f)
        reflectiveCurveToRelative(0f, 0f, -0.06f, 0f)
        horizontalLineToRelative(-0.1f)
        lineToRelative(-0.14f, 0f)
        lineToRelative(-0.07f, 0f)
        lineToRelative(-0.09f, 0.05f)
        lineToRelative(-0.17f, 0.11f)
        lineToRelative(-0.1f, 0.07f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.05f, 0.05f)
        lineToRelative(-0.1f, 0.09f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.21f, 0.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.06f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 253.16f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.58f, 253.27f)
        lineToRelative(0f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.58f, 253.27f)
        lineTo(58.57f, 253.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60f, 253.73f)
        horizontalLineTo(58.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.58f, 253.15f)
        horizontalLineTo(60f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.54f, 253.15f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.57f, 253.71f)
        lineTo(58.34f, 253.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(47.62f, 177.24f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.55f, 0.28f)
        lineToRelative(0.28f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51f, 249.85f)
        lineTo(52.3f, 249.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.3f, 249.85f)
        arcToRelative(1.36f, 1.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.44f, 0.08f)
        arcToRelative(1.14f, 1.14f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.38f, 0.23f)
        lineToRelative(0.15f, 0.17f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.51f, 253.16f)
        lineToRelative(0.07f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61f, 254.35f)
        horizontalLineToRelative(0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.42f, 254.31f)
        horizontalLineTo(61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.75f, 250.42f)
        horizontalLineTo(5.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.04f, 250.42f)
        lineTo(5.75f, 250.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.3f, 250.42f)
        lineTo(51f, 250.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.43f, 244.41f)
        reflectiveCurveToRelative(0f, 0.08f, 0f, 0.13f)
        lineToRelative(-0.12f, 0.08f)
        lineToRelative(-0.09f, 0f)
        lineToRelative(-0.06f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.21f, 0.08f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7f, 249.85f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.3f, 249.85f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.75f, 250.42f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51f, 249.85f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.42f, 177.24f)
        lineToRelative(0.05f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(47.62f, 177.24f)
        lineToRelative(0f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.49f, 251.31f)
        lineTo(85.49f, 251.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86f, 251.84f)
        horizontalLineToRelative(-0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86f, 251.84f)
        arcToRelative(2.35f, 2.35f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, -0.26f)
        lineToRelative(-0.29f, -0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 254.34f)
        verticalLineToRelative(-0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(120.54f, 253.77f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 254.41f)
        horizontalLineTo(111f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(111f, 254.42f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.56f, 0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 251.31f)
        lineTo(87.51f, 250.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 253.8f)
        horizontalLineToRelative(2.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 252.63f)
        lineTo(90.35f, 252.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(116.3f, 254.4f)
        horizontalLineToRelative(-0.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.49f, 251.31f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 244.22f)
        horizontalLineTo(84.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 251.76f)
        arcToRelative(4.45f, 4.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.81f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87f, 245.62f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.56f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87f, 248.47f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, 0.06f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.18f, 0.14f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, 0.17f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.95f, 248.47f)
        lineTo(87.02f, 248.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.87f, 248.54f)
        lineToRelative(-0.31f, 0f)
        horizontalLineTo(84.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87f, 248.54f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.49f, 0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.07f, 252f)
        arcToRelative(0.43f, 0.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.1f, 252.15f)
        reflectiveCurveToRelative(0f, -0.07f, 0f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.42f)
        horizontalLineToRelative(-0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 254.34f)
        lineTo(120.54f, 254.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.37f)
        horizontalLineToRelative(2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.69f, 254.43f)
        lineTo(89.61f, 254.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(110.58f, 254.58f)
        lineToRelative(-0.08f, 0.06f)
        lineToRelative(-0.06f, 0f)
        horizontalLineToRelative(-0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.69f, 254.43f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.19f, 0.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.08f, 284.26f)
        lineToRelative(-0.37f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.88f, 249.86f)
        arcToRelative(2.25f, 2.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, 0.16f)
        arcToRelative(0.42f, 0.42f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, 0.09f)
        lineToRelative(-0.07f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        curveToRelative(0f, 0.05f, -0.09f, 0.1f, -0.13f, 0.16f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.11f, 276f)
        horizontalLineToRelative(0.36f)
        lineToRelative(0.37f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.53f, 282.15f)
        curveToRelative(0.07f, 0.17f, 0.14f, 0.36f, 0.22f, 0.54f)
        reflectiveCurveToRelative(0.08f, 0.19f, 0.13f, 0.28f)
        lineToRelative(0.06f, 0.14f)
        lineToRelative(0f, 0.07f)
        verticalLineToRelative(0.07f)
        curveToRelative(0.15f, 0.3f, 0.33f, 0.65f, 0.54f, 1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.24f, 249.7f)
        lineToRelative(-0.36f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11.69f, 171.87f)
        arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3.7f, 3.7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 252.72f)
        horizontalLineTo(86.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.86f, 252.71f)
        lineTo(89.86f, 252.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.78f, 253.54f)
        verticalLineToRelative(-0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.41f)
        lineToRelative(-0.21f, 0f)
        horizontalLineToRelative(-0.15f)
        lineToRelative(-0.19f, -0.06f)
        lineToRelative(-0.08f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.16f, -0.13f)
        arcTo(0.46f, 0.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 90f, 254f)
        lineToRelative(0f, 0f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.72f, 0.72f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, -0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.86f, 252.71f)
        arcToRelative(1.09f, 1.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.08f, 0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 252.72f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.28f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.15f)
        arcToRelative(0.17f, 0.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.07f)
        reflectiveCurveToRelative(0f, -0.06f, 0f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.66f, 252.16f)
        reflectiveCurveToRelative(0f, 0.05f, 0f, 0.06f)
        verticalLineToRelative(0.08f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.14f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.86f, 252.71f)
        horizontalLineToRelative(-0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.29f, 251.88f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.11f, 0.09f)
        lineToRelative(0.21f, 0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.71f, 252.72f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.46f, 252.71f)
        lineTo(64.73f, 252.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.66f, 252.58f)
        lineTo(62.42f, 252.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.02f, 252.72f)
        lineTo(64.93f, 252.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.85f, 251.59f)
        lineToRelative(0.09f, -0.22f)
        arcToRelative(0.93f, 0.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.13f)
        arcToRelative(0.14f, 0.14f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.06f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        arcToRelative(1.85f, 1.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, -0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.83f, 252.2f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.17f, 0.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.66f, 252.58f)
        verticalLineTo(252f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.89f, 252.77f)
        horizontalLineToRelative(0f)
        lineToRelative(0.05f, 0f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0.13f, 0f)
        lineToRelative(0.27f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.46f, 252.71f)
        lineToRelative(-0.27f, 0.08f)
        lineToRelative(-0.14f, 0f)
        lineToRelative(-0.07f, 0f)
        horizontalLineToRelative(-0.07f)
    }
}
private fun ImageVector.Builder.addAaruyPaths11() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.42f, 252.58f)
        arcToRelative(0.93f, 0.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.46f, 252.71f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.15f)
        reflectiveCurveToRelative(0f, 0f, 0.07f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.77f, 254.25f)
        arcToRelative(0.37f, 0.37f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.2f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.22f, 252.72f)
        horizontalLineTo(65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65f, 252.16f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.81f, 252.15f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.14f)
        lineToRelative(0f, 0.14f)
        lineToRelative(0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 252.72f)
        horizontalLineToRelative(-0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 252.72f)
        verticalLineToRelative(-0.56f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.83f, 252.2f)
        lineToRelative(0.22f, -0.18f)
        lineToRelative(0.11f, -0.08f)
        lineToRelative(0f, -0.05f)
        lineToRelative(0.05f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.73f, 252.71f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.9f, -0.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.83f, 252.2f)
        arcToRelative(0.33f, 0.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.09f, -0.27f)
        arcToRelative(1.11f, 1.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.85f, 251.59f)
        curveToRelative(0.1f, -0.05f, 0.23f, -0.14f, 0.23f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.78f, 399.63f)
        reflectiveCurveToRelative(0f, -0.08f, 0f, -0.09f)
        verticalLineToRelative(-0.08f)
        arcToRelative(1.09f, 1.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(48.46f, 399.63f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.09f)
        verticalLineToRelative(-0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.87f, 252.12f)
        lineToRelative(0.53f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(120.12f, 252.6f)
        verticalLineToRelative(-1.13f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.93f, 252.12f)
        lineTo(89.87f, 252.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.87f, 252.12f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.86f, 252.15f)
        lineToRelative(0.53f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 253f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90f, 252.54f)
        lineToRelative(0.22f, 0f)
        arcToRelative(0.38f, 0.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, 0f)
        reflectiveCurveToRelative(0.08f, 0f, 0.08f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.31f, 252.83f)
        arcToRelative(0.44f, 0.44f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, -0.13f)
        lineToRelative(-0.19f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.32f, 252.86f)
        lineToRelative(0f, -0.01f)
        lineToRelative(-0.01f, -0.01f)
        lineToRelative(0f, -0.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.93f, 252.49f)
        arcToRelative(2.48f, 2.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.61f, -0.07f)
        arcToRelative(3.27f, 3.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.58f, -0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.86f, 252.15f)
        lineTo(87.7f, 252.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.74f, 252.23f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.61f, 251.8f)
        arcToRelative(2.76f, 2.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, -0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.71f, 251.7f)
        lineToRelative(-0.1f, 0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.53f, 251.53f)
        lineToRelative(0.18f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.53f, 251.53f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.7f, 251f)
        curveToRelative(0f, 0.07f, 0f, 0.13f, 0.07f, 0.19f)
        reflectiveCurveToRelative(0f, 0.05f, 0f, 0.08f)
        verticalLineToRelative(0.06f)
        horizontalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0.07f, 0f, 0.1f)
        arcToRelative(0.06f, 0.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
        verticalLineToRelative(0f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.7f, 251f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, -0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.7f, 252.15f)
        arcToRelative(3.58f, 3.58f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.71f, 251.7f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.22f, 0.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.78f, 252.49f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90f, 252.54f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123.38f, 260.03f)
        lineTo(123.38f, 256.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.47f, 248.58f)
        lineTo(82.36f, 248.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.58f, 248.59f)
        lineTo(82.47f, 248.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.01f, 248.59f)
        lineTo(68.9f, 248.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.12f, 248.58f)
        lineTo(69.01f, 248.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.86f, 242.21f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 254.42f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115.6f, 254.41f)
        horizontalLineToRelative(-2.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.82f, 252f)
        arcToRelative(2.51f, 2.51f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.51f, 0f)
        lineToRelative(0.19f, 0f)
        lineToRelative(0.08f, 0f)
        horizontalLineToRelative(0.07f)
        lineToRelative(0.22f, 0.08f)
        lineToRelative(0.1f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 252.63f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.53f, 250.38f)
        horizontalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.09f)
        verticalLineToRelative(0.08f)
        horizontalLineToRelative(0f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.14f, 0.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 253.82f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.78f, 252.15f)
        horizontalLineToRelative(-0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.78f, 252.15f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.75f, 400.2f)
        lineTo(56.65f, 401.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.66f, 400.19f)
        horizontalLineToRelative(1.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.66f, 400.19f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.66f, 400.15f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.81f, 399.66f)
        horizontalLineTo(55.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.61f, 399.63f)
        lineTo(88.25f, 399.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.27f, 367.3f)
        curveToRelative(0f, -0.25f, -0.25f, -0.42f, -0.44f, -0.5f)
        arcToRelative(1.66f, 1.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.69f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.25f, 364.25f)
        curveToRelative(0f, -0.05f, -0.07f, -0.09f, -0.11f, -0.1f)
        lineToRelative(-0.14f, 0f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.25f, 347.85f)
        reflectiveCurveToRelative(-0.07f, 0.08f, -0.11f, 0.1f)
        lineTo(125f, 348f)
        arcToRelative(1.55f, 1.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.31f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.27f, 344.8f)
        curveToRelative(0f, 0.25f, -0.25f, 0.42f, -0.44f, 0.5f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.69f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.52f, 252.72f)
        lineTo(65.52f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 245.09f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.56f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.28f, 252.16f)
        horizontalLineToRelative(0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.28f, 252.16f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 250.88f)
        lineTo(64.08f, 251.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60f, 253.75f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(59.32f, 252.02f)
        lineTo(59.65f, 252.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.27f, 244.2f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.09f, -0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 250.84f)
        lineTo(87.51f, 250.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.59f, 250.43f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.42f, 249.43f)
        reflectiveCurveToRelative(0.05f, 0.11f, 0.08f, 0.14f)
        lineToRelative(0.11f, 0.1f)
        lineToRelative(0.27f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 253.8f)
        curveToRelative(0f, -0.11f, -0.16f, -0.18f, -0.24f, -0.2f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.33f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.52f, 252.72f)
        horizontalLineToRelative(-0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.28f, 252.16f)
        reflectiveCurveToRelative(0f, 0.12f, 0f, 0.14f)
        reflectiveCurveToRelative(0f, 0.09f, 0f, 0.14f)
        verticalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.39f, 252.31f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 252.63f)
        arcToRelative(1.17f, 1.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 252.63f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.24f, 249.7f)
        lineTo(4.24f, 250.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.42f, 177.24f)
        curveToRelative(1.66f, -0.68f, 3.33f, -1.35f, 5f, -2f)
        lineToRelative(1.06f, -0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.61f, 400.15f)
        lineTo(57.69f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.5f, 402.15f)
        arcToRelative(1.06f, 1.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.59f, -0.21f)
        arcToRelative(0.63f, 0.63f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.26f, -0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.92f, 402.15f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.29f)
        arcToRelative(0.26f, 0.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.22f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.65f, 401.4f)
        verticalLineToRelative(0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(125.24f, 361.72f)
        horizontalLineToRelative(-0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(8.13f, 285.08f)
        lineToRelative(-0.28f, -0.42f)
        lineToRelative(-0.3f, -0.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 252.36f)
        verticalLineToRelative(-0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.25f, 252.44f)
        lineTo(85.25f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 252.36f)
        arcToRelative(2.24f, 2.24f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.57f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.37f, 252.44f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.54f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 251.76f)
        verticalLineToRelative(0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.55f, 252f)
        arcToRelative(0.87f, 0.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.82f, 0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.37f, 252.44f)
        arcToRelative(2.59f, 2.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, -0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46f, 174.37f)
        lineToRelative(-4.63f, -1.53f)
        lineTo(38.92f, 172f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.58f, 253.27f)
        lineTo(58.2f, 252.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.19f, 212.19f)
        horizontalLineToRelative(-0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.38f, 173.3f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        lineToRelative(0f, 0.1f)
        lineToRelative(0f, 0.13f)
        lineToRelative(-0.09f, 0.27f)
        lineToRelative(-0.18f, 0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.49f, 251.84f)
        arcToRelative(2.59f, 2.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.24f, 0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.25f, 252.44f)
        arcToRelative(0.87f, 0.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.82f, -0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.52f, 282.67f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.13f, -0.06f)
        lineToRelative(0.25f, -0.13f)
        lineToRelative(0.51f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.55f, 284.26f)
        arcToRelative(1.78f, 1.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.44f, 0.21f)
        curveToRelative(-0.11f, 0f, -0.36f, 0.11f, -0.46f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.77f, 248.76f)
        horizontalLineToRelative(0f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0.1f, 0f)
        lineToRelative(0.2f, 0.09f)
        lineToRelative(0.39f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.78f, 252.15f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0.12f)
        lineToRelative(0f, 0.14f)
        lineToRelative(0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.25f, 252.6f)
        verticalLineToRelative(-1.13f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 251.46f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(1.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(91.08f, 251.57f)
        lineToRelative(-0.11f, 0f)
        lineToRelative(-0.35f, 0.07f)
        horizontalLineToRelative(-0.06f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.11f, 0f)
        arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.18f, 0.08f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0f)
        lineTo(90f, 252f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.14f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.93f, 251.55f)
        verticalLineToRelative(0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.77f, 401.47f)
        arcToRelative(0.17f, 0.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.15f, 285.15f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.14f, 0.09f)
        horizontalLineToRelative(0.3f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.54f, -0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.87f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.22f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.76f, 165.24f)
        horizontalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.8f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.3f, 165.24f)
        horizontalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.26f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.63f, 165.24f)
        horizontalLineTo(27.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.57f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25f, 165.24f)
        horizontalLineTo(24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(24f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.6f, 165.24f)
        horizontalLineToRelative(-0.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.71f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.68f, 165.24f)
        horizontalLineTo(18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22.39f, 152.49f)
        horizontalLineToRelative(1.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.46f, 152.49f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.74f, 152.49f)
        horizontalLineTo(27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.13f, 152.49f)
        horizontalLineToRelative(0.74f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29.29f, 152.49f)
        horizontalLineToRelative(1.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.83f, 152.49f)
        horizontalLineTo(34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(36.13f, 152.49f)
        horizontalLineToRelative(1.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(39f, 152.49f)
        horizontalLineToRelative(0.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15f, 152.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15f, 152.49f)
        horizontalLineToRelative(0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.46f, 152.49f)
        horizontalLineToRelative(0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16f, 152.49f)
        horizontalLineToRelative(0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.13f, 152.49f)
        horizontalLineTo(17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17f, 152.49f)
        horizontalLineToRelative(0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.87f, 152.49f)
        horizontalLineToRelative(0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.05f, 152.49f)
        horizontalLineToRelative(1.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.25f, 152.49f)
        horizontalLineToRelative(0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.43f, 152.49f)
        horizontalLineToRelative(0.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.66f, 152.49f)
        horizontalLineToRelative(1.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22.14f, 152.49f)
        horizontalLineToRelative(0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.53f, 152.49f)
        horizontalLineToRelative(0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.79f, 152.49f)
        horizontalLineToRelative(1.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.46f, 152.49f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27f, 152.49f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.25f, 152.49f)
        horizontalLineTo(29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 152.49f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(30.55f, 152.49f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(30.83f, 152.49f)
        horizontalLineToRelative(1.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34f, 152.49f)
        horizontalLineToRelative(0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.88f, 152.49f)
        horizontalLineToRelative(0.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.19f, 152.49f)
        horizontalLineToRelative(0.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.77f, 152.49f)
        horizontalLineTo(39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(39.84f, 152.49f)
        horizontalLineTo(40f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40f, 152.49f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.06f, 152.49f)
        horizontalLineToRelative(0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.93f, 152.49f)
        horizontalLineToRelative(0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.28f, 152.49f)
        horizontalLineToRelative(0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.58f, 152.49f)
        horizontalLineToRelative(0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43f, 152.49f)
        horizontalLineToRelative(0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.26f, 152.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.7f, 153f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.23f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.93f, 153.05f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.46f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22.14f, 152.49f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.44f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.57f, 153.05f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.28f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.14f)
        verticalLineToRelative(-0.07f)
        arcToRelative(0.11f, 0.11f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.05f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(24.05f, 153f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.26f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.82f, 153f)
        reflectiveCurveToRelative(0f, 0f, 0.11f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.93f, 153.05f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.53f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.25f, 152.49f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.43f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.82f, 153f)
        arcToRelative(3.56f, 3.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25f, 153f)
        arcToRelative(1.37f, 1.37f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.33f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.37f, 153.05f)
        arcToRelative(0.69f, 0.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.35f)
        arcToRelative(0.35f, 0.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.29f, -0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.46f, 152.49f)
        arcTo(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 25f, 153f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.15f, 153.05f)
        arcToRelative(1.63f, 1.63f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.31f)
        arcToRelative(0.83f, 0.83f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.15f)
        reflectiveCurveToRelative(-0.06f, -0.1f, -0.11f, -0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.59f, 153f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.34f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17f, 152.49f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.27f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.63f, 153.05f)
        arcToRelative(0.86f, 0.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.05f, -0.32f)
        curveToRelative(0f, -0.08f, 0.08f, -0.24f, 0.19f, -0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.1f, 153f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.05f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.63f, 153f)
        arcToRelative(2.22f, 2.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.41f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 153.05f)
        arcToRelative(1.13f, 1.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.05f, -0.32f)
        curveToRelative(0f, -0.08f, 0.09f, -0.24f, 0.2f, -0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 152.49f)
        arcToRelative(0.72f, 0.72f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.38f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(30.86f, 153.05f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -0.34f)
        curveToRelative(0f, -0.09f, -0.12f, -0.22f, -0.24f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.23f, 153f)
        arcToRelative(0.69f, 0.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.4f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.65f, 153.05f)
        arcToRelative(0.82f, 0.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, -0.35f)
        arcToRelative(0.36f, 0.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, -0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.07f, 153f)
        curveToRelative(0f, -0.11f, -0.07f, -0.43f, 0.06f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.25f, 153f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.47f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.29f, 153.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.86f, 153f)
        curveToRelative(0f, -0.16f, 0f, -0.44f, 0.16f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.66f, 153f)
        arcToRelative(3.53f, 3.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.5f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(36.16f, 153.05f)
        curveToRelative(0f, -0.09f, 0f, -0.19f, 0f, -0.28f)
        verticalLineToRelative(-0.14f)
        arcToRelative(0.17f, 0.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.07f)
        arcToRelative(0.07f, 0.07f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.05f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.88f, 152.49f)
        arcToRelative(1.46f, 1.46f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.69f, 153.05f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.5f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.86f, 153f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.44f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.66f, 153f)
        arcToRelative(3.48f, 3.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.49f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(39.15f, 153.05f)
        arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.31f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.14f)
        reflectiveCurveToRelative(0f, -0.11f, -0.1f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.77f, 152.49f)
        curveToRelative(0f, 0.1f, -0.09f, 0.43f, -0.11f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.39f, 153.05f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.55f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.05f, 153f)
        arcToRelative(3.09f, 3.09f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.45f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.5f, 153.05f)
        arcToRelative(0.79f, 0.79f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.06f, -0.34f)
        curveToRelative(0f, -0.09f, -0.11f, -0.22f, -0.23f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.06f, 152.49f)
        curveToRelative(0.08f, 0.1f, 0f, 0.44f, 0f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.69f, 153f)
        arcToRelative(2.34f, 2.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.38f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.07f, 153.05f)
        arcToRelative(0.64f, 0.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.36f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.31f, -0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.58f, 152.49f)
        arcToRelative(0.35f, 0.35f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, 0.25f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.26f, 152.49f)
        arcToRelative(0.27f, 0.27f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.16f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.82f, 153f)
        lineToRelative(0f, 0f)
        lineToRelative(0.1f, -0.06f)
        lineToRelative(0.15f, -0.09f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25f, 153f)
        horizontalLineToRelative(-0.39f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.05f, 0f)
        lineToRelative(-0.08f, 0f)
        lineToRelative(-0.09f, 0f)
        lineToRelative(-0.16f, 0.09f)
        lineToRelative(-0.09f, 0.06f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.59f, 153f)
        lineToRelative(0f, 0f)
        lineToRelative(0.08f, -0.06f)
        lineToRelative(0.15f, -0.09f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0.06f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0.05f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.23f, 153f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, -0.06f)
        lineToRelative(0.13f, -0.09f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0.07f, 0f)
        lineToRelative(0.06f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.66f, 153f)
        horizontalLineToRelative(-0.5f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0f)
        lineToRelative(-0.07f, 0f)
        lineToRelative(-0.1f, 0.09f)
        lineToRelative(0f, 0.06f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.05f, 153f)
        horizontalLineToRelative(-0.42f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        arcToRelative(0.16f, 0.16f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
        arcToRelative(0.11f, 0.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.69f, 153f)
        horizontalLineToRelative(-0.34f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.86f, 153f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, -0.06f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.07f, 153f)
        lineToRelative(0f, 0f)
        lineToRelative(0.08f, -0.06f)
        lineToRelative(0.12f, -0.09f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.07f, 0f)
        lineToRelative(0.05f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.82f, 153f)
        horizontalLineToRelative(-0.19f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0f)
        lineToRelative(-0.09f, 0f)
        lineToRelative(-0.14f, 0.09f)
        lineToRelative(-0.09f, 0.06f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.1f, 153f)
        lineTo(18f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.68f, 165.24f)
        quadToRelative(0.06f, -6.11f, 0.14f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.07f, 153f)
        quadToRelative(-0.08f, 6.1f, -0.17f, 12.22f)
    }
}
private fun ImageVector.Builder.addAaruyPaths12() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.86f, 153f)
        quadToRelative(-0.11f, 6.1f, -0.19f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.34f, 153f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.87f, 165.24f)
        lineTo(42.69f, 153f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.53f, 153.05f)
        horizontalLineToRelative(-0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.79f, 165.24f)
        quadToRelative(-0.06f, -6.11f, -0.13f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(39.15f, 153.05f)
        horizontalLineToRelative(1.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.69f, 153.05f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.17f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(36.16f, 153.05f)
        horizontalLineToRelative(1.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.44f, 153.05f)
        arcToRelative(0.94f, 0.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.28f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.65f, 153.05f)
        arcToRelative(2.67f, 2.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.42f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(30.86f, 153.05f)
        arcToRelative(1.77f, 1.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.37f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 153.05f)
        horizontalLineToRelative(1.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.63f, 153.05f)
        arcToRelative(3.18f, 3.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.47f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.15f, 153.05f)
        arcToRelative(2.51f, 2.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.44f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.37f, 153.05f)
        horizontalLineToRelative(1.78f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.33f, 153.05f)
        arcToRelative(3.48f, 3.48f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.49f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.93f, 153.05f)
        horizontalLineToRelative(1.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.57f, 153.05f)
        arcToRelative(3.23f, 3.23f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.48f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.93f, 153.05f)
        horizontalLineToRelative(1.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.68f, 165.91f)
        horizontalLineToRelative(1.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.74f, 165.91f)
        lineToRelative(-0.11f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 165.91f)
        horizontalLineToRelative(0.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.83f, 165.91f)
        lineToRelative(-0.12f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16f, 165.91f)
        horizontalLineToRelative(0.58f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.75f, 165.91f)
        horizontalLineToRelative(0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.81f, 165.91f)
        lineToRelative(0.06f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.8f, 165.91f)
        verticalLineToRelative(-0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.66f, 165.91f)
        horizontalLineTo(35.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.8f, 165.91f)
        lineToRelative(0f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.8f, 165.91f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38f, 165.24f)
        lineToRelative(-0.12f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.68f, 165.24f)
        lineToRelative(0.07f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.14f, 165.91f)
        horizontalLineToRelative(1.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.37f, 165.91f)
        curveToRelative(0f, -0.22f, 0f, -0.44f, 0f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.37f, 165.91f)
        horizontalLineToRelative(2.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.77f, 165.24f)
        curveToRelative(0f, 0.23f, -0.07f, 0.45f, -0.11f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.9f, 165.24f)
        lineToRelative(0f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.48f, 165.91f)
        horizontalLineToRelative(1.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.73f, 165.91f)
        lineToRelative(-0.07f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.73f, 165.91f)
        horizontalLineToRelative(2.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.23f, 165.24f)
        lineToRelative(-0.09f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 165.24f)
        curveToRelative(0f, 0.23f, 0f, 0.45f, 0f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.92f, 165.91f)
        horizontalLineToRelative(1.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.12f, 165.91f)
        lineToRelative(-0.1f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.12f, 165.91f)
        horizontalLineToRelative(2.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.55f, 165.24f)
        curveToRelative(0f, 0.23f, 0f, 0.45f, -0.07f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.7f, 165.24f)
        curveToRelative(0f, 0.23f, 0f, 0.45f, 0f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.74f, 165.91f)
        horizontalLineToRelative(2.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(24f, 165.24f)
        curveToRelative(0f, 0.23f, 0f, 0.45f, 0f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.92f, 166.38f)
        horizontalLineToRelative(1.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.63f, 166.38f)
        horizontalLineToRelative(1.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29.11f, 166.38f)
        horizontalLineToRelative(1.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.58f, 166.38f)
        horizontalLineToRelative(1.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(22.27f, 166.38f)
        horizontalLineToRelative(1.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.81f, 165.91f)
        arcToRelative(0.89f, 0.89f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0.28f)
        curveToRelative(0f, 0.08f, -0.11f, 0.19f, -0.21f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.18f, 165.91f)
        arcToRelative(1.15f, 1.15f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.26f)
        lineToRelative(0f, 0.12f)
        reflectiveCurveToRelative(0f, 0.09f, -0.09f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.8f, 165.91f)
        verticalLineToRelative(0.23f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.12f)
        verticalLineToRelative(0.12f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.83f, 165.91f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.54f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.8f, 165.91f)
        arcToRelative(2.11f, 2.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.25f)
        lineToRelative(0f, 0.12f)
        reflectiveCurveToRelative(0f, 0.1f, 0.07f, 0.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.66f, 165.91f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.5f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.37f, 165.91f)
        arcToRelative(1.27f, 1.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.06f, 0.28f)
        curveToRelative(0f, 0.07f, 0.1f, 0.19f, 0.2f, 0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.14f, 165.91f)
        curveToRelative(0f, 0.21f, -0.17f, 0.47f, -0.42f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.73f, 165.91f)
        arcToRelative(0.77f, 0.77f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.11f, 0.3f)
        arcToRelative(0.33f, 0.33f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.27f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.48f, 165.91f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.29f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.23f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(23.92f, 165.91f)
        arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.26f)
        curveToRelative(0f, 0.06f, -0.06f, 0.21f, -0.15f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 165.91f)
        arcToRelative(1.94f, 1.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.24f)
        reflectiveCurveToRelative(0f, 0.08f, 0f, 0.12f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0.06f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16f, 165.91f)
        arcToRelative(1.28f, 1.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.05f, 0.27f)
        curveToRelative(0f, 0.06f, 0.07f, 0.2f, 0.17f, 0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.75f, 165.91f)
        arcToRelative(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.09f, 0.3f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, 0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.55f, 153.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.81f, 152.49f)
        horizontalLineToRelative(0.1f)
        lineToRelative(0.11f, 0f)
        lineToRelative(0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.43f, 152.93f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.41f, -0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.1f, 152.57f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.08f, 152.51f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.7f, 152.66f)
        lineToRelative(0f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.59f, 153.05f)
        lineTo(43.8f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.8f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.38f, 153.05f)
        lineTo(42.56f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.77f, 165.24f)
        lineTo(14.97f, 153.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.77f, 165.24f)
        lineToRelative(-0.12f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.45f, 165.91f)
        lineToRelative(0.11f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.53f, 165.24f)
        lineToRelative(-0.11f, 0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.57f, 165.24f)
        lineTo(40.57f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.92f, 165.91f)
        lineToRelative(-0.12f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.53f, 165.91f)
        lineToRelative(-0.12f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.64f, 165.91f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, 0.34f)
        lineToRelative(-0.08f, 0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.41f, 165.91f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.55f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.92f, 166.38f)
        horizontalLineTo(34.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.63f, 166.38f)
        horizontalLineTo(30.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.57f, 166.38f)
        horizontalLineTo(23.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.09f, 166.38f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.37f, -0.13f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.48f, 166.38f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.37f, -0.13f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.87f, 165.24f)
        horizontalLineToRelative(-0.3f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.22f, 165.24f)
        horizontalLineTo(40.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.77f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38f, 165.24f)
        horizontalLineTo(35.77f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.32f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.66f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.23f, 165.24f)
        horizontalLineTo(28.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.55f, 165.24f)
        horizontalLineTo(25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.63f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(24f, 165.24f)
        horizontalLineTo(21.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.71f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.8f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.42f, 152.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.27f, 151.85f)
        horizontalLineToRelative(-1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.55f, 153.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.05f, 152.5f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.59f, 153f)
        horizontalLineToRelative(0.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.46f, 153.05f)
        lineToRelative(-0.12f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.37f, 153.05f)
        lineToRelative(-0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.45f, 165.91f)
        horizontalLineToRelative(0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.42f, 152.5f)
        horizontalLineToRelative(0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.59f, 153f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.05f, 152.5f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.66f, 152.71f)
        lineToRelative(0f, 0f)
        arcToRelative(1.21f, 1.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.05f, 0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.9f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.38f, 165.24f)
        horizontalLineTo(15.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.68f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.77f, 165.24f)
        horizontalLineToRelative(-0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153.03f)
        lineTo(40.6f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.8f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.42f, 165.91f)
        horizontalLineToRelative(0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.45f, 165.91f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.9f, 165.24f)
        horizontalLineTo(14.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.67f, 165.24f)
        horizontalLineTo(14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.53f, 153.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.56f, 153.05f)
        horizontalLineToRelative(1.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.53f, 152.49f)
        horizontalLineTo(16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.48f, 165.24f)
        horizontalLineTo(41.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.53f, 165.91f)
        horizontalLineTo(18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.45f, 165.91f)
        arcToRelative(0.58f, 0.58f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, 0.34f)
        lineToRelative(-0.11f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29.11f, 166.38f)
        horizontalLineTo(27.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(91f, 251.62f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.81f, 152.49f)
        horizontalLineToRelative(0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.57f, 165.25f)
        lineToRelative(-0.12f, 0.66f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.52f, 152.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.77f, 402.61f)
        arcToRelative(0.26f, 0.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.22f, -0.17f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.76f, 400.15f)
        curveToRelative(0f, 0.41f, -0.07f, 0.83f, -0.11f, 1.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.59f, 250.43f)
        horizontalLineTo(5.52f)
        lineToRelative(-0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 245.09f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87f, 245.64f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, -0.06f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, -0.13f)
        arcToRelative(0.52f, 0.52f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.4f, 402.55f)
        arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.36f, -0.21f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, -0.17f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.13f, -0.22f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.1f, -0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.77f, 401.47f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.22f)
        arcToRelative(1.12f, 1.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.26f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.13f, 0.22f)
        arcToRelative(1.29f, 1.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.16f, 0.17f)
        arcToRelative(0.91f, 0.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.37f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(91f, 251.6f)
        lineToRelative(0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.77f, 165.24f)
        horizontalLineTo(32.32f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.21f, 152.49f)
        horizontalLineToRelative(0.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.39f, 153.05f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.52f, 152.49f)
        arcToRelative(0.34f, 0.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.17f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.76f, 152.49f)
        horizontalLineTo(15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.56f, 165.24f)
        horizontalLineTo(38.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15f, 153.05f)
        horizontalLineToRelative(0.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.52f, 252.73f)
        lineTo(63.08f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.86f, 252.73f)
        lineTo(86.1f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.1f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.18f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.47f, 252.73f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.45f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.97f, 254.16f)
        lineTo(61.97f, 254.17f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.98f, 253.74f)
        lineTo(61.97f, 254.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.62f, 254.67f)
        lineTo(89.62f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.61f, 254.41f)
        lineTo(89.61f, 254.43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.56f, 252.05f)
        lineToRelative(0.14f, 0.08f)
        arcToRelative(1.48f, 1.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.3f, 0.26f)
        arcToRelative(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.58f, 337.61f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.31f, 335.05f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.13f, 337.61f)
        arcToRelative(11.31f, 11.31f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.22f, 0.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 337.6f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.57f, 254.71f)
        horizontalLineTo(7.76f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.57f, 254.71f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.15f, 331.39f)
        lineTo(58.15f, 331.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.58f, 337.35f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 399.28f)
        horizontalLineToRelative(4.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.82f, 399.28f)
        lineTo(113.02f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.76f, 399.28f)
        lineTo(90.82f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.27f, 399.28f)
        lineTo(90.76f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.21f, 399.28f)
        lineTo(88.27f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(48.44f, 399.28f)
        lineTo(48.49f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.94f, 399.28f)
        lineTo(48.44f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.88f, 399.28f)
        lineTo(45.94f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.4f, 254.62f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.62f, 316.08f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.17f, 39.14f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(103.57f, 53.11f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 399.28f)
        verticalLineToRelative(0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.56f, 337.37f)
        lineTo(63.56f, 337.1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(73.15f, 337.1f)
        lineTo(73.15f, 337.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.8f, 256.67f)
        lineTo(54.54f, 256.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.57f, 254.58f)
        lineTo(4.57f, 254.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.57f, 254.71f)
        lineTo(4.57f, 254.62f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.6f, 177.71f)
        arcToRelative(4.15f, 4.15f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.82f, -0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 366f)
        arcToRelative(7f, 7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, 0.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(17.83f, 399.28f)
        lineToRelative(0.75f, 0.28f)
        curveToRelative(0.25f, 0.08f, 0.5f, 0.15f, 0.76f, 0.21f)
        reflectiveCurveToRelative(0.5f, 0.12f, 0.76f, 0.17f)
        arcToRelative(18.68f, 18.68f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.09f, 0.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.12f, 346.75f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
        curveToRelative(0f, -0.14f, 0f, -0.33f, 0f, -0.54f)
        reflectiveCurveToRelative(0f, -0.47f, 0f, -0.74f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 366.72f)
        lineToRelative(0.18f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.12f, 284.22f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(81.35f, 252.73f)
        curveToRelative(0.39f, -0.11f, 0.78f, -0.24f, 1.16f, -0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.62f, 254.67f)
        lineTo(110.35f, 254.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.62f, 254.61f)
        lineTo(90.91f, 254.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(49.86f, 178.45f)
        lineToRelative(-0.09f, -0.3f)
        arcToRelative(5.24f, 5.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.35f, -0.85f)
        arcToRelative(4.84f, 4.84f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.47f, -0.79f)
        arcToRelative(6.54f, 6.54f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.6f, -0.7f)
        arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.69f, -0.6f)
        arcToRelative(5.73f, 5.73f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.79f, -0.48f)
        arcToRelative(5.86f, 5.86f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.85f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.85f, 249.84f)
        horizontalLineTo(51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.69f, 254.27f)
        horizontalLineToRelative(0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 250.41f)
        horizontalLineTo(7f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.59f, 250.47f)
        horizontalLineToRelative(-0.07f)
        arcToRelative(0.74f, 0.74f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.22f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.07f, 254.71f)
        verticalLineToRelative(-0.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.1f, 252.73f)
        lineTo(86.1f, 252.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.61f, 254.41f)
        lineTo(90.91f, 254.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(110.9f, 254.71f)
        arcToRelative(1.13f, 1.13f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.32f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 287.13f)
        lineTo(8.13f, 285.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 242.13f)
        lineToRelative(-0.12f, 0.05f)
        lineToRelative(-0.06f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineTo(7f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0.05f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.86f, 252.72f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.74f)
        arcToRelative(0.11f, 0.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 254.18f)
        horizontalLineToRelative(0f)
        arcToRelative(0.39f, 0.39f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 254.55f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.11f, -0.08f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        arcToRelative(0.09f, 0.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.58f, 337.61f)
        lineTo(72.58f, 337.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.27f, 399.28f)
        lineTo(88.27f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.76f, 399.28f)
        lineTo(90.76f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.21f, 399.28f)
        curveToRelative(0f, 0.06f, 0f, 0.12f, 0f, 0.17f)
        reflectiveCurveToRelative(0f, 0.06f, 0f, 0.09f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.94f, 399.28f)
        lineTo(45.94f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(48.44f, 399.28f)
        lineTo(48.44f, 399.64f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(45.88f, 399.28f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.17f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.09f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.88f, 256.56f)
        horizontalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.13f, 337.35f)
        lineTo(64.13f, 337.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.08f, 337.59f)
        lineTo(64.08f, 337.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.62f, 337.33f)
        lineTo(72.62f, 337.6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 243.55f)
        curveToRelative(-0.92f, 1.84f, -1.82f, 3.68f, -2.65f, 5.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.64f, 280.4f)
        curveToRelative(0.28f, 0.59f, 0.59f, 1.17f, 0.89f, 1.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.19f, 171.87f)
        lineTo(11.69f, 171.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.73f, 399.28f)
        lineTo(79.81f, 400.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.37f, 252.73f)
        lineTo(66.37f, 252.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.67f, 250.47f)
        lineTo(50.85f, 245.83f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.78f, 180f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.58f, 166.38f)
        horizontalLineToRelative(1.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.46f, 166.38f)
        horizontalLineTo(37.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.57f, 174.84f)
        lineToRelative(1f, 0.4f)
        curveToRelative(1.68f, 0.65f, 3.34f, 1.32f, 5f, 2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.31f, 253.16f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.34f, 253.19f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.59f, 77.2f)
        horizontalLineToRelative(-0.32f)
        lineToRelative(-0.15f, 0f)
        horizontalLineTo(88f)
        reflectiveCurveToRelative(-0.07f, 0f, -0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 77.15f)
        arcToRelative(3.69f, 3.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.06f, 0.66f)
        curveToRelative(0f, 0.14f, -0.12f, 0.46f, -0.17f, 0.6f)
        arcToRelative(1.26f, 1.26f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.09f, 0.22f)
        reflectiveCurveToRelative(-0.11f, 0.24f, -0.18f, 0.35f)
        arcToRelative(2.2f, 2.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.19f, 0.3f)
        arcToRelative(3.21f, 3.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.39f, 0.46f)
        arcToRelative(4.21f, 4.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.16f, 0.79f)
        arcToRelative(3.33f, 3.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.43f, 0.3f)
    }
}
private fun ImageVector.Builder.addAaruyPaths13() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 84.71f)
        lineTo(68.4f, 84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 85f)
        verticalLineTo(82.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 32.76f)
        verticalLineTo(30f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.39f, 85.09f)
        horizontalLineToRelative(-0.55f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.24f, 80.83f)
        horizontalLineToRelative(11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.25f, 80.83f)
        horizontalLineToRelative(2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.28f, 81.44f)
        curveToRelative(0f, -0.11f, 0f, -0.21f, 0f, -0.31f)
        verticalLineTo(81f)
        arcToRelative(0.22f, 0.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.08f)
        verticalLineToRelative(-0.05f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.57f, 38.56f)
        horizontalLineTo(88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 38.59f)
        verticalLineToRelative(-0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 37.86f)
        arcToRelative(3.93f, 3.93f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.08f, -2.6f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.54f, -0.45f)
        arcToRelative(4.34f, 4.34f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.66f, -0.35f)
        arcToRelative(3.55f, 3.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.41f, -0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.27f, 33.57f)
        verticalLineTo(34f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.08f)
        verticalLineToRelative(0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.23f, 34.18f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.41f, -1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.82f, 82.24f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.41f, -1.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(71.26f, 81.58f)
        verticalLineTo(81f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
        verticalLineToRelative(-0.07f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115.48f, 260.94f)
        horizontalLineToRelative(7.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 261f)
        horizontalLineToRelative(-7.87f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115.07f, 261f)
        horizontalLineToRelative(-9.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.75f, 261f)
        horizontalLineTo(62.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.45f, 254.71f)
        horizontalLineToRelative(4.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.59f, 254.71f)
        horizontalLineTo(82.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.9f, 254.71f)
        horizontalLineToRelative(6.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(110.35f, 254.71f)
        horizontalLineToRelative(4.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.8f, 256.94f)
        verticalLineToRelative(-2.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115.07f, 261f)
        lineToRelative(-1.46f, -0.06f)
        lineToRelative(-1.38f, -0.05f)
        lineToRelative(-2.48f, -0.08f)
        curveToRelative(-1.53f, 0f, -2.8f, -0.06f, -3.82f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.92f, 256.94f)
        curveToRelative(0f, 0.33f, 0f, 0.67f, 0f, 1f)
        curveToRelative(0f, 0.73f, 0f, 1.46f, 0f, 2.19f)
        curveToRelative(0f, 0.27f, 0f, 0.55f, 0f, 0.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115f, 256.94f)
        curveToRelative(0f, 0.3f, 0f, 0.59f, 0f, 0.89f)
        lineToRelative(0.06f, 3.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.18f, 260.72f)
        curveToRelative(1.15f, 0f, 2.58f, 0f, 4.29f, -0.08f)
        lineToRelative(2.78f, -0.1f)
        lineToRelative(1.55f, -0.07f)
        lineToRelative(0.81f, 0f)
        lineToRelative(0.42f, 0f)
        lineToRelative(0.42f, 0f)
        quadToRelative(3.45f, -0.16f, 6f, -0.24f)
        curveToRelative(1.71f, -0.05f, 3.14f, -0.08f, 4.28f, -0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(82.92f, 256.94f)
        horizontalLineTo(62.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62.34f, 256.94f)
        quadToRelative(0f, -0.57f, 0f, -1.14f)
        curveToRelative(0f, -0.19f, 0f, -0.37f, 0.06f, -0.56f)
        arcToRelative(2.33f, 2.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.28f)
        arcToRelative(0.6f, 0.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, -0.19f)
        reflectiveCurveToRelative(0f, -0.06f, 0.05f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(115f, 256.94f)
        arcToRelative(9.18f, 9.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.07f, -1.17f)
        curveToRelative(0f, -0.19f, -0.06f, -0.39f, -0.11f, -0.58f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.27f)
        arcToRelative(0.51f, 0.51f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.09f, -0.16f)
        arcToRelative(0.14f, 0.14f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.92f, 256.94f)
        horizontalLineTo(115f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.92f, 256.94f)
        arcToRelative(2.27f, 2.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.27f, -2.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(105.9f, 261f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        verticalLineToRelative(-0.09f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(63.08f, 253.28f)
        lineTo(88.5f, 253.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.1f, 252.73f)
        lineTo(65.52f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.97f, 254.17f)
        lineTo(61.96f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88.5f, 253.28f)
        lineTo(88.5f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(89.61f, 254.43f)
        lineTo(89.62f, 254.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.42f, 253.29f)
        verticalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.34f, 253.85f)
        verticalLineToRelative(-0.54f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.76f, 254.69f)
        arcToRelative(1.49f, 1.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.2f, -0.69f)
        arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.42f, -0.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.19f, 254.69f)
        lineTo(58.19f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.4f, 254.76f)
        lineTo(55.42f, 253.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.34f, 253.85f)
        arcToRelative(0.67f, 0.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, 0.07f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.07f, 0.05f)
        lineToRelative(0.13f, 0.1f)
        lineToRelative(0f, 0.07f)
        lineToRelative(0.06f, 0.06f)
        arcToRelative(1.09f, 1.09f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.08f, 0.15f)
        arcToRelative(0.82f, 0.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.07f, 0.31f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.42f, 253.85f)
        lineTo(57.34f, 253.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.58f, 169.83f)
        horizontalLineTo(38.46f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42f, 175.07f)
        horizontalLineTo(16.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 180.6f)
        horizontalLineTo(7.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(29f, 178f)
        horizontalLineTo(9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(50.85f, 180.6f)
        horizontalLineTo(29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(49.1f, 178f)
        horizontalLineTo(29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.31f, 253.26f)
        lineTo(54.31f, 253.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.68f, 251f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(1.36f, 252.8f)
        lineTo(3.7f, 253.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.55f, 252.73f)
        lineToRelative(0.37f, 0f)
        lineToRelative(0.18f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.89f, 253.18f)
        lineToRelative(0.42f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.48f, 252.73f)
        lineTo(54.22f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.22f, 252.73f)
        arcToRelative(1.52f, 1.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.12f, 0.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.11f, 251.9f)
        curveToRelative(1f, 0.07f, 5.4f, 0.11f, 6.37f, 0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 254.03f)
        lineTo(1.35f, 253.99f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.56f, 254.37f)
        verticalLineToRelative(-0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.48f, 254.37f)
        curveToRelative(1.54f, -0.05f, 4.43f, -0.08f, 6f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.45f, 253.44f)
        curveToRelative(-1f, 0f, -5.72f, -0.05f, -6.75f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.69f, 254.31f)
        horizontalLineToRelative(0.79f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.34f, 253.32f)
        lineTo(54.34f, 253.01f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.79f, 254f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.13f, 0.36f)
        arcToRelative(0.65f, 0.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.18f, 0.15f)
        arcToRelative(0.66f, 0.66f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, 0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.42f, 254.61f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.14f, -0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.57f, 254.3f)
        verticalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.55f, 254.58f)
        arcToRelative(0.27f, 0.27f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.19f, -0.08f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -0.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.36f, 254.32f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0.21f)
        arcToRelative(0.18f, 0.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.07f, 0.05f)
        arcToRelative(0.12f, 0.12f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.92f, 254.6f)
        lineToRelative(0.67f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.27f, 254.65f)
        curveToRelative(-4.16f, -0.13f, -17.15f, -0.15f, -21.31f, -0.16f)
        curveToRelative(-7.1f, 0f, -14.2f, 0f, -21.31f, 0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.65f, 254.56f)
        curveToRelative(-1.52f, 0f, -4.36f, 0f, -5.87f, 0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.83f, 252.73f)
        lineTo(1.36f, 252.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.83f, 252.73f)
        arcToRelative(1.52f, 1.52f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.52f, -0.6f)
        arcToRelative(1.19f, 1.19f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, -0.17f)
        arcToRelative(1.34f, 1.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.4f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.28f, 253f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, -0.3f)
        arcToRelative(1.32f, 1.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, -0.44f)
        arcToRelative(1.29f, 1.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.45f, -0.29f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.53f, -0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.48f, 254.37f)
        arcToRelative(0.32f, 0.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.15f, 0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(10.45f, 254.28f)
        curveToRelative(7.18f, -0.07f, 14.37f, -0.08f, 21.55f, -0.07f)
        curveToRelative(4.18f, 0f, 17.38f, 0f, 21.56f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.54f, 252.73f)
        curveToRelative(0f, -0.3f, 0f, -0.59f, 0f, -0.86f)
        reflectiveCurveToRelative(0f, -0.27f, 0f, -0.39f)
        reflectiveCurveToRelative(0f, -0.24f, 0f, -0.35f)
        arcToRelative(2.46f, 2.46f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.28f)
        curveToRelative(0f, -0.08f, 0f, -0.15f, 0f, -0.21f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.13f)
        arcToRelative(0.06f, 0.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.62f, 337.6f)
        arcToRelative(10.55f, 10.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.48f, -1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70f, 315.78f)
        horizontalLineToRelative(-0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.34f, 315.7f)
        arcToRelative(11.61f, 11.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.35f, 314.68f)
        arcToRelative(12.46f, 12.46f, 0f, isMoreThanHalf = true, isPositiveArc = true, -8.8f, 3.65f)
        arcTo(12.44f, 12.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 68.35f, 314.68f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.35f, 338.74f)
        arcToRelative(11.61f, 11.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -23.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.99f, 365.87f)
        lineTo(122.99f, 377.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.99f, 260.95f)
        lineTo(122.99f, 346.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.33f, 253f)
        horizontalLineTo(0.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 284.27f)
        horizontalLineToRelative(-37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.51f, 272.93f)
        horizontalLineToRelative(49f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.7f, 255.28f)
        horizontalLineTo(52.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(52.84f, 255.28f)
        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.23f, 0.76f)
        curveToRelative(0f, 0.14f, 0f, 0.3f, -0.06f, 0.46f)
        arcToRelative(3.7f, 3.7f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(2.94f, 256.69f)
        verticalLineToRelative(2.82f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(2.94f, 256.69f)
        lineTo(3.7f, 256.69f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.11f, 259.53f)
        lineTo(3.51f, 259.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 284.34f)
        lineTo(9.18f, 289.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113.02f, 377.73f)
        lineTo(113.02f, 260.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122f, 363.65f)
        arcToRelative(16.65f, 16.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.22f, 1.66f)
        curveToRelative(0.09f, 0.54f, 0.19f, 1f, 0.3f, 1.48f)
        curveToRelative(0f, 0.23f, 0.1f, 0.45f, 0.15f, 0.65f)
        reflectiveCurveToRelative(0.09f, 0.38f, 0.13f, 0.55f)
        reflectiveCurveToRelative(0.07f, 0.33f, 0.09f, 0.47f)
        arcToRelative(1.84f, 1.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 343.25f)
        arcToRelative(1.91f, 1.91f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.39f)
        curveToRelative(0f, 0.14f, -0.05f, 0.29f, -0.09f, 0.46f)
        reflectiveCurveToRelative(-0.08f, 0.36f, -0.13f, 0.56f)
        reflectiveCurveToRelative(-0.1f, 0.42f, -0.15f, 0.64f)
        curveToRelative(-0.11f, 0.46f, -0.21f, 0.95f, -0.3f, 1.48f)
        reflectiveCurveToRelative(-0.17f, 1.09f, -0.22f, 1.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 343.25f)
        lineTo(122.94f, 260.95f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 377.73f)
        lineTo(122.94f, 368.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.91f, 350.38f)
        curveToRelative(0f, -2.38f, 0f, -4.75f, 0f, -7.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 368.85f)
        quadToRelative(0f, -3.57f, 0f, -7.13f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 377.73f)
        horizontalLineToRelative(37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(2.94f, 254.71f)
        lineTo(4.22f, 254.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.72f, 255.14f)
        lineTo(2.94f, 255.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(54.54f, 257f)
        verticalLineToRelative(-2.27f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(4.22f, 254.71f)
        horizontalLineToRelative(0.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.76f, 254.71f)
        horizontalLineToRelative(45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.94f, 316.19f)
        lineTo(69.94f, 315.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70f, 315.78f)
        lineToRelative(-0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.77f, 316.19f)
        lineTo(66.77f, 315.75f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.77f, 315.75f)
        lineToRelative(-0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.7f, 315.78f)
        lineToRelative(-0.19f, 0.23f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.19f, 316f)
        lineToRelative(-0.18f, -0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.34f, 316f)
        lineToRelative(-0.14f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.51f, 316f)
        lineToRelative(-0.14f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.37f, 316f)
        arcToRelative(11.28f, 11.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, -8.22f, 6.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(70.19f, 316f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 361.72f)
        curveToRelative(0f, 0.41f, 0f, 3.74f, 0f, 4.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(123f, 346.23f)
        curveToRelative(0f, 0.41f, 0f, 3.74f, 0f, 4.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.61f, 336.18f)
        arcToRelative(10.49f, 10.49f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.47f, 1.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(0.52f, 254.71f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.33f, -0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.12f, 284.22f)
        arcToRelative(14.17f, 14.17f, 0f, isMoreThanHalf = false, isPositiveArc = true, -5.61f, -11.29f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(111.94f, 57.77f)
        verticalLineToRelative(1.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(108.15f, 58.06f)
        verticalLineToRelative(-5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(111.94f, 55.38f)
        verticalLineToRelative(2.39f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(108.15f, 61.92f)
        verticalLineTo(58.06f)
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(76.75f, 325.72f)
        arcToRelative(2.53f, 2.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.28f, -0.47f)
        arcToRelative(1.35f, 1.35f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.16f, -0.19f)
        arcToRelative(3.11f, 3.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.07f, -0.83f)
        arcToRelative(3.22f, 3.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.22f, -0.29f)
        arcToRelative(3.08f, 3.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.89f, 0.52f)
        arcToRelative(5.73f, 5.73f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.11f, 1f)
        lineToRelative(1.21f, 1.23f)
        curveToRelative(0.31f, -0.21f, 0.83f, -1f, 1.48f, -1f)
        horizontalLineToRelative(0.35f)
        arcTo(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 75.29f, 327f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.11f, 1.43f)
        curveToRelative(-0.39f, -0.24f, -2f, -1.89f, -2.41f, -2.33f)
        arcToRelative(10.53f, 10.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.94f, -1f)
        curveToRelative(0.45f, -0.67f, 2.75f, -1.89f, 1.66f, -4.58f)
        arcToRelative(3.15f, 3.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.1f, -1.32f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, 4.94f)
        arcToRelative(5.17f, 5.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.58f, 0.57f)
        lineToRelative(1.24f, -1.26f)
        arcToRelative(6.41f, 6.41f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.65f, -0.63f)
        arcToRelative(1.44f, 1.44f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, -2.43f)
        arcTo(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, 70f, 321.89f)
        arcToRelative(1.62f, 1.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.66f, 1.19f)
        curveToRelative(-0.37f, 0.37f, -2.66f, 2.62f, -2.78f, 2.8f)
        curveToRelative(-0.19f, -0.12f, -0.91f, -0.91f, -1.3f, -1.23f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.14f, -0.71f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, 3.05f)
        arcToRelative(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.77f, 2.24f)
        arcToRelative(3.18f, 3.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.64f, 1f)
        arcToRelative(3.37f, 3.37f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.19f, -0.22f)
        arcToRelative(5.24f, 5.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.45f, -1.24f)
        lineToRelative(-1.21f, -1.24f)
        curveToRelative(-0.23f, 0.16f, -0.46f, 0.47f, -0.68f, 0.66f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.38f, -0.92f)
        lineTo(61.9f, 327f)
        arcToRelative(1.42f, 1.42f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.23f, -1.23f)
        curveToRelative(0.78f, 0f, 0.91f, 0.11f, 1.49f, 0.69f)
        curveToRelative(0.36f, 0.36f, 2.62f, 2.57f, 2.74f, 2.76f)
        curveToRelative(-0.44f, 0.66f, -2.76f, 1.92f, -1.65f, 4.61f)
        arcToRelative(3.21f, 3.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6.12f, -1.33f)
        curveToRelative(0f, -1.74f, -1.22f, -2.41f, -1.54f, -2.89f)
        lineTo(69f, 330.8f)
        curveToRelative(0.4f, 0.6f, 1f, 0.65f, 1f, 1.73f)
        arcToRelative(1.43f, 1.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.73f, 0.51f)
        arcToRelative(1.57f, 1.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, -1.83f)
        curveToRelative(0.38f, -0.37f, 2.67f, -2.71f, 2.86f, -2.84f)
        arcToRelative(9.45f, 9.45f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.7f, 0.73f)
        arcToRelative(3.29f, 3.29f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 1.2f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.74f, -3f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 76.75f, 325.72f)
        close()
        moveTo(68.57f, 327.92f)
        lineTo(67.78f, 327.14f)
        lineTo(68.57f, 326.35f)
        lineTo(69.35f, 327.14f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.58f, 337.35f)
        arcToRelative(11f, 11f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, -6f)
        arcToRelative(11.07f, 11.07f, 0f, isMoreThanHalf = false, isPositiveArc = true, -6f, 6f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.12f, 327.14f)
        arcTo(10.77f, 10.77f, 0f, isMoreThanHalf = true, isPositiveArc = false, 76f, 334.75f)
        arcTo(10.75f, 10.75f, 0f, isMoreThanHalf = false, isPositiveArc = false, 79.12f, 327.14f)
        close()
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(61.78f, 318.25f)
        lineToRelative(0.08f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.96f, 361.72f)
        lineTo(124.41f, 361.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 350.38f)
        lineTo(122.95f, 350.38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 350.38f)
        verticalLineToRelative(-3.62f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 346.75f)
        lineTo(122.97f, 346.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 365.35f)
        lineTo(122.97f, 365.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.41f, 365.35f)
        verticalLineToRelative(-3.63f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 365.06f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(-3.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(124.69f, 350.38f)
        verticalLineToRelative(-3.32f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(48.49f, 399.28f)
        lineTo(88.21f, 399.28f)
    }
}
private fun ImageVector.Builder.addAaruyPaths14() {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(117.32f, 399.28f)
        arcToRelative(5.67f, 5.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.67f, -5.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113f, 393.61f)
        horizontalLineToRelative(9.92f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(122.94f, 377.73f)
        horizontalLineTo(113f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 377.73f)
        lineTo(46.21f, 393.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 393.61f)
        lineTo(113.02f, 393.61f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(9.18f, 393.61f)
        horizontalLineToRelative(37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(46.21f, 377.73f)
        horizontalLineToRelative(-37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(113f, 393.61f)
        verticalLineToRelative(5.66f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(117.27f, 399.28f)
        arcToRelative(5.67f, 5.67f, 0f, isMoreThanHalf = false, isPositiveArc = false, 5.67f, -5.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.85f, 399.28f)
        horizontalLineTo(42.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.54f, 399.28f)
        horizontalLineToRelative(0.37f)
        lineToRelative(0.35f, -0.07f)
        arcToRelative(2.94f, 2.94f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.67f, -0.27f)
        arcToRelative(3.2f, 3.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.62f, -0.46f)
        curveToRelative(0.09f, -0.09f, 0.19f, -0.19f, 0.28f, -0.3f)
        lineToRelative(0.27f, -0.35f)
        arcToRelative(5.55f, 5.55f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.46f, -0.84f)
        curveToRelative(0.07f, -0.16f, 0.13f, -0.32f, 0.19f, -0.49f)
        reflectiveCurveToRelative(0.11f, -0.34f, 0.16f, -0.52f)
        reflectiveCurveToRelative(0.09f, -0.37f, 0.13f, -0.56f)
        arcToRelative(5.4f, 5.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.09f, -0.58f)
        arcToRelative(9.71f, 9.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.08f, -1.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(85.94f, 57.5f)
        arcToRelative(17.54f, 17.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -35.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 31.53f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        arcToRelative(0.13f, 0.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.06f)
        reflectiveCurveToRelative(0f, -0.06f, 0f, -0.08f)
        reflectiveCurveToRelative(0f, -0.12f, 0f, -0.17f)
        verticalLineToRelative(-0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 30.86f)
        arcToRelative(1.06f, 1.06f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.31f, 0.05f)
        arcToRelative(1.48f, 1.48f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.29f, 0.12f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, 0.19f)
        arcToRelative(2.31f, 2.31f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.2f, 0.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.92f, 31.53f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        arcToRelative(0.19f, 0.19f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.08f)
        verticalLineTo(31.2f)
        curveToRelative(0f, -0.12f, 0f, -0.23f, 0f, -0.34f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.22f, 31.53f)
        reflectiveCurveToRelative(0f, -0.06f, 0f, -0.07f)
        arcToRelative(0.29f, 0.29f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.09f)
        arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.18f)
        curveToRelative(0f, -0.12f, 0f, -0.24f, 0f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.3f, 33.16f)
        arcToRelative(2.9f, 2.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.83f, -2.33f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.18f, 84.2f)
        arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.88f, -2.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 22.07f)
        verticalLineToRelative(0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.83f, 68.11f)
        arcToRelative(9.17f, 9.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1f, 3.32f)
        arcToRelative(10.32f, 10.32f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.76f, 3.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.19f, 54.81f)
        arcToRelative(0.47f, 0.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.1f, -0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.52f, 74.65f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.1f, -0.71f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.83f, 68.33f)
        arcToRelative(0.43f, 0.43f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.25f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.58f, 47f)
        arcToRelative(0.45f, 0.45f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.26f, -0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.66f, 41.08f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.14f, -0.73f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.83f, 75.87f)
        horizontalLineTo(88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(55.51f, 75.87f)
        horizontalLineToRelative(2.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.14f, 39.14f)
        horizontalLineTo(55.51f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 39.14f)
        horizontalLineTo(69.82f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 92.71f)
        verticalLineToRelative(0.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 92.36f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.22f)
        arcToRelative(0.76f, 0.76f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.12f, 0.19f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.41f, 0.16f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.83f, 22.07f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.22f, 0f)
        arcToRelative(0.59f, 0.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.36f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 37.72f)
        verticalLineTo(29.89f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.86f, 33.89f)
        verticalLineToRelative(0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.85f, 34.39f)
        verticalLineToRelative(2.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 82.81f)
        arcToRelative(0.68f, 0.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.11f, 0.37f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 85.11f)
        verticalLineTo(77.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 82.81f)
        reflectiveCurveToRelative(0.05f, 0f, 0.06f, 0f)
        lineToRelative(0.07f, 0f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.18f, 84.2f)
        curveToRelative(0f, -0.12f, 0f, -0.24f, 0f, -0.37f)
        verticalLineToRelative(-0.18f)
        reflectiveCurveToRelative(0f, -0.06f, 0f, -0.09f)
        reflectiveCurveToRelative(0f, -0.08f, 0f, -0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(57.88f, 84.15f)
        verticalLineToRelative(-0.34f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.17f)
        arcToRelative(0.28f, 0.28f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.09f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 84.14f)
        verticalLineToRelative(-0.33f)
        curveToRelative(0f, -0.06f, 0f, -0.12f, 0f, -0.17f)
        reflectiveCurveToRelative(0f, -0.06f, 0f, -0.09f)
        arcToRelative(0.13f, 0.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.06f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.06f, 84.21f)
        arcToRelative(1.31f, 1.31f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.27f, -0.06f)
        arcToRelative(1.11f, 1.11f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.29f, -0.14f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.25f, -0.21f)
        arcToRelative(2.56f, 2.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, -0.26f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(67.84f, 29.84f)
        horizontalLineToRelative(0.5f)
        reflectiveCurveToRelative(0.07f, 0f, 0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.91f, 33.38f)
        arcToRelative(2.54f, 2.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.34f, -0.8f)
        arcToRelative(2.61f, 2.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.47f, -0.51f)
        horizontalLineToRelative(0f)
        lineToRelative(0f, 0f)
        lineToRelative(0.09f, -0.07f)
        lineToRelative(0.17f, -0.1f)
        arcToRelative(1.84f, 1.84f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.38f, -0.17f)
        arcToRelative(2.33f, 2.33f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.78f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.25f, 33.83f)
        horizontalLineToRelative(0.31f)
        lineToRelative(0.15f, 0f)
        horizontalLineToRelative(0.08f)
        reflectiveCurveToRelative(0.06f, 0f, 0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.26f, 34.35f)
        horizontalLineToRelative(0.52f)
        lineToRelative(0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(53.21f, 36.77f)
        arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.33f, 0f)
        lineToRelative(0.17f, 0f)
        lineToRelative(0.08f, 0f)
        reflectiveCurveToRelative(0.06f, 0f, 0.06f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(69.86f, 39.28f)
        lineToRelative(2.24f, 0f)
        lineToRelative(2.34f, 0f)
        horizontalLineToRelative(1.07f)
        lineToRelative(1.1f, 0f)
        lineToRelative(2.29f, 0f)
        horizontalLineTo(80f)
        lineToRelative(1.07f, 0f)
        lineToRelative(2.21f, 0.05f)
        lineToRelative(4.73f, 0.11f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(88f, 75.36f)
        lineToRelative(-4.73f, 0.11f)
        lineToRelative(-4.34f, 0.11f)
        horizontalLineTo(77.63f)
        lineToRelative(-1.21f, 0f)
        lineToRelative(-2.29f, 0f)
        curveToRelative(-1.47f, 0f, -2.88f, 0f, -4.27f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58f, 39.42f)
        arcToRelative(0.94f, 0.94f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.16f)
        reflectiveCurveToRelative(0.06f, -0.12f, 0.12f, -0.12f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(68.41f, 91.45f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.58f, 0.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34f, 60.35f)
        arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.19f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.64f, 46.73f)
        horizontalLineToRelative(0f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.15f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.62f, 41.06f)
        lineToRelative(-0.01f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.01f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.62f, 73.94f)
        lineTo(38.6f, 73.94f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 76.28f)
        arcToRelative(0.08f, 0.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 0f)
        arcToRelative(0.62f, 0.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.11f, -0.19f)
        lineTo(58.5f, 76f)
        lineToRelative(-0.1f, -0.06f)
        lineToRelative(-0.11f, 0f)
        horizontalLineTo(58.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.2f, 39.14f)
        horizontalLineToRelative(0.09f)
        lineToRelative(0.11f, 0f)
        arcToRelative(0.21f, 0.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.1f, -0.06f)
        lineToRelative(0.09f, -0.07f)
        lineToRelative(0.07f, -0.09f)
        arcToRelative(0.3f, 0.3f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.1f)
        arcToRelative(0.08f, 0.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 252.16f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.85f, -0.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(60.82f, 252f)
        arcTo(1.13f, 1.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 62f, 253.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.79f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.57f, 0.57f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.91f, 254.37f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.56f, -0.55f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 251.34f)
        arcToRelative(0.85f, 0.85f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.73f, 0.81f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.81f, 400.15f)
        lineToRelative(0.42f, 0f)
        horizontalLineToRelative(0.4f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.65f, 400.15f)
        lineTo(80.58f, 399.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.84f, 399.28f)
        lineTo(56.76f, 400.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(79.92f, 402.15f)
        arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.58f, -0.21f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.26f, -0.54f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(80.76f, 401.4f)
        curveToRelative(0f, -0.42f, -0.07f, -0.84f, -0.11f, -1.25f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(56.76f, 400.15f)
        lineTo(56.76f, 400.2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(120.33f, 401.54f)
        arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.3f, -1.67f)
        arcToRelative(9.06f, 9.06f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.66f, -6.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(65.52f, 252.15f)
        lineTo(65.52f, 252.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(64.93f, 252.16f)
        horizontalLineToRelative(0.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(62f, 253.15f)
        lineToRelative(-0.16f, 0f)
        horizontalLineToRelative(-0.45f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(66.91f, 245f)
        horizontalLineToRelative(-1f)
        lineToRelative(-1f, 0f)
        horizontalLineToRelative(-0.75f)
        lineToRelative(-0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(58.72f, 254.4f)
        lineTo(61.39f, 254.4f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.18f, 243.89f)
        verticalLineToRelative(-0.09f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(7.2f, 212.19f)
        horizontalLineTo(6.86f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(51.18f, 243.81f)
        reflectiveCurveToRelative(0f, 0.11f, -0.06f, 0.19f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(-0.06f, 0.07f)
        lineToRelative(-0.16f, 0.17f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.86f, 180f)
        horizontalLineToRelative(0.41f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.87f, 242.15f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 250.82f)
        lineTo(87.51f, 250.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(93.18f, 252.6f)
        horizontalLineTo(90.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.35f, 252.66f)
        lineTo(90.35f, 253.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.51f, 249.09f)
        lineToRelative(-0.08f, 0f)
        horizontalLineTo(84.68f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(84.68f, 245f)
        horizontalLineToRelative(2.75f)
        lineToRelative(0.08f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.1f, 252.71f)
        lineTo(86.1f, 252.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(86.1f, 252.16f)
        horizontalLineToRelative(0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(5.57f, 254.71f)
        verticalLineToRelative(-0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(3.5f, 249.29f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        lineToRelative(0.08f, 0f)
        lineToRelative(0.09f, 0f)
        lineToRelative(0.18f, 0.1f)
        lineToRelative(0.37f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.87f, 242.16f)
        arcTo(0.21f, 0.21f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 242f)
        lineToRelative(0.11f, -0.05f)
        lineToRelative(0.07f, 0f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(6.86f, 175.57f)
        horizontalLineTo(8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(11.69f, 171.87f)
        verticalLineToRelative(-1.12f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(19.33f, 171.48f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.11f, -0.21f)
        lineToRelative(-0.05f, -0.09f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        lineToRelative(-0.14f, -0.27f)
        curveToRelative(0f, -0.05f, -0.05f, -0.08f, 0f, -0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.71f, 252.27f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.22f, 0.42f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.74f, 252.23f)
        lineToRelative(0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(87.71f, 252.27f)
        arcToRelative(1.38f, 1.38f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.85f, 0.44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(72.58f, 337.61f)
        lineTo(72.58f, 337.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(90.4f, 252.3f)
        arcToRelative(1.08f, 1.08f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, -0.47f)
        arcToRelative(1.18f, 1.18f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.25f, -0.19f)
        lineToRelative(0.07f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.79f, 165.24f)
        horizontalLineTo(38f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.7f, 165.24f)
        horizontalLineToRelative(-2f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18f, 165.24f)
        horizontalLineToRelative(-1.5f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.45f, 165.24f)
        horizontalLineToRelative(-0.07f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.67f, 152.49f)
        horizontalLineTo(43f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.55f, 152.49f)
        horizontalLineToRelative(0.28f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.29f, 152.49f)
        horizontalLineToRelative(1.59f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.42f, 152.49f)
        horizontalLineToRelative(1.35f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.27f, 151.85f)
        horizontalLineTo(14.77f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.33f, 153.05f)
        arcToRelative(2.81f, 2.81f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
        reflectiveCurveToRelative(0f, -0.1f, 0f, -0.14f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, -0.07f)
        reflectiveCurveToRelative(0f, -0.06f, 0.05f, -0.06f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.56f, 153.05f)
        arcToRelative(0.57f, 0.57f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.57f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.72f, 153.05f)
        arcToRelative(2.71f, 2.71f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.29f)
        lineToRelative(0f, -0.15f)
        arcToRelative(0.25f, 0.25f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.07f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.55f, 152.49f)
        arcToRelative(0.87f, 0.87f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.3f, 0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.44f, 153.05f)
        arcToRelative(0.69f, 0.69f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.1f, -0.37f)
        arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.32f, -0.19f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.72f, 153f)
        arcToRelative(0.63f, 0.63f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.43f, -0.53f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.51f, 153.05f)
        curveToRelative(0f, -0.26f, 0.16f, -0.56f, 0.46f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.76f, 153.05f)
        arcToRelative(0.53f, 0.53f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.49f, -0.56f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.86f, 153f)
        verticalLineToRelative(0f)
        reflectiveCurveToRelative(0f, 0f, 0f, -0.06f)
        arcToRelative(0.47f, 0.47f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.06f, -0.09f)
        lineToRelative(0.06f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        lineToRelative(0f, 0f)
        horizontalLineToRelative(0f)
        verticalLineToRelative(0f)
        horizontalLineToRelative(0.48f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.82f, 153f)
        quadToRelative(-0.06f, 6.1f, -0.11f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.6f, 165.24f)
        quadToRelative(0f, -6.11f, 0.1f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.76f, 153.05f)
        horizontalLineTo(44f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.07f, 153.05f)
        horizontalLineToRelative(0.52f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.22f, 165.24f)
        quadToRelative(-0.09f, -6.11f, -0.17f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.5f, 153.05f)
        horizontalLineToRelative(0.88f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.86f, 153f)
        lineTo(38f, 165.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(34.72f, 153f)
        quadToRelative(0f, 6.1f, 0.08f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(35.76f, 165.24f)
        quadToRelative(0f, -6.11f, -0.1f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.51f, 153.05f)
        arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.35f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(31.23f, 153f)
        lineToRelative(0f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.3f, 165.24f)
        lineToRelative(0f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(32.72f, 153.05f)
        horizontalLineToRelative(1.72f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(27.59f, 153f)
        quadToRelative(0f, 6.1f, 0f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(28.63f, 165.24f)
        verticalLineTo(153f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(24.05f, 153f)
        quadToRelative(0f, 6.1f, -0.07f, 12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25f, 165.24f)
        quadToRelative(0f, -6.11f, 0.05f, -12.22f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.81f, 165.91f)
        horizontalLineToRelative(0.84f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.18f, 165.91f)
        curveToRelative(0f, -0.22f, 0f, -0.44f, 0f, -0.67f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(41.18f, 165.91f)
        horizontalLineToRelative(1.24f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(37.83f, 165.91f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(38.8f, 165.91f)
        horizontalLineToRelative(1.65f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.83f, 165.91f)
        horizontalLineToRelative(1.85f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.12f, 165.91f)
        arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.46f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(21.74f, 165.91f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.53f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(20.68f, 165.91f)
        arcToRelative(1.93f, 1.93f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.24f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.12f)
        arcToRelative(0.13f, 0.13f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.06f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(18.83f, 165.91f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.55f, 0.47f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(25.25f, 147.67f)
        horizontalLineTo(32.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.55f, 153f)
        arcToRelative(0.65f, 0.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.19f)
        lineToRelative(0.07f, -0.14f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15f, 153.05f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -0.16f)
        arcToRelative(0.61f, 0.61f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.15f, -0.24f)
        arcToRelative(0.55f, 0.55f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.28f, -0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(15.51f, 152.49f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153f)
        verticalLineTo(153f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.1f, 152.57f)
        lineToRelative(0.11f, 0.08f)
        arcToRelative(0.8f, 0.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.13f, 0.19f)
        arcToRelative(0.78f, 0.78f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.21f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43f, 152.49f)
        horizontalLineToRelative(0.08f)
        arcToRelative(0.56f, 0.56f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.32f, 0.15f)
        arcToRelative(0.49f, 0.49f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.14f, 0.24f)
        arcToRelative(0.4f, 0.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 0.15f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(44.19f, 165.24f)
        horizontalLineTo(43.8f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.77f, 165.24f)
        horizontalLineToRelative(-0.9f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(42.56f, 165.24f)
        horizontalLineToRelative(-0.08f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.44f, 153.03f)
        lineTo(40.44f, 153.03f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(40.6f, 165.24f)
        horizontalLineToRelative(0f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.15f, 153.05f)
        horizontalLineToRelative(0.36f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(16.38f, 165.24f)
        lineTo(16.55f, 153.05f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(14.92f, 165.91f)
        horizontalLineToRelative(1f)
    }
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 0.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ) {
        moveTo(43.37f, 166.3f)
        arcToRelative(0.54f, 0.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.28f, 0.08f)
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(98.36f, 271.91f)
        arcToRelative(3.25f, 3.25f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.61f, 2f)
        arcToRelative(2.22f, 2.22f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.78f, 0.83f)
        arcToRelative(2.62f, 2.62f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -0.87f)
        lineToRelative(0.09f, 0.88f)
        horizontalLineToRelative(1.28f)
        reflectiveCurveToRelative(0f, 0f, 0f, 0f)
        curveToRelative(0f, -0.16f, 0f, -0.32f, 0f, -0.47f)
        curveToRelative(0f, -0.63f, 0f, -1.26f, 0f, -1.89f)
        lineToRelative(0f, -3.77f)
        horizontalLineToRelative(-1.44f)
        curveToRelative(0f, 1.07f, 0f, 2.14f, 0f, 3.21f)
        arcToRelative(1.54f, 1.54f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, 1.63f)
        arcToRelative(1.28f, 1.28f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.15f, -0.59f)
        arcToRelative(2.36f, 2.36f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.25f, -1.38f)
        curveToRelative(0f, -1f, 0f, -1.91f, 0f, -2.87f)
        horizontalLineTo(98.37f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(93.13f, 274.75f)
        horizontalLineToRelative(1.44f)
        curveToRelative(0f, -1.09f, 0f, -2.17f, 0f, -3.25f)
        arcTo(1.34f, 1.34f, 0f, isMoreThanHalf = false, isPositiveArc = true, 96f, 270f)
        arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 0.39f)
        lineToRelative(0.33f, -0.63f)
        curveToRelative(0.11f, -0.2f, 0.23f, -0.41f, 0.33f, -0.62f)
        arcToRelative(2.24f, 2.24f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.5f, -0.56f)
        arcToRelative(1.71f, 1.71f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.65f, 0.83f)
        curveToRelative(0f, -0.28f, -0.07f, -0.56f, -0.11f, -0.83f)
        horizontalLineTo(93.13f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(108.57f, 271.34f)
        lineTo(108f, 273f)
        lineToRelative(-0.54f, -1.65f)
        curveToRelative(-0.28f, -0.89f, -0.57f, -1.78f, -0.86f, -2.67f)
        horizontalLineToRelative(-1.68f)
        lineToRelative(2.27f, 5.88f)
        lineTo(106f, 277f)
        horizontalLineToRelative(1.58f)
        lineToRelative(2.17f, -5f)
        curveToRelative(0.36f, -0.83f, 0.72f, -1.66f, 1.09f, -2.49f)
        lineToRelative(0.33f, -0.76f)
        lineToRelative(0.06f, -0.15f)
        horizontalLineToRelative(-1.59f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(92f, 270.92f)
        verticalLineToRelative(-2.31f)
        horizontalLineTo(90.58f)
        curveToRelative(0f, 0.31f, 0f, 0.63f, 0f, 0.95f)
        arcToRelative(2.15f, 2.15f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.86f, -1f)
        arcToRelative(3.08f, 3.08f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 6.15f)
        arcToRelative(2.21f, 2.21f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.89f, -1f)
        lineToRelative(0.07f, 1f)
        horizontalLineTo(92f)
        close()
        moveTo(90f, 273f)
        arcToRelative(1.59f, 1.59f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.21f, 0.47f)
        arcToRelative(1.68f, 1.68f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.73f, -1.77f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.72f, -1.76f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.21f, 0.48f)
        arcToRelative(1.82f, 1.82f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.5f, 1.28f)
        arcTo(1.86f, 1.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, 90f, 273f)
        close()
    }
    path(fill = SolidColor(Color(0xFFDDDCD5))) {
        moveTo(84.79f, 268.59f)
        lineTo(83.4f, 268.59f)
        curveToRelative(0f, 0.32f, 0f, 0.64f, 0f, 0.95f)
        arcToRelative(2.17f, 2.17f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.86f, -0.95f)
        arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, 3.05f)
        arcToRelative(2.85f, 2.85f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3f, 3.1f)
        arcToRelative(2.18f, 2.18f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.87f, -1f)
        curveToRelative(0f, 0.34f, 0.06f, 0.68f, 0.08f, 1f)
        horizontalLineToRelative(1.36f)
        quadTo(84.8f, 271.66f, 84.79f, 268.59f)
        close()
        moveTo(82.88f, 272.93f)
        arcToRelative(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.21f, 0.48f)
        arcToRelative(1.69f, 1.69f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.74f, -1.78f)
        arcToRelative(1.64f, 1.64f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.71f, -1.76f)
        arcToRelative(1.67f, 1.67f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.21f, 0.48f)
        arcToRelative(1.86f, 1.86f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.51f, 1.28f)
        arcTo(1.9f, 1.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 82.88f, 272.93f)
        close()
    }
}
@Suppress("ObjectPropertyName")
private var _AaruyPumpBind: ImageVector? = null
