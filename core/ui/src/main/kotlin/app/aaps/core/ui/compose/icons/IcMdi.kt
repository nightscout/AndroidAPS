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
 * Syringe / insulin pen icon. Used for Sources.MDI (Multiple Daily Injections) — the label
 * applied to user-entered treatments via PumpType.USER.
 *
 * replaces ic_ict
 *
 * Viewport: 64x64 (plunger + barrel + tick marks + needle)
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

@Preview(showBackground = true)
@Composable
private fun IcMdiIconPreview() {
    Icon(
        imageVector = IcMdi,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="64"
    android:viewportHeight="64">
    <path
        android:pathData="M6.171,24.3397C3.8502,26.496 3.5322,29.698 5.4601,31.4917C7.3872,33.2847 10.8312,32.9912 13.152,30.835L6.171,24.3397ZM6.1447,24.3153L13.1257,30.8105L32.4135,12.8898L25.4325,6.3946L6.1447,24.3153Z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M27.8111,21.5928C28.4437,21.5932 28.9565,21.048 28.9569,20.3756C28.9573,19.7033 28.4444,19.1566 27.8126,19.1562C27.18,19.1558 26.6664,19.701 26.666,20.3742L27.8118,20.3749L27.8111,21.5928Z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M28.3077,20.9977C28.4591,20.8544 28.4651,20.615 28.3218,20.4636L27.8025,19.9148C27.6593,19.7634 27.4206,19.7567 27.2692,19.9L13.0015,33.3152C12.8501,33.4584 13.1194,33.5198 13.2627,33.6712L13.7819,34.22C13.9252,34.3715 14.1646,34.3788 14.316,34.2355L28.3077,20.9977Z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M12.8796,30.7117l1.4542,1.4634l-1.2991,1.2909l-1.4542,-1.4634z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M11.2926,57.0326L15.0819,53.5125L15.523,53.923L11.7337,57.4431L10.9249,57.7851L11.2926,57.0326Z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M16.3239,48.4068l4.6153,4.2949l-3.3673,3.1281l-4.6153,-4.2949z"
        android:strokeWidth="1.15"
        android:fillColor="#00000000"
        android:strokeColor="?attr/colorControlNormal" />
    <path
        android:pathData="M32.6362,32.3306l5.8109,5.4075l-16.8087,15.6146l-5.8109,-5.4075z"
        android:strokeWidth="0.68"
        android:fillColor="#00000000"
        android:strokeColor="?attr/colorControlNormal" />
    <path
        android:pathData="M30.0742,35.3171l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M19.738,44.8949l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M21.8556,42.9278l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M23.955,40.9775l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M26.0246,39.0549l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M28.0977,37.1291l2.3081,2.1478l-0.4207,0.3908l-2.3081,-2.1478z"
        android:fillColor="?attr/colorControlNormal" />
    <path
        android:pathData="M50.4865,14.7282C53.0833,12.3159 56.7436,11.8079 58.6621,13.5932C60.5806,15.3785 60.0311,18.7813 57.4343,21.1936L38.5619,38.7252L31.6141,32.2598L50.4865,14.7282Z"
        android:fillColor="?attr/colorControlNormal" />
</vector>
 */
