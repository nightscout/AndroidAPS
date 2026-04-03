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
 * Icon for Question mark.
 * Represents comment for help.
 *
 * replaces ic_cp_question
 *
 * Bounding box: x: 5.5-18.3, y: 1.2-22.8 (viewport: 24x24, ~90% height)
 */
val IcQuestion: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcQuestion",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFFFEAF05)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(12.062f, 17.861f)
            curveToRelative(-0.58f, 0f, -1.05f, -0.47f, -1.05f, -1.051f)
            curveToRelative(0f, -3.776f, 1.76f, -5.094f, 3.175f, -6.153f)
            curveToRelative(1.185f, -0.888f, 2.042f, -1.529f, 2.042f, -3.605f)
            curveToRelative(0f, -2.862f, -2.749f, -3.752f, -3.75f, -3.752f)
            curveToRelative(-2.157f, 0f, -3.9f, 1.293f, -4.78f, 3.547f)
            curveTo(7.487f, 7.388f, 6.876f, 7.656f, 6.338f, 7.444f)
            curveToRelative(-0.541f, -0.211f, -0.807f, -0.82f, -0.596f, -1.361f)
            curveTo(6.936f, 3.025f, 9.454f, 1.2f, 12.479f, 1.2f)
            curveToRelative(2.359f, 0f, 5.852f, 1.86f, 5.852f, 5.852f)
            curveToRelative(0f, 3.127f, -1.599f, 4.324f, -2.885f, 5.286f)
            curveToRelative(-1.304f, 0.976f, -2.333f, 1.746f, -2.333f, 4.472f)
            curveTo(13.113f, 17.39f, 12.642f, 17.861f, 12.062f, 17.861f)
            close()

            moveTo(13.575f, 21.349f)
            curveToRelative(0f, 0.801f, -0.65f, 1.451f, -1.451f, 1.451f)
            curveToRelative(-0.801f, 0f, -1.451f, -0.65f, -1.451f, -1.451f)
            curveToRelative(0f, -0.801f, 0.65f, -1.451f, 1.451f, -1.451f)
            curveTo(12.925f, 19.898f, 13.575f, 20.548f, 13.575f, 21.349f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcQuestionIconPreview() {
    Icon(
        imageVector = IcQuestion,
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
<g id="ic_question">
	<g display="inline">
		<circle fill="#FEAF05" cx="12.124" cy="21.349" r="1.451"/>
		<path fill="#FEAF05" d="M12.062,17.861c-0.58,0-1.05-0.47-1.05-1.051c0-3.776,1.76-5.094,3.175-6.153
			c1.185-0.888,2.042-1.529,2.042-3.605c0-2.862-2.749-3.752-3.75-3.752c-2.157,0-3.9,1.293-4.78,3.547
			C7.487,7.388,6.876,7.656,6.338,7.444c-0.541-0.211-0.807-0.82-0.596-1.361C6.936,3.025,9.454,1.2,12.479,1.2
			c2.359,0,5.852,1.86,5.852,5.852c0,3.127-1.599,4.324-2.885,5.286c-1.304,0.976-2.333,1.746-2.333,4.472
			C13.113,17.39,12.642,17.861,12.062,17.861z"/>
	</g>
</g>
</svg>
 */