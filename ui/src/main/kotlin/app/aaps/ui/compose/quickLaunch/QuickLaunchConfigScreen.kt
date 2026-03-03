package app.aaps.ui.compose.quickLaunch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.ui.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QuickLauchConfigScreen(
    viewModel: QuickLaunchConfigViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingProfileAction by remember { mutableStateOf<QuickLaunchAction.ProfileAction?>(null) }

    LaunchedEffect(Unit) { viewModel.loadState() }

    editingProfileAction?.let { action ->
        ProfilePresetDialog(
            profileName = action.profileName,
            initialPercentage = action.percentage,
            initialDurationMinutes = action.durationMinutes,
            onDismiss = { editingProfileAction = null },
            onConfirm = { pct, dur ->
                viewModel.updateProfileAction(action, pct, dur)
                editingProfileAction = null
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveItem(from.index - 1, to.index - 1) // -1 to account for the header item
    }

    Column(modifier = modifier.fillMaxSize()) {
        AapsTopAppBar(
            title = { Text(stringResource(R.string.quick_launch_configure)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            }
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Selected actions ──
            item(key = "header_selected") {
                SectionHeader(stringResource(R.string.quick_launch_selected_actions))
            }

            if (state.selectedItems.isEmpty()) {
                item(key = "empty_selected") {
                    EmptyHint(stringResource(R.string.quick_launch_no_dynamic_items))
                }
            }

            itemsIndexed(
                items = state.selectedItems,
                key = { _, item -> "selected_${item.action.typeId}_${item.action.dynamicId ?: ""}" }
            ) { _, item ->
                ReorderableItem(
                    reorderableState,
                    key = "selected_${item.action.typeId}_${item.action.dynamicId ?: ""}"
                ) { isDragging ->
                    val elevation = if (isDragging) 8.dp else 0.dp
                    Surface(shadowElevation = elevation, tonalElevation = elevation) {
                        SelectedActionItem(
                            item = item,
                            onRemove = { viewModel.removeAction(item.action) },
                            onEdit = (item.action as? QuickLaunchAction.ProfileAction)?.let { pa ->
                                { editingProfileAction = pa }
                            },
                            dragModifier = Modifier.draggableHandle()
                        )
                    }
                }
            }

            // ── Available: Treatment ──
            val treatmentItems = state.availableStaticItems.filter { it.action.category == QuickLaunchAction.Category.TREATMENT }
            if (treatmentItems.isNotEmpty()) {
                item(key = "divider_treatment") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_treatment") {
                    SectionHeader(stringResource(R.string.quick_launch_category_treatment))
                }
                items(treatmentItems, key = { "avail_${it.action.typeId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Available: Care Portal ──
            val careItems = state.availableStaticItems.filter { it.action.category == QuickLaunchAction.Category.CARE }
            if (careItems.isNotEmpty()) {
                item(key = "divider_care") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_care") {
                    SectionHeader(stringResource(R.string.quick_launch_category_care))
                }
                items(careItems, key = { "avail_${it.action.typeId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Dynamic: Quick Wizard ──
            if (state.availableQuickWizardItems.isNotEmpty()) {
                item(key = "divider_qw") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_qw") {
                    SectionHeader(stringResource(R.string.quick_launch_category_quick_wizard))
                }
                items(state.availableQuickWizardItems, key = { "avail_qw_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Dynamic: Automation ──
            if (state.availableAutomationItems.isNotEmpty()) {
                item(key = "divider_auto") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_auto") {
                    SectionHeader(stringResource(R.string.quick_launch_category_automation))
                }
                items(state.availableAutomationItems, key = { "avail_auto_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Dynamic: TT Presets ──
            if (state.availableTtPresetItems.isNotEmpty()) {
                item(key = "divider_tt") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_tt") {
                    SectionHeader(stringResource(R.string.quick_launch_category_temp_target))
                }
                items(state.availableTtPresetItems, key = { "avail_tt_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Dynamic: Profiles ──
            if (state.availableProfileItems.isNotEmpty()) {
                item(key = "divider_profiles") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_profiles") {
                    SectionHeader(stringResource(R.string.quick_launch_category_profile))
                }
                items(state.availableProfileItems, key = { "avail_profile_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // ── Dynamic: Plugins (grouped by type) ──
            state.availablePluginGroups.forEach { group ->
                item(key = "divider_plugin_${group.pluginType}") {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
                item(key = "header_plugin_${group.pluginType}") {
                    SectionHeader(stringResource(group.labelResId))
                }
                items(group.items, key = { "avail_plugin_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) })
                }
            }

            // Bottom spacing
            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SelectedActionItem(
    item: ResolvedQuickLaunchItem,
    onRemove: () -> Unit,
    onEdit: (() -> Unit)?,
    dragModifier: Modifier
) {
    val color = item.action.tintColor()
    ListItem(
        headlineContent = {
            Text(text = item.label, color = color)
        },
        supportingContent = item.description?.let { desc ->
            { Text(text = desc, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {},
                    modifier = dragModifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.reorder),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TonalIcon(painter = rememberVectorPainter(item.icon), color = color)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    OutlinedIconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.quick_launch_edit_profile_preset),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                OutlinedIconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(app.aaps.core.ui.R.string.remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
    )
}

@Composable
private fun AvailableActionItem(
    item: ResolvedQuickLaunchItem,
    onAdd: () -> Unit
) {
    val color = item.action.tintColor()
    ListItem(
        headlineContent = {
            Text(text = item.label, color = color)
        },
        supportingContent = item.description?.let { desc ->
            { Text(text = desc, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        leadingContent = {
            TonalIcon(painter = rememberVectorPainter(item.icon), color = color)
        },
        trailingContent = {
            OutlinedIconButton(
                onClick = onAdd,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.add),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}

@Composable
private fun ProfilePresetDialog(
    profileName: String,
    initialPercentage: Int,
    initialDurationMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (percentage: Int, durationMinutes: Int) -> Unit
) {
    var percentage by remember { mutableIntStateOf(initialPercentage) }
    var durationMinutes by remember { mutableIntStateOf(initialDurationMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_launch_edit_profile_preset)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                NumberInputRow(
                    labelResId = app.aaps.core.ui.R.string.percent,
                    value = percentage.toDouble(),
                    onValueChange = { percentage = it.toInt() },
                    valueRange = 50.0..200.0,
                    step = 5.0,
                    unitLabelResId = app.aaps.core.keys.R.string.units_percent
                )

                NumberInputRow(
                    labelResId = app.aaps.core.ui.R.string.duration,
                    value = durationMinutes.toDouble(),
                    onValueChange = { durationMinutes = it.toInt() },
                    valueRange = 0.0..600.0,
                    step = 10.0,
                    unitLabelResId = app.aaps.core.keys.R.string.units_min
                )
                if (durationMinutes == 0) {
                    Text(
                        text = stringResource(R.string.quick_launch_profile_permanent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(percentage, durationMinutes) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
