package app.aaps.core.ui.compose.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for BG Check.
 * Represents blood glucose check or measurement.
 *
 * replaces ic_cp_bgcheck
 *
 * Bounding box: x: 1.2-22.8, y: 1.8-21.8 (viewport: 24x24, ~90% width)
 *
 * @see IcBgCheckIconPreview
 */
val IcBgCheck: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcBgCheck",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFE83258)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(3.511f, 15.551f)
            curveToRelative(-0.028f, -0.497f, 0.039f, -0.963f, 0.644f, -1.034f)
            curveToRelative(0.555f, -0.065f, 0.756f, 0.335f, 0.819f, 0.795f)
            curveToRelative(0.239f, 1.743f, 1.188f, 2.773f, 2.958f, 3.001f)
            curveToRelative(0.518f, 0.067f, 0.912f, 0.296f, 0.814f, 0.883f)
            curveToRelative(-0.09f, 0.541f, -0.575f, 0.598f, -1.008f, 0.597f)
            curveTo(5.653f, 19.788f, 3.535f, 17.651f, 3.511f, 15.551f)
            close()

            moveTo(22.669f, 7.574f)
            curveToRelative(-0.581f, -2.289f, -2.977f, -5.731f, -3.477f, -5.731f)
            curveToRelative(-0.5f, 0f, -2.904f, 3.449f, -3.482f, 5.734f)
            curveToRelative(-0.387f, 1.528f, 0.103f, 2.839f, 1.454f, 3.731f)
            curveToRelative(1.318f, 0.87f, 2.739f, 0.856f, 4.056f, -0.012f)
            curveTo(22.573f, 10.404f, 23.06f, 9.113f, 22.669f, 7.574f)
            close()

            moveTo(20.542f, 10.266f)
            curveToRelative(-0.458f, 0.302f, -0.916f, 0.455f, -1.365f, 0.455f)
            curveToRelative(-0.441f, 0f, -0.889f, -0.149f, -1.335f, -0.442f)
            curveToRelative(-0.903f, -0.597f, -1.201f, -1.36f, -0.938f, -2.4f)
            curveToRelative(0.364f, -1.438f, 1.536f, -3.342f, 2.287f, -4.335f)
            curveToRelative(0.749f, 0.992f, 1.917f, 2.894f, 2.283f, 4.333f)
            curveTo(21.738f, 8.914f, 21.441f, 9.673f, 20.542f, 10.266f)
            close()

            moveTo(14.841f, 13.727f)
            curveToRelative(-1.119f, -4.405f, -5.999f, -11.21f, -6.692f, -11.21f)
            curveToRelative(-0.693f, 0f, -5.589f, 6.819f, -6.702f, 11.215f)
            curveToRelative(-0.745f, 2.94f, 0.198f, 5.464f, 2.798f, 7.181f)
            curveToRelative(2.537f, 1.675f, 5.272f, 1.648f, 7.807f, -0.023f)
            curveTo(14.656f, 19.174f, 15.593f, 16.69f, 14.841f, 13.727f)
            close()

            moveTo(11.373f, 19.862f)
            curveToRelative(-1.07f, 0.704f, -2.165f, 1.062f, -3.254f, 1.062f)
            curveToRelative(-1.076f, 0f, -2.151f, -0.349f, -3.195f, -1.04f)
            curveToRelative(-2.14f, -1.411f, -2.907f, -3.38f, -2.282f, -5.849f)
            curveToRelative(0.867f, -3.421f, 5.135f, -9.463f, 5.507f, -9.463f)
            curveToRelative(0.372f, 0f, 4.628f, 6.035f, 5.498f, 9.459f)
            curveTo(14.273f, 16.493f, 13.507f, 18.455f, 11.373f, 19.862f)
            close()
        }
    }.build()
}
