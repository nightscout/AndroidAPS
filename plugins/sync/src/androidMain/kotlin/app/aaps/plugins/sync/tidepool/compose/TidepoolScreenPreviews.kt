package app.aaps.plugins.sync.tidepool.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun TidepoolScreenPreview() {
    MaterialTheme {
        TidepoolScreenContent(
            uiState = TidepoolUiState(
                connectionStatus = "SESSION_ESTABLISHED",
                logList = listOf(
                    TidepoolLog(status = "Starting upload"),
                    TidepoolLog(status = "Uploading 24 records"),
                    TidepoolLog(status = "Upload successful"),
                    TidepoolLog(status = "Session token refreshed"),
                )
            )
        )
    }
}
