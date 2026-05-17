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
 * AAPS app logo. Used for the Aaps / BgFragment user-entry sources.
 *
 * replaces ic_aaps
 *
 * Viewport: 24x24 (outer circle with C shape + inner left C)
 */
val IcAaps: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcAaps",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Outer ring + right-side C
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
            moveTo(22.107f, 7.852f)
            curveToRelative(-1.11f, -2.694f, -3.265f, -4.849f, -5.959f, -5.959f)
            curveTo(14.869f, 1.365f, 13.468f, 1.074f, 12f, 1.074f)
            curveToRelative(-1.468f, 0f, -2.869f, 0.292f, -4.148f, 0.819f)
            curveToRelative(-2.694f, 1.11f, -4.849f, 3.265f, -5.959f, 5.959f)
            curveTo(1.365f, 9.131f, 1.074f, 10.532f, 1.074f, 12f)
            curveToRelative(0f, 1.468f, 0.292f, 2.869f, 0.819f, 4.148f)
            curveToRelative(1.11f, 2.694f, 3.265f, 4.849f, 5.959f, 5.959f)
            curveToRelative(1.28f, 0.527f, 2.681f, 0.819f, 4.148f, 0.819f)
            curveToRelative(1.468f, 0f, 2.869f, -0.292f, 4.148f, -0.819f)
            curveToRelative(2.694f, -1.11f, 4.849f, -3.265f, 5.959f, -5.959f)
            curveToRelative(0.527f, -1.28f, 0.819f, -2.681f, 0.819f, -4.148f)
            curveTo(22.926f, 10.532f, 22.635f, 9.131f, 22.107f, 7.852f)
            close()
            moveTo(18.837f, 7.97f)
            curveToRelative(-0.694f, -1.216f, -1.645f, -2.172f, -2.852f, -2.866f)
            curveToRelative(-0.527f, -0.303f, -1.079f, -0.539f, -1.658f, -0.71f)
            curveToRelative(-0.638f, -0.188f, -1.274f, 0.312f, -1.274f, 0.977f)
            verticalLineToRelative(0f)
            curveToRelative(0f, 0.449f, 0.3f, 0.831f, 0.727f, 0.969f)
            curveToRelative(0.386f, 0.125f, 0.755f, 0.293f, 1.108f, 0.503f)
            curveToRelative(0.874f, 0.523f, 1.564f, 1.236f, 2.068f, 2.139f)
            curveToRelative(0.504f, 0.904f, 0.756f, 1.916f, 0.756f, 3.037f)
            curveToRelative(0f, 1.122f, -0.252f, 2.134f, -0.756f, 3.037f)
            curveToRelative(-0.504f, 0.903f, -1.193f, 1.611f, -2.068f, 2.125f)
            curveToRelative(-0.236f, 0.138f, -0.481f, 0.253f, -0.732f, 0.354f)
            verticalLineToRelative(-4.045f)
            curveToRelative(0f, -0.32f, -0.104f, -0.584f, -0.311f, -0.791f)
            curveToRelative(-0.208f, -0.207f, -0.471f, -0.311f, -0.792f, -0.311f)
            curveToRelative(-0.302f, 0f, -0.561f, 0.104f, -0.778f, 0.311f)
            curveToRelative(-0.217f, 0.208f, -0.325f, 0.471f, -0.325f, 0.791f)
            verticalLineToRelative(5.246f)
            curveToRelative(0f, 0.302f, 0.108f, 0.561f, 0.325f, 0.778f)
            curveToRelative(0.217f, 0.217f, 0.476f, 0.325f, 0.778f, 0.325f)
            curveToRelative(0.171f, 0f, 0.325f, -0.033f, 0.464f, -0.096f)
            curveToRelative(0.558f, -0.131f, 1.091f, -0.322f, 1.598f, -0.581f)
            curveToRelative(1.027f, -0.523f, 1.872f, -1.24f, 2.538f, -2.153f)
            verticalLineToRelative(1.711f)
            curveToRelative(0f, 0.174f, 0.031f, 0.33f, 0.092f, 0.468f)
            curveToRelative(-1.576f, 1.262f, -3.574f, 2.018f, -5.745f, 2.018f)
            curveToRelative(-5.077f, 0f, -9.208f, -4.131f, -9.208f, -9.208f)
            reflectiveCurveTo(6.923f, 2.792f, 12f, 2.792f)
            reflectiveCurveTo(21.208f, 6.923f, 21.208f, 12f)
            curveToRelative(0f, 1.742f, -0.486f, 3.372f, -1.33f, 4.762f)
            verticalLineTo(12.02f)
            curveTo(19.878f, 10.537f, 19.531f, 9.187f, 18.837f, 7.97f)
            close()
        }
        // Left-side C (inside the ring)
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
            moveTo(10.847f, 4.131f)
            curveToRelative(-0.135f, 0f, -0.26f, 0.024f, -0.378f, 0.066f)
            curveTo(9.559f, 4.353f, 8.705f, 4.649f, 7.914f, 5.104f)
            curveTo(6.707f, 5.798f, 5.756f, 6.754f, 5.062f, 7.97f)
            curveToRelative(-0.694f, 1.217f, -1.041f, 2.567f, -1.041f, 4.05f)
            curveToRelative(0f, 1.483f, 0.337f, 2.829f, 1.012f, 4.035f)
            curveToRelative(0.675f, 1.208f, 1.597f, 2.158f, 2.766f, 2.852f)
            curveToRelative(0.545f, 0.324f, 1.12f, 0.571f, 1.725f, 0.742f)
            curveToRelative(0.661f, 0.188f, 1.321f, -0.302f, 1.321f, -0.989f)
            curveToRelative(0f, -0.459f, -0.309f, -0.849f, -0.746f, -0.989f)
            curveToRelative(-0.383f, -0.122f, -0.751f, -0.285f, -1.103f, -0.489f)
            curveToRelative(-0.884f, -0.514f, -1.578f, -1.222f, -2.082f, -2.125f)
            curveTo(6.412f, 14.154f, 6.16f, 13.142f, 6.16f, 12.02f)
            curveToRelative(0f, -1.122f, 0.252f, -2.134f, 0.756f, -3.037f)
            curveTo(7.42f, 8.08f, 8.114f, 7.367f, 8.998f, 6.844f)
            curveToRelative(0.241f, -0.142f, 0.491f, -0.26f, 0.746f, -0.363f)
            verticalLineToRelative(3.999f)
            curveToRelative(0f, 0.302f, 0.108f, 0.561f, 0.325f, 0.778f)
            curveToRelative(0.217f, 0.217f, 0.476f, 0.325f, 0.778f, 0.325f)
            curveToRelative(0.32f, 0f, 0.584f, -0.108f, 0.792f, -0.325f)
            curveToRelative(0.207f, -0.216f, 0.311f, -0.475f, 0.311f, -0.778f)
            verticalLineTo(5.233f)
            curveToRelative(0f, -0.32f, -0.104f, -0.584f, -0.311f, -0.791f)
            curveTo(11.431f, 4.235f, 11.167f, 4.131f, 10.847f, 4.131f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcAapsIconPreview() {
    Icon(
        imageVector = IcAaps,
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
        android:pathData="M22.107,7.852c-1.11,-2.694 -3.265,-4.849 -5.959,-5.959C14.869,1.365 13.468,1.074 12,1.074c-1.468,0 -2.869,0.292 -4.148,0.819c-2.694,1.11 -4.849,3.265 -5.959,5.959C1.365,9.131 1.074,10.532 1.074,12c0,1.468 0.292,2.869 0.819,4.148c1.11,2.694 3.265,4.849 5.959,5.959c1.28,0.527 2.681,0.819 4.148,0.819c1.468,0 2.869,-0.292 4.148,-0.819c2.694,-1.11 4.849,-3.265 5.959,-5.959c0.527,-1.28 0.819,-2.681 0.819,-4.148C22.926,10.532 22.635,9.131 22.107,7.852zM18.837,7.97c-0.694,-1.216 -1.645,-2.172 -2.852,-2.866c-0.527,-0.303 -1.079,-0.539 -1.658,-0.71c-0.638,-0.188 -1.274,0.312 -1.274,0.977v0c0,0.449 0.3,0.831 0.727,0.969c0.386,0.125 0.755,0.293 1.108,0.503c0.874,0.523 1.564,1.236 2.068,2.139c0.504,0.904 0.756,1.916 0.756,3.037c0,1.122 -0.252,2.134 -0.756,3.037c-0.504,0.903 -1.193,1.611 -2.068,2.125c-0.236,0.138 -0.481,0.253 -0.732,0.354v-4.045c0,-0.32 -0.104,-0.584 -0.311,-0.791c-0.208,-0.207 -0.471,-0.311 -0.792,-0.311c-0.302,0 -0.561,0.104 -0.778,0.311c-0.217,0.208 -0.325,0.471 -0.325,0.791v5.246c0,0.302 0.108,0.561 0.325,0.778c0.217,0.217 0.476,0.325 0.778,0.325c0.171,0 0.325,-0.033 0.464,-0.096c0.558,-0.131 1.091,-0.322 1.598,-0.581c1.027,-0.523 1.872,-1.24 2.538,-2.153v1.711c0,0.174 0.031,0.33 0.092,0.468c-1.576,1.262 -3.574,2.018 -5.745,2.018c-5.077,0 -9.208,-4.131 -9.208,-9.208S6.923,2.792 12,2.792S21.208,6.923 21.208,12c0,1.742 -0.486,3.372 -1.33,4.762V12.02C19.878,10.537 19.531,9.187 18.837,7.97z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M10.847,4.131c-0.135,0 -0.26,0.024 -0.378,0.066C9.559,4.353 8.705,4.649 7.914,5.104C6.707,5.798 5.756,6.754 5.062,7.97c-0.694,1.217 -1.041,2.567 -1.041,4.05c0,1.483 0.337,2.829 1.012,4.035c0.675,1.208 1.597,2.158 2.766,2.852c0.545,0.324 1.12,0.571 1.725,0.742c0.661,0.188 1.321,-0.302 1.321,-0.989c0,-0.459 -0.309,-0.849 -0.746,-0.989c-0.383,-0.122 -0.751,-0.285 -1.103,-0.489c-0.884,-0.514 -1.578,-1.222 -2.082,-2.125C6.412,14.154 6.16,13.142 6.16,12.02c0,-1.122 0.252,-2.134 0.756,-3.037C7.42,8.08 8.114,7.367 8.998,6.844c0.241,-0.142 0.491,-0.26 0.746,-0.363v3.999c0,0.302 0.108,0.561 0.325,0.778c0.217,0.217 0.476,0.325 0.778,0.325c0.32,0 0.584,-0.108 0.792,-0.325c0.207,-0.216 0.311,-0.475 0.311,-0.778V5.233c0,-0.32 -0.104,-0.584 -0.311,-0.791C11.431,4.235 11.167,4.131 10.847,4.131z"
        android:fillColor="?attr/colorControlNormal" />
</vector>
 */
