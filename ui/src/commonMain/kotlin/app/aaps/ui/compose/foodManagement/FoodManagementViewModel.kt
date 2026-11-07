package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.SelectableListToolbar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.book.EatenItem
import app.aaps.ui.compose.foodManagement.book.EatenMeal
import app.aaps.ui.compose.foodManagement.book.ExportFile
import app.aaps.ui.compose.foodManagement.book.FileResult
import app.aaps.ui.compose.foodManagement.book.FoodBackupMath
import app.aaps.ui.compose.foodManagement.book.FoodBookMath
import app.aaps.ui.compose.foodManagement.book.FoodCsv
import app.aaps.ui.compose.foodManagement.book.FoodExportFiles
import app.aaps.ui.compose.foodManagement.book.FoodBookRepository
import app.aaps.ui.compose.foodManagement.book.MealTarget
import app.aaps.ui.compose.foodManagement.book.Nutrition
import app.aaps.ui.compose.foodManagement.book.Recipe
import app.aaps.ui.compose.foodManagement.book.RecipeItem
import app.aaps.ui.compose.foodManagement.book.RecipeKind
import app.aaps.ui.compose.foodManagement.book.TreatmentLink
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffResult
import app.aaps.ui.compose.foodManagement.openfoodfacts.OpenFoodFactsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class FoodManagementViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
    private val openFoodFacts: OpenFoodFactsRepository,
    private val rh: TextResolver,
    private val foodBook: FoodBookRepository,
    private val dateUtil: DateUtil,
    /** For the bolus calculation detail shown from the history. */
    val decimalFormatter: DecimalFormatter,
    private val foodCsv: FoodCsv,
    private val exportFiles: FoodExportFiles,
) : ViewModel() {

    // ---- Export, backup and restore (proof of concept, personal use) ----

    /** False where the platform has no exports folder: the menu does not offer these actions. */
    val exportAvailable: Boolean get() = exportFiles.available

    fun openCsvExport() = _uiState.update { it.copy(showCsvExport = true) }

    fun closeCsvExport() = _uiState.update { it.copy(showCsvExport = false) }

    /**
     * Saves the meal history and the list of foods, dishes and meals as two CSV files in the exports
     * folder. The treatments and the boluses given are looked up first, so the carbs and insulin
     * columns are filled.
     *
     * @param days only the meals of the last [days] days, or all meals when null
     */
    fun exportCsv(days: Int?) {
        closeCsvExport()
        viewModelScope.launch {
            loadTreatmentLinks()
            val state = uiState.value
            val from = days?.let { dateUtil.now() - it * DAY_MS }
            val result = withContext(aapsIoDispatcher) {
                runCatching { foodCsv.export(state.eatenMeals, state.treatmentLinks, insulinGiven(state.treatmentLinks), from, state.foods, state.recipes) }
                    .onFailure { aapsLogger.error(LTag.UI, "Food CSV export failed", it) }
                    .getOrDefault(FileResult.FAILED)
            }
            showResult(result, UiStrings.food_csv_exported)
        }
    }

    /** Eaten meal id to the insulin really given for its bolus calculation. See [FoodBookMath.bolusesGiven]. */
    private suspend fun insulinGiven(links: Map<String, TreatmentLink>): Map<String, Double> {
        val results = links.mapNotNull { (mealId, link) -> link.bcr?.let { mealId to it } }
        if (results.isEmpty()) return emptyMap()
        val boluses = persistenceLayer.getBolusesFromTime(results.minOf { it.second.timestamp } - LINK_SLACK_MS, true)
        val given = FoodBookMath.bolusesGiven(results.map { it.second }, boluses, LINK_SLACK_MS, LINK_WINDOW_MS, BOLUS_TIME_WINDOW_MS)
        return results.mapNotNull { (mealId, result) -> given[result.id]?.let { mealId to it.amount } }.toMap()
    }

    /** Saves all the data of the food POC (foods, dishes, meals, history) in one JSON file. */
    fun exportBackup() {
        viewModelScope.launch {
            val result = runCatching {
                val text = FoodBackupMath.encode(persistenceLayer.getFoods(), foodBook.book.value, dateUtil.now())
                withContext(aapsIoDispatcher) { exportFiles.save(FoodBackupMath.FILE_NAME, BACKUP_MIME_TYPE, text) }
            }
                .onFailure { aapsLogger.error(LTag.UI, "Food backup failed", it) }
                .getOrDefault(FileResult.FAILED)
            showResult(result, UiStrings.food_backup_saved)
        }
    }

    /** Opens the list of the backups found in the exports folder. */
    fun openRestore() {
        viewModelScope.launch {
            val files = withContext(aapsIoDispatcher) { exportFiles.list(FoodBackupMath.FILE_NAME) }
            _uiState.update { it.copy(restoreFiles = files) }
        }
    }

    fun closeRestore() = _uiState.update { it.copy(restoreFiles = null) }

    /**
     * Adds what [file] has and this phone does not: foods (in the database), dishes, standard meals
     * and eaten meals. Nothing already here is deleted or changed.
     */
    fun restore(file: ExportFile) {
        closeRestore()
        viewModelScope.launch {
            try {
                val backup = withContext(aapsIoDispatcher) { exportFiles.read(file) }?.let { FoodBackupMath.decode(it) }
                if (backup == null) {
                    showMessage(UiStrings.food_restore_invalid)
                    return@launch
                }
                // The database does not give back the id of a new food: read the foods again after.
                val missing = FoodBackupMath.missingFoods(backup, persistenceLayer.getFoods())
                missing.forEach { persistenceLayer.insertOrUpdateFood(it) }
                foodBook.load()
                val merge = FoodBackupMath.merge(backup, persistenceLayer.getFoods(), foodBook.book.value)
                val meals = merge.meals.map { checkedTreatment(it) }
                foodBook.addRestored(merge.copy(meals = meals))
                showMessage(UiStrings.food_restore_done, missing.size, merge.recipes.size, meals.size)
                loadTreatmentLinks()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Food restore failed", e)
                showMessage(UiStrings.food_export_failed)
            }
        }
    }

    /**
     * On another phone, or after a new install, the treatment id of a meal can be the id of another
     * record. It is kept only when that record has the time of the meal. Otherwise the meal is found
     * again by time and note, like a meal of an older POC.
     */
    private suspend fun checkedTreatment(meal: EatenMeal): EatenMeal {
        val id = meal.treatmentId ?: return meal
        val time = when (meal.target) {
            MealTarget.WIZARD -> persistenceLayer.getBolusCalculatorResultById(id)?.timestamp
            MealTarget.CARBS  -> persistenceLayer.getCarbsById(id)?.timestamp
        }
        return if (time != null && time == meal.validatedAt) meal else meal.copy(treatmentId = null)
    }

    private fun showResult(result: FileResult, saved: TextRef) = when (result) {
        FileResult.SAVED     -> showMessage(saved)
        FileResult.NO_FOLDER -> showMessage(UiStrings.food_export_no_folder)
        FileResult.FAILED    -> showMessage(UiStrings.food_export_failed)
    }

    /** Gives every meal item its own key, so the same food can be in the meal twice. */
    private var nextMealKey = 1L

    private val _uiState = MutableStateFlow(FoodManagementUiState())
    val uiState: StateFlow<FoodManagementUiState> = _uiState.asStateFlow()

    /** Derived: foods filtered by current search query and category/subcategory filters */
    val filteredFoods: StateFlow<List<FD>> = uiState
        .map { s ->
            s.foods.filter { food ->
                (s.searchQuery.isBlank() || food.name.contains(s.searchQuery, ignoreCase = true))
                    && (s.filterCategory == null || food.category == s.filterCategory)
                    && (s.filterSubCategory == null || food.subCategory == s.filterSubCategory)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Derived: subcategories available for the currently selected category filter */
    val filteredSubCategories: StateFlow<List<String>> = uiState
        .map { s ->
            val foods = s.foods
            val cat = s.filterCategory
            val relevant = if (cat != null) foods.filter { it.category == cat } else foods
            relevant.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadData()
        observeChanges()
        viewModelScope.launch {
            foodBook.load()
            refreshTreatmentLinks()
            foodBook.book.collect { book -> _uiState.update { it.copy(recipes = book.recipes, eatenMeals = book.eaten) } }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        persistenceLayer.observeChanges<FD>()
            .debounce(1000L)
            .onEach { loadData() }
            .launchIn(viewModelScope)
        // A meal sent to the wizard or to the carbs dialog is confirmed when its treatment is saved.
        merge(persistenceLayer.observeChanges<BCR>(), persistenceLayer.observeChanges<CA>())
            .debounce(1000L)
            .onEach { refreshTreatmentLinks() }
            .launchIn(viewModelScope)
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val foods = persistenceLayer.getFoods()
                val categories = foods.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val subCategories = foods.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
                _uiState.update {
                    it.copy(
                        foods = foods,
                        allCategories = categories,
                        allSubCategories = subCategories,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load foods", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterCategory(category: String?) {
        _uiState.update { it.copy(filterCategory = category, filterSubCategory = null) }
    }

    fun setFilterSubCategory(subCategory: String?) {
        _uiState.update { it.copy(filterSubCategory = subCategory) }
    }

    fun openEditor(food: FD? = null) {
        if (food != null) {
            _uiState.update {
                it.copy(
                    editorFood = food,
                    editorName = food.name,
                    editorCategory = food.category ?: "",
                    editorSubCategory = food.subCategory ?: "",
                    editorPortion = food.portion.toString(),
                    editorUnit = food.unit,
                    editorCarbs = food.carbs.toString(),
                    editorFat = food.fat?.toString() ?: "",
                    editorProtein = food.protein?.toString() ?: "",
                    editorEnergy = food.energy?.toString() ?: "",
                    editorSaveAttempted = false,
                    editorOffProduct = null,
                    showEditor = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    editorFood = null,
                    editorName = "",
                    editorCategory = "",
                    editorSubCategory = "",
                    editorPortion = "",
                    editorUnit = "g",
                    editorCarbs = "",
                    editorFat = "",
                    editorProtein = "",
                    editorEnergy = "",
                    editorSaveAttempted = false,
                    editorOffProduct = null,
                    showEditor = true
                )
            }
        }
    }

    // ---- Open Food Facts import (proof of concept) ----

    fun openImport() = _uiState.update { it.copy(showImport = true, importResult = null, showScanner = false) }

    fun closeImport() = _uiState.update { it.copy(showImport = false, showScanner = false, importInProgress = false) }

    fun updateImportQuery(v: String) = _uiState.update { it.copy(importQuery = v) }

    fun openScanner() = _uiState.update { it.copy(showScanner = true) }

    fun closeScanner() = _uiState.update { it.copy(showScanner = false) }

    /** The camera reads the same code many times a second: only the first read is used. */
    fun onBarcodeScanned(code: String) {
        if (!uiState.value.showScanner) return
        _uiState.update { it.copy(showScanner = false, importQuery = code) }
        runImport()
    }

    /** Sent only when the user asks: OFF allows about 10 searches per minute. */
    fun runImport() {
        val state = uiState.value
        val query = state.importQuery.trim()
        if (query.isEmpty() || state.importInProgress) return
        _uiState.update { it.copy(importInProgress = true, importResult = null) }
        viewModelScope.launch {
            val result = openFoodFacts.lookup(query)
            _uiState.update { it.copy(importInProgress = false, importResult = result) }
        }
    }

    /**
     * Fills the food editor with an OFF product. Nothing is saved: the user checks the values and
     * presses Save, as for a food typed by hand.
     */
    fun selectOffProduct(product: OffProduct) {
        openEditor()
        _uiState.update {
            it.copy(
                showImport = false,
                showScanner = false,
                editorName = MealMath.foodValuesFrom(product).name,
                editorCategory = OFF_CATEGORY,
                editorOffProduct = product,
            )
        }
        setOffPerServing(true)
    }

    /**
     * Fills portion, unit and values from the OFF product: for its serving ("1 pot (125 g)") when
     * [perServing] and OFF knows one, else for 100 g. Name and category are left as they are.
     */
    fun setOffPerServing(perServing: Boolean) {
        val product = uiState.value.editorOffProduct ?: return
        val values = MealMath.foodValuesFrom(product, perServing)
        _uiState.update {
            it.copy(
                editorPortion = MealMath.formatAmount(values.portion),
                editorUnit = values.unit,
                editorCarbs = values.carbs?.toString() ?: "",
                editorFat = values.fat?.toString() ?: "",
                editorProtein = values.protein?.toString() ?: "",
                editorEnergy = values.energyKj?.toString() ?: "",
                editorOffPerServing = perServing && MealMath.servingPortion(product) != null,
                editorOffGramsPerUnit = values.gramsPerUnit,
                editorOffComputedPortion = values.portion,
            )
        }
    }

    // ---- Meal (proof of concept, in memory only) ----

    fun askQuantity(food: FD) = _uiState.update {
        it.copy(quantityFood = food, quantityRecipe = null, quantityLineKey = null, quantityText = if (food.portion > 0) MealMath.formatAmount(food.portion) else "1")
    }

    fun updateQuantity(v: String) = _uiState.update { it.copy(quantityText = v) }

    fun dismissQuantity() = _uiState.update { it.copy(quantityFood = null, quantityRecipe = null, quantityLineKey = null) }

    /**
     * Adds the food or dish of the quantity dialog to the meal. When the dialog was opened from a
     * line of the meal, that line gets the new quantity instead, at the same place.
     */
    fun confirmQuantity() {
        val state = uiState.value
        val quantity = MealMath.parseAmount(state.quantityText) ?: return
        val item = state.quantityFood?.let { foodLine(it, quantity) }
            ?: state.quantityRecipe?.let { recipeLine(it, quantity) ?: return showMessage(UiStrings.food_book_missing) }
            ?: return
        _uiState.update { s ->
            val key = s.quantityLineKey
            val items = if (key == null) s.mealItems + item else s.mealItems.map { if (it.key == key) item.copy(key = key) else it }
            s.copy(quantityFood = null, quantityRecipe = null, quantityLineKey = null, mealItems = items)
        }
    }

    /** Opens the quantity dialog for one line of the meal, to change how much of it is eaten. */
    fun editLine(key: Long) {
        val item = uiState.value.mealItems.firstOrNull { it.key == key } ?: return
        val food = item.foodId?.let { foodsById()[it] }
        val recipe = item.recipeId?.let { recipesById()[it] }
        if (food == null && recipe == null) return showMessage(UiStrings.food_book_missing)
        _uiState.update { it.copy(quantityFood = food, quantityRecipe = recipe, quantityLineKey = key, quantityText = MealMath.formatAmount(item.quantity)) }
    }

    /** Carbs of [quantity] in the open quantity dialog, or null when they cannot be computed. */
    fun quantityCarbs(quantity: Double): Double? {
        val state = uiState.value
        state.quantityFood?.let { return MealMath.carbsFor(it, quantity) }
        return state.quantityRecipe?.let { recipe -> nutritionOf(recipe)?.carbs?.times(quantity) }
    }

    private fun foodLine(food: FD, quantity: Double): MealItem {
        val nutrition = FoodBookMath.nutritionOf(food, quantity)
        return MealItem(
            key = nextMealKey++,
            name = food.name,
            quantity = quantity,
            unit = if (food.portion > 0) food.unit else rh.gs(UiStrings.food_meal_portions),
            carbs = nutrition.carbs,
            fat = nutrition.fat,
            protein = nutrition.protein,
            energyKj = nutrition.energyKj,
            foodId = food.id,
        )
    }

    fun removeMealItem(key: Long) = _uiState.update { s ->
        val items = s.mealItems.filterNot { it.key == key }
        // While a dish is changed the sheet stays open: removing its last food is a step, not the end.
        s.copy(mealItems = items, showMeal = s.showMeal && (items.isNotEmpty() || s.editing != null))
    }

    fun clearMeal() = _uiState.update { it.copy(mealItems = emptyList(), showMeal = false, mealSource = null, pendingMealId = null) }

    fun openMeal() = _uiState.update { it.copy(showMeal = it.mealItems.isNotEmpty() || it.editing != null) }

    fun closeMeal() = _uiState.update { it.copy(showMeal = false) }

    // ---- Dishes and standard meals (proof of concept 2) ----

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short messages for a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun showMessage(text: TextRef, vararg args: Any) {
        _messages.tryEmit(rh.gs(text, *args))
    }

    /** Another tab ends the selection: the delete button only deletes what is on screen. */
    fun selectTab(tab: FoodTab) = _uiState.update {
        if (it.tab == tab) it else it.copy(tab = tab, selecting = false, selectedFoodIds = emptySet(), selectedRecipeIds = emptySet())
    }

    private fun foodsById(): Map<Long, FD> = uiState.value.foods.associateBy { it.id }
    private fun recipesById(): Map<String, Recipe> = uiState.value.recipes.associateBy { it.id }

    /** Nutrition of a whole dish or meal, or null when a food or a dish in it is missing. */
    fun nutritionOf(recipe: Recipe): Nutrition? = FoodBookMath.nutritionOf(recipe, foodsById(), recipesById())

    /** Nutrition of one line of a dish or meal, or null when its food or dish is missing. */
    fun nutritionOf(item: RecipeItem): Nutrition? = FoodBookMath.nutritionOf(item, foodsById(), recipesById())

    /** Asks how many servings of a dish go into the meal. */
    fun askQuantity(recipe: Recipe) = _uiState.update { it.copy(quantityFood = null, quantityRecipe = recipe, quantityLineKey = null, quantityText = "1") }

    private fun recipeLine(recipe: Recipe, servings: Double): MealItem? {
        val nutrition = nutritionOf(recipe)?.times(servings) ?: return null
        return MealItem(
            key = nextMealKey++,
            name = recipe.name,
            quantity = servings,
            unit = rh.gs(UiStrings.food_book_servings),
            carbs = nutrition.carbs,
            fat = nutrition.fat,
            protein = nutrition.protein,
            energyKj = nutrition.energyKj,
            recipeId = recipe.id,
        )
    }

    /** The lines of a dish or meal as meal lines, or null when one of them cannot be computed. */
    private fun linesOf(recipe: Recipe): List<MealItem>? = recipe.items.map { item ->
        val line = item.foodId?.let { id -> foodsById()[id]?.let { foodLine(it, item.quantity) } }
            ?: item.recipeId?.let { id -> recipesById()[id]?.let { recipeLine(it, item.quantity) } }
        line ?: return null
    }

    /**
     * Puts the lines of a standard meal into the meal being put together, so each one can still be
     * removed or changed before the bolus wizard ("breakfast, but croissants instead of bread").
     */
    fun addRecipeLines(recipe: Recipe) {
        val lines = linesOf(recipe) ?: return showMessage(UiStrings.food_book_missing)
        _uiState.update {
            it.copy(mealItems = it.mealItems + lines, mealSource = if (it.mealItems.isEmpty()) recipe else it.mealSource, showMeal = true)
        }
    }

    /**
     * Opens a dish or a meal to change it (the pencil). The meal being put together is put aside and
     * comes back when the change is saved or cancelled, so changing a dish never starts a meal.
     */
    fun startEdit(recipe: Recipe) {
        if (uiState.value.editing != null || uiState.value.selecting) return
        val lines = linesOf(recipe) ?: return showMessage(UiStrings.food_book_missing)
        _uiState.update {
            it.copy(
                parkedItems = it.mealItems,
                parkedSource = it.mealSource,
                mealItems = lines,
                mealSource = null,
                editing = recipe,
                editName = recipe.name,
                showMeal = true,
            )
        }
    }

    fun updateEditName(v: String) = _uiState.update { it.copy(editName = v) }

    fun cancelEdit() = endEdit()

    /** Writes the changed dish or meal back, with the same id and kind. */
    fun saveEdit() {
        val state = uiState.value
        val recipe = state.editing ?: return
        val name = state.editName.trim()
        if (name.isEmpty() || state.mealItems.isEmpty()) return
        if (nameUsed(name, recipe.kind, except = recipe.id)) return showMessage(UiStrings.food_book_name_used, name)
        val items = recipeItemsOf(state.mealItems)
        if (FoodBookMath.makesLoop(recipe.id, items, recipesById())) return showMessage(UiStrings.food_book_loop)
        viewModelScope.launch { foodBook.saveRecipe(recipe.copy(name = name, items = items)) }
        endEdit()
        showMessage(UiStrings.food_book_saved, name)
    }

    private fun endEdit() = _uiState.update {
        it.copy(
            mealItems = it.parkedItems,
            mealSource = it.parkedSource,
            parkedItems = emptyList(),
            parkedSource = null,
            editing = null,
            editName = "",
            showMeal = false,
        )
    }

    private fun recipeItemsOf(items: List<MealItem>): List<RecipeItem> =
        items.map { RecipeItem(name = it.name, quantity = it.quantity, unit = it.unit, foodId = it.foodId, recipeId = it.recipeId) }

    private fun nameUsed(name: String, kind: RecipeKind, except: String? = null): Boolean =
        uiState.value.recipes.any { it.kind == kind && it.id != except && it.name.equals(name, ignoreCase = true) }

    fun openSave() = _uiState.update { it.copy(showSave = true, saveName = "", saveKind = it.mealSource?.kind ?: RecipeKind.MEAL) }

    fun updateSaveName(v: String) = _uiState.update { it.copy(saveName = v) }
    fun updateSaveKind(kind: RecipeKind) = _uiState.update { it.copy(saveKind = kind) }
    fun dismissSave() = _uiState.update { it.copy(showSave = false) }

    /**
     * Saves the meal being put together as a new dish or standard meal. A name already used is
     * refused: an existing one is changed with its pencil, so it is never replaced by mistake.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun confirmSave() {
        val state = uiState.value
        val name = state.saveName.trim()
        if (name.isEmpty() || state.mealItems.isEmpty()) return
        if (nameUsed(name, state.saveKind)) return showMessage(UiStrings.food_book_name_used, name)
        val recipe = Recipe(id = Uuid.random().toString(), name = name, kind = state.saveKind, items = recipeItemsOf(state.mealItems))
        viewModelScope.launch { foodBook.saveRecipe(recipe) }
        _uiState.update { it.copy(showSave = false, mealSource = recipe) }
        showMessage(UiStrings.food_book_saved, name)
    }

    // ---- Meals eaten (proof of concept 3) ----

    /**
     * Sends the meal being put together to the wizard or to the carbs dialog. Keeps a copy of it and
     * returns what the dialog gets: the rounded carbs and the note.
     *
     * The copy waits until its treatment is saved (same note, a few minutes later). Then it shows in
     * the history and the meal is cleared. When the dialog is cancelled the meal stays here.
     */
    fun send(target: MealTarget): Pair<Int, String> {
        val state = uiState.value
        val (id, result) = record(state.mealItems, state.mealSource?.name, target)
        _uiState.update { it.copy(pendingMealId = id, showMeal = false) }
        return result
    }

    /** The carbs or wizard button of a food card: that food, at its portion. */
    fun send(food: FD, target: MealTarget): Pair<Int, String> =
        record(listOf(foodLine(food, if (food.portion > 0) food.portion else 1.0)), null, target).second

    /** The carbs or wizard button of a dish (one serving) or of a standard meal (all its lines). */
    fun send(recipe: Recipe, target: MealTarget): Pair<Int, String>? {
        val lines = if (recipe.kind == RecipeKind.MEAL) linesOf(recipe) else recipeLine(recipe, 1.0)?.let { listOf(it) }
        if (lines == null) {
            showMessage(UiStrings.food_book_missing)
            return null
        }
        return record(lines, recipe.name, target).second
    }

    /** Returns the id of the saved copy, and the carbs and note for the dialog. */
    @OptIn(ExperimentalUuidApi::class)
    private fun record(items: List<MealItem>, name: String?, target: MealTarget): Pair<String, Pair<Int, String>> {
        val carbs = MealMath.wizardCarbs(items)
        val note = noteFor(items)
        val meal = EatenMeal(
            id = Uuid.random().toString(),
            timestamp = dateUtil.now(),
            name = name,
            note = note,
            wizardCarbs = carbs,
            items = items.map { EatenItem(it.name, it.quantity, it.unit, it.carbs, it.fat, it.protein, it.energyKj) },
            target = target,
        )
        viewModelScope.launch { foodBook.addEatenMeal(meal) }
        return meal.id to (carbs to note)
    }

    fun timeText(timestamp: Long): String = dateUtil.dateAndTimeString(timestamp)

    /**
     * Finds, for each eaten meal, the treatment saved for it. The dialogs do not know about meals,
     * so the link is found afterwards: a treatment made within [LINK_WINDOW_MS] after the meal was
     * sent, with the same note, or else with the same carbs.
     *
     * A meal with a treatment is marked as confirmed and keeps the treatment id. A meal still without
     * one after the window was cancelled, and is removed. A meal whose treatment was deleted is
     * removed too: the meal and its treatment go together. When the meal being put together is
     * confirmed, it is cleared.
     */
    fun refreshTreatmentLinks() {
        viewModelScope.launch { loadTreatmentLinks() }
    }

    private suspend fun loadTreatmentLinks() {
        try {
            val meals = foodBook.book.value.eaten
            if (meals.isEmpty()) return
            // A meal tied to its treatment follows that record, found by its id. When the record
            // is deleted in the treatments history, the meal goes too. A record that is simply
            // not found (not expected) keeps the meal: nothing is removed on a guess.
            val known = mutableMapOf<String, TreatmentLink>()
            val deleted = mutableSetOf<String>()
            for (meal in meals) {
                val id = meal.treatmentId ?: continue
                when (meal.target) {
                    MealTarget.WIZARD -> persistenceLayer.getBolusCalculatorResultById(id)?.let { if (it.isValid) known[meal.id] = FoodBookMath.linkOf(meal, it) else deleted += meal.id }
                    MealTarget.CARBS  -> persistenceLayer.getCarbsById(id)?.let { if (it.isValid) known[meal.id] = FoodBookMath.linkOf(meal, it) else deleted += meal.id }
                }
            }
            // The other meals are still waiting, or were saved by an older POC: find their
            // treatment by time and note. A treatment already tied to a meal is not used again.
            val open = meals.filter { it.treatmentId == null }
            val found = if (open.isEmpty()) emptyMap() else {
                val from = open.minOf { it.timestamp } - LINK_SLACK_MS
                val tied = meals.filter { it.treatmentId != null }
                val tiedResults = tied.filter { it.target == MealTarget.WIZARD }.mapNotNull { it.treatmentId }.toSet()
                val tiedCarbs = tied.filter { it.target == MealTarget.CARBS }.mapNotNull { it.treatmentId }.toSet()
                val results = persistenceLayer.getBolusCalculatorResultsFromTime(from, true).filter { it.id !in tiedResults }
                // A carbs entry can be dated in the past in the dialog: look further back for those.
                val carbs = persistenceLayer.getCarbsFromTime(from - CARBS_LOOK_BACK_MS, true).filter { it.id !in tiedCarbs }
                FoodBookMath.linkTreatments(open, results, carbs, LINK_SLACK_MS, LINK_WINDOW_MS)
            }
            val now = dateUtil.now()
            val removed = deleted + open.filter { it.validatedAt == null && it.id !in found && now - it.timestamp > LINK_WINDOW_MS + LINK_SLACK_MS }.map { it.id }
            if (found.isNotEmpty() || removed.isNotEmpty()) foodBook.updateEatenMeals(found, removed)
            val links = known + found
            val validated = found.keys
            _uiState.update { s ->
                val pending = s.pendingMealId
                when {
                    // The meal was entered: the next one starts empty. While a dish is being
                    // changed, the meal is the one put aside.
                    pending != null && pending in validated ->
                        if (s.editing != null) s.copy(treatmentLinks = links, parkedItems = emptyList(), parkedSource = null, pendingMealId = null)
                        else s.copy(treatmentLinks = links, mealItems = emptyList(), mealSource = null, pendingMealId = null)
                    // Cancelled long ago: the meal stays, it is only not waited for any more.
                    pending != null && pending in removed   -> s.copy(treatmentLinks = links, pendingMealId = null)
                    else                                    -> s.copy(treatmentLinks = links)
                }
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.UI, "Food POC: cannot read the treatments", e)
        }
    }

    /**
     * The note the wizard stores with the bolus and carbs, for example
     * "Bread 80 g (40 g), Milk 250 ml (12 g)".
     *
     * For now this text is the only trace of the meal in the history. Storing the meal itself is the
     * next step of the roadmap.
     */
    fun mealNote(): String = noteFor(uiState.value.mealItems)

    private fun noteFor(items: List<MealItem>): String =
        items
            .map { item ->
                rh.gs(
                    UiStrings.food_meal_note_item,
                    item.name,
                    rh.gs(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit),
                    rh.gs(InterfacesStrings.format_carbs, item.carbs.roundToInt())
                )
            }
            .reduceOrNull { acc, next -> rh.gs(UiStrings.food_meal_note_join, acc, next) }
            .orEmpty()

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false) }
    }

    fun updateEditorName(v: String) = _uiState.update { it.copy(editorName = v) }
    fun updateEditorCategory(v: String) = _uiState.update { it.copy(editorCategory = v) }
    fun updateEditorSubCategory(v: String) = _uiState.update { it.copy(editorSubCategory = v) }
    /**
     * For a food filled from OFF, the values follow the portion: they are computed again from the
     * exact OFF values. A value the user has typed is kept: only a value still equal to the one
     * computed for the old portion is replaced. A food typed by hand is never recalculated.
     */
    fun updateEditorPortion(v: String) = _uiState.update { s ->
        val product = s.editorOffProduct
        val newPortion = MealMath.parseAmount(v)
        // Compared with the portion the values were last computed for, so clearing the field and
        // typing a new number still works.
        if (product == null || newPortion == null) return@update s.copy(editorPortion = v)
        val old = MealMath.nutrientsFor(product, s.editorOffComputedPortion, s.editorOffGramsPerUnit)
        val new = MealMath.nutrientsFor(product, newPortion, s.editorOffGramsPerUnit)
        fun follow(current: String, oldValue: Int?, newValue: Int?) = if (current == (oldValue?.toString() ?: "")) newValue?.toString() ?: "" else current
        s.copy(
            editorPortion = v,
            editorCarbs = follow(s.editorCarbs, old.carbs, new.carbs),
            editorFat = follow(s.editorFat, old.fat, new.fat),
            editorProtein = follow(s.editorProtein, old.protein, new.protein),
            editorEnergy = follow(s.editorEnergy, old.energyKj, new.energyKj),
            editorOffComputedPortion = newPortion,
        )
    }
    /** The unit is shown next to the portion on every card: it is kept short. */
    fun updateEditorUnit(v: String) = _uiState.update { it.copy(editorUnit = v.take(MAX_UNIT_LENGTH)) }
    fun updateEditorCarbs(v: String) = _uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = _uiState.update { it.copy(editorFat = v) }
    fun updateEditorProtein(v: String) = _uiState.update { it.copy(editorProtein = v) }
    fun updateEditorEnergy(v: String) = _uiState.update { it.copy(editorEnergy = v) }

    fun saveFood() {
        _uiState.update { it.copy(editorSaveAttempted = true) }
        val state = uiState.value
        val name = state.editorName.trim()
        if (name.isBlank()) return
        val category = sameAs(state.editorCategory, state.allCategories)

        val food = FD(
            id = state.editorFood?.id ?: 0,
            version = state.editorFood?.version ?: 0,
            dateCreated = state.editorFood?.dateCreated ?: -1,
            isValid = true,
            referenceId = state.editorFood?.referenceId,
            ids = state.editorFood?.ids ?: IDs(),
            name = name,
            // "fruits " is saved as the existing "Fruits": a typing difference must not make a new group.
            category = category,
            subCategory = sameAs(state.editorSubCategory, subCategoriesOf(category.orEmpty())),
            // "1,5" typed with a comma keyboard must not become 0.
            portion = state.editorPortion.trim().replace(',', '.').toDoubleOrNull() ?: 0.0,
            unit = state.editorUnit.trim().ifBlank { "g" },
            carbs = state.editorCarbs.toIntOrNull() ?: 0,
            fat = state.editorFat.toIntOrNull(),
            protein = state.editorProtein.toIntOrNull(),
            energy = state.editorEnergy.toIntOrNull(),
            // Not editable here, but must survive an edit (a food from Nightscout can have one).
            gi = state.editorFood?.gi,
        )

        viewModelScope.launch {
            try {
                persistenceLayer.insertOrUpdateFood(food)
                _uiState.update { it.copy(showEditor = false) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save food", e)
            }
        }
    }

    // ---- Category and subcategory suggestions ----

    /** [typed] trimmed, written as in [existing] when it is there with other capitals. Null when empty. */
    private fun sameAs(typed: String, existing: List<String>): String? {
        val text = typed.trim()
        if (text.isEmpty()) return null
        return existing.firstOrNull { it.trim().equals(text, ignoreCase = true) }?.trim() ?: text
    }

    /** The subcategories already used with [category] (same letters, any case). */
    fun subCategoriesOf(category: String): List<String> {
        val wanted = category.trim()
        if (wanted.isEmpty()) return emptyList()
        return uiState.value.foods
            .filter { it.category?.trim().equals(wanted, ignoreCase = true) }
            .mapNotNull { it.subCategory?.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    // ---- Delete: long press, then the delete button of the top bar, as in the treatments ----

    /** A long press starts the selection with that food. */
    fun startSelection(food: FD) = startSelection { it.copy(selectedFoodIds = setOf(food.id)) }

    /** A long press starts the selection with that dish or meal. */
    fun startSelection(recipe: Recipe) = startSelection { it.copy(selectedRecipeIds = setOf(recipe.id)) }

    private fun startSelection(select: (FoodManagementUiState) -> FoodManagementUiState) = _uiState.update {
        // Not while a dish is changed: deleting what is being changed would lose the change.
        if (it.selecting || it.editing != null) it else select(it.copy(selecting = true, selectedFoodIds = emptySet(), selectedRecipeIds = emptySet()))
    }

    fun toggleSelection(food: FD) = _uiState.update { s ->
        val ids = if (food.id in s.selectedFoodIds) s.selectedFoodIds - food.id else s.selectedFoodIds + food.id
        s.copy(selectedFoodIds = ids)
    }

    fun toggleSelection(recipe: Recipe) = _uiState.update { s ->
        val ids = if (recipe.id in s.selectedRecipeIds) s.selectedRecipeIds - recipe.id else s.selectedRecipeIds + recipe.id
        s.copy(selectedRecipeIds = ids)
    }

    fun exitSelection() = _uiState.update { it.copy(selecting = false, selectedFoodIds = emptySet(), selectedRecipeIds = emptySet(), showDeleteConfirm = false) }

    /** The top bar while items are selected: close and delete, the same bar as in the treatments. */
    fun selectionToolbar(onNavigateBack: () -> Unit): ToolbarConfig = SelectableListToolbar(
        isRemovingMode = true,
        selectedCount = uiState.value.selectedCount,
        onExitRemovingMode = ::exitSelection,
        onNavigateBack = onNavigateBack,
        onDelete = ::requestDelete,
        rh = rh,
    )

    fun requestDelete() = _uiState.update { it.copy(showDeleteConfirm = it.selectedCount > 0) }

    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirm = false) }

    /**
     * What the delete dialog lists: the names of the selected items, and for each one still used by
     * a dish or a meal that stays, the names of those. Their total will not be known any more, so
     * the user must be told before.
     */
    fun deleteSummary(): DeleteSummary {
        val state = uiState.value
        val foods = state.foods.filter { it.id in state.selectedFoodIds }
        val recipes = state.recipes.filter { it.id in state.selectedRecipeIds }
        val staying = state.recipes.filter { it.id !in state.selectedRecipeIds }
        val usedBy = foods.map { food -> food.name to staying.filter { r -> r.items.any { it.foodId == food.id } } } +
            recipes.map { recipe -> recipe.name to staying.filter { r -> r.items.any { it.recipeId == recipe.id } } }
        return DeleteSummary(
            names = foods.map { it.name } + recipes.map { it.name },
            usedIn = usedBy.filter { it.second.isNotEmpty() }.map { (name, users) -> name to users.map { it.name } },
        )
    }

    /** Deletes the selected foods (in the database) and dishes or meals (in the food book). */
    fun confirmDelete() {
        val state = uiState.value
        val foods = state.foods.filter { it.id in state.selectedFoodIds }
        val recipeIds = state.selectedRecipeIds
        viewModelScope.launch {
            foods.forEach { food ->
                try {
                    persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Failed to delete food", e)
                }
            }
            if (recipeIds.isNotEmpty()) foodBook.deleteRecipes(recipeIds)
        }
        _uiState.update { s -> s.copy(foods = s.foods.filterNot { it.id in s.selectedFoodIds }) }
        exitSelection()
    }
}

/**
 * Content of the delete dialog.
 *
 * @param usedIn a deleted food or dish to the dishes and meals that still use it
 */
data class DeleteSummary(val names: List<String>, val usedIn: List<Pair<String, List<String>>>)

@Immutable
data class FoodManagementUiState(
    val foods: List<FD> = emptyList(),
    val isLoading: Boolean = true,
    // Filters
    val searchQuery: String = "",
    val filterCategory: String? = null,
    val filterSubCategory: String? = null,
    val allCategories: List<String> = emptyList(),
    val allSubCategories: List<String> = emptyList(),
    // Editor
    val showEditor: Boolean = false,
    val editorFood: FD? = null,
    val editorName: String = "",
    val editorCategory: String = "",
    val editorSubCategory: String = "",
    val editorPortion: String = "",
    val editorUnit: String = "g",
    val editorCarbs: String = "",
    val editorFat: String = "",
    val editorProtein: String = "",
    val editorEnergy: String = "",
    val editorSaveAttempted: Boolean = false,
    /** Set when the editor was filled from Open Food Facts: the exact values are shown with a warning. */
    val editorOffProduct: OffProduct? = null,
    /** The editor shows the OFF serving ("1 pot (125 g)"), not 100 g. */
    val editorOffPerServing: Boolean = false,
    /** Weight in g (or ml) of one unit of the editor portion, for a food filled from OFF. */
    val editorOffGramsPerUnit: Double = 1.0,
    /** The portion the editor values were last computed for from OFF. */
    val editorOffComputedPortion: Double = 0.0,
    // Open Food Facts import (proof of concept)
    val showImport: Boolean = false,
    val importQuery: String = "",
    val importInProgress: Boolean = false,
    val importResult: OffResult? = null,
    val showScanner: Boolean = false,
    // Meal (proof of concept)
    val mealItems: List<MealItem> = emptyList(),
    val showMeal: Boolean = false,
    /** The food the quantity dialog is open for, or null when it is closed. */
    val quantityFood: FD? = null,
    /** The dish the quantity dialog is open for (quantity in servings). */
    val quantityRecipe: Recipe? = null,
    val quantityText: String = "",
    // Dishes, standard meals and eaten meals (proof of concept 2 and 3)
    val tab: FoodTab = FoodTab.FOODS,
    val recipes: List<Recipe> = emptyList(),
    val eatenMeals: List<EatenMeal> = emptyList(),
    /** The dish or meal the meal being put together was loaded from, if any. */
    val mealSource: Recipe? = null,
    val showSave: Boolean = false,
    val saveName: String = "",
    val saveKind: RecipeKind = RecipeKind.MEAL,
    /** Eaten meal id to the treatment found for it. */
    val treatmentLinks: Map<String, TreatmentLink> = emptyMap(),
    /** The line of the meal the quantity dialog changes, or null when the dialog adds a new line. */
    val quantityLineKey: Long? = null,
    /** The dish or meal being changed (pencil). While set, the meal lines are its lines. */
    val editing: Recipe? = null,
    val editName: String = "",
    /** The meal being put together, kept aside while a dish or meal is changed. */
    val parkedItems: List<MealItem> = emptyList(),
    val parkedSource: Recipe? = null,
    /** The meal sent to a dialog from the meal sheet, until its treatment is found. */
    val pendingMealId: String? = null,
    /** Long press: items of the open tab are selected to be deleted. */
    val selecting: Boolean = false,
    val selectedFoodIds: Set<Long> = emptySet(),
    val selectedRecipeIds: Set<String> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    /** The dialog that asks which period of meals to export as CSV. */
    val showCsvExport: Boolean = false,
    /** The backups offered to restore, newest first, or null when that dialog is closed. */
    val restoreFiles: List<ExportFile>? = null,
) {

    val selectedCount: Int get() = selectedFoodIds.size + selectedRecipeIds.size

    /** The meals shown in the history: only those whose treatment was found. */
    val confirmedMeals: List<EatenMeal> get() = eatenMeals.filter { it.validatedAt != null || it.id in treatmentLinks }
}

enum class FoodTab { FOODS, DISHES, MEALS, HISTORY }

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A bolus without the note of the calculation belongs to it only when given this soon after it. */
private const val BOLUS_TIME_WINDOW_MS = 5 * 60 * 1000L

private const val BACKUP_MIME_TYPE = "application/json"

/** The periods offered for the CSV export of the meals, in days. Null is "all". */
val CSV_EXPORT_DAYS: List<Int?> = listOf(7, 14, 30, null)

/** A bolus calculation made this long after the meal was sent to the wizard still belongs to it. */
private const val LINK_WINDOW_MS = 30 * 60 * 1000L

/** Clock differences of a few seconds must not break the link. */
private const val LINK_SLACK_MS = 60 * 1000L

/** How far back a carbs entry may be dated in the carbs dialog and still be found. */
private const val CARBS_LOOK_BACK_MS = 24 * 60 * 60 * 1000L

/** Longest unit a user can type, so it fits next to the portion on a card. */
internal const val MAX_UNIT_LENGTH = 16

/** Imported foods get this category, so they are easy to find with the category filter. */
internal const val OFF_CATEGORY = "Open Food Facts"
