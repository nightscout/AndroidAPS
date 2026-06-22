package app.aaps.plugins.automation.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.MasterOfflineBanner
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.plugins.automation.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// Standard Material disabled opacity, applied to the whole row when editing is disabled.
private const val DISABLED_ROW_ALPHA = 0.38f

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    onToggleEnabled: (position: Int, checked: Boolean) -> Unit,
    onEditEvent: (position: Int) -> Unit,
    onDeleteEvent: (position: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onMoveFinished: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    // When false (client whose master is unreachable) the whole list is greyed out and
    // non-interactive and the add FAB is hidden — edits couldn't sync to the master right now.
    editingEnabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MasterOfflineBanner(editingEnabled = editingEnabled)
                if (state.events.isEmpty()) {
                    EmptyState(modifier = Modifier.weight(1f))
                } else {
                    EventsList(
                        state = state,
                        onToggleEnabled = onToggleEnabled,
                        onEditEvent = onEditEvent,
                        onDeleteEvent = onDeleteEvent,
                        onMove = onMove,
                        onMoveFinished = onMoveFinished,
                        editingEnabled = editingEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                LogPanel(
                    logHtml = state.logHtml,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (editingEnabled) {
                AapsFab(
                    onClick = onAddClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(AapsSpacing.extraLarge)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_automation)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AapsSpacing.xxLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.automation),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.automation_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )
    }
}

@Composable
private fun EventsList(
    state: AutomationUiState,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onEditEvent: (Int) -> Unit,
    onDeleteEvent: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit,
    editingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AapsSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        itemsIndexed(
            items = state.events,
            key = { _, e -> e.key }
        ) { _, event ->
            ReorderableItem(
                state = reorderState,
                key = event.key
            ) { isDragging ->
                val elevation = if (isDragging) AapsSpacing.medium else AapsSpacing.extraSmall
                AutomationEventCard(
                    event = event,
                    elevation = elevation,
                    editingEnabled = editingEnabled,
                    dragModifier = if (editingEnabled && !event.readOnly) Modifier.draggableHandle(onDragStopped = { onMoveFinished() }) else Modifier,
                    onToggleEnabled = { checked -> onToggleEnabled(event.position, checked) },
                    onEdit = { onEditEvent(event.position) },
                    onDelete = { onDeleteEvent(event.position) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AutomationEventCard(
    event: AutomationEventUi,
    elevation: Dp,
    editingEnabled: Boolean,
    dragModifier: Modifier,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Invalid actions are signalled by error-tinting the title — matching the Scenes screen's
    // isInvalid treatment instead of a card border.
    val nameColor = if (!event.actionsValid) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editingEnabled) Modifier else Modifier.alpha(DISABLED_ROW_ALPHA)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // editingEnabled is folded into every control's `enabled` so the greyed row is both
            // non-interactive AND accessibility-correct: a disabled control is skipped by TalkBack's
            // activation but still readable. (A pointer-consuming overlay blocked touch only and leaked
            // activation via accessibility services.) The drag handle is also disabled via dragModifier.
            Checkbox(
                checked = event.isEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = !event.readOnly && editingEnabled
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = nameColor
                )
                IconRow(event = event)
            }
            IconButton(onClick = onEdit, enabled = editingEnabled) {
                Icon(Icons.Default.Edit, contentDescription = null)
            }
            if (event.readOnly) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.system_automation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IconButton(onClick = onDelete, enabled = editingEnabled) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
            }
            IconButton(
                onClick = {},
                modifier = dragModifier,
                enabled = !event.readOnly && editingEnabled
            ) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.reorder)
                )
            }
        }
    }
}

@Composable
private fun IconRow(event: AutomationEventUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AapsSpacing.extraSmall)
    ) {
        event.triggerIcons.forEach { ai ->
            Icon(
                imageVector = ai.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = AapsSpacing.small),
                tint = ai.tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .padding(horizontal = AapsSpacing.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        event.actionIcons.forEach { ai ->
            Icon(
                imageVector = ai.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = AapsSpacing.small),
                tint = ai.tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun LogPanel(logHtml: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column {
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
            ) {
                Text(
                    text = htmlToAnnotated(logHtml),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun htmlToAnnotated(html: String): AnnotatedString {
    if (html.isEmpty()) return AnnotatedString("")
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return AnnotatedString(spanned.toString())
}

// ---------- Previews ----------

private fun sampleState() =
    AutomationUiState(
        events = listOf(
            AutomationEventUi(
                key = 0,
                position = 0,
                title = "Morning wakeup TT",
                isEnabled = true,
                readOnly = false,
                userAction = false,
                systemAction = false,
                actionsValid = true,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation))
            ),
            AutomationEventUi(
                key = 1,
                position = 1,
                title = "User: Snack reminder",
                isEnabled = true,
                readOnly = false,
                userAction = true,
                systemAction = false,
                actionsValid = true,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation))
            ),
            AutomationEventUi(
                key = 2,
                position = 2,
                title = "Broken rule (invalid actions)",
                isEnabled = false,
                readOnly = true,
                userAction = false,
                systemAction = true,
                actionsValid = false,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation))
            )
        ),
        logHtml = "12:00 Morning wakeup TT triggered<br>12:05 Snack reminder dismissed"
    )

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun PreviewAutomationScreen() {
    MaterialTheme {
        AutomationScreen(
            state = sampleState(),
            onToggleEnabled = { _, _ -> },
            onEditEvent = {},
            onDeleteEvent = {},
            onMove = { _, _ -> },
            onMoveFinished = {},
            onAddClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun PreviewAutomationScreenEmpty() {
    MaterialTheme {
        AutomationScreen(
            state = AutomationUiState(),
            onToggleEnabled = { _, _ -> },
            onEditEvent = {},
            onDeleteEvent = {},
            onMove = { _, _ -> },
            onMoveFinished = {},
            onAddClick = {}
        )
    }
}
