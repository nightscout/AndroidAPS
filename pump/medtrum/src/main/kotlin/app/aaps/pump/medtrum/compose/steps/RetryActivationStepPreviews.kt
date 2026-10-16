package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Retry - Prompt")
@Composable
internal fun PreviewRetryPrompt() {
    MaterialTheme {
        RetryActivationContent(isConnecting = false, onRetry = {}, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Retry - Connecting")
@Composable
internal fun PreviewRetryConnecting() {
    MaterialTheme {
        RetryActivationContent(isConnecting = true, onRetry = {}, onDiscard = {}, onCancel = {})
    }
}
