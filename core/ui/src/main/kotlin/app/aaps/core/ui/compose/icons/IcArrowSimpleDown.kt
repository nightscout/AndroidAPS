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
 * Icon for Simple Down Arrow.
 * Represents downward trend or direction.
 *
 * Bounding box: x: 5.4-18.6, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcArrowSimpleDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowSimpleDown",
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
            moveTo(6.228f, 16.653f)
            curveToRelative(2.121f, 1.967f, 4.65f, 4.43f, 5.771f, 6.145f)
            verticalLineTo(22.8f)
            lineTo(12f, 22.799f)
            lineToRelative(0.001f, 0.001f)
            verticalLineToRelative(-0.002f)
            curveToRelative(1.121f, -1.715f, 3.65f, -4.178f, 5.771f, -6.145f)
            lineToRelative(-1.44f, -1.979f)
            curveToRelative(0f, 0f, -1.715f, 1.53f, -3.188f, 2.964f)
            verticalLineTo(1.2f)
            horizontalLineToRelative(-2.286f)
            verticalLineToRelative(16.438f)
            curveToRelative(-1.474f, -1.434f, -3.189f, -2.964f, -3.189f, -2.964f)
            lineTo(6.228f, 16.653f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowSimpleDownIconPreview() {
    Icon(
        imageVector = IcArrowSimpleDown,
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
<g id="ic_arrow_simple_down">
	<path display="inline" fill="#36FF00" d="M6.228,16.653c2.121,1.967,4.65,4.43,5.771,6.145V22.8L12,22.799l0.001,0.001v-0.002
		c1.121-1.715,3.65-4.178,5.771-6.145l-1.44-1.979c0,0-1.715,1.53-3.188,2.964V1.2h-2.286v16.438
		c-1.474-1.434-3.189-2.964-3.189-2.964L6.228,16.653z"/>
</g>
</svg>
 */