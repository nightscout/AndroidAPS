package app.aaps.pump.diaconn.compose

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.pump.PumpHistoryScreen
import app.aaps.pump.diaconn.common.RecordTypes
import app.aaps.pump.diaconn.database.DiaconnHistoryRecord

@Composable
fun DiaconnHistoryScreen(
    viewModel: DiaconnHistoryViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PumpHistoryScreen(
        state = state,
        onSelectType = viewModel::selectType,
        onReload = viewModel::reload,
        itemKey = { it.timestamp },
        historyItem = { record, type -> DiaconnHistoryItem(record, type, viewModel) }
    )
}

@Composable
private fun DiaconnHistoryItem(
    record: DiaconnHistoryRecord,
    type: Byte,
    viewModel: DiaconnHistoryViewModel
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = viewModel.formatTime(record),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (type) {
                RecordTypes.RECORD_TYPE_ALARM -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = record.alarm, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = viewModel.formatValue(record),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (record.stringValue.isNotEmpty()) {
                        Text(text = record.stringValue, style = MaterialTheme.typography.bodySmall)
                    }
                }

                RecordTypes.RECORD_TYPE_BOLUS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = record.bolusType, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = viewModel.formatValue(record) + " U",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (record.duration > 0) {
                            Text(
                                text = "${record.duration}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (record.stringValue.isNotEmpty()) {
                        Text(text = record.stringValue, style = MaterialTheme.typography.bodySmall)
                    }
                }

                RecordTypes.RECORD_TYPE_DAILY -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LabeledValue(label = stringResource(app.aaps.core.ui.R.string.bolus), value = viewModel.formatDailyBolus(record))
                        LabeledValue(label = stringResource(app.aaps.core.ui.R.string.basal), value = viewModel.formatDailyBasal(record))
                        LabeledValue(label = stringResource(app.aaps.core.ui.R.string.wizard_total), value = viewModel.formatDailyTotal(record))
                    }
                }

                RecordTypes.RECORD_TYPE_TB -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.formatValue(record),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (record.duration > 0) {
                            Text(
                                text = "${record.duration}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (record.stringValue.isNotEmpty()) {
                        Text(text = record.stringValue, style = MaterialTheme.typography.bodySmall)
                    }
                }

                RecordTypes.RECORD_TYPE_SUSPEND -> {
                    Text(text = record.stringValue, style = MaterialTheme.typography.bodyMedium)
                }

                else -> {
                    // BASALHOUR, REFILL
                    Text(
                        text = viewModel.formatValue(record),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (record.stringValue.isNotEmpty()) {
                        Text(text = record.stringValue, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
