package app.aaps.plugins.sync.openhumans.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Not logged in")
@Composable
internal fun OHScreenNotLoggedInPreview() {
    MaterialTheme {
        OHScreenContent(
            uiState = OHUiState(isLoggedIn = false),
            onSetup = {},
            onLogout = {},
            onUploadNow = {}
        )
    }
}

@Preview(showBackground = true, name = "Logged in")
@Composable
internal fun OHScreenLoggedInPreview() {
    MaterialTheme {
        OHScreenContent(
            uiState = OHUiState(isLoggedIn = true, projectMemberId = "12345678"),
            onSetup = {},
            onLogout = {},
            onUploadNow = {}
        )
    }
}
