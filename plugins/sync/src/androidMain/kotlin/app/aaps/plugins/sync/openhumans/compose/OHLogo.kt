package app.aaps.plugins.sync.openhumans.compose

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Open Humans full-color logo as Compose ImageVector.
 * Converted from open_humans.xml vector drawable with original brand colors.
 */
internal val OHLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "OpenHumansLogo",
        defaultWidth = 100.dp,
        defaultHeight = 100.527f.dp,
        viewportWidth = 107.79796f,
        viewportHeight = 108.36626f
    ).apply {
        // Person silhouette (head + body)
        path(fill = SolidColor(OHColors.Orange)) {
            moveTo(59.75f, 62.316f)
            curveToRelative(5.179f, -2.256f, 8.801f, -7.417f, 8.801f, -13.427f)
            curveToRelative(0f, -8.086f, -6.555f, -14.641f, -14.641f, -14.641f)
            curveToRelative(-8.086f, 0f, -14.641f, 6.555f, -14.641f, 14.641f)
            curveToRelative(0f, 6.01f, 3.622f, 11.171f, 8.801f, 13.427f)
            curveToRelative(-7.849f, 1.589f, -14.555f, 6.318f, -18.76f, 12.817f)
            curveToRelative(5.968f, 6.896f, 14.774f, 11.272f, 24.589f, 11.272f)
            curveToRelative(9.821f, 0f, 18.633f, -4.382f, 24.601f, -11.286f)
            curveToRelative(-4.205f, -6.491f, -10.907f, -11.215f, -18.75f, -12.803f)
            close()
        }
        // Top-left segment
        path(fill = SolidColor(OHColors.Orange)) {
            moveTo(21.689f, 33.33f)
            lineTo(10.002f, 21.643f)
            curveToRelative(-5.155f, 7f, -8.677f, 15.271f, -10.002f, 24.25f)
            lineToRelative(16.523f, 0f)
            curveToRelative(0.968f, -4.535f, 2.741f, -8.776f, 5.166f, -12.563f)
            close()
        }
        // Top-right segment
        path(fill = SolidColor(OHColors.Orange)) {
            moveTo(91.275f, 45.893f)
            lineToRelative(16.523f, 0f)
            curveTo(106.473f, 36.909f, 102.947f, 28.634f, 97.787f, 21.631f)
            lineTo(86.101f, 33.317f)
            curveToRelative(2.429f, 3.79f, 4.205f, 8.035f, 5.174f, 12.576f)
            close()
        }
        // Top-center-right wedge
        path(fill = SolidColor(OHColors.Orange)) {
            moveTo(86.305f, 10.106f)
            curveTo(79.304f, 4.91f, 71.02f, 1.351f, 62.022f, 0f)
            lineToRelative(0f, 15.422f)
            lineToRelative(13.059f, 5.908f)
            close()
        }
        // Top-center-left wedge
        path(fill = SolidColor(OHColors.Orange)) {
            moveTo(45.754f, 15.339f)
            lineTo(45.754f, 0.003f)
            curveToRelative(-8.995f, 1.354f, -17.276f, 4.915f, -24.274f, 10.113f)
            lineToRelative(10.963f, 10.963f)
            close()
        }
        // Bottom arc
        path(fill = SolidColor(OHColors.Blue)) {
            moveTo(26.558f, 80.554f)
            curveToRelative(-4.881f, -5.002f, -8.405f, -11.333f, -9.971f, -18.394f)
            lineToRelative(-16.546f, 0f)
            curveToRelative(4.001f, 26.128f, 26.629f, 46.206f, 53.858f, 46.206f)
            curveToRelative(27.229f, 0f, 49.857f, -20.077f, 53.858f, -46.206f)
            lineToRelative(-16.546f, 0f)
            curveToRelative(-1.564f, 7.053f, -5.082f, 13.378f, -9.955f, 18.378f)
            curveToRelative(-6.946f, 7.127f, -16.643f, 11.56f, -27.357f, 11.56f)
            curveToRelative(-10.706f, 0f, -20.396f, -4.427f, -27.341f, -11.544f)
            close()
        }
    }.build()
}
