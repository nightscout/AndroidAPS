package app.aaps.pump.equil.compose

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.equil.R
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.driver.definition.EquilHistoryEntryGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private val BolusColor = Color(0xFFE37575)
private val BasalColor = Color(0xFF67B4EF)
private val NormalColor = Color.Unspecified

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EquilHistoryScreen(
    viewModel: EquilHistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val commandHistory by viewModel.filteredCommandHistory.collectAsStateWithLifecycle()
    val pumpEvents by viewModel.pumpEvents.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.equil_title_history_events)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.equil_tab_action)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.equil_tab_equil)) }
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 0
                ) { page ->
                    when (page) {
                        0 -> CommandHistoryTab(commandHistory, selectedGroup, viewModel)
                        1 -> PumpEventTab(pumpEvents)
                    }
                }
            }
        }
    }
}

// region Tab 1: Command history

@Composable
private fun CommandHistoryTab(
    records: List<EquilHistoryRecord>,
    selectedGroup: EquilHistoryEntryGroup,
    viewModel: EquilHistoryViewModel
) {
    Column(Modifier.fillMaxSize()) {
        GroupFilterDropdown(selectedGroup, onSelected = viewModel::setFilter)

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.equil_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(records, key = { it.id }) { record ->
                    CommandHistoryRow(record, viewModel)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryRow(record: EquilHistoryRecord, viewModel: EquilHistoryViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = dateFormat.format(record.timestamp),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(120.dp),
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            record.type?.let {
                Text(
                    text = stringResource(it.resourceId),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = commandValue(record, viewModel),
                style = MaterialTheme.typography.bodySmall,
                color = if (record.isSuccess()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun commandValue(record: EquilHistoryRecord, viewModel: EquilHistoryViewModel): String {
    if (!record.isSuccess()) {
        return stringResource(EquilHistoryViewModel.failureStringRes(record.resolvedStatus))
    }
    return when (record.type) {
        EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL -> {
            val tbr = record.tempBasalRecord
            val duration = (tbr?.duration?.div(60_000) ?: 0)
            stringResource(R.string.equil_common_history_tbr_value, tbr?.rate ?: 0.0, duration)
        }

        EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS  -> {
            val tbr = record.tempBasalRecord
            val duration = (tbr?.duration?.div(60_000) ?: 0)
            val rate = if (duration > 0) (tbr!!.rate * (60.0 / duration)) else 0.0
            stringResource(R.string.equil_common_history_tbr_value, rate, duration)
        }

        EquilHistoryRecord.EventType.SET_BOLUS           ->
            record.bolusRecord?.let { stringResource(R.string.equil_common_history_bolus_value, it.amount) } ?: ""

        EquilHistoryRecord.EventType.INSERT_CANNULA      ->
            stringResource(R.string.history_manual_confirm)

        EquilHistoryRecord.EventType.EQUIL_ALARM         ->
            record.note ?: ""

        EquilHistoryRecord.EventType.SET_BASAL_PROFILE   ->
            record.basalValuesRecord?.let {
                viewModel.profileUtil.getBasalProfilesDisplayable(it.segments.toTypedArray(), PumpType.EQUIL)
            } ?: ""

        else                                             ->
            stringResource(R.string.equil_success)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFilterDropdown(
    selected: EquilHistoryEntryGroup,
    onSelected: (EquilHistoryEntryGroup) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        TextField(
            value = stringResource(selected.resourceId),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EquilHistoryEntryGroup.entries.forEach { group ->
                DropdownMenuItem(
                    text = { Text(stringResource(group.resourceId)) },
                    onClick = {
                        onSelected(group)
                        expanded = false
                    }
                )
            }
        }
    }
}

// endregion

// region Tab 2: Pump events

@Composable
private fun PumpEventTab(events: List<PumpEventItem>) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.equil_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(events, key = { "${it.timestamp}_${it::class.simpleName}_${it.hashCode()}" }) { item ->
                PumpEventRow(item)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PumpEventRow(item: PumpEventItem) {
    val (text, color) = when (item) {
        is PumpEventItem.Bolus -> {
            stringResource(R.string.equil_record_bolus, item.amountU) to BolusColor
        }

        is PumpEventItem.BasalChange -> {
            val res = if (item.isTemporary) R.string.equil_record_basal_temp else R.string.equil_record_basal
            stringResource(res, "%.3f".format(item.rateUH)) to BasalColor
        }

        is PumpEventItem.Event -> {
            stringResource(item.descriptionRes) to NormalColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = dateFormat.format(item.timestamp),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(120.dp),
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

// endregion
