package app.aaps.ui.compose.alertKinds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.interfaces.alerts.AlertChoice
import app.aaps.core.interfaces.alerts.AlertKind
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * Ring, Silent or Off for each kind of alert, on this phone (`_docs/ALARMS.md`, section 6). A change applies at once,
 * so there is no Save: the back arrow only goes back. A protected kind shows that it always rings, with no choice.
 */
@Composable
fun AlertKindsScreen(
    groups: List<AlertKindGroup>,
    silentOrOff: Int,
    onChoice: (AlertKind, AlertChoice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(UiStrings.alert_kinds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(UiStrings.alert_kinds_intro), style = MaterialTheme.typography.bodyMedium)
                    if (silentOrOff > 0)
                        Text(stringResource(UiStrings.alert_kinds_silent_or_off, silentOrOff), style = MaterialTheme.typography.bodyMedium)
                }
            }
            groups.forEach { group ->
                item(key = "group:" + group.rows.first().kind.source) {
                    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                        HorizontalDivider()
                        Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(group.rows, key = { it.kind.id }) { row -> AlertKindRowView(row, onChoice) }
            }
        }
    }
}

@Composable
private fun AlertKindRowView(row: AlertKindRow, onChoice: (AlertKind, AlertChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        Text(stringResource(row.kind.title), style = MaterialTheme.typography.bodyLarge)
        if (row.kind.isProtected) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(UiStrings.alert_kind_protected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AlertChoice.entries.forEachIndexed { index, choice ->
                    SegmentedButton(
                        selected = row.choice == choice,
                        onClick = { onChoice(row.kind, choice) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AlertChoice.entries.size),
                        icon = {}
                    ) {
                        Text(stringResource(choice.label()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun AlertChoice.label(): TextRef = when (this) {
    AlertChoice.RING   -> UiStrings.alert_choice_ring
    AlertChoice.SILENT -> UiStrings.alert_choice_silent
    AlertChoice.OFF    -> UiStrings.alert_choice_off
}
