package app.aaps.pump.insight.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.pump.insight.descriptors.AlertStatus

/**
 * UI state for [InsightAlertScreen], produced by [app.aaps.pump.insight.app_layer.activities.InsightAlertActivity]
 * from observing [app.aaps.pump.insight.InsightAlertService.alertLiveData].
 */
data class InsightAlertUiState(
    val iconRes: Int,
    val errorCode: String,
    val title: String,
    val description: CharSequence?,
    val alertStatus: AlertStatus?,
    val muteEnabled: Boolean,
    val confirmEnabled: Boolean
)

@Composable
fun InsightAlertScreen(
    state: InsightAlertUiState,
    onMute: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = state.iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = state.errorCode,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        state.description?.let { desc ->
            Text(
                text = desc.toString(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.size(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (state.alertStatus != AlertStatus.SNOOZED) {
                TextButton(
                    onClick = onMute,
                    enabled = state.muteEnabled
                ) {
                    Text(stringResource(id = app.aaps.core.ui.R.string.mute))
                }
            }
            OutlinedButton(
                onClick = onConfirm,
                enabled = state.confirmEnabled
            ) {
                Text(stringResource(id = app.aaps.core.ui.R.string.confirm))
            }
        }
    }
}
