package app.aaps.plugins.sync.xdrip.compose

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.sync.SyncLogEntry

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun XdripScreenPreview() {
    MaterialTheme {
        XdripScreenContent(
            uiState = XdripUiState(
                queue = "3",
                logList = listOf(
                    SyncLogEntry(action = "BG", text = "Sending glucose value 5.5"),
                    SyncLogEntry(action = "TREATMENT", text = "Sending bolus 1.5U"),
                    SyncLogEntry(action = "STATUS", text = "Loop running, IOB 2.3U"),
                )
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun XdripScreenEmptyPreview() {
    MaterialTheme {
        XdripScreenContent(
            uiState = XdripUiState(
                queue = "0",
                logList = emptyList()
            )
        )
    }
}
