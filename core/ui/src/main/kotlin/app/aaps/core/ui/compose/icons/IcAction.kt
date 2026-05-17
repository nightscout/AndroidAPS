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
 * Running figure icon. Used for the Actions source in user entries.
 *
 * replaces ic_action
 *
 * Viewport: 24x24 (head ellipse, body + leg path, tiny oval detail)
 */
val IcAction: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAction",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Head (ellipse)
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
            moveTo(14.27f, 6f)
            curveTo(13.72f, 6.95f, 14.05f, 8.18f, 15f, 8.73f)
            curveToRelative(0.95f, 0.55f, 2.18f, 0.22f, 2.73f, -0.73f)
            curveToRelative(0.55f, -0.95f, 0.22f, -2.18f, -0.73f, -2.73f)
            curveTo(16.05f, 4.72f, 14.82f, 5.05f, 14.27f, 6f)
            close()
        }
        // Body + legs
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
            moveTo(15.84f, 10.41f)
            curveToRelative(0f, 0f, -1.63f, -0.94f, -2.6f, -1.5f)
            curveToRelative(-2.38f, -1.38f, -3.2f, -4.44f, -1.82f, -6.82f)
            lineToRelative(-1.73f, -1f)
            curveTo(8.1f, 3.83f, 8.6f, 7.21f, 10.66f, 9.4f)
            lineToRelative(-5.15f, 8.92f)
            lineToRelative(1.73f, 1f)
            lineToRelative(1.5f, -2.6f)
            lineToRelative(1.73f, 1f)
            lineToRelative(-3f, 5.2f)
            lineToRelative(1.73f, 1f)
            lineToRelative(6.29f, -10.89f)
            curveToRelative(1.14f, 1.55f, 1.33f, 3.69f, 0.31f, 5.46f)
            lineToRelative(1.73f, 1f)
            curveTo(19.13f, 16.74f, 18.81f, 12.91f, 15.84f, 10.41f)
            close()
        }
        // Small oval detail (earring / accessory dot)
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
            moveTo(12.75f, 3.8f)
            curveToRelative(0.72f, 0.41f, 1.63f, 0.17f, 2.05f, -0.55f)
            curveToRelative(0.41f, -0.72f, 0.17f, -1.63f, -0.55f, -2.05f)
            curveToRelative(-0.72f, -0.41f, -1.63f, -0.17f, -2.05f, 0.55f)
            curveTo(11.79f, 2.47f, 12.03f, 3.39f, 12.75f, 3.8f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcActionIconPreview() {
    Icon(
        imageVector = IcAction,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="?attr/colorControlNormal"
        android:pathData="M14.27,6C13.72,6.95 14.05,8.18 15,8.73c0.95,0.55 2.18,0.22 2.73,-0.73c0.55,-0.95 0.22,-2.18 -0.73,-2.73C16.05,4.72 14.82,5.05 14.27,6z" />
    <path
        android:fillColor="?attr/colorControlNormal"
        android:pathData="M15.84,10.41c0,0 -1.63,-0.94 -2.6,-1.5c-2.38,-1.38 -3.2,-4.44 -1.82,-6.82l-1.73,-1C8.1,3.83 8.6,7.21 10.66,9.4l-5.15,8.92l1.73,1l1.5,-2.6l1.73,1l-3,5.2l1.73,1l6.29,-10.89c1.14,1.55 1.33,3.69 0.31,5.46l1.73,1C19.13,16.74 18.81,12.91 15.84,10.41z" />
    <path
        android:fillColor="?attr/colorControlNormal"
        android:pathData="M12.75,3.8c0.72,0.41 1.63,0.17 2.05,-0.55c0.41,-0.72 0.17,-1.63 -0.55,-2.05c-0.72,-0.41 -1.63,-0.17 -2.05,0.55C11.79,2.47 12.03,3.39 12.75,3.8z" />
</vector>
 */
