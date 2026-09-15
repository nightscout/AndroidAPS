package app.aaps.pump.eopatch.compose.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.eopatch.R

@Preview(showBackground = true, name = "Connect - Scanning")
@Composable
internal fun ConnectStepScanningPreview() {
    MaterialTheme {
        WizardStepLayout {
            Text(text = stringResource(R.string.patch_connect_new), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.patch_connect_new_desc), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(R.string.patch_wake_up_pairing_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}
