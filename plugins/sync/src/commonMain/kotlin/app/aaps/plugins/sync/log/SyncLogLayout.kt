package app.aaps.plugins.sync.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.ActionButtons
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.sync.SyncStrings

/**
 * The layout of the sync screens (NSClient, xDrip+, Tidepool): status rows at the top, the log in the
 * middle and the action buttons at the bottom.
 */
@Composable
internal fun SyncLogLayout(
    logList: List<SyncLogEntry>,
    dateUtil: DateUtil?,
    actions: List<ScreenAction>,
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        header()
        HorizontalDivider()
        SyncLogList(
            entries = logList,
            dateUtil = dateUtil,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        ActionButtons(actions)
    }
}

/** A "label value" row, like "Status: Connected". */
@Composable
internal fun LabelValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    boldValue: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (boldValue) FontWeight.Bold else null,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** The same label and icon on every sync screen. */
@Composable
internal fun fullSyncAction(onClick: () -> Unit) =
    ScreenAction(label = stringResource(SyncStrings.full_sync), icon = Icons.Filled.Sync, onClick = onClick)

@Composable
internal fun clearLogAction(onClick: () -> Unit) =
    ScreenAction(label = stringResource(SyncStrings.clear_log), icon = Icons.Filled.DeleteSweep, onClick = onClick)
