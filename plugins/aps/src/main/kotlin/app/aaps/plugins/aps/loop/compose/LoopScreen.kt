package app.aaps.plugins.aps.loop.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsCard
import app.aaps.plugins.aps.R

@Composable
fun LoopScreen(
    state: LoopUiState,
    onRefresh: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status message when no data
            if (state.statusMessage.isNotEmpty() && state.lastRun.isEmpty()) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                )
            }

            if (state.lastRun.isNotEmpty() || state.source.isNotEmpty()) {
                // General info card
                AapsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LoopInfoRow(label = stringResource(R.string.last_run_label), value = state.lastRun)
                        if (state.source.isNotEmpty()) {
                            LoopDivider()
                            LoopInfoRow(label = stringResource(R.string.loop_aps_label), value = state.source)
                        }
                        if (state.request.isNotEmpty()) {
                            LoopDivider()
                            LoopFullWidthRow(label = stringResource(R.string.request_label), value = state.request)
                        }
                        if (state.constraintsProcessed.isNotEmpty()) {
                            LoopDivider()
                            LoopFullWidthRow(label = stringResource(R.string.loop_constraints_processed_label), value = state.constraintsProcessed)
                        }
                        if (state.constraints.isNotEmpty()) {
                            LoopDivider()
                            LoopFullWidthRow(label = stringResource(R.string.constraints), value = state.constraints)
                        }
                    }
                }

                // TBR card
                AapsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LoopInfoRow(label = stringResource(R.string.loop_tbr_request_time_label), value = state.tbrRequestTime)
                        LoopDivider()
                        LoopInfoRow(label = stringResource(R.string.loop_tbr_execution_time_label), value = state.tbrExecutionTime)
                        if (state.tbrSetByPump.isNotEmpty()) {
                            LoopDivider()
                            LoopFullWidthRow(label = stringResource(R.string.loop_tbr_set_by_pump_label), value = state.tbrSetByPump)
                        }
                    }
                }

                // SMB card
                AapsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LoopInfoRow(label = stringResource(R.string.loop_smb_request_time_label), value = state.smbRequestTime)
                        LoopDivider()
                        LoopInfoRow(label = stringResource(R.string.loop_smb_execution_time_label), value = state.smbExecutionTime)
                        if (state.smbSetByPump.isNotEmpty()) {
                            LoopDivider()
                            LoopFullWidthRow(label = stringResource(R.string.loop_smb_set_by_pump_label), value = state.smbSetByPump)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoopInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun LoopFullWidthRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun LoopDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
