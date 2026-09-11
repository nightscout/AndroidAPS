package app.aaps.pump.medtronic.compose

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.pump.common.defs.PumpHistoryEntryGroup
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MedtronicHistoryScreen(
    state: MedtronicHistoryUiState,
    groups: List<PumpHistoryEntryGroup>,
    rh: ResourceHelper,
    onSelectGroup: (PumpHistoryEntryGroup) -> Unit
) {
    val dateUtil = LocalDateUtil.current

    val groupedByDay by remember(state.records) {
        derivedStateOf {
            state.records
                .filter { it.atechDateTime != 0L }
                .groupBy { dateUtil.dateString(it.atechDateTime) }
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
                    selected = state.selectedGroup == group,
                    onClick = { onSelectGroup(group) },
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
                        text = dateUtil.dateStringRelative(itemsForDay.first().atechDateTime, rh),
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
                    MedtronicHistoryCard(record, dateUtil)
                }
            }
        }
    }
}

@Composable
private fun MedtronicHistoryCard(
    record: PumpHistoryEntry,
    dateUtil: DateUtil
) {
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AapsSpacing.large),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = record.entryType.description,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(AapsSpacing.medium))
                    Text(
                        text = dateUtil.timeString(record.atechDateTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (record.displayableValue.isNotEmpty()) {
                    Text(
                        text = record.displayableValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AapsSpacing.small)
                    )
                }
            }
        }
    }
}
