package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalPreferences

@Composable
fun VersionOverlay(
    modifier: Modifier = Modifier,
) {
    val config = LocalConfig.current
    val preferences = LocalPreferences.current
    if (config.APS || config.PUMPCONTROL) {
        val colors = AapsTheme.generalColors
        val versionColor = when {
            config.COMMITTED                                                          -> colors.versionCommitted
            preferences.get(LongComposedKey.AppExpiration, config.VERSION_NAME) != 0L -> colors.versionWarning
            else                                                                      -> colors.versionUncommitted
        }
        Text(
            text = "${config.VERSION_NAME} (${config.HEAD.substring(0, minOf(4, config.HEAD.length))})",
            color = versionColor,
            fontSize = 10.sp,
            modifier = modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VersionOverlayPreview() {
    MaterialTheme {
        Text(
            text = "3.3.0 (abc1)",
            color = Color(0xFF4CAF50),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        )
    }
}
