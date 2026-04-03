package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icon for xDrip.
 * Represents xDrip CGM integration.
 *
 * replaces ic_xdrip
 *
 * Bounding box: x: 4.0-20.0, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcXDrip: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcXdrip",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFB92929)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.046f, 1.2f)
            curveToRelative(1.709f, 1.721f, 3.367f, 3.401f, 5.036f, 5.069f)
            curveToRelative(1.108f, 1.108f, 2.236f, 2.184f, 2.957f, 3.622f)
            curveToRelative(1.601f, 3.192f, 1.091f, 7.127f, -1.339f, 9.92f)
            curveToRelative(-2.272f, 2.611f, -6.202f, 3.666f, -9.472f, 2.543f)
            curveTo(2.791f, 20.143f, 0.95f, 12.334f, 5.736f, 7.479f)
            curveTo(7.786f, 5.399f, 9.875f, 3.358f, 12.046f, 1.2f)
            close()

            moveTo(11.965f, 4.442f)
            curveTo(10.344f, 6.054f, 8.812f, 7.569f, 7.29f, 9.093f)
            curveToRelative(-1.479f, 1.481f, -2.126f, 3.275f, -1.986f, 5.359f)
            curveToRelative(0.228f, 3.373f, 3.434f, 6.299f, 6.662f, 6.041f)
            curveTo(11.965f, 15.185f, 11.965f, 9.873f, 11.965f, 4.442f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcXdripIconPreview() {
    Icon(
        imageVector = IcXDrip,
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
<g id="ic_xdrip">
	<path display="inline" fill="#B92929" d="M12.046,1.2c1.709,1.721,3.367,3.401,5.036,5.069c1.108,1.108,2.236,2.184,2.957,3.622
		c1.601,3.192,1.091,7.127-1.339,9.92c-2.272,2.611-6.202,3.666-9.472,2.543C2.791,20.143,0.95,12.334,5.736,7.479
		C7.786,5.399,9.875,3.358,12.046,1.2z M11.965,4.442C10.344,6.054,8.812,7.569,7.29,9.093c-1.479,1.481-2.126,3.275-1.986,5.359
		c0.228,3.373,3.434,6.299,6.662,6.041C11.965,15.185,11.965,9.873,11.965,4.442z"/>
</g>
</svg>
 */