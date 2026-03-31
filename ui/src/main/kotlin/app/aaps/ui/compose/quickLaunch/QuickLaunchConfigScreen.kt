package app.aaps.ui.compose.quickLaunch

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.aaps.core.ui.compose.navigation.ElementCategory
import app.aaps.ui.R
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QuickLauchConfigScreen(
    viewModel: QuickLaunchConfigViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingProfileIndex by remember { mutableIntStateOf(-1) }
    val editingProfileAction = (state.selectedItems.getOrNull(editingProfileIndex)?.action as? QuickLaunchAction.ProfileAction)

    LaunchedEffect(Unit) { viewModel.loadState() }

    editingProfileAction?.let { action ->
        ProfilePresetDialog(
            profileName = action.profileName,
            initialPercentage = action.percentage,
            initialDurationMinutes = action.durationMinutes,
            onDismiss = { editingProfileIndex = -1 },
            onConfirm = { pct, dur ->
                viewModel.updateProfileActionAt(editingProfileIndex, pct, dur)
                editingProfileIndex = -1
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveItem(from.index - 1, to.index - 1) // -1 to account for the header item
    }
    val coroutineScope = rememberCoroutineScope()
    var previousSelectedCount by remember { mutableIntStateOf(state.selectedItems.size) }
    LaunchedEffect(state.selectedItems.size) {
        if (state.selectedItems.size > previousSelectedCount) {
            // Scroll to the last selected item (index 0 = header, so last item = size)
            coroutineScope.launch { lazyListState.animateScrollToItem(state.selectedItems.size) }
        }
        previousSelectedCount = state.selectedItems.size
    }

    Column(modifier = modifier.fillMaxSize()) {
        AapsTopAppBar(
            title = { Text(stringResource(app.aaps.core.ui.R.string.quick_launch_configure)) },
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
                key = { index, item ->
                    val base = "selected_${item.action.typeId}_${item.action.dynamicId ?: ""}"
                    val dupIndex = state.selectedItems.take(index).count {
                        it.action.typeId == item.action.typeId && it.action.dynamicId == item.action.dynamicId
                    }
                    if (dupIndex > 0) "${base}_$dupIndex" else base
                }
            ) { index, item ->
                val base = "selected_${item.action.typeId}_${item.action.dynamicId ?: ""}"
                val dupIndex = state.selectedItems.take(index).count {
                    it.action.typeId == item.action.typeId && it.action.dynamicId == item.action.dynamicId
                }
                val stableKey = if (dupIndex > 0) "${base}_$dupIndex" else base
                ReorderableItem(
                    reorderableState,
                    key = stableKey,
                    modifier = Modifier.animateItem()
                ) { isDragging ->
                    val elevation = if (isDragging) 8.dp else 0.dp
                    Surface(shadowElevation = elevation, tonalElevation = elevation) {
                        SelectedActionItem(
                            item = item,
                            onRemove = { viewModel.removeActionAt(index) },
                            onEdit = if (item.action is QuickLaunchAction.ProfileAction) {
                                { editingProfileIndex = index }
                            } else null,
                            dragModifier = Modifier.draggableHandle()
                        )
                    }
                }
            }

            // ── Available: Treatment ──
            val treatmentItems = state.availableStaticItems.filter {
                it.action.elementType?.category in setOf(ElementCategory.TREATMENT, ElementCategory.CGM)
            }
            item(key = "divider_treatment") {
                HorizontalDivider()
            }
            item(key = "header_treatment") {
                SectionHeader(stringResource(R.string.quick_launch_category_treatment))
            }
            if (treatmentItems.isEmpty()) {
                item(key = "empty_treatment") { EmptyHint(stringResource(R.string.quick_launch_all_selected)) }
            } else {
                items(treatmentItems, key = { "avail_${it.action.typeId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Available: Care Portal ──
            val careItems = state.availableStaticItems.filter {
                it.action.elementType?.category in setOf(ElementCategory.CAREPORTAL, ElementCategory.DEVICE)
            }
            item(key = "divider_care") {
                HorizontalDivider()
            }
            item(key = "header_care") {
                SectionHeader(stringResource(R.string.quick_launch_category_care))
            }
            if (careItems.isEmpty()) {
                item(key = "empty_care") { EmptyHint(stringResource(R.string.quick_launch_all_selected)) }
            } else {
                items(careItems, key = { "avail_${it.action.typeId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Dynamic: Quick Wizard ──
            item(key = "divider_qw") {
                HorizontalDivider()
            }
            item(key = "header_qw") {
                SectionHeader(stringResource(R.string.quick_launch_category_quick_wizard))
            }
            if (state.availableQuickWizardItems.isEmpty()) {
                item(key = "empty_qw") { EmptyHint(stringResource(R.string.quick_launch_no_quick_wizard)) }
            } else {
                items(state.availableQuickWizardItems, key = { "avail_qw_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Dynamic: Automation ──
            item(key = "divider_auto") {
                HorizontalDivider()
            }
            item(key = "header_auto") {
                SectionHeader(stringResource(R.string.quick_launch_category_automation))
            }
            if (state.availableAutomationItems.isEmpty()) {
                item(key = "empty_auto") { EmptyHint(stringResource(R.string.quick_launch_no_automation)) }
            } else {
                items(state.availableAutomationItems, key = { "avail_auto_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Dynamic: TT Presets ──
            item(key = "divider_tt") {
                HorizontalDivider()
            }
            item(key = "header_tt") {
                SectionHeader(stringResource(R.string.quick_launch_category_temp_target))
            }
            if (state.availableTtPresetItems.isEmpty()) {
                item(key = "empty_tt") { EmptyHint(stringResource(R.string.quick_launch_no_tt_presets)) }
            } else {
                items(state.availableTtPresetItems, key = { "avail_tt_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Dynamic: Profiles ──
            item(key = "divider_profiles") {
                HorizontalDivider()
            }
            item(key = "header_profiles") {
                SectionHeader(stringResource(R.string.quick_launch_category_profile))
            }
            if (state.availableProfileItems.isEmpty()) {
                item(key = "empty_profiles") { EmptyHint(stringResource(R.string.quick_launch_no_profiles)) }
            } else {
                items(state.availableProfileItems, key = { "avail_profile_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
                }
            }

            // ── Dynamic: Plugins (grouped by type) ──
            state.availablePluginGroups.forEach { group ->
                item(key = "divider_plugin_${group.pluginType}") {
                    HorizontalDivider()
                }
                item(key = "header_plugin_${group.pluginType}") {
                    SectionHeader(stringResource(group.labelResId))
                }
                items(group.items, key = { "avail_plugin_${it.action.dynamicId}" }) { item ->
                    AvailableActionItem(item = item, onAdd = { viewModel.addAction(item.action) }, modifier = Modifier.animateItem())
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun resolveActionColor(item: ResolvedQuickLaunchItem): Color {
    val action = item.action
    if (action is QuickLaunchAction.QuickWizardAction) {
        return when (item.icon) {
            IcBolus -> ElementType.INSULIN.color()
            IcCarbs -> ElementType.CARBS.color()
            else    -> ElementType.QUICK_WIZARD.color()
        }
    }
    return action.elementType?.color() ?: MaterialTheme.colorScheme.primary
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SelectedActionItem(
    item: ResolvedQuickLaunchItem,
    onRemove: () -> Unit,
    onEdit: (() -> Unit)?,
    dragModifier: Modifier
) {
    val color = resolveActionColor(item)
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
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = resolveActionColor(item)
    ListItem(
        modifier = modifier,
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
