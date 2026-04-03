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
 * Icon for TDD (Total Daily Dose).
 * Represents total daily insulin dose.
 *
 * replaces ic_stats
 *
 * Bounding box: x: 2.7-21.3, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcStats: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcStats",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
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
            moveTo(4.145f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(11.96f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(9.382f)
            curveTo(5.602f, 22.147f, 4.949f, 22.8f, 4.145f, 22.8f)
            close()

            moveTo(9.381f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(7.051f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(14.291f)
            curveTo(10.839f, 22.147f, 10.186f, 22.8f, 9.381f, 22.8f)
            close()

            moveTo(14.618f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(8.979f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            reflectiveCurveToRelative(1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(12.363f)
            curveTo(16.076f, 22.147f, 15.423f, 22.8f, 14.618f, 22.8f)
            close()

            moveTo(19.855f, 22.8f)
            curveToRelative(-0.805f, 0f, -1.458f, -0.653f, -1.458f, -1.458f)
            verticalLineTo(2.658f)
            curveToRelative(0f, -0.805f, 0.653f, -1.458f, 1.458f, -1.458f)
            curveToRelative(0.805f, 0f, 1.458f, 0.653f, 1.458f, 1.458f)
            verticalLineToRelative(18.684f)
            curveTo(21.313f, 22.147f, 20.66f, 22.8f, 19.855f, 22.8f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcStatsIconPreview() {
    Icon(
        imageVector = IcStats,
        contentDescription = null,
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
<g id="ic_tdd">
	<g display="inline">
		<path fill="#FEAF05" d="M4.145,22.8c-0.805,0-1.458-0.653-1.458-1.458V11.96c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v9.382C5.602,22.147,4.949,22.8,4.145,22.8z"/>
		<path fill="#FEAF05" d="M9.381,22.8c-0.805,0-1.458-0.653-1.458-1.458V7.051c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v14.291C10.839,22.147,10.186,22.8,9.381,22.8z"/>
		<path fill="#FEAF05" d="M14.618,22.8c-0.805,0-1.458-0.653-1.458-1.458V8.979c0-0.805,0.653-1.458,1.458-1.458
			s1.458,0.653,1.458,1.458v12.363C16.076,22.147,15.423,22.8,14.618,22.8z"/>
		<path fill="#FEAF05" d="M19.855,22.8c-0.805,0-1.458-0.653-1.458-1.458V2.658c0-0.805,0.653-1.458,1.458-1.458
			c0.805,0,1.458,0.653,1.458,1.458v18.684C21.313,22.147,20.66,22.8,19.855,22.8z"/>
	</g>
</g>
</svg>
 */