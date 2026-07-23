package app.aaps.core.ui.compose.icons

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
internal fun IcSmbIconPreview() {
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
