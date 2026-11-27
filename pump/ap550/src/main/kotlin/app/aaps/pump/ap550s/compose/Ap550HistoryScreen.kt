package app.aaps.pump.ap550s.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.PumpHistoryScreen
import app.aaps.pump.ap550.protocol.comm.RecordTypes
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550s.database.Ap550HistoryRecord

@Composable
fun Ap550HistoryScreen(
    viewModel: Ap550HistoryViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PumpHistoryScreen(
        state = state,
        onSelectType = viewModel::selectType,
        onReload = viewModel::reload,
        itemKey = { it.timestamp },
        historyItem = { record, type -> Ap550HistoryItem(record, type, viewModel) }
    )
}

@Composable
private fun Ap550HistoryItem(
    record: Ap550HistoryRecord,
    type: Byte,
    viewModel: Ap550HistoryViewModel
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            Text(
                text = viewModel.formatTime(record),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (type) {
                RecordTypes.RECORD_TYPE_ALARM -> {
                    Text(text = record.alarm, style = MaterialTheme.typography.bodyMedium)
                }

                RecordTypes.RECORD_TYPE_BOLUS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = record.bolusType, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = viewModel.formatBolus(record),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                RecordTypes.RECORD_TYPE_DAILY -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LabeledValue(
                            label = stringResource(R.string.ap550_history_col_basal),
                            value = viewModel.formatDailyBasal(record)
                        )
                        LabeledValue(
                            label = stringResource(R.string.ap550_history_bolus),
                            value = viewModel.formatDailyBolus(record)
                        )
                        LabeledValue(
                            label = stringResource(R.string.ap550_history_dailyinsulin),
                            value = viewModel.formatDailyTotal(record)
                        )
                    }
                }

                RecordTypes.RECORD_TYPE_BASALHOUR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.formatTempBasal(record),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = viewModel.formatDuration(record),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                RecordTypes.RECORD_TYPE_REFILL -> {
                    Text(text = record.stringValue, style = MaterialTheme.typography.bodyMedium)
                }

                RecordTypes.RECORD_TYPE_PARAMMODIFY -> {
                    Text(
                        text = record.stringValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = record.alarm,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RecordTypes.RECORD_TYPE_BASALMODIFY -> {
                    Text(
                        text = record.stringValue,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                else -> {
                    Text(
                        text = viewModel.formatValue(record),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}