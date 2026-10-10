package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.sync.SyncLogEntry

@Preview(showBackground = true)
@Composable
internal fun TidepoolScreenPreview() {
    MaterialTheme {
        TidepoolScreenContent(
            uiState = TidepoolUiState(
                connectionStatus = "SESSION_ESTABLISHED",
                logList = listOf(
                    SyncLogEntry(action = "Starting upload"),
                    SyncLogEntry(action = "Uploading 24 records"),
                    SyncLogEntry(action = "Upload successful"),
                    SyncLogEntry(action = "Session token refreshed"),
                )
            )
        )
    }
}
