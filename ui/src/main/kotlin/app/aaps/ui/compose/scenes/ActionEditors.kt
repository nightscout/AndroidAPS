package app.aaps.ui.compose.scenes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TTPreset
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow

// --- Helpers ---

@Composable
internal fun presetDisplayName(preset: TTPreset): String = when {
    preset.nameRes != null       -> stringResource(preset.nameRes!!)
    !preset.name.isNullOrEmpty() -> preset.name!!
    else                         -> preset.reason.text
}

// --- Type-specific editors ---

@Composable
internal fun TempTargetEditor(
    action: SceneAction.TempTarget?,
    onUpdate: (SceneAction) -> Unit,
    ttPresets: List<TTPreset>,
    formatBgWithUnits: (Double) -> String
) {
    if (ttPresets.isEmpty()) {
        Text(
            text = stringResource(R.string.scene_editor_no_tt_presets),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    // Find the currently matching preset (exact match first, then by reason only)
    val currentPreset = action?.let { a ->
        ttPresets.firstOrNull { it.reason == a.reason && it.targetValue == a.targetMgdl }
            ?: ttPresets.firstOrNull { it.reason == a.reason }
    }
    val placeholder = stringResource(R.string.scene_editor_select_tt_preset)
    val currentPresetName = currentPreset?.let { presetDisplayName(it) } ?: placeholder

    // Preset dropdown
    val presetNames = ttPresets.map { it to presetDisplayName(it) }
    DropdownSelector(
        label = stringResource(R.string.scene_editor_tt_preset),
        selected = currentPresetName,
        options = presetNames.map { it.second },
        onSelect = { selectedName ->
            val preset = presetNames.first { it.second == selectedName }.first
            onUpdate(SceneAction.TempTarget(reason = preset.reason, targetMgdl = preset.targetValue))
        }
    )

    // Display target value (read-only, only when a preset is selected)
    if (action != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.scene_editor_target),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatBgWithUnits(action.targetMgdl),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun ProfileSwitchEditor(
    action: SceneAction.ProfileSwitch,
    onUpdate: (SceneAction) -> Unit,
    profileNames: List<String>
) {
    // Profile name dropdown with "keep current" option
    val keepCurrentLabel = stringResource(R.string.scene_editor_keep_current_profile)
    val options = listOf(keepCurrentLabel) + profileNames
    val selected = if (action.profileName.isEmpty()) keepCurrentLabel else action.profileName
    DropdownSelector(
        label = stringResource(R.string.scene_editor_profile),
        selected = selected,
        options = options,
        onSelect = { choice ->
            val name = if (choice == keepCurrentLabel) "" else choice
            onUpdate(action.copy(profileName = name))
        }
    )

    // Percentage slider
    NumberInputRow(
        labelResId = R.string.scene_editor_percentage,
        value = action.percentage.toDouble(),
        onValueChange = { onUpdate(action.copy(percentage = it.toInt())) },
        valueRange = 50.0..200.0,
        step = 5.0,
        unitLabelResId = app.aaps.core.keys.R.string.units_percent
    )
}

@Composable
internal fun SmbToggleEditor(
    action: SceneAction.SmbToggle,
    onUpdate: (SceneAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (action.enabled) stringResource(R.string.scene_editor_enabled) else stringResource(R.string.scene_editor_disabled),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = action.enabled,
            onCheckedChange = { onUpdate(action.copy(enabled = it)) }
        )
    }
}

@Composable
internal fun LoopModeEditor(
    action: SceneAction.LoopModeChange,
    onUpdate: (SceneAction) -> Unit
) {
    val modes = listOf(
        RM.Mode.CLOSED_LOOP,
        RM.Mode.CLOSED_LOOP_LGS,
        RM.Mode.OPEN_LOOP,
        RM.Mode.DISABLED_LOOP,
        RM.Mode.SUSPENDED_BY_USER,
        RM.Mode.DISCONNECTED_PUMP
    )
    // Pre-resolve display names in composable context
    val modeNames = modes.map { it to loopModeDisplayName(it) }
    DropdownSelector(
        label = stringResource(R.string.scene_editor_mode),
        selected = modeNames.firstOrNull { it.first == action.mode }?.second ?: action.mode.name,
        options = modeNames.map { it.second },
        onSelect = { selectedName ->
            val mode = modeNames.first { it.second == selectedName }.first
            onUpdate(action.copy(mode = mode))
        }
    )
}

@Composable
internal fun loopModeDisplayName(mode: RM.Mode): String = when (mode) {
    RM.Mode.CLOSED_LOOP      -> stringResource(R.string.closedloop)
    RM.Mode.CLOSED_LOOP_LGS  -> stringResource(R.string.lowglucosesuspend)
    RM.Mode.OPEN_LOOP         -> stringResource(R.string.openloop)
    RM.Mode.DISABLED_LOOP     -> stringResource(R.string.disableloop)
    RM.Mode.SUSPENDED_BY_USER -> stringResource(R.string.suspendloop)
    RM.Mode.DISCONNECTED_PUMP -> stringResource(R.string.pump_disconnected)
    else                      -> mode.name
}

@Composable
internal fun CarePortalEditor(
    action: SceneAction.CarePortalEvent,
    onUpdate: (SceneAction) -> Unit,
    translateEventType: (TE.Type) -> String
) {
    val types = listOf(
        TE.Type.NOTE,
        TE.Type.EXERCISE,
        TE.Type.ANNOUNCEMENT,
        TE.Type.QUESTION,
        TE.Type.SICKNESS,
        TE.Type.STRESS,
        TE.Type.PRE_PERIOD,
        TE.Type.ALCOHOL,
        TE.Type.CORTISONE,
        TE.Type.FEELING_LOW,
        TE.Type.FEELING_HIGH,
        TE.Type.FALLING_ASLEEP,
        TE.Type.WAKING_UP
    )
    val typeNames = types.map { it to translateEventType(it) }
    DropdownSelector(
        label = stringResource(R.string.scene_editor_event_type),
        selected = translateEventType(action.type),
        options = typeNames.map { it.second },
        onSelect = { selectedName ->
            val type = typeNames.first { it.second == selectedName }.first
            onUpdate(action.copy(type = type))
        }
    )

    // Optional note
    OutlinedTextField(
        value = action.note,
        onValueChange = { onUpdate(action.copy(note = it)) },
        label = { Text(stringResource(R.string.scene_editor_note)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

// --- Shared dropdown helper ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownSelector(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// --- Icon picker ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SceneIconPicker(
    selectedKey: String,
    onIconSelected: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = remember(selectedKey) { SceneIcons.fromKey(selectedKey) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(AapsSpacing.large)) {
            // Collapsed: selected icon + label + expand arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = selected.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.scene_icon),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(selected.label),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            // Expanded: categorized grid
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = AapsSpacing.medium)) {
                    SceneIcons.categories.forEach { category ->
                        Text(
                            text = stringResource(category.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = AapsSpacing.medium)
                        )
                        FlowRow(
                            modifier = Modifier.padding(top = AapsSpacing.small),
                            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraSmall)
                        ) {
                            category.icons.forEach { entry ->
                                SceneIconButton(entry, entry.key == selectedKey) {
                                    onIconSelected(it)
                                    expanded = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SceneIconButton(
    entry: SceneIconEntry,
    isSelected: Boolean,
    onIconSelected: (String) -> Unit
) {
    IconButton(onClick = { onIconSelected(entry.key) }) {
        Icon(
            imageVector = entry.icon,
            contentDescription = stringResource(entry.label),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(if (isSelected) 28.dp else 24.dp)
        )
    }
}
