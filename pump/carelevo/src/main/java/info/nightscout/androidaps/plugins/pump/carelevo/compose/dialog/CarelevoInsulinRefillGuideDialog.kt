package info.nightscout.androidaps.plugins.pump.carelevo.compose.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import info.nightscout.androidaps.plugins.pump.carelevo.R

@Composable
internal fun CarelevoInsulinRefillGuideDialog(
    onDismissRequest: () -> Unit
) {
    val steps = listOf(
        stringResource(R.string.carelevo_insulin_refill_step1),
        stringResource(R.string.carelevo_insulin_refill_step2),
        stringResource(R.string.carelevo_insulin_refill_step3),
        stringResource(R.string.carelevo_insulin_refill_step4),
        stringResource(R.string.carelevo_insulin_refill_step5),
        stringResource(R.string.carelevo_insulin_refill_step6),
        stringResource(R.string.carelevo_insulin_refill_step7),
        stringResource(R.string.carelevo_insulin_refill_step8),
        stringResource(R.string.carelevo_insulin_refill_step9),
        stringResource(R.string.carelevo_insulin_refill_step10),
        stringResource(R.string.carelevo_insulin_refill_step11),
        stringResource(R.string.carelevo_insulin_refill_step12)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.carelevo_insulin_refill_guide_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                steps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(app.aaps.core.ui.R.string.ok))
            }
        }
    )
}
