package app.aaps.plugins.automation.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import app.aaps.core.ui.compose.AapsFab
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.plugins.automation.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AutomationScreen(
    state: AutomationUiState,
    onToggleEnabled: (position: Int, checked: Boolean) -> Unit,
    onClickEvent: (position: Int) -> Unit,
    onLongClickEvent: () -> Unit,
    onToggleSelect: (position: Int, checked: Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onMoveFinished: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                EventsList(
                    state = state,
                    onToggleEnabled = onToggleEnabled,
                    onClickEvent = onClickEvent,
                    onLongClickEvent = onLongClickEvent,
                    onToggleSelect = onToggleSelect,
                    onMove = onMove,
                    onMoveFinished = onMoveFinished,
                    modifier = Modifier.weight(1f)
                )
                LogPanel(
                    logHtml = state.logHtml,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.selectionMode == AutomationSelectionMode.None) {
                AapsFab(
                    onClick = onAddClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
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
private fun EventsList(
    state: AutomationUiState,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onClickEvent: (Int) -> Unit,
    onLongClickEvent: () -> Unit,
    onToggleSelect: (Int, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(
            items = state.events,
            key = { _, e -> "${e.position}_${e.title}" }
        ) { _, event ->
            ReorderableItem(
                state = reorderState,
                key = "${event.position}_${event.title}"
            ) { isDragging ->
                val elevation = if (isDragging) 8.dp else 1.dp
                AutomationEventCard(
                    event = event,
                    selectionMode = state.selectionMode,
                    elevation = elevation,
                    dragModifier = Modifier.draggableHandle(onDragStopped = { onMoveFinished() }),
                    onToggleEnabled = { checked -> onToggleEnabled(event.position, checked) },
                    onClick = { onClickEvent(event.position) },
                    onLongClick = onLongClickEvent,
                    onToggleSelect = { checked -> onToggleSelect(event.position, checked) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AutomationEventCard(
    event: AutomationEventUi,
    selectionMode: AutomationSelectionMode,
    elevation: Dp,
    dragModifier: Modifier,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: (Boolean) -> Unit
) {
    val containerColor = when {
        event.isSelected && selectionMode == AutomationSelectionMode.Remove ->
            MaterialTheme.colorScheme.secondaryContainer

        else                                                                ->
            MaterialTheme.colorScheme.surfaceContainer
    }
    val border = if (!event.actionsValid)
        BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    else null
    Surface(
        color = containerColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        shape = CardDefaults.elevatedShape,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = { IconRow(event = event) },
            leadingContent = when {
                event.systemAction -> {
                    {
                        Icon(
                            painter = painterResource(app.aaps.core.objects.R.drawable.ic_aaps),
                            contentDescription = stringResource(R.string.system_automation),
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                event.userAction   -> {
                    {
                        AssistChip(
                            onClick = onClick,
                            label = { Text(stringResource(R.string.user_action)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = AapsTheme.elementColors.userEntry.copy(alpha = 0.25f)
                            )
                        )
                    }
                }

                else               -> null
            },
            trailingContent = {
                when (selectionMode) {
                    AutomationSelectionMode.Remove -> Checkbox(
                        checked = event.isSelected,
                        onCheckedChange = onToggleSelect,
                        enabled = !event.readOnly
                    )

                    AutomationSelectionMode.Sort   -> IconButton(
                        onClick = {},
                        modifier = dragModifier
                    ) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.reorder)
                        )
                    }

                    AutomationSelectionMode.None   ->
                        if (event.readOnly) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Switch(
                                checked = event.isEnabled,
                                onCheckedChange = onToggleEnabled
                            )
                        }
                }
            }
        )
    }
}

@Composable
private fun IconRow(event: AutomationEventUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
    ) {
        event.triggerIcons.forEach { ai ->
            Icon(
                imageVector = ai.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 4.dp),
                tint = ai.tint ?: MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .padding(horizontal = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        event.actionIcons.forEach { ai ->
            Icon(
                imageVector = ai.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 4.dp),
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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

private fun sampleState(selectionMode: AutomationSelectionMode = AutomationSelectionMode.None) =
    AutomationUiState(
        events = listOf(
            AutomationEventUi(
                position = 0,
                title = "Morning wakeup TT",
                isEnabled = true,
                readOnly = false,
                userAction = false,
                systemAction = false,
                actionsValid = true,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation)),
                isSelected = false
            ),
            AutomationEventUi(
                position = 1,
                title = "User: Snack reminder",
                isEnabled = true,
                readOnly = false,
                userAction = true,
                systemAction = false,
                actionsValid = true,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation)),
                isSelected = selectionMode == AutomationSelectionMode.Remove
            ),
            AutomationEventUi(
                position = 2,
                title = "Broken rule (invalid actions)",
                isEnabled = false,
                readOnly = false,
                userAction = false,
                systemAction = true,
                actionsValid = false,
                triggerIcons = listOf(AutomationIcon(IcAutomation)),
                actionIcons = listOf(AutomationIcon(IcAutomation)),
                isSelected = false
            )
        ),
        logHtml = "12:00 Morning wakeup TT triggered<br>12:05 Snack reminder dismissed",
        selectionMode = selectionMode
    )

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun PreviewAutomationScreenIdle() {
    MaterialTheme {
        AutomationScreen(
            state = sampleState(),
            onToggleEnabled = { _, _ -> },
            onClickEvent = {},
            onLongClickEvent = {},
            onToggleSelect = { _, _ -> },
            onMove = { _, _ -> },
            onMoveFinished = {},
            onAddClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun PreviewAutomationScreenRemove() {
    MaterialTheme {
        AutomationScreen(
            state = sampleState(AutomationSelectionMode.Remove),
            onToggleEnabled = { _, _ -> },
            onClickEvent = {},
            onLongClickEvent = {},
            onToggleSelect = { _, _ -> },
            onMove = { _, _ -> },
            onMoveFinished = {},
            onAddClick = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 640)
@Composable
private fun PreviewAutomationScreenSort() {
    MaterialTheme {
        AutomationScreen(
            state = sampleState(AutomationSelectionMode.Sort),
            onToggleEnabled = { _, _ -> },
            onClickEvent = {},
            onLongClickEvent = {},
            onToggleSelect = { _, _ -> },
            onMove = { _, _ -> },
            onMoveFinished = {},
            onAddClick = {}
        )
    }
}
