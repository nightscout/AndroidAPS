package app.aaps.plugins.sync.wear.compose

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.PushedWatchfaceId

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun WearMainContentPreview() {
    MaterialTheme {
        WearMainContent(
            uiState = WearUiState(
                connectedDevice = "Galaxy Watch 5 (a1b2)",
                isDeviceConnected = true,
                hasCustomWatchface = true,
                watchfaceName = "AAPS V2",
                watchFacePushSupported = true,
                installedWatchface = PushedWatchfaceId.CWF
            ),
            onResendData = {},
            onOpenSettings = {},
            onLoadWatchface = {},
            onInfosWatchface = {},
            onExportTemplate = {},
            onMoreWatchfaces = {},
            onSelectPushedWatchface = {},
            onDismissCustomWatchfaceNotShown = {}
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun WearMainContentComplicationsFacePreview() {
    MaterialTheme {
        WearMainContent(
            // The complications face chosen while the watch still holds the custom one: the chosen
            // row carries the "Installing…" note, and its picture shows under the choice
            uiState = WearUiState(
                connectedDevice = "Galaxy Watch 8 (c3d4)",
                isDeviceConnected = true,
                hasCustomWatchface = true,
                watchfaceName = "Analog G-Watch",
                customWatchfaceSelected = false,
                watchFacePushSupported = true,
                installedWatchface = PushedWatchfaceId.CWF
            ),
            onResendData = {},
            onOpenSettings = {},
            onLoadWatchface = {},
            onInfosWatchface = {},
            onExportTemplate = {},
            onMoreWatchfaces = {},
            onSelectPushedWatchface = {},
            onDismissCustomWatchfaceNotShown = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun WearMainContentDisconnectedPreview() {
    MaterialTheme {
        WearMainContent(
            uiState = WearUiState(
                connectedDevice = "No watch connected",
                isDeviceConnected = false
            ),
            onResendData = {},
            onOpenSettings = {},
            onLoadWatchface = {},
            onInfosWatchface = {},
            onExportTemplate = {},
            onMoreWatchfaces = {},
            onSelectPushedWatchface = {},
            onDismissCustomWatchfaceNotShown = {}
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun CwfInfosContentPreview() {
    MaterialTheme {
        CwfInfosContent(
            state = CwfInfosState(
                title = "AAPS V2 (1.0)",
                fileName = "Filename: AAPS_V2.zip",
                author = "Author: Someone",
                createdAt = "Created: 2025-01-15",
                version = "Version: 1.0",
                isVersionOk = true,
                comment = "Comment: Custom watchface for AAPS",
                prefTitle = "Required preferences (locked by CWF)",
                preferences = listOf(
                    CwfPrefItem("Show IOB", true),
                    CwfPrefItem("Show COB", true),
                    CwfPrefItem("Show Delta", false)
                ),
                viewElements = listOf(
                    CwfViewItem("\"status\":", "Loop status"),
                    CwfViewItem("\"iob1\":", "IOB value"),
                    CwfViewItem("\"cob1\":", "COB value")
                )
            )
        )
    }
}
