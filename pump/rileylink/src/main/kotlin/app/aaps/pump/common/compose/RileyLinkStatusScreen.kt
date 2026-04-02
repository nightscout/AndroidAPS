package app.aaps.pump.common.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.common.hw.rileylink.R
import kotlinx.coroutines.launch

@Composable
fun RileyLinkStatusScreen(
    viewModel: RileyLinkStatusViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(app.aaps.core.ui.R.string.settings)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(app.aaps.core.ui.R.string.history)) }
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> GeneralTab(state)
                1 -> HistoryTab(state)
            }
        }
    }
}

@Composable
private fun GeneralTab(state: RileyLinkStatusUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        // RileyLink card
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(
                    text = stringResource(R.string.rileylink_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                InfoRow(stringResource(R.string.rileylink_address), state.address)
                InfoRow(stringResource(R.string.rileylink_name), state.name)
                state.batteryLevel?.let { InfoRow(stringResource(R.string.rileylink_battery_level), it) }
                InfoRow(stringResource(R.string.rileylink_connection_status), state.connectionStatus)
                InfoRow(stringResource(R.string.rileylink_connection_error), state.connectionError)
                InfoRow(stringResource(R.string.rileylink_firmware_version), state.firmwareVersion)
            }
        }

        // Device card
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AapsSpacing.large), verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Text(
                    text = stringResource(R.string.rileylink_device),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                InfoRow(stringResource(R.string.rileylink_device_type), state.deviceType)
                state.configuredDeviceModel?.let { InfoRow(stringResource(R.string.rileylink_configured_device_model), it) }
                state.connectedDeviceModel?.let { InfoRow(stringResource(R.string.rileylink_connected_device_model), it) }
                InfoRow(stringResource(R.string.rileylink_pump_serial_number), state.serialNumber)
                InfoRow(stringResource(R.string.rileylink_pump_frequency), state.pumpFrequency)
                state.lastUsedFrequency?.let { InfoRow(stringResource(R.string.rileylink_last_used_frequency), it) }
                InfoRow(stringResource(R.string.rileylink_last_device_contact), state.lastDeviceContact)
            }
        }
    }
}

@Composable
private fun HistoryTab(state: RileyLinkStatusUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        items(state.historyItems) { item ->
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(AapsSpacing.large)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.source,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(AapsSpacing.medium))
                            Text(
                                text = item.time,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AapsSpacing.small)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.5f)
        )
    }
}
