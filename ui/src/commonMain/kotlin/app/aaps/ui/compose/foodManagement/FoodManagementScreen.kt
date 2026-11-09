package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSearchField
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.book.MealTarget
import app.aaps.ui.compose.foodManagement.book.RecipeKind
import app.aaps.ui.compose.treatments.WizardInfoDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodManagementScreen(
    viewModel: FoodManagementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWizard: (carbs: Int, name: String) -> Unit = { _, _ -> },
    onNavigateToCarbs: (carbs: Int, name: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
    val filteredSubCategories by viewModel.filteredSubCategories.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // Messages of the dishes and meals POC
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // The treatments are looked up again when the history is shown: a meal still waiting for its
    // treatment is removed there once it is clearly cancelled.
    LaunchedEffect(state.tab) {
        if (state.tab == FoodTab.HISTORY) viewModel.refreshTreatmentLinks()
    }

    // The bolus calculation opened from the history, as in the treatments history.
    var shownCalculation by remember { mutableStateOf<BCR?>(null) }
    shownCalculation?.let { WizardInfoDialog(bcr = it, decimalFormatter = viewModel.decimalFormatter, onDismiss = { shownCalculation = null }) }

    fun go(target: MealTarget, result: Pair<Int, String>?) {
        val (carbs, note) = result ?: return
        if (target == MealTarget.WIZARD) onNavigateToWizard(carbs, note) else onNavigateToCarbs(carbs, note)
    }

    val editing = state.editing != null
    val selecting = state.selecting

    // Back ends the selection first, as the close button of the top bar does.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selecting,
        onBackCompleted = { viewModel.exitSelection() }
    )

    if (state.showDeleteConfirm) {
        DeleteDialog(summary = viewModel.deleteSummary(), onConfirm = viewModel::confirmDelete, onDismiss = viewModel::dismissDelete)
    }
    if (state.showCsvExport) CsvExportDialog(onExport = viewModel::exportCsv, onDismiss = viewModel::closeCsvExport)
    state.restoreFiles?.let { files -> RestoreDialog(files = files, onRestore = viewModel::restore, onDismiss = viewModel::closeRestore) }

    Scaffold(
        topBar = {
            // While cards are selected: the bar of the treatments history, with close and delete.
            if (selecting) {
                val bar = viewModel.selectionToolbar(onNavigateBack)
                AapsTopAppBar(title = { Text(bar.title) }, navigationIcon = bar.navigationIcon, actions = bar.actions)
            } else AapsTopAppBar(
                title = {
                    AapsSearchField(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openImport() }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = stringResource(UiStrings.food_off_import))
                    }
                    if (viewModel.exportAvailable) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(CoreUiStrings.more_options))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(UiStrings.food_csv_export)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.openCsvExport()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(UiStrings.food_backup)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportBackup()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(UiStrings.food_restore)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.openRestore()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.mealItems.isNotEmpty() || editing) MealBar(items = state.mealItems, editingName = state.editing?.name, onClick = { viewModel.openMeal() })
        },
        floatingActionButton = {
            if (state.tab == FoodTab.FOODS && !selecting) {
                FloatingActionButton(onClick = { viewModel.openEditor() }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(CoreUiStrings.add))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FoodTabsRow(selected = state.tab, onSelect = viewModel::selectTab)
            when (state.tab) {
                FoodTab.FOODS   -> Unit
                FoodTab.DISHES,
                FoodTab.MEALS   -> {
                    val kind = if (state.tab == FoodTab.DISHES) RecipeKind.DISH else RecipeKind.MEAL
                    RecipeList(
                        recipes = state.recipes,
                        kind = kind,
                        editing = editing,
                        selecting = selecting,
                        selectedIds = state.selectedRecipeIds,
                        nutritionOf = viewModel::nutritionOf,
                        onAdd = { if (kind == RecipeKind.DISH) viewModel.askQuantity(it) else viewModel.addRecipeLines(it) },
                        onSelect = viewModel::startSelection,
                        onToggle = viewModel::toggleSelection,
                        onEdit = viewModel::startEdit,
                        onCarbs = { go(MealTarget.CARBS, viewModel.send(it, MealTarget.CARBS)) },
                        onCalculate = { go(MealTarget.WIZARD, viewModel.send(it, MealTarget.WIZARD)) },
                    )
                    return@Column
                }

                FoodTab.HISTORY -> {
                    EatenMealList(
                        meals = state.confirmedMeals,
                        links = state.treatmentLinks,
                        timeText = viewModel::timeText,
                        onShowCalculation = { shownCalculation = it },
                    )
                    return@Column
                }
            }
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
                        text = stringResource(CoreUiStrings.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AapsSpacing.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    items(filteredFoods, key = { it.id }) { food ->
                        FoodItem(
                            food = food,
                            image = state.foodImages[food.id],
                            editing = editing,
                            selecting = selecting,
                            selected = food.id in state.selectedFoodIds,
                            onAdd = { viewModel.askQuantity(food) },
                            onSelect = { viewModel.startSelection(food) },
                            onToggle = { viewModel.toggleSelection(food) },
                            onEdit = { viewModel.openEditor(food) },
                            onCarbs = { go(MealTarget.CARBS, viewModel.send(food, MealTarget.CARBS)) },
                            onCalculate = { go(MealTarget.WIZARD, viewModel.send(food, MealTarget.WIZARD)) },
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
                    categories = state.allCategories,
                    subCategories = viewModel.subCategoriesOf(state.editorCategory),
                    onNameChange = viewModel::updateEditorName,
                    onCategoryChange = viewModel::updateEditorCategory,
                    onSubCategoryChange = viewModel::updateEditorSubCategory,
                    onPortionChange = viewModel::updateEditorPortion,
                    onUnitChange = viewModel::updateEditorUnit,
                    onCarbsChange = viewModel::updateEditorCarbs,
                    onFatChange = viewModel::updateEditorFat,
                    onProteinChange = viewModel::updateEditorProtein,
                    onEnergyChange = viewModel::updateEditorEnergy,
                    onOffPerServingChange = viewModel::setOffPerServing,
                    onSave = { focusManager.clearFocus(); viewModel.saveFood() },
                    onCancel = { focusManager.clearFocus(); viewModel.closeEditor() },
                    isEditing = state.editorFood != null
                )
            }
        }

        // Open Food Facts import (proof of concept)
        if (state.showImport) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val focusManager = LocalFocusManager.current
            ModalBottomSheet(
                onDismissRequest = { focusManager.clearFocus(); viewModel.closeImport() },
                sheetState = sheetState,
            ) {
                OffImportContent(
                    state = state,
                    onQueryChange = viewModel::updateImportQuery,
                    onSearch = { focusManager.clearFocus(); viewModel.runImport() },
                    onOpenScanner = { focusManager.clearFocus(); viewModel.openScanner() },
                    onCloseScanner = viewModel::closeScanner,
                    onScanned = viewModel::onBarcodeScanned,
                    onSelect = viewModel::selectOffProduct,
                )
            }
        }

        // Meal (proof of concept)
        if (state.showMeal) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeMeal() },
                sheetState = sheetState,
            ) {
                val recipe = state.editing
                if (recipe != null) {
                    RecipeEditContent(
                        recipe = recipe,
                        name = state.editName,
                        items = state.mealItems,
                        onNameChange = viewModel::updateEditName,
                        onEditLine = viewModel::editLine,
                        onRemove = viewModel::removeMealItem,
                        onAddFood = { viewModel.closeMeal(); viewModel.selectTab(FoodTab.FOODS) },
                        onCancel = viewModel::cancelEdit,
                        onSave = viewModel::saveEdit,
                    )
                } else {
                    MealContent(
                        items = state.mealItems,
                        sourceName = state.mealSource?.name,
                        onEditLine = viewModel::editLine,
                        onRemove = viewModel::removeMealItem,
                        onClear = viewModel::clearMeal,
                        onAddFood = { viewModel.closeMeal(); viewModel.selectTab(FoodTab.FOODS) },
                        onSave = viewModel::openSave,
                        onCarbs = { go(MealTarget.CARBS, viewModel.send(MealTarget.CARBS)) },
                        onCalculate = { go(MealTarget.WIZARD, viewModel.send(MealTarget.WIZARD)) },
                    )
                }
            }
        }

        SaveRecipeDialog(
            state = state,
            onNameChange = viewModel::updateSaveName,
            onKindChange = viewModel::updateSaveKind,
            onConfirm = viewModel::confirmSave,
            onDismiss = viewModel::dismissSave,
        )

        QuantityDialog(
            state = state,
            carbsFor = viewModel::quantityCarbs,
            onQuantityChange = viewModel::updateQuantity,
            onConfirm = viewModel::confirmQuantity,
            onDismiss = viewModel::dismissQuantity,
        )
    }
}

@Composable
private fun FoodItem(
    food: FD,
    image: ByteArray?,
    editing: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onAdd: () -> Unit,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onCarbs: () -> Unit,
    onCalculate: () -> Unit,
) {
    // A tap adds the food to the meal (or to the dish being changed). The pencil changes the food.
    // A long press selects it, to delete it with the button of the top bar.
    SelectableCard(
        selecting = selecting,
        selected = selected,
        onClick = if (selecting) onToggle else onAdd,
        onLongClick = { if (selecting) onToggle() else if (!editing) onSelect() },
        actions = { if (!editing) CardActions(name = food.name, onEdit = onEdit, onCarbs = onCarbs, onCalculate = onCalculate) },
    ) {
        val extras = buildList {
            // F and P were never translated; only the separator and the unit are.
            val grams = stringResource(CoreUiStrings.units_grams)
            food.fat?.let { if (it > 0) add(stringResource(InterfacesStrings.confirmation_line, "F", stringResource(CoreUiStrings.value_with_unit, it.toString(), grams))) }
            food.protein?.let { if (it > 0) add(stringResource(InterfacesStrings.confirmation_line, "P", stringResource(CoreUiStrings.value_with_unit, it.toString(), grams))) }
            food.energy?.let { if (it > 0) add(stringResource(CoreUiStrings.value_with_unit, it.toString(), "kJ")) }
        }
        // Only foods imported from Open Food Facts have a photo. It tells close products apart.
        if (image != null) {
            FoodPhoto(bytes = image, size = AapsSpacing.foodThumbnailSize)
            Spacer(modifier = Modifier.width(AapsSpacing.medium))
        }
        CardTexts(name = food.name, detail = joinLines(extras))
        CardValues(
            portion = if (food.portion > 0) stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(food.portion), food.unit) else null,
            carbs = stringResource(InterfacesStrings.format_carbs, food.carbs),
        )
    }
}

/**
 * A text field that proposes the values already used, so the same group is not typed twice in two
 * ways. The list shows the values that contain what is typed. A new text makes a new value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionField(value: String, onValueChange: (String) -> Unit, label: String, suggestions: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    // True once the user types in the field. A value set from outside (the "OFF" category of an
    // import) is not a search: it must not hide the other choices.
    var typedByUser by remember { mutableStateOf(false) }
    val typed = value.trim()
    // A value already chosen or filled in shows every choice, so another one can be picked without
    // clearing it. While a new text is typed, only the values that contain it are shown.
    val filtering = typedByUser && typed.isNotEmpty() && suggestions.none { it.equals(typed, ignoreCase = true) }
    val shown = if (filtering) suggestions.filter { it.contains(typed, ignoreCase = true) } else suggestions
    val open = expanded && shown.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); typedByUser = true; expanded = true },
            label = { Text(label) },
            singleLine = true,
            // No arrow when there is nothing to propose: the label keeps the whole width.
            trailingIcon = if (suggestions.isEmpty()) null else { { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            shown.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = { onValueChange(item); typedByUser = false; expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
internal fun FoodEditorContent(
    state: FoodManagementUiState,
    categories: List<String> = emptyList(),
    subCategories: List<String> = emptyList(),
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
    isEditing: Boolean,
    onOffPerServingChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AapsSpacing.xxLarge)
            .padding(bottom = AapsSpacing.xxLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Text(
            text = stringResource(if (isEditing) CoreUiStrings.edit_food else CoreUiStrings.add_food),
            style = MaterialTheme.typography.titleLarge
        )

        // The package, to check the values against it. Downloaded again when the file is missing.
        state.editorImage?.let { FoodPhoto(bytes = it, size = AapsSpacing.foodPhotoSize, modifier = Modifier.align(Alignment.CenterHorizontally)) }

        state.editorOffProduct?.let {
            OffEditorBanner(product = it, perServing = state.editorOffPerServing, onPerServingChange = onOffPerServingChange)
        }

        OutlinedTextField(
            value = state.editorName,
            onValueChange = onNameChange,
            label = { Text(stringResource(CoreUiStrings.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.editorSaveAttempted && state.editorName.isBlank()
        )

        // One field per row: the list arrow takes width, and a long label must not wrap.
        // Only the subcategories of the chosen category are proposed.
        SuggestionField(
            value = state.editorCategory,
            onValueChange = onCategoryChange,
            label = stringResource(CoreUiStrings.category),
            suggestions = categories,
            modifier = Modifier.fillMaxWidth()
        )
        SuggestionField(
            value = state.editorSubCategory,
            onValueChange = onSubCategoryChange,
            label = stringResource(CoreUiStrings.subcategory),
            suggestions = subCategories,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorPortion,
                onValueChange = onPortionChange,
                label = { Text(stringResource(CoreUiStrings.portion)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = state.editorUnit,
                onValueChange = onUnitChange,
                label = { Text(stringResource(CoreUiStrings.unit_label)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = state.editorCarbs,
            onValueChange = onCarbsChange,
            label = { Text(stringResource(CoreUiStrings.label_carbs_g)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorFat,
                onValueChange = onFatChange,
                label = { Text(stringResource(CoreUiStrings.label_fat_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = state.editorProtein,
                onValueChange = onProteinChange,
                label = { Text(stringResource(CoreUiStrings.label_protein_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = state.editorEnergy,
                onValueChange = onEnergyChange,
                label = { Text(stringResource(CoreUiStrings.label_energy_kj)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Spacer(Modifier.height(AapsSpacing.medium))

        // Delete is done from the list: long press, then the delete button of the top bar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(CoreUiStrings.cancel))
            }
            Spacer(Modifier.width(AapsSpacing.medium))
            TextButton(
                onClick = onSave,
                enabled = !state.editorSaveAttempted || state.editorName.isNotBlank()
            ) {
                Text(stringResource(CoreUiStrings.save))
            }
        }
    }
}
