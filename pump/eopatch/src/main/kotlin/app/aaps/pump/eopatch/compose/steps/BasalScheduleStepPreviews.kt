package app.aaps.pump.eopatch.compose.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardStepLayout

@Preview(showBackground = true, name = "Basal Schedule Complete")
@Composable
internal fun BasalScheduleStepPreview() {
    MaterialTheme {
        WizardStepLayout(
            primaryButton = WizardButton(text = "Finish", onClick = {})
        ) {
            Text(text = "Patch activation completed!", style = MaterialTheme.typography.titleLarge)
            Text(text = "'Basal 1' program has been enabled.", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Alerts you when the Patch nears its expiration time.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
