package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.graph.BasalProfileGraphCompose
import app.aaps.core.graph.IcProfileGraphCompose
import app.aaps.core.graph.IsfProfileGraphCompose
import app.aaps.core.graph.TargetBgProfileGraphCompose
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.SliderWithButtons
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.dialogs.ValueInputDialog
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileUiState
import app.aaps.ui.compose.profileManagement.viewmodels.SingleProfileState
import java.text.DecimalFormat

@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Unsaved changes dialog
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onSave = {
                viewModel.saveProfile()
                showUnsavedChangesDialog = false
                onBackClick()
            },
            onDiscard = {
                viewModel.resetProfile()
                showUnsavedChangesDialog = false
                onBackClick()
            },
            onCancel = { showUnsavedChangesDialog = false }
        )
    }

    // Handle back navigation with unsaved changes check
    val handleBack: () -> Unit = {
        if (state.isEdited) {
            showUnsavedChangesDialog = true
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                            contentDescription = null,
                            tint = ElementType.PROFILE_MANAGEMENT.color(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(stringResource(R.string.localprofile))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.isEdited) {
                        // Reset button
                        IconButton(onClick = { viewModel.resetProfile() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.reset)
                            )
                        }
                        // Save button
                        IconButton(
                            onClick = { viewModel.saveProfile() },
                            enabled = state.isValid
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = stringResource(R.string.save),
                                tint = if (state.isValid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        if (state.isLocked) {
            // Locked state - show unlock button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalButton(onClick = { /* Unlock handled by activity */ }) {
                    Text(stringResource(R.string.unlock_settings))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .clearFocusOnTap(focusManager)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Profile name header with edit capability
                ProfileNameHeader(
                    profileName = state.currentProfile?.name ?: "",
                    onProfileNameChange = { viewModel.updateProfileName(it) },
                    units = state.units
                )

                Spacer(Modifier.height(12.dp))

                // Tab layout with error indication
                state.tabErrors.containsKey(ProfileErrorType.DIA)
                val icHasError = state.tabErrors.containsKey(ProfileErrorType.IC)
                val isfHasError = state.tabErrors.containsKey(ProfileErrorType.ISF)
                val basalHasError = state.tabErrors.containsKey(ProfileErrorType.BASAL)
                val targetHasError = state.tabErrors.containsKey(ProfileErrorType.TARGET)

                PrimaryTabRow(selectedTabIndex = state.selectedTab) {
                    Tab(
                        selected = state.selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        modifier = Modifier.background(
                            if (icHasError) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                        ),
                        text = { Text(stringResource(R.string.ic_short)) }
                    )
                    Tab(
                        selected = state.selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        modifier = Modifier.background(
                            if (isfHasError) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                        ),
                        text = { Text(stringResource(R.string.isf_short)) }
                    )
                    Tab(
                        selected = state.selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        modifier = Modifier.background(
                            if (basalHasError) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                        ),
                        text = { Text(stringResource(R.string.basal_short)) }
                    )
                    Tab(
                        selected = state.selectedTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        modifier = Modifier.background(
                            if (targetHasError) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                        ),
                        text = { Text(stringResource(R.string.target_short)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Tab content with error display
                state.currentProfile?.let { profile ->
                    // Get error message for current tab
                    val currentTabError = when (state.selectedTab) {
                        0 -> state.tabErrors[ProfileErrorType.DIA]
                        1 -> state.tabErrors[ProfileErrorType.IC]
                        2 -> state.tabErrors[ProfileErrorType.ISF]
                        3 -> state.tabErrors[ProfileErrorType.BASAL]
                        4 -> state.tabErrors[ProfileErrorType.TARGET]
                        else -> null
                    }

                    // Show error message if present
                    currentTabError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    when (state.selectedTab) {

                        0 -> IcContent(
                            viewModel = viewModel,
                            profile = profile,
                            state = state,
                            supportsDynamic = state.supportsDynamicIc
                        )

                        1 -> IsfContent(
                            viewModel = viewModel,
                            profile = profile,
                            state = state,
                            supportsDynamic = state.supportsDynamicIsf
                        )

                        2 -> BasalContent(
                            viewModel = viewModel,
                            profile = profile,
                            state = state
                        )

                        3 -> TargetContent(
                            viewModel = viewModel,
                            profile = profile,
                            state = state
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProfileNameHeader(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    units: String
) {
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(profileName) { mutableStateOf(profileName) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when entering edit mode
    LaunchedEffect(isEditingName) {
        if (isEditingName) {
            focusRequester.requestFocus()
        }
    }

    if (isEditingName) {
        // Full width editing mode
        OutlinedTextField(
            value = editedName,
            onValueChange = { editedName = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            trailingIcon = {
                Row {
                    // Cancel button
                    IconButton(onClick = {
                        editedName = profileName // Reset to original
                        isEditingName = false
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Confirm button
                    IconButton(onClick = {
                        onProfileNameChange(editedName)
                        isEditingName = false
                    }) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.ok),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Profile name with edit icon
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isEditingName = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = profileName.ifEmpty { stringResource(R.string.profile_name) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_profile_name),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Units display
            Text(
                text = "${stringResource(R.string.units_colon)} $units",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.unsaved_changes)) },
        text = { Text(stringResource(R.string.unsaved_changes_message)) },
        confirmButton = {
            FilledTonalButton(onClick = onSave) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.discard))
                }
            }
        }
    )
}

@Composable
private fun DiaContent(
    dia: Double,
    onDiaChange: (Double) -> Unit,
    minDia: Double,
    maxDia: Double
) {
    var showDialog by remember { mutableStateOf(false) }
    val valueFormat = remember { DecimalFormat("0.0") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.dia_long_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dia),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${valueFormat.format(dia)} h",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showDialog = true }
                )
            }

            SliderWithButtons(
                value = dia,
                onValueChange = onDiaChange,
                valueRange = minDia..maxDia,
                step = 0.1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDialog) {
        ValueInputDialog(
            currentValue = dia,
            valueRange = minDia..maxDia,
            step = 0.1,
            label = stringResource(R.string.dia),
            unitLabel = "h",
            valueFormat = valueFormat,
            onValueConfirm = onDiaChange,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun IcContent(
    viewModel: ProfileEditorViewModel,
    profile: SingleProfileState,
    state: ProfileUiState,
    supportsDynamic: Boolean
) {
    Column {
        if (supportsDynamic) {
            Text(
                text = stringResource(R.string.ic_dynamic_label_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            TimeValueList(
                title = stringResource(R.string.ic_long_label),
                entries = profile.ic,
                onEntryChange = { index, entry -> viewModel.updateIcEntry(index, entry) },
                onAddEntry = { index -> viewModel.addIcEntry(index) },
                onRemoveEntry = { index -> viewModel.removeIcEntry(index) },
                minValue = state.icMin,
                maxValue = state.icMax,
                step = 0.1,
                valueFormat = DecimalFormat("0.0"),
                unitLabel = stringResource(R.string.profile_carbs_per_unit),
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Graph
        viewModel.getEditedProfile()?.let { pureProfile ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                IcProfileGraphCompose(
                    profile1 = ProfileSealed.Pure(pureProfile, null),
                    profile1Name = profile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun IsfContent(
    viewModel: ProfileEditorViewModel,
    profile: SingleProfileState,
    state: ProfileUiState,
    supportsDynamic: Boolean
) {
    Column {
        if (supportsDynamic) {
            Text(
                text = stringResource(R.string.isf_dynamic_label_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            TimeValueList(
                title = stringResource(R.string.isf_long_label),
                entries = profile.isf,
                onEntryChange = { index, entry -> viewModel.updateIsfEntry(index, entry) },
                onAddEntry = { index -> viewModel.addIsfEntry(index) },
                onRemoveEntry = { index -> viewModel.removeIsfEntry(index) },
                minValue = state.isfMin,
                maxValue = state.isfMax,
                step = if (profile.mgdl) 1.0 else 0.1,
                valueFormat = if (profile.mgdl) DecimalFormat("0") else DecimalFormat("0.0"),
                unitLabel = "${state.units}/${stringResource(R.string.insulin_unit_shortname)}",
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Graph
        viewModel.getEditedProfile()?.let { pureProfile ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                IsfProfileGraphCompose(
                    profile1 = ProfileSealed.Pure(pureProfile, null),
                    profile1Name = profile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BasalContent(
    viewModel: ProfileEditorViewModel,
    profile: SingleProfileState,
    state: ProfileUiState
) {
    Column {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.basal_long_label)} [${stringResource(R.string.profile_ins_units_per_hour)}]",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "∑ ${stringResource(R.string.format_insulin_units, viewModel.getBasalSum())}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(8.dp))

                TimeValueList(
                    title = "",
                    entries = profile.basal,
                    onEntryChange = { index, entry -> viewModel.updateBasalEntry(index, entry) },
                    onAddEntry = { index -> viewModel.addBasalEntry(index) },
                    onRemoveEntry = { index -> viewModel.removeBasalEntry(index) },
                    minValue = state.basalMin,
                    maxValue = state.basalMax,
                    step = 0.01,
                    valueFormat = DecimalFormat("0.00"),
                    unitLabel = ""
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Graph
        viewModel.getEditedProfile()?.let { pureProfile ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                BasalProfileGraphCompose(
                    profile1 = ProfileSealed.Pure(pureProfile, null),
                    profile1Name = profile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TargetContent(
    viewModel: ProfileEditorViewModel,
    profile: SingleProfileState,
    state: ProfileUiState
) {
    Column {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            TargetValueList(
                title = stringResource(R.string.target_long_label),
                lowEntries = profile.targetLow,
                highEntries = profile.targetHigh,
                onEntryChange = { index, low, high ->
                    viewModel.updateTargetEntry(index, low, high)
                },
                onAddEntry = { index -> viewModel.addTargetEntry(index) },
                onRemoveEntry = { index -> viewModel.removeTargetEntry(index) },
                minValue = state.targetMin,
                maxValue = state.targetMax,
                step = if (profile.mgdl) 1.0 else 0.1,
                valueFormat = if (profile.mgdl) DecimalFormat("0") else DecimalFormat("0.0"),
                unitLabel = state.units,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Graph
        viewModel.getEditedProfile()?.let { pureProfile ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                TargetBgProfileGraphCompose(
                    profile1 = ProfileSealed.Pure(pureProfile, null),
                    profile1Name = profile.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}
