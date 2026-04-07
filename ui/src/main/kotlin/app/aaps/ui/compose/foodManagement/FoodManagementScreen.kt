package app.aaps.ui.compose.foodManagement

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.FD
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsSearchField
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcPluginFood

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodManagementScreen(
    viewModel: FoodManagementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWizard: (carbs: Int, name: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
    val filteredSubCategories by viewModel.filteredSubCategories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(CoreUiR.string.undo)
    val deletedLabel = stringResource(CoreUiR.string.food_deleted)

    // Show undo snackbar when a food is deleted
    LaunchedEffect(state.undoFood) {
        state.undoFood?.let {
            val result = snackbarHostState.showSnackbar(
                message = deletedLabel,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearUndo()
            }
        }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = IcPluginFood,
                            contentDescription = null,
                            modifier = Modifier.size(AapsSpacing.chipIconSize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(AapsSpacing.medium))
                        AapsSearchField(
                            query = state.searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(CoreUiR.string.add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            if (state.allCategories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AapsSpacing.extraLarge),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    state.allCategories.forEach { cat ->
                        FilterChip(
                            selected = state.filterCategory == cat,
                            onClick = {
                                viewModel.setFilterCategory(if (state.filterCategory == cat) null else cat)
                            },
                            label = { Text(cat) }
                        )
                    }
                }
                if (filteredSubCategories.isNotEmpty() && state.filterCategory != null) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AapsSpacing.extraLarge),
                        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                    ) {
                        filteredSubCategories.forEach { sub ->
                            FilterChip(
                                selected = state.filterSubCategory == sub,
                                onClick = {
                                    viewModel.setFilterSubCategory(if (state.filterSubCategory == sub) null else sub)
                                },
                                label = { Text(sub) }
                            )
                        }
                    }
                }
            }

            // Food list
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredFoods.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(CoreUiR.string.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    items(filteredFoods, key = { it.id }) { food ->
                        FoodItem(
                            food = food,
                            onEdit = { viewModel.openEditor(food) },
                            onCalculate = { onNavigateToWizard(food.carbs, food.name) }
                        )
                    }
                }
            }
        }

        // Editor bottom sheet
        if (state.showEditor) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val focusManager = LocalFocusManager.current
            ModalBottomSheet(
                onDismissRequest = { focusManager.clearFocus(); viewModel.closeEditor() },
                sheetState = sheetState,
            ) {
                FoodEditorContent(
                    state = state,
                    onNameChange = viewModel::updateEditorName,
                    onCategoryChange = viewModel::updateEditorCategory,
                    onSubCategoryChange = viewModel::updateEditorSubCategory,
                    onPortionChange = viewModel::updateEditorPortion,
                    onUnitChange = viewModel::updateEditorUnit,
                    onCarbsChange = viewModel::updateEditorCarbs,
                    onFatChange = viewModel::updateEditorFat,
                    onProteinChange = viewModel::updateEditorProtein,
                    onEnergyChange = viewModel::updateEditorEnergy,
                    onSave = { focusManager.clearFocus(); viewModel.saveFood() },
                    onCancel = { focusManager.clearFocus(); viewModel.closeEditor() },
                    onDelete = if (state.editorFood != null) {
                        { focusManager.clearFocus(); viewModel.deleteAndCloseEditor() }
                    } else null,
                    isEditing = state.editorFood != null
                )
            }
        }
    }
}

@Composable
private fun FoodItem(
    food: FD,
    onEdit: () -> Unit,
    onCalculate: () -> Unit,
) {
    ElevatedCard(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge)
            .animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AapsSpacing.extraLarge, top = AapsSpacing.large, bottom = AapsSpacing.large, end = AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.titleSmall
                )
                val details = buildString {
                    if (food.portion > 0) append("${food.portion} ${food.unit}")
                    if (food.category != null) {
                        if (isNotEmpty()) append(" · ")
                        append(food.category)
                        if (food.subCategory != null) append(" / ${food.subCategory}")
                    }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${food.carbs}g",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val extras = buildList {
                    food.fat?.let { if (it > 0) add("F:${it}g") }
                    food.protein?.let { if (it > 0) add("P:${it}g") }
                    food.energy?.let { if (it > 0) add("${it}kJ") }
                }
                if (extras.isNotEmpty()) {
                    Text(
                        text = extras.joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onCalculate) {
                Icon(
                    imageVector = IcCalculator,
                    contentDescription = stringResource(CoreUiR.string.boluswizard),
                    modifier = Modifier.size(AapsSpacing.chipIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun FoodEditorContent(
    state: FoodManagementUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubCategoryChange: (String) -> Unit,
    onPortionChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onEnergyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    isEditing: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AapsSpacing.xxLarge)
            .padding(bottom = AapsSpacing.xxLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Text(
            text = stringResource(if (isEditing) CoreUiR.string.edit_food else CoreUiR.string.add_food),
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = state.editorName,
            onValueChange = onNameChange,
            label = { Text(stringResource(CoreUiR.string.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.editorSaveAttempted && state.editorName.isBlank()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorCategory,
                onValueChange = onCategoryChange,
                label = { Text(stringResource(CoreUiR.string.category)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.editorSubCategory,
                onValueChange = onSubCategoryChange,
                label = { Text(stringResource(CoreUiR.string.subcategory)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorPortion,
                onValueChange = onPortionChange,
                label = { Text(stringResource(CoreUiR.string.portion)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = state.editorUnit,
                onValueChange = onUnitChange,
                label = { Text(stringResource(CoreUiR.string.unit_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = state.editorCarbs,
            onValueChange = onCarbsChange,
            label = { Text(stringResource(CoreUiR.string.label_carbs_g)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorFat,
                onValueChange = onFatChange,
                label = { Text(stringResource(CoreUiR.string.label_fat_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.editorProtein,
                onValueChange = onProteinChange,
                label = { Text(stringResource(CoreUiR.string.label_protein_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.editorEnergy,
                onValueChange = onEnergyChange,
                label = { Text(stringResource(CoreUiR.string.label_energy_kj)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(Modifier.height(AapsSpacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(CoreUiR.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(Modifier.width(AapsSpacing.extraSmall))
            }
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(CoreUiR.string.cancel))
                }
                Spacer(Modifier.width(AapsSpacing.medium))
                TextButton(
                    onClick = onSave,
                    enabled = !state.editorSaveAttempted || state.editorName.isNotBlank()
                ) {
                    Text(stringResource(CoreUiR.string.save))
                }
            }
        }
    }
}
