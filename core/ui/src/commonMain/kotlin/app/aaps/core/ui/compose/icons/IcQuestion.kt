package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for Question mark.
 * Represents comment for help.
 *
 * replaces ic_cp_question
 *
 * Bounding box: x: 5.5-18.3, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 *
 * @see IcQuestionIconPreview
 */
val IcQuestion: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcQuestion",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFFEAF05)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.062f, 17.861f)
            curveToRelative(-0.58f, 0f, -1.05f, -0.47f, -1.05f, -1.051f)
            curveToRelative(0f, -3.776f, 1.76f, -5.094f, 3.175f, -6.153f)
            curveToRelative(1.185f, -0.888f, 2.042f, -1.529f, 2.042f, -3.605f)
            curveToRelative(0f, -2.862f, -2.749f, -3.752f, -3.75f, -3.752f)
            curveToRelative(-2.157f, 0f, -3.9f, 1.293f, -4.78f, 3.547f)
            curveTo(7.487f, 7.388f, 6.876f, 7.656f, 6.338f, 7.444f)
            curveToRelative(-0.541f, -0.211f, -0.807f, -0.82f, -0.596f, -1.361f)
            curveTo(6.936f, 3.025f, 9.454f, 1.2f, 12.479f, 1.2f)
            curveToRelative(2.359f, 0f, 5.852f, 1.86f, 5.852f, 5.852f)
            curveToRelative(0f, 3.127f, -1.599f, 4.324f, -2.885f, 5.286f)
            curveToRelative(-1.304f, 0.976f, -2.333f, 1.746f, -2.333f, 4.472f)
            curveTo(13.113f, 17.39f, 12.642f, 17.861f, 12.062f, 17.861f)
            close()

            moveTo(13.575f, 21.349f)
            curveToRelative(0f, 0.801f, -0.65f, 1.451f, -1.451f, 1.451f)
            curveToRelative(-0.801f, 0f, -1.451f, -0.65f, -1.451f, -1.451f)
            curveToRelative(0f, -0.801f, 0.65f, -1.451f, 1.451f, -1.451f)
            curveTo(12.925f, 19.898f, 13.575f, 20.548f, 13.575f, 21.349f)
            close()
        }
    }.build()
}
