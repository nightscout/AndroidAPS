package app.aaps.plugins.sync.nsclientV3.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.sync.SyncLogEntry

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
                    SyncLogEntry(action = "UPLOAD", text = "Uploading treatments"),
                    SyncLogEntry(action = "READ", text = "Reading entries"),
                    SyncLogEntry(action = "SYNC", text = "Synchronization complete"),
                )
            )
        )
    }
}
