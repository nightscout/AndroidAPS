package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout

@Preview(showBackground = true, name = "Safe Deactivation")
@Composable
internal fun SafeDeactivationStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Discard Patch", onClick = {}),
            secondaryButton = WizardButton(text = "Force Reset", onClick = {})
        ) {
            Text(text = "Discard Patch", style = MaterialTheme.typography.titleLarge)
            Text(text = "To change to new Patch, the current Patch must be discarded.", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Expiration time: 48:00:00 (3 day)", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Reservoir: 50U+", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
