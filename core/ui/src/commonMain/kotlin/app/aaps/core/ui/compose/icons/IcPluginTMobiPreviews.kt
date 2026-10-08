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
internal fun IcPluginTMobiPreview() {
    Icon(
        imageVector = IcPluginTMobi,
        contentDescription = "T-Mobi Plugin Icon",
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
<g id="Plugin-t-mobi">
	<g>
		<g>
			<path opacity="0.3" fill="#FFFFFF" d="M22.8,13.043h-2.314c-0.217,0-0.392,0.176-0.392,0.392v1.943
				c0,0.217,0.176,0.392,0.392,0.392H22.8V13.043z"/>
			<rect x="18.412" y="14.005" opacity="0.3" fill="#FFFFFF" width="1.737" height="0.804"/>
			<polygon opacity="0.3" fill="#FFFFFF" points="17.879,14.607 17.879,14.206 18.412,14.005 18.583,14.206 18.583,14.607
				18.412,14.809 			"/>
			<path opacity="0.3" fill="#FFFFFF" d="M15.349,14.953c-0.08,0-0.145-0.065-0.145-0.145v-0.804c0-0.08,0.065-0.145,0.145-0.145
				h0.825c0.08,0,0.145,0.065,0.145,0.145v0.804c0,0.08-0.065,0.145-0.145,0.145H15.349z"/>
			<polygon opacity="0.3" fill="#FFFFFF" points="15.504,14.953 15.504,13.86 15.13,13.393 15.13,15.421 			"/>
			<rect x="16.851" y="14.206" opacity="0.3" fill="#FFFFFF" width="1.028" height="0.401"/>
			<rect x="17.879" y="14.206" opacity="0.3" fill="#FFFFFF" width="0.495" height="0.401"/>
			<polygon opacity="0.3" fill="#FFFFFF" points="16.851,14.607 16.851,14.206 16.319,14.005 16.319,14.809 			"/>
			<polygon opacity="0.3" fill="#FFFFFF" points="15.786,14.607 15.786,14.206 16.319,14.005 16.319,14.809 			"/>
			<rect x="15.838" y="14.206" opacity="0.3" fill="#FFFFFF" width="0.481" height="0.401"/>
			<rect x="16.319" y="14.206" opacity="0.3" fill="#FFFFFF" width="0.532" height="0.401"/>
			<rect x="15.495" y="14.206" opacity="0.3" fill="#FFFFFF" width="0.343" height="0.401"/>
			<path opacity="0.3" fill="#FFFFFF" d="M22.8,13.261h-2.409c-0.075,0-0.137,0.06-0.137,0.133v2.041
				c0,0.074,0.061,0.133,0.137,0.133H22.8V13.261z"/>
			<polygon opacity="0.3" fill="#FFFFFF" points="20.279,14.867 20.279,13.946 20.092,13.553 20.092,15.261 			"/>
			<rect x="18.375" y="14.206" opacity="0.3" fill="#FFFFFF" width="1.811" height="0.401"/>
			<rect x="20.147" y="14.206" opacity="0.3" fill="#FFFFFF" width="0.394" height="0.401"/>
			<rect x="20.279" y="13.946" opacity="0.3" fill="#FFFFFF" width="2.521" height="0.921"/>
			<g opacity="0.5">
				<path fill="#FFFFFF" d="M21.505,13.464c0.069,0,0.125,0.054,0.125,0.12l-0.324,1.646c0,0.066-0.056,0.12-0.125,0.12h-0.209
					c-0.069,0-0.125-0.054-0.125-0.12l0.324-1.646c0-0.066,0.056-0.12,0.125-0.12H21.505z"/>
				<path fill="#FFFFFF" d="M22.214,13.464c0.069,0,0.125,0.054,0.125,0.12l-0.324,1.646c0,0.066-0.056,0.12-0.125,0.12h-0.209
					c-0.069,0-0.125-0.054-0.125-0.12l0.324-1.646c0-0.066,0.056-0.12,0.125-0.12H22.214z"/>
				<path fill="#FFFFFF" d="M20.796,13.464c0.069,0,0.125,0.054,0.125,0.12l-0.324,1.646c0,0.066-0.056,0.12-0.125,0.12h0.001
					c-0.011,0-0.011-0.054-0.011-0.12v-1.646c0-0.066,0.056-0.12,0.125-0.12H20.796z"/>
				<path fill="#FFFFFF" d="M22.725,15.23l0.075-0.383v-1.383h-0.086c-0.069,0-0.125,0.054-0.125,0.12l-0.324,1.646
					c0,0.066,0.056,0.12,0.125,0.12h0.209C22.668,15.35,22.725,15.296,22.725,15.23z"/>
			</g>
		</g>
		<path opacity="0.3" fill="#D9DBDF" d="M8.898,12.753v4.267h5.03c0.67,0,1.213-0.543,1.213-1.213v-3.054h-0.603H8.898z"/>
		<path fill="#FFFFFF" d="M13.928,6.98H2.413C1.743,6.98,1.2,7.523,1.2,8.192v7.615c0,0.67,0.543,1.213,1.213,1.213h6.485v-0.188
			h0.416v-4.079h0.245v4.251h0.34v-4.251h0.283v4.251h0.34v-4.251h4.016h0.603V8.192C15.14,7.523,14.598,6.98,13.928,6.98z"/>
		<path opacity="0.4" fill="#FFFFFF" d="M13.229,15.465c-0.069,0-0.125-0.056-0.125-0.125v-1.866c0-0.069,0.056-0.125,0.125-0.125
			h0.016c0.069,0,0.125,0.056,0.125,0.125v1.866c0,0.069-0.056,0.125-0.125,0.125H13.229z"/>
		<path opacity="0.4" fill="#FFFFFF" d="M12.264,15.465c-0.069,0-0.125-0.056-0.125-0.125v-1.866c0-0.069,0.056-0.125,0.125-0.125
			h0.016c0.069,0,0.125,0.056,0.125,0.125v1.866c0,0.069-0.056,0.125-0.125,0.125H12.264z"/>
		<path opacity="0.4" fill="#FFFFFF" d="M11.299,15.465c-0.069,0-0.125-0.056-0.125-0.125v-1.866c0-0.069,0.056-0.125,0.125-0.125
			h0.016c0.069,0,0.125,0.056,0.125,0.125v1.866c0,0.069-0.056,0.125-0.125,0.125H11.299z"/>
		<g>
			<path opacity="0.2" fill="#FFFFFF" d="M8.898,16.484h5.191c0.289,0,0.523-0.235,0.523-0.526V12.28
				c0-0.29-0.234-0.526-0.523-0.526H8.898V16.484z"/>
			<path opacity="0.5" fill="#FFFFFF" d="M14.089,16.57H8.812v-4.902h5.277c0.336,0,0.609,0.274,0.609,0.612v3.678
				C14.698,16.295,14.425,16.57,14.089,16.57z M8.984,16.398h5.105c0.241,0,0.437-0.197,0.437-0.44V12.28
				c0-0.243-0.196-0.44-0.437-0.44H8.984V16.398z"/>
		</g>
	</g>
</g>
</svg>
 */
