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
 * Icon for None Arrow.
 * Represents no direction or neutral.
 *
 * Bounding box: x: 1.2-22.8, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcArrowNone: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcArrowNone",
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
            moveTo(22.8f, 11.999f)
            horizontalLineToRelative(-0.001f)
            curveToRelative(-1.133f, -0.741f, -2.76f, -2.412f, -4.06f, -3.813f)
            lineTo(17.43f, 9.138f)
            curveToRelative(0f, 0f, 1.011f, 1.133f, 1.959f, 2.107f)
            horizontalLineTo(15.28f)
            curveToRelative(-0.288f, -1.254f, -1.271f, -2.237f, -2.525f, -2.525f)
            verticalLineTo(4.611f)
            curveToRelative(0.974f, 0.948f, 2.107f, 1.959f, 2.107f, 1.959f)
            lineToRelative(0.952f, -1.308f)
            curveToRelative(-1.401f, -1.3f, -3.072f, -2.927f, -3.813f, -4.06f)
            verticalLineTo(1.2f)
            lineTo(12f, 1.201f)
            lineTo(12f, 1.2f)
            lineToRelative(0f, 0.001f)
            curveToRelative(-0.741f, 1.133f, -2.412f, 2.76f, -3.813f, 4.06f)
            lineTo(9.138f, 6.57f)
            curveToRelative(0f, 0f, 1.133f, -1.011f, 2.107f, -1.959f)
            verticalLineTo(8.72f)
            curveTo(9.991f, 9.008f, 9.008f, 9.991f, 8.72f, 11.245f)
            horizontalLineTo(4.611f)
            curveTo(5.558f, 10.271f, 6.57f, 9.138f, 6.57f, 9.138f)
            lineTo(5.262f, 8.186f)
            curveToRelative(-1.3f, 1.401f, -2.927f, 3.072f, -4.06f, 3.813f)
            horizontalLineTo(1.2f)
            lineTo(1.201f, 12f)
            lineTo(1.2f, 12.001f)
            horizontalLineToRelative(0.001f)
            curveToRelative(1.133f, 0.741f, 2.76f, 2.412f, 4.06f, 3.813f)
            lineToRelative(1.308f, -0.952f)
            curveToRelative(0f, 0f, -1.011f, -1.133f, -1.959f, -2.107f)
            horizontalLineTo(8.72f)
            curveToRelative(0.288f, 1.254f, 1.271f, 2.237f, 2.525f, 2.525f)
            verticalLineToRelative(4.109f)
            curveToRelative(-0.974f, -0.948f, -2.107f, -1.959f, -2.107f, -1.959f)
            lineToRelative(-0.952f, 1.308f)
            curveToRelative(1.401f, 1.3f, 3.072f, 2.927f, 3.813f, 4.06f)
            verticalLineTo(22.8f)
            lineTo(12f, 22.799f)
            lineToRelative(0.001f, 0.001f)
            verticalLineToRelative(-0.001f)
            curveToRelative(0.741f, -1.133f, 2.412f, -2.76f, 3.813f, -4.06f)
            lineToRelative(-0.952f, -1.308f)
            curveToRelative(0f, 0f, -1.133f, 1.011f, -2.107f, 1.959f)
            verticalLineTo(15.28f)
            curveToRelative(1.254f, -0.288f, 2.237f, -1.271f, 2.525f, -2.525f)
            horizontalLineToRelative(4.109f)
            curveToRelative(-0.948f, 0.974f, -1.959f, 2.107f, -1.959f, 2.107f)
            lineToRelative(1.308f, 0.952f)
            curveToRelative(1.3f, -1.401f, 2.927f, -3.072f, 4.06f, -3.813f)
            horizontalLineTo(22.8f)
            verticalLineTo(11.999f)
            lineTo(22.8f, 11.999f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcArrowNonePreview() {
    Icon(
        imageVector = IcArrowNone,
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
<g id="ic_arrow_none">
	<path fill="#36FF00" d="M22.8,11.999h-0.001c-1.133-0.741-2.76-2.412-4.06-3.813L17.43,9.138c0,0,1.011,1.133,1.959,2.107H15.28
		c-0.288-1.254-1.271-2.237-2.525-2.525V4.611c0.974,0.948,2.107,1.959,2.107,1.959l0.952-1.308c-1.401-1.3-3.072-2.927-3.813-4.06
		V1.2L12,1.201L12,1.2l0,0.001c-0.741,1.133-2.412,2.76-3.813,4.06L9.138,6.57c0,0,1.133-1.011,2.107-1.959V8.72
		C9.991,9.008,9.008,9.991,8.72,11.245H4.611C5.558,10.271,6.57,9.138,6.57,9.138L5.262,8.186c-1.3,1.401-2.927,3.072-4.06,3.813
		H1.2L1.201,12L1.2,12.001h0.001c1.133,0.741,2.76,2.412,4.06,3.813l1.308-0.952c0,0-1.011-1.133-1.959-2.107H8.72
		c0.288,1.254,1.271,2.237,2.525,2.525v4.109c-0.974-0.948-2.107-1.959-2.107-1.959l-0.952,1.308c1.401,1.3,3.072,2.927,3.813,4.06
		V22.8L12,22.799l0.001,0.001v-0.001c0.741-1.133,2.412-2.76,3.813-4.06l-0.952-1.308c0,0-1.133,1.011-2.107,1.959V15.28
		c1.254-0.288,2.237-1.271,2.525-2.525h4.109c-0.948,0.974-1.959,2.107-1.959,2.107l1.308,0.952c1.3-1.401,2.927-3.072,4.06-3.813
		H22.8V11.999L22.8,11.999z"/>
</g>
</svg>
 */