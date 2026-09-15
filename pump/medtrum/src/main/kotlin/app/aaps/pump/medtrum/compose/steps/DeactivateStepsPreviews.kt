package app.aaps.pump.medtrum.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "Confirm Deactivate")
@Composable
internal fun PreviewConfirmDeactivate() {
    MaterialTheme {
        ConfirmDeactivateContent(onNext = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivating - In Progress")
@Composable
internal fun PreviewDeactivating() {
    MaterialTheme {
        DeactivatingContent(isError = false, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivating - Error")
@Composable
internal fun PreviewDeactivatingError() {
    MaterialTheme {
        DeactivatingContent(isError = true, onDiscard = {}, onCancel = {})
    }
}

@Preview(showBackground = true, name = "Deactivate Complete")
@Composable
internal fun PreviewDeactivateComplete() {
    MaterialTheme {
        DeactivateCompleteContent(onNewPatch = {}, onDone = {})
    }
}
