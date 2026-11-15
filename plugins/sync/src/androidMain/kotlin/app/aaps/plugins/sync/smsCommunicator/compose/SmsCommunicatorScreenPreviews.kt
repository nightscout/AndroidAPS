package app.aaps.plugins.sync.smsCommunicator.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true)
@Composable
internal fun SmsCommunicatorScreenPreview() {
    MaterialTheme {
        SmsCommunicatorScreenContent(
            uiState = SmsCommunicatorUiState(
                messages = listOf(
                    SmsItem(time = "12:00", phoneNumber = "+1234567890", text = "BG", isReceived = true, isSent = false, isProcessed = true, isIgnored = false),
                    SmsItem(time = "12:00", phoneNumber = "+1234567890", text = "BG: 5.5 mmol/l", isReceived = false, isSent = true, isProcessed = true, isIgnored = false),
                    SmsItem(time = "12:05", phoneNumber = "+9876543210", text = "BOLUS 1.5", isReceived = true, isSent = false, isProcessed = false, isIgnored = false),
                    SmsItem(time = "12:10", phoneNumber = "+1111111111", text = "LOOP STATUS", isReceived = true, isSent = false, isProcessed = false, isIgnored = true),
                )
            )
        )
    }
}
