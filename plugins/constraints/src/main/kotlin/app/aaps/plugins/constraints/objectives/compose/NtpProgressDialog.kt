package app.aaps.plugins.constraints.objectives.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun NtpProgressDialog(
    state: NtpVerificationState
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(text = stringResource(app.aaps.core.ui.R.string.please_wait))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { }
    )
}
