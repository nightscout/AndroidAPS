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
 * Icon for Double Up Arrow.
 * Represents double upward trend or direction.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcArrowDoubleUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowDoubleUp",
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
            moveTo(1.895f, 7.347f)
            curveToRelative(2.121f, -1.967f, 4.65f, -4.43f, 5.771f, -6.145f)
            verticalLineTo(1.2f)
            curveToRelative(0f, 0f, 0.001f, 0.001f, 0.001f, 0.001f)
            lineTo(7.668f, 1.2f)
            verticalLineToRelative(0.002f)
            curveToRelative(1.121f, 1.715f, 3.65f, 4.178f, 5.771f, 6.145f)
            lineToRelative(-1.44f, 1.979f)
            curveToRelative(0f, 0f, -1.715f, -1.53f, -3.188f, -2.964f)
            verticalLineTo(22.8f)
            horizontalLineTo(6.524f)
            verticalLineTo(6.362f)
            curveTo(5.05f, 7.796f, 3.335f, 9.327f, 3.335f, 9.327f)
            lineTo(1.895f, 7.347f)
            close()

            moveTo(10.561f, 7.347f)
            curveToRelative(2.121f, -1.967f, 4.65f, -4.43f, 5.771f, -6.145f)
            verticalLineTo(1.2f)
            lineToRelative(0.001f, 0.001f)
            curveToRelative(0f, 0f, 0.001f, -0.001f, 0.001f, -0.001f)
            lineToRelative(0f, 0.002f)
            curveToRelative(1.121f, 1.715f, 3.65f, 4.178f, 5.771f, 6.145f)
            lineToRelative(-1.44f, 1.979f)
            curveToRelative(0f, 0f, -1.715f, -1.53f, -3.188f, -2.964f)
            verticalLineTo(22.8f)
            horizontalLineTo(15.19f)
            verticalLineTo(6.362f)
            curveToRelative(-1.474f, 1.434f, -3.189f, 2.964f, -3.189f, 2.964f)
            lineTo(10.561f, 7.347f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowDoubleUpIconPreview() {
    Icon(
        imageVector = IcArrowDoubleUp,
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
<g id="ic_arrow_double_up">
	<g display="inline">
		<path fill="#36FF00" d="M1.895,7.347c2.121-1.967,4.65-4.43,5.771-6.145V1.2c0,0,0.001,0.001,0.001,0.001L7.668,1.2v0.002
			c1.121,1.715,3.65,4.178,5.771,6.145l-1.44,1.979c0,0-1.715-1.53-3.188-2.964V22.8H6.524V6.362
			C5.05,7.796,3.335,9.327,3.335,9.327L1.895,7.347z"/>
		<path fill="#36FF00" d="M10.561,7.347c2.121-1.967,4.65-4.43,5.771-6.145V1.2l0.001,0.001c0,0,0.001-0.001,0.001-0.001l0,0.002
			c1.121,1.715,3.65,4.178,5.771,6.145l-1.44,1.979c0,0-1.715-1.53-3.188-2.964V22.8H15.19V6.362
			c-1.474,1.434-3.189,2.964-3.189,2.964L10.561,7.347z"/>
	</g>
</g>
</svg>
 */