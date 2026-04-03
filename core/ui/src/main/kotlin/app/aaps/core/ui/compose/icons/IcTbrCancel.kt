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
 * Icon for Cancel TBR (Temporary Basal Rate).
 * Represents cancellation of temporary basal rate.
 *
 * replacing ic_cancel_basal
 *
 * Bounding box: x: 1.2-22.8, y: 3.7-20.3 (viewport: 24x24, ~90% width)
 */
val IcTbrCancel: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcTbrCancel",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFCF8BFE)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(1.948f, 12.668f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            curveToRelative(-0.267f, -0.267f, -0.267f, -0.699f, 0.001f, -0.966f)
            lineToRelative(7.533f, -7.534f)
            curveToRelative(0.268f, -0.267f, 0.699f, -0.267f, 0.967f, 0.001f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, -0.001f, 0.967f)
            lineToRelative(-7.533f, 7.533f)
            curveTo(2.297f, 12.602f, 2.123f, 12.668f, 1.948f, 12.668f)
            close()

            moveTo(9.482f, 12.668f)
            curveToRelative(-0.175f, 0f, -0.35f, -0.067f, -0.484f, -0.2f)
            lineTo(1.465f, 4.936f)
            curveToRelative(-0.267f, -0.267f, -0.267f, -0.7f, -0.001f, -0.967f)
            curveToRelative(0.267f, -0.268f, 0.699f, -0.267f, 0.967f, -0.001f)
            lineToRelative(7.533f, 7.534f)
            curveToRelative(0.267f, 0.267f, 0.267f, 0.699f, 0.001f, 0.966f)
            curveTo(9.832f, 12.602f, 9.657f, 12.668f, 9.482f, 12.668f)
            close()

            moveTo(19.151f, 20.281f)
            lineTo(19.151f, 5.105f)
            lineTo(14.068f, 5.105f)
            lineTo(14.068f, 20.281f)
            lineTo(1.2f, 20.281f)
            lineTo(1.2f, 18.893f)
            lineTo(12.681f, 18.893f)
            lineTo(12.681f, 3.719f)
            lineTo(20.539f, 3.719f)
            lineTo(20.539f, 18.893f)
            lineTo(22.8f, 18.893f)
            lineTo(22.8f, 20.281f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcTbrCancelIconPreview() {
    Icon(
        imageVector = IcTbrCancel,
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
<g id="ic_tbr_cancel">
	<g display="inline">
		<g>
			<path fill="#CF8BFE" d="M1.948,12.668c-0.175,0-0.35-0.067-0.484-0.2c-0.267-0.267-0.267-0.699,0.001-0.966l7.533-7.534
				c0.268-0.267,0.699-0.267,0.967,0.001c0.267,0.267,0.267,0.699-0.001,0.967l-7.533,7.533C2.297,12.602,2.123,12.668,1.948,12.668
				z"/>
			<path fill="#CF8BFE" d="M9.482,12.668c-0.175,0-0.35-0.067-0.484-0.2L1.465,4.936c-0.267-0.267-0.267-0.7-0.001-0.967
				c0.267-0.268,0.699-0.267,0.967-0.001l7.533,7.534c0.267,0.267,0.267,0.699,0.001,0.966C9.832,12.602,9.657,12.668,9.482,12.668z
				"/>
		</g>
		<polygon fill="#CF8BFE" points="19.151,20.281 19.151,5.105 14.068,5.105 14.068,20.281 1.2,20.281 1.2,18.893 12.681,18.893
			12.681,3.719 20.539,3.719 20.539,18.893 22.8,18.893 22.8,20.281 		"/>
	</g>
</g>
</svg>
 */