package app.aaps.pump.omnipod.omnipod5.ui.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.omnipod5.R
import app.aaps.pump.omnipod.common.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.common.history.data.BolusRecord
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.history.data.HistoryRecord
import app.aaps.pump.omnipod.common.history.data.TempBasalRecord
import app.aaps.core.ui.R as CoreUiR
import app.aaps.pump.omnipod.common.R as OmnipodCommonR

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun O5PodHistoryScreen(
    records: List<HistoryRecord>,
    rh: ResourceHelper, profileUtil: ProfileUtil,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) {
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = { Text(stringResource(OmnipodCommonR.string.omnipod_common_pod_management_button_pod_history)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(CoreUiR.string.back)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            O5PodHistoryContent(rh, records, profileUtil, Modifier.padding(paddingValues))
        }
    } else {
        O5PodHistoryContent(rh, records, profileUtil, Modifier)
    }
}

@Composable
private fun O5PodHistoryContent(
    rh: ResourceHelper,
    records: List<HistoryRecord>,
    profileUtil: ProfileUtil,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) return
    val groups = remember { PumpHistoryEntryGroup.getTranslatedList(rh) }
    var selectedGroup by remember { mutableStateOf(PumpHistoryEntryGroup.All) }
    val dateUtil = LocalDateUtil.current
    val filtered = if (selectedGroup == PumpHistoryEntryGroup.All) records else records.filter {
        groupForCommandType(it.commandType) == selectedGroup
    }
    val grouped = filtered.groupBy { dateUtil.dateString(it.displayTimestamp()) }

    LazyColumn(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        item {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                groups.forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        label = { Text(group.translated ?: "") }
                    )
                }
            }
        }
        grouped.forEach { (date, dayRecords) ->
            stickyHeader(key = date) {
                Text(
                    text = dateUtil.dateStringRelative(dayRecords.first().displayTimestamp(), rh),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = AapsSpacing.medium),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(dayRecords, key = { it.id }) { record ->
                O5HistoryCard(record, rh, profileUtil, dateUtil)
            }
        }
    }
}

@Composable
private fun O5HistoryCard(record: HistoryRecord, rh: ResourceHelper, profileUtil: ProfileUtil, dateUtil: DateUtil) {
    AapsCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(AapsSpacing.large), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (record.isSuccess()) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                tint = if (record.isSuccess()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(AapsSpacing.large))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = rh.gs(record.commandType.resourceId),
                        fontWeight = FontWeight.SemiBold,
                        color = if (record.isSuccess()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    Text(dateUtil.timeString(record.displayTimestamp()), style = MaterialTheme.typography.bodySmall)
                }
                formatValue(record, rh, profileUtil)?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = AapsSpacing.small))
                }
            }
        }
    }
}

private fun formatValue(record: HistoryRecord, rh: ResourceHelper, profileUtil: ProfileUtil): String? {
    if (!record.isSuccess()) return rh.gs(R.string.omnipod_o5_history_command_failed)
    return when (val value = record.record) {
        is TempBasalRecord -> rh.gs(R.string.omnipod_o5_history_tbr_value, value.rate, value.duration)
        is BolusRecord -> when (value.bolusType) {
            BolusType.BASAL_CORRECTION ->
                rh.gs(R.string.omnipod_o5_history_basal_drift_compensation_bolus_value, value.amount)
            else -> rh.gs(R.string.omnipod_o5_history_bolus_value, value.amount)
        }
        is BasalValuesRecord -> profileUtil.getBasalProfilesDisplayable(value.segments.toTypedArray(), PumpType.OMNIPOD_5)
        null -> null
    }
}

private fun groupForCommandType(type: OmnipodCommandType): PumpHistoryEntryGroup = when (type) {
    OmnipodCommandType.INITIALIZE_POD, OmnipodCommandType.INSERT_CANNULA,
    OmnipodCommandType.DEACTIVATE_POD, OmnipodCommandType.DISCARD_POD -> PumpHistoryEntryGroup.Prime
    OmnipodCommandType.CANCEL_TEMPORARY_BASAL, OmnipodCommandType.SET_BASAL_PROFILE,
    OmnipodCommandType.SET_TEMPORARY_BASAL, OmnipodCommandType.RESUME_DELIVERY,
    OmnipodCommandType.SUSPEND_DELIVERY -> PumpHistoryEntryGroup.Basal
    OmnipodCommandType.SET_BOLUS, OmnipodCommandType.CANCEL_BOLUS -> PumpHistoryEntryGroup.Bolus
    OmnipodCommandType.ACKNOWLEDGE_ALERTS, OmnipodCommandType.CONFIGURE_ALERTS,
    OmnipodCommandType.PLAY_TEST_BEEP -> PumpHistoryEntryGroup.Alarm
    OmnipodCommandType.GET_POD_STATUS, OmnipodCommandType.SET_TIME -> PumpHistoryEntryGroup.Configuration
    OmnipodCommandType.READ_POD_PULSE_LOG -> PumpHistoryEntryGroup.Unknown
}
