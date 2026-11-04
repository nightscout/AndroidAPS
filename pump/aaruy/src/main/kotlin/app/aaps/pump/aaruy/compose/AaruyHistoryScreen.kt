package app.aaps.pump.aaruy.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.pump.PumpHistoryScreen

@Composable
fun AaruyHistoryScreen(viewModel: AaruyHistoryViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PumpHistoryScreen(
        state = state,
        onSelectType = viewModel::selectType,
        onReload = viewModel::reload,
        // A key must fit in a Bundle. The time alone is not unique: two entries can share the same second.
        itemKey = { it.toString() },
        historyItem = { record, _ ->
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = viewModel.timeText(record),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = viewModel.valueText(record), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(text = viewModel.detailText(record), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}
