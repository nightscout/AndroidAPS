package app.aaps.pump.omnipod.eros.ui.compose

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile.ProfileValue
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.omnipod.eros.R
import app.aaps.pump.omnipod.eros.definition.PodHistoryEntryType
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ErosPodHistoryScreen(
    records: List<ErosHistoryRecordEntity>,
    rh: ResourceHelper,
    profileUtil: ProfileUtil,
    aapsOmnipodUtil: AapsOmnipodUtil
) {
    val groups = remember { PumpHistoryEntryGroup.getTranslatedList(rh) }
    var selectedGroup by remember { mutableStateOf(PumpHistoryEntryGroup.All) }

    val filteredRecords by remember(records, selectedGroup) {
        derivedStateOf {
            if (selectedGroup == PumpHistoryEntryGroup.All) records
            else records.filter { PodHistoryEntryType.getByCode(it.podEntryTypeCode).group == selectedGroup }
        }
    }

    val dateUtil = LocalDateUtil.current
    val groupedByDay by remember(filteredRecords) {
        derivedStateOf {
            filteredRecords.groupBy { dateUtil.dateString(it.date) }
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
                        text = dateUtil.dateStringRelative(itemsForDay.first().date, rh),
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

                items(itemsForDay) { record ->
                    ErosHistoryCard(record, rh, profileUtil, aapsOmnipodUtil, dateUtil)
                }
            }
        }
    }
}

@Composable
private fun ErosHistoryCard(
    record: ErosHistoryRecordEntity,
    rh: ResourceHelper,
    profileUtil: ProfileUtil,
    aapsOmnipodUtil: AapsOmnipodUtil,
    dateUtil: DateUtil
) {
    val entryType = PodHistoryEntryType.getByCode(record.podEntryTypeCode)
    ErosHistoryCardContent(
        commandName = rh.gs(entryType.resourceId),
        time = dateUtil.timeString(record.date),
        isSuccess = record.isSuccess,
        description = formatErosValue(record, entryType, rh, profileUtil, aapsOmnipodUtil)
    )
}

@Composable
internal fun ErosHistoryCardContent(
    commandName: String,
    time: String,
    isSuccess: Boolean,
    description: String = ""
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
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(AapsSpacing.medium))
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
                        color = if (isSuccess) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = AapsSpacing.small)
                    )
                }
            }
        }
    }
}

// region Previews

@Preview(showBackground = true, name = "Eros History - Success")
@Composable
private fun PreviewSuccess() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Set Bolus",
            time = "14:32",
            isSuccess = true,
            description = "2.50 U (15 g carbs)"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - TBR")
@Composable
private fun PreviewTbr() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Set Temporary Basal",
            time = "08:00",
            isSuccess = true,
            description = "1.50 U/h, 60 min"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - Failure")
@Composable
private fun PreviewFailure() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Deactivate Pod",
            time = "11:47",
            isSuccess = false,
            description = "No response from RileyLink"
        )
    }
}

@Preview(showBackground = true, name = "Eros History - Simple")
@Composable
private fun PreviewSimple() {
    MaterialTheme {
        ErosHistoryCardContent(
            commandName = "Get Pod Status",
            time = "09:15",
            isSuccess = true
        )
    }
}

// endregion

private fun formatErosValue(
    record: ErosHistoryRecordEntity,
    entryType: PodHistoryEntryType,
    rh: ResourceHelper,
    profileUtil: ProfileUtil,
    aapsOmnipodUtil: AapsOmnipodUtil
): String {
    if (!record.isSuccess) {
        return record.data ?: ""
    }
    return when (entryType) {
        PodHistoryEntryType.SET_TEMPORARY_BASAL,
        PodHistoryEntryType.SPLIT_TEMPORARY_BASAL -> {
            try {
                val tbp = aapsOmnipodUtil.gsonInstance.fromJson(record.data, TempBasalPair::class.java)
                rh.gs(R.string.omnipod_eros_history_tbr_value, tbp.insulinRate, tbp.durationMinutes)
            } catch (_: Exception) {
                ""
            }
        }

        PodHistoryEntryType.INSERT_CANNULA,
        PodHistoryEntryType.SET_BASAL_SCHEDULE    -> {
            record.data?.let {
                try {
                    val profileValues = aapsOmnipodUtil.gsonInstance.fromJson(it, Array<ProfileValue>::class.java)
                    profileUtil.getBasalProfilesDisplayable(profileValues, PumpType.OMNIPOD_EROS)
                } catch (_: Exception) {
                    ""
                }
            } ?: ""
        }

        PodHistoryEntryType.SET_BOLUS             -> {
            record.data?.let {
                if (it.contains(";")) {
                    val parts = it.split(";")
                    rh.gs(R.string.omnipod_eros_history_bolus_value_with_carbs, parts[0].toDouble(), parts[1].toDouble())
                } else {
                    rh.gs(R.string.omnipod_eros_history_bolus_value, it.toDouble())
                }
            } ?: ""
        }

        PodHistoryEntryType.PLAY_TEST_BEEP        -> record.data ?: ""

        else                                      -> ""
    }
}
