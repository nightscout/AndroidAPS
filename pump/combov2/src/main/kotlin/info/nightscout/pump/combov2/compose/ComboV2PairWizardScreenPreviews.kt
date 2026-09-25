package info.nightscout.pump.combov2.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun IdleSectionPreview() {
    MaterialTheme { IdleSection(onStartPairing = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun InProgressScanningPreview() {
    MaterialTheme {
        InProgressSection(
            state = ComboV2PairWizardUiState(
                phase = PairWizardPhase.InProgress,
                stepDescription = "Scanning for pump",
                overallProgress = 0.1f,
                scanningIndeterminate = true,
                pinEntryVisible = false
            ),
            onPinTextChange = {},
            onSubmitPin = { true },
            onRequestCancel = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun InProgressPinEntryPreview() {
    MaterialTheme {
        InProgressSection(
            state = ComboV2PairWizardUiState(
                phase = PairWizardPhase.InProgress,
                stepDescription = "Pump requests PIN",
                overallProgress = 0.6f,
                scanningIndeterminate = false,
                pinEntryVisible = true,
                pinText = "1234567",
                pinFailed = true
            ),
            onPinTextChange = {},
            onSubmitPin = { true },
            onRequestCancel = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun ConfirmCancelSectionPreview() {
    MaterialTheme { ConfirmCancelSection(onConfirm = {}, onDismiss = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun FinishedSectionPreview() {
    MaterialTheme { FinishedSection(onOk = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun AbortedSectionPreview() {
    MaterialTheme {
        AbortedSection(
            reason = "Pairing failed due to error: example error",
            onOk = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun DriverNotInitializedSectionPreview() {
    MaterialTheme { DriverNotInitializedSection(onGoBack = {}) }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
internal fun PermissionsDeniedSectionPreview() {
    MaterialTheme { PermissionsDeniedSection(onRetry = {}, onGoBack = {}) }
}
