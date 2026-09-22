package app.aaps.pump.carelevo.compose.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.pump.carelevo.R

@Composable
internal fun CarelevoPumpStopDurationDialog(
    options: List<Int>,
    labels: List<String>,
    initialIndex: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedIndex by remember(initialIndex) { mutableIntStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.carelevo_pump_stop_duration_title)) },
        text = {
            Column {
                labels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index }
                        )
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selectedIndex]) }) {
                Text(text = stringResource(R.string.carelevo_btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(R.string.carelevo_btn_cancel))
            }
        }
    )
}
