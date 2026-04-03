package app.aaps.pump.omnipod.dash.ui.compose

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalDateUtil
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.utils.DateTimeUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.dash.R
import app.aaps.pump.omnipod.dash.history.data.BasalValuesRecord
import app.aaps.pump.omnipod.dash.history.data.BolusRecord
import app.aaps.pump.omnipod.dash.history.data.HistoryRecord
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.history.data.TempBasalRecord

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashPodHistoryScreen(
    records: List<HistoryRecord>,
    rh: ResourceHelper,
    profileUtil: ProfileUtil
) {
    val groups = remember { PumpHistoryEntryGroup.getTranslatedList(rh) }
    var selectedGroup by remember { mutableStateOf(PumpHistoryEntryGroup.All) }

    val filteredRecords by remember(records, selectedGroup) {
        derivedStateOf {
            if (selectedGroup == PumpHistoryEntryGroup.All) records
            else records.filter { groupForCommandType(it.commandType) == selectedGroup }
        }
    }

    val dateUtil = LocalDateUtil.current
    val groupedByDay by remember(filteredRecords) {
        derivedStateOf {
            filteredRecords.groupBy { dateUtil.dateString(it.displayTimestamp()) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        FlowRow(
            modifier = Modifier
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

        // History list with day headers
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            groupedByDay.forEach { (dateString, itemsForDay) ->
                stickyHeader(key = "header_$dateString") {
                    Text(
                        text = dateUtil.dateStringRelative(itemsForDay.first().displayTimestamp(), rh),
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

                items(itemsForDay, key = { it.displayTimestamp() }) { record ->
                    DashHistoryCard(record, rh, profileUtil, dateUtil)
                }
            }
        }
    }
}

@Composable
private fun DashHistoryCard(
    record: HistoryRecord,
    rh: ResourceHelper,
    profileUtil: ProfileUtil,
    dateUtil: DateUtil
) {
    HistoryCardContent(
        commandName = rh.gs(record.commandType.resourceId),
        time = dateUtil.timeString(record.displayTimestamp()),
        isSuccess = record.isSuccess(),
        description = formatValue(record, rh, profileUtil),
        extra = record.totalAmountDelivered?.let { rh.gs(R.string.omnipod_common_history_total_delivered, it) }
    )
}

@Composable
internal fun HistoryCardContent(
    commandName: String,
    time: String,
    isSuccess: Boolean,
    description: String = "",
    extra: String? = null
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AapsSpacing.large),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                tint = if (isSuccess) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.width(AapsSpacing.large))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = commandName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AapsSpacing.small)
                    )
                }

                extra?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AapsSpacing.extraSmall)
                    )
                }
            }
        }
    }
}

// region Previews

@Preview(showBackground = true, name = "History Card - Success with details")
@Composable
private fun PreviewSuccessCard() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Bolus",
            time = "14:32",
            isSuccess = true,
            description = "2.50 U",
            extra = "Total delivered: 48.25 U"
        )
    }
}

@Preview(showBackground = true, name = "History Card - Success simple")
@Composable
private fun PreviewSuccessSimple() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Acknowledge Alerts",
            time = "09:15",
            isSuccess = true
        )
    }
}

@Preview(showBackground = true, name = "History Card - Failure")
@Composable
private fun PreviewFailure() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "11:47",
            isSuccess = false,
            description = "Command not received by the pod"
        )
    }
}

@Preview(showBackground = true, name = "History Card - TBR")
@Composable
private fun PreviewTbr() {
    MaterialTheme {
        HistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "08:00",
            isSuccess = true,
            description = "1.50 U/h for 60 min"
        )
    }
}

@Preview(showBackground = true, name = "Filter Chips")
@Composable
private fun PreviewFilterChips() {
    val groups = listOf("All", "Bolus", "Basal", "Prime", "Alarm", "Config")
    MaterialTheme {
        FlowRow(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            groups.forEachIndexed { index, name ->
                FilterChip(
                    selected = index == 0,
                    onClick = {},
                    label = { Text(name) }
                )
            }
        }
    }
}

// endregion

private fun formatValue(record: HistoryRecord, rh: ResourceHelper, profileUtil: ProfileUtil): String {
    if (!record.isSuccess()) {
        return rh.gs(translatedFailure(record))
    }
    return when (record.commandType) {
        OmnipodCommandType.SET_TEMPORARY_BASAL -> {
            val tbr = record.record as? TempBasalRecord
            tbr?.let { rh.gs(R.string.omnipod_common_history_tbr_value, it.rate, it.duration) } ?: ""
        }

        OmnipodCommandType.SET_BOLUS           -> {
            val bolus = record.record as? BolusRecord
            bolus?.let { rh.gs(R.string.omnipod_common_history_bolus_value, it.amout) } ?: ""
        }

        OmnipodCommandType.SET_BASAL_PROFILE,
        OmnipodCommandType.SET_TIME,
        OmnipodCommandType.INSERT_CANNULA,
        OmnipodCommandType.RESUME_DELIVERY     -> {
            val basal = record.record as? BasalValuesRecord
            basal?.let { profileUtil.getBasalProfilesDisplayable(it.segments.toTypedArray(), PumpType.OMNIPOD_DASH) } ?: ""
        }

        else                                   -> ""
    }
}

private fun translatedFailure(record: HistoryRecord): Int = when {
    record.initialResult == InitialResult.FAILURE_SENDING                                         -> R.string.omnipod_dash_failed_to_send
    record.initialResult == InitialResult.NOT_SENT                                                -> R.string.omnipod_dash_command_not_sent
    record.initialResult == InitialResult.SENT && record.resolvedResult == ResolvedResult.FAILURE -> R.string.omnipod_dash_command_not_received_by_the_pod
    else                                                                                          -> R.string.omnipod_dash_unknown
}

private fun groupForCommandType(type: OmnipodCommandType): PumpHistoryEntryGroup = when (type) {
    OmnipodCommandType.INITIALIZE_POD,
    OmnipodCommandType.INSERT_CANNULA,
    OmnipodCommandType.DEACTIVATE_POD,
    OmnipodCommandType.DISCARD_POD            -> PumpHistoryEntryGroup.Prime

    OmnipodCommandType.CANCEL_TEMPORARY_BASAL,
    OmnipodCommandType.SET_BASAL_PROFILE,
    OmnipodCommandType.SET_TEMPORARY_BASAL,
    OmnipodCommandType.RESUME_DELIVERY,
    OmnipodCommandType.SUSPEND_DELIVERY       -> PumpHistoryEntryGroup.Basal

    OmnipodCommandType.SET_BOLUS,
    OmnipodCommandType.CANCEL_BOLUS           -> PumpHistoryEntryGroup.Bolus

    OmnipodCommandType.ACKNOWLEDGE_ALERTS,
    OmnipodCommandType.CONFIGURE_ALERTS,
    OmnipodCommandType.PLAY_TEST_BEEP         -> PumpHistoryEntryGroup.Alarm

    OmnipodCommandType.GET_POD_STATUS,
    OmnipodCommandType.SET_TIME               -> PumpHistoryEntryGroup.Configuration

    OmnipodCommandType.READ_POD_PULSE_LOG     -> PumpHistoryEntryGroup.Unknown
}
