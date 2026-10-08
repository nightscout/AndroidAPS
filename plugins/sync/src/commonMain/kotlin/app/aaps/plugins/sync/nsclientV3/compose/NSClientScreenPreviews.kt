package app.aaps.plugins.sync.nsclientV3.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.nsclient.NSClientLog

@Preview(showBackground = true)
@Composable
internal fun NSClientScreenPreview() {
    MaterialTheme {
        NSClientScreenContent(
            uiState = NSClientUiState(
                url = "https://nightscout.example.com",
                status = "Connected",
                queue = "0",
                paused = false,
                logList = listOf(
                    NSClientLog(action = "UPLOAD", logText = "Uploading treatments"),
                    NSClientLog(action = "READ", logText = "Reading entries"),
                    NSClientLog(action = "SYNC", logText = "Synchronization complete"),
                )
            )
        )
    }
}
