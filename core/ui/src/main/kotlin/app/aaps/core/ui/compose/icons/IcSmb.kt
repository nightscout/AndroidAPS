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
 * Icon for SMB (Super Micro Bolus).
 * Represents super micro bolus insulin delivery.
 *
 * Bounding box: x: 8.5-15.9, y: 1.2-22.3 (viewport: 24x24, ~90% height)
 */
val IcSmb: ImageVector by lazy {
    ImageVector.Builder(
        name = "IcSmb",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color(0xFF1E88E5)),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f
        ) {
            moveTo(15.478f, 6.931f)
            curveTo(14.897f, 4.642f, 12.501f, 1.2f, 12.001f, 1.2f)
            curveToRelative(-0.5f, 0f, -2.904f, 3.449f, -3.482f, 5.734f)
            curveToRelative(-0.387f, 1.528f, 0.103f, 2.839f, 1.454f, 3.731f)
            curveToRelative(1.318f, 0.87f, 2.739f, 0.856f, 4.056f, -0.012f)
            curveTo(15.382f, 9.76f, 15.869f, 8.47f, 15.478f, 6.931f)
            close()

            moveTo(13.351f, 9.623f)
            curveToRelative(-0.457f, 0.302f, -0.916f, 0.455f, -1.365f, 0.455f)
            curveToRelative(-0.441f, 0f, -0.889f, -0.149f, -1.335f, -0.442f)
            curveToRelative(-0.903f, -0.597f, -1.201f, -1.36f, -0.938f, -2.4f)
            curveToRelative(0.364f, -1.438f, 1.536f, -3.342f, 2.287f, -4.335f)
            curveToRelative(0.749f, 0.992f, 1.917f, 2.894f, 2.283f, 4.333f)
            curveTo(14.547f, 8.271f, 14.25f, 9.03f, 13.351f, 9.623f)
            close()

            moveTo(15.478f, 18.42f)
            curveToRelative(-0.581f, -2.289f, -2.977f, -5.731f, -3.477f, -5.731f)
            curveToRelative(-0.5f, 0f, -2.904f, 3.449f, -3.482f, 5.734f)
            curveToRelative(-0.387f, 1.528f, 0.103f, 2.839f, 1.454f, 3.731f)
            curveToRelative(1.318f, 0.87f, 2.739f, 0.856f, 4.056f, -0.012f)
            curveTo(15.382f, 21.25f, 15.869f, 19.96f, 15.478f, 18.42f)
            close()

            moveTo(13.351f, 21.113f)
            curveToRelative(-0.457f, 0.302f, -0.916f, 0.455f, -1.365f, 0.455f)
            curveToRelative(-0.441f, 0f, -0.889f, -0.149f, -1.335f, -0.442f)
            curveToRelative(-0.903f, -0.597f, -1.201f, -1.36f, -0.938f, -2.4f)
            curveToRelative(0.364f, -1.438f, 1.536f, -3.342f, 2.287f, -4.335f)
            curveToRelative(0.749f, 0.992f, 1.917f, 2.894f, 2.283f, 4.333f)
            curveTo(14.547f, 19.761f, 14.25f, 20.519f, 13.351f, 21.113f)
            close()
        }
    }.build()
}

@Preview(showBackground = true)
@Composable
private fun IcSmbIconPreview() {
    Icon(
        imageVector = IcSmb,
        contentDescription = null,
        modifier = Modifier
            .padding(0.dp)
            .size(48.dp),
        tint = Color.Unspecified
    )
}

/*

<g id="ic_smb" display="none">
	<g display="inline">
		<g>
			<g>
				<path fill="#1E88E5" d="M15.478,6.931C14.897,4.642,12.501,1.2,12.001,1.2c-0.5,0-2.904,3.449-3.482,5.734
					c-0.387,1.528,0.103,2.839,1.454,3.731c1.318,0.87,2.739,0.856,4.056-0.012C15.382,9.76,15.869,8.47,15.478,6.931z
					 M13.351,9.623c-0.457,0.302-0.916,0.455-1.365,0.455c-0.441,0-0.889-0.149-1.335-0.442c-0.903-0.597-1.201-1.36-0.938-2.4
					c0.364-1.438,1.536-3.342,2.287-4.335c0.749,0.992,1.917,2.894,2.283,4.333C14.547,8.271,14.25,9.03,13.351,9.623z"/>
			</g>
		</g>
	</g>
	<g display="inline">
		<g>
			<g>
				<path fill="#1E88E5" d="M15.478,18.42c-0.581-2.289-2.977-5.731-3.477-5.731c-0.5,0-2.904,3.449-3.482,5.734
					c-0.387,1.528,0.103,2.839,1.454,3.731c1.318,0.87,2.739,0.856,4.056-0.012C15.382,21.25,15.869,19.96,15.478,18.42z
					 M13.351,21.113c-0.457,0.302-0.916,0.455-1.365,0.455c-0.441,0-0.889-0.149-1.335-0.442c-0.903-0.597-1.201-1.36-0.938-2.4
					c0.364-1.438,1.536-3.342,2.287-4.335c0.749,0.992,1.917,2.894,2.283,4.333C14.547,19.761,14.25,20.519,13.351,21.113z"/>
			</g>
		</g>
	</g>
</g>

 */