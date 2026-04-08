package app.aaps.plugins.aps.autotune.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.OkDialog
import app.aaps.core.ui.compose.pickers.WeekDaySelector
import app.aaps.core.ui.elements.WeekDay
import app.aaps.plugins.aps.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutotuneScreen(
    state: AutotuneUiState,
    onProfileSelected: (Int) -> Unit,
    onDaysChanged: (Double) -> Unit,
    onDayToggle: (WeekDay.DayOfWeek, Boolean) -> Unit,
    onToggleWeekDays: () -> Unit,
    onRunAutotune: () -> Unit,
    onLoadLastRun: () -> Unit,
    onCopyLocal: () -> Unit,
    onUpdateProfile: () -> Unit,
    onRevertProfile: () -> Unit,
    onProfileSwitch: () -> Unit,
    onCheckInputProfile: () -> Unit,
    onCompareProfiles: () -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    onCopyLocalConfirm: (String) -> Unit
) {
    LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Settings card ---
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Profile dropdown
                ProfileDropdown(
                    profileNames = state.profileList,
                    selectedIndex = state.selectedProfileIndex,
                    onSelect = onProfileSelected,
                    enabled = !state.isRunning
                )

                // Days input row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NumberInputRow(
                        labelResId = R.string.autotune_default_tune_days_title,
                        value = state.daysBack,
                        onValueChange = onDaysChanged,
                        valueRange = 1.0..30.0,
                        step = 1.0,
                        enabled = !state.isRunning,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.showCalcDays) {
                        Text(
                            text = "(${state.calcDays})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggleWeekDays) {
                        Icon(
                            painter = painterResource(app.aaps.core.ui.R.drawable.ic_visibility),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Week day selector
                AnimatedVisibility(visible = state.showWeekDays) {
                    WeekDaySelector(
                        selectedDays = state.weekdays,
                        onDayToggle = onDayToggle,
                        enabled = !state.isRunning
                    )
                }
            }
        }

        // --- Info card ---
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(
                    label = stringResource(R.string.autotune_last_run),
                    value = state.lastRunText,
                    valueClickable = true,
                    onValueClick = onLoadLastRun
                )
                if (state.warningText.isNotEmpty()) {
                    InfoRow(
                        label = stringResource(R.string.autotune_warning),
                        value = state.warningText
                    )
                }
            }
        }

        // --- Results card ---
        if (state.showResultsCard) {
            AapsCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.resultText.isNotBlank()) {
                        // Result header
                        Text(
                            text = state.resultText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Param results table
                        if (state.paramRows.isNotEmpty()) {
                            ResultsTableHeader(isBasal = false)
                            state.paramRows.forEach { row -> ResultsTableRow(row) }
                        }

                        // Basal results table
                        if (state.basalRows.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = stringResource(app.aaps.core.ui.R.string.basal),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            ResultsTableHeader(isBasal = true)
                            state.basalRows.forEach { row -> ResultsTableRow(row) }
                        }
                    }

                    // Buttons grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 2
                    ) {
                        val buttonModifier = Modifier.weight(1f)
                        if (state.showProfileSwitch) {
                            AutotuneButton(stringResource(app.aaps.core.ui.R.string.activate_profile), Icons.Filled.SwapHoriz, buttonModifier, onClick = onProfileSwitch)
                        }
                        if (state.showCompare) {
                            AutotuneButton(stringResource(R.string.autotune_compare_profile), Icons.Filled.CompareArrows, buttonModifier, onClick = onCompareProfiles)
                        }
                        if (state.showCopyLocal) {
                            AutotuneButton(stringResource(R.string.autotune_copy_localprofile_button), Icons.Filled.ContentCopy, buttonModifier, onClick = onCopyLocal)
                        }
                        if (state.showUpdateProfile) {
                            AutotuneButton(stringResource(R.string.autotune_update_input_profile_button), Icons.Filled.Save, buttonModifier, onClick = onUpdateProfile)
                        }
                        if (state.showRevertProfile) {
                            AutotuneButton(stringResource(R.string.autotune_revert_input_profile_button), Icons.Filled.Restore, buttonModifier, onClick = onRevertProfile)
                        }
                        if (state.showRun) {
                            AutotuneButton(stringResource(R.string.autotune_run), Icons.Filled.PlayArrow, buttonModifier, onClick = onRunAutotune)
                        }
                        if (state.showCheckInput) {
                            AutotuneButton(stringResource(R.string.autotune_check_input_profile_button), Icons.Filled.PersonSearch, buttonModifier, onClick = onCheckInputProfile)
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    when (val dialog = state.dialogState) {
        is DialogState.CopyLocal        -> {
            OkCancelDialog(
                title = stringResource(R.string.autotune_copy_localprofile_button),
                message = stringResource(R.string.autotune_copy_local_profile_message) + "\n" + dialog.localName,
                onConfirm = { onCopyLocalConfirm(dialog.localName) },
                onDismiss = onDialogDismiss
            )
        }

        is DialogState.UpdateProfile    -> {
            OkCancelDialog(
                title = stringResource(R.string.autotune_update_input_profile_button),
                message = stringResource(R.string.autotune_update_local_profile_message, dialog.profileName),
                onConfirm = onDialogConfirm,
                onDismiss = onDialogDismiss
            )
        }

        is DialogState.RevertProfile    -> {
            OkCancelDialog(
                title = stringResource(R.string.autotune_revert_input_profile_button),
                message = stringResource(R.string.autotune_revert_local_profile_message, dialog.profileName),
                onConfirm = onDialogConfirm,
                onDismiss = onDialogDismiss
            )
        }

        is DialogState.ProfileSwitch    -> {
            OkCancelDialog(
                message = stringResource(app.aaps.core.ui.R.string.activate_profile) + ": " + dialog.profileName + "?",
                onConfirm = onDialogConfirm,
                onDismiss = onDialogDismiss
            )
        }

        is DialogState.PumpDisconnected -> {
            OkDialog(
                title = dialog.title,
                message = stringResource(R.string.pump_disconnected),
                onDismiss = onDialogDismiss
            )
        }

        DialogState.None                -> { /* no dialog */
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(
    profileNames: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = profileNames.getOrElse(selectedIndex) { "" }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.autotune_profile)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profileNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueClickable: Boolean = false,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (valueClickable) TextDecoration.Underline else TextDecoration.None,
            modifier = Modifier
                .weight(2f)
                .then(if (valueClickable && onValueClick != null) Modifier.clickable { onValueClick() } else Modifier)
        )
    }
}

@Composable
private fun ResultsTableHeader(isBasal: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val headerStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        Text(text = if (isBasal) stringResource(app.aaps.core.ui.R.string.time) else stringResource(R.string.autotune_param), style = headerStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = stringResource(app.aaps.core.ui.R.string.profile), style = headerStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = stringResource(R.string.autotune_tunedprofile_name), style = headerStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = stringResource(R.string.autotune_percent), style = headerStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
        if (isBasal) {
            Text(text = stringResource(R.string.autotune_missing), style = headerStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResultsTableRow(row: ResultRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val textStyle = MaterialTheme.typography.bodySmall
        Text(text = row.label, style = textStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = row.profileValue, style = textStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = row.tunedValue, style = textStyle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(text = row.percent, style = textStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
        if (row.missing.isNotEmpty()) {
            Text(text = row.missing, style = textStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AutotuneButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

