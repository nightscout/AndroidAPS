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
 * Icon for Left Arrow.
 * Represents left direction.
 *
 * Bounding box: x: 1.2-22.8, y: 5.4-18.6 (viewport: 24x24, ~90% width)
 */
val IcArrowLeft: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowLeft",
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
            moveTo(7.347f, 17.772f)
            curveToRelative(-1.967f, -2.121f, -4.43f, -4.65f, -6.145f, -5.771f)
            horizontalLineTo(1.2f)
            lineTo(1.201f, 12f)
            lineTo(1.2f, 11.999f)
            horizontalLineToRelative(0.002f)
            curveToRelative(1.715f, -1.121f, 4.178f, -3.65f, 6.145f, -5.771f)
            lineToRelative(1.979f, 1.44f)
            curveToRelative(0f, 0f, -1.53f, 1.715f, -2.964f, 3.188f)
            horizontalLineTo(22.8f)
            verticalLineToRelative(2.286f)
            horizontalLineTo(6.362f)
            curveToRelative(1.434f, 1.474f, 2.964f, 3.189f, 2.964f, 3.189f)
            lineTo(7.347f, 17.772f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowLeftPreview() {
    Icon(
        imageVector = IcArrowLeft,
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
<g id="ic_arrow_left">
	<path fill="#36FF00" d="M7.347,17.772c-1.967-2.121-4.43-4.65-6.145-5.771H1.2L1.201,12L1.2,11.999h0.002
		c1.715-1.121,4.178-3.65,6.145-5.771l1.979,1.44c0,0-1.53,1.715-2.964,3.188H22.8v2.286H6.362c1.434,1.474,2.964,3.189,2.964,3.189
		L7.347,17.772z"/>
</g>
</svg>
 */