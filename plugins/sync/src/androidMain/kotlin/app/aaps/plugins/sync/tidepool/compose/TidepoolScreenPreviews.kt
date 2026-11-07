package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut

@Preview(showBackground = true)
@Composable
internal fun TidepoolScreenPreview() {
    MaterialTheme {
        TidepoolScreenContent(
            uiState = TidepoolUiState(
                connectionStatus = AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED,
                loggedIn = true,
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
