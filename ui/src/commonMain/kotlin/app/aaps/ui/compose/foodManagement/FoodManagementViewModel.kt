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
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
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
import app.aaps.ui.compose.foodManagement.book.FoodBook
import app.aaps.ui.compose.foodManagement.book.FoodBookMath
import app.aaps.ui.compose.foodManagement.book.FoodCsv
import app.aaps.ui.compose.foodManagement.book.FoodExportFiles
import app.aaps.ui.compose.foodManagement.book.FoodImages
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
    private val foodImages: FoodImages,
    private val preferences: Preferences,
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

    /**
     * Saves all the data of the food POC (foods, dishes, meals, history) and the food photos in one
     * archive. A restore on a new phone then needs no download.
     */
    fun exportBackup() {
        viewModelScope.launch {
            val result = runCatching {
                val foods = persistenceLayer.getFoods()
                foodBook.load()
                val text = FoodBackupMath.encode(foods, foodBook.book.value, dateUtil.now())
                val images = foodImages.filesFor(foods.map { it.id })
                val entries = mapOf(FoodBackupMath.FILE_NAME to text.encodeToByteArray()) +
                    images.mapKeys { (key, _) -> FoodBackupMath.imageEntry(key) }
                withContext(aapsIoDispatcher) { exportFiles.saveArchive(FoodBackupMath.ARCHIVE_NAME, FoodBackupMath.ARCHIVE_MIME_TYPE, entries) }
            }
                .onFailure { aapsLogger.error(LTag.UI, "Food backup failed", it) }
                .getOrDefault(FileResult.FAILED)
            showResult(result, UiStrings.food_backup_saved)
        }
    }

    /** Opens the list of the backups found in the exports folder. */
    fun openRestore() {
        viewModelScope.launch {
            // The archives, and the JSON files of format 1 made before.
            val files = withContext(aapsIoDispatcher) {
                (exportFiles.list(FoodBackupMath.ARCHIVE_NAME) + exportFiles.list(FoodBackupMath.FILE_NAME)).sortedByDescending { it.name }
            }
            _uiState.update { it.copy(restoreFiles = files) }
        }
    }

    fun closeRestore() = _uiState.update { it.copy(restoreFiles = null) }

    /**
     * Adds what [file] has and this phone does not: foods (in the database), dishes, standard meals,
     * eaten meals and photos. Nothing already here is deleted or changed: a photo file already on the
     * phone is kept, and a food that has a photo of the user keeps it.
     */
    fun restore(file: ExportFile) {
        closeRestore()
        viewModelScope.launch {
            try {
                val archive = if (file.name.endsWith(FoodBackupMath.ARCHIVE_NAME)) withContext(aapsIoDispatcher) { exportFiles.readArchive(file) } else null
                val text = if (archive != null) archive[FoodBackupMath.FILE_NAME]?.decodeToString()
                else withContext(aapsIoDispatcher) { exportFiles.read(file) }
                val backup = text?.let { FoodBackupMath.decode(it) }
                if (backup == null) {
                    showMessage(UiStrings.food_restore_invalid)
                    return@launch
                }
                // The database does not give back the id of a new food: read the foods again after.
                val missing = FoodBackupMath.missingFoods(backup, persistenceLayer.getFoods())
                missing.forEach { persistenceLayer.insertOrUpdateFood(it) }
                foodBook.load()
                val merge = FoodBackupMath.merge(backup, persistenceLayer.getFoods(), foodBook.book.value)
                val meals = withTreatmentAtItsTime(merge.meals.map { checkedTreatment(it) }, foodBook.book.value.eaten)
                foodBook.addRestored(merge.copy(meals = meals))
                // OFF photos are named by barcode and shared: all are kept. A photo of the user only
                // when its food took it, so a food that kept its own photo leaves no unused file.
                val userKeys = merge.photos.map { it.key }.toSet() +
                    merge.recipes.mapNotNull { it.photo } +
                    meals.flatMap { meal -> listOfNotNull(meal.photo) + meal.items.mapNotNull { it.photo } }
                archive?.forEach { (entry, bytes) ->
                    val key = FoodBackupMath.keyOfEntry(entry) ?: return@forEach
                    if (!key.startsWith(FoodImages.USER_KEY_PREFIX) || key in userKeys) foodImages.restoreFile(key, bytes)
                }
                loadData()
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

    /**
     * A file of format 3 does not keep the id of the treatment of a meal: its treatment is the record
     * of the same kind at the time of [EatenMeal.validatedAt]. A record already tied to another meal
     * is not used again. A meal whose treatment is not on this phone (yet) stays without id: it is
     * found later by time and note, and it is never removed for that.
     */
    private suspend fun withTreatmentAtItsTime(meals: List<EatenMeal>, others: List<EatenMeal>): List<EatenMeal> {
        val times = meals.filter { it.treatmentId == null }.mapNotNull { it.validatedAt }
        if (times.isEmpty()) return meals
        val tied = others + meals
        val usedResults = tied.filter { it.target == MealTarget.WIZARD }.mapNotNull { it.treatmentId }.toMutableSet()
        val usedCarbs = tied.filter { it.target == MealTarget.CARBS }.mapNotNull { it.treatmentId }.toMutableSet()
        val results = persistenceLayer.getBolusCalculatorResultsFromTime(times.min(), true)
        val carbs = persistenceLayer.getCarbsFromTime(times.min(), true)
        return meals.map { meal ->
            val time = meal.validatedAt
            if (meal.treatmentId != null || time == null) return@map meal
            val id = when (meal.target) {
                MealTarget.WIZARD -> results.firstOrNull { it.timestamp == time && it.id !in usedResults }?.id?.also { usedResults += it }
                MealTarget.CARBS  -> carbs.firstOrNull { it.timestamp == time && it.id !in usedCarbs }?.id?.also { usedCarbs += it }
            }
            meal.copy(treatmentId = id)
        }
    }

    private fun showResult(result: FileResult, saved: TextRef) = when (result) {
        FileResult.SAVED     -> showMessage(saved)
        FileResult.NO_FOLDER -> showMessage(UiStrings.food_export_no_folder)
        FileResult.FAILED    -> showMessage(UiStrings.food_export_failed)
    }

    /** Gives every meal item its own key, so the same food can be in the meal twice. */
    private var nextMealKey = 1L

    private val _uiState = MutableStateFlow(FoodManagementUiState(sortFoodsByName = preferences.get(BooleanNonKey.FoodSortByName)))
    val uiState: StateFlow<FoodManagementUiState> = _uiState.asStateFlow()

    /**
     * Derived: foods filtered by the search and by the category and subcategory filters. The newest
     * come first (the order of the database), or the names from A to Z.
     */
    val filteredFoods: StateFlow<List<FD>> = uiState
        .map { s ->
            val shown = s.foods.filter { food ->
                (s.searchQuery.isBlank() || food.name.contains(s.searchQuery, ignoreCase = true))
                    && s.filterCategory.matches(food.category)
                    && s.filterSubCategory.matches(food.subCategory)
            }
            if (s.sortFoodsByName) shown.sortedBy { nameSortKey(it.name) } else shown
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Derived: the choices of the category filter of the foods. */
    val foodCategoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.foods.map { it.category }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    /** Derived: the choices of the subcategory filter of the foods, for the chosen category. */
    val foodSubCategoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.foods.filter { s.filterCategory.matches(it.category) }.map { it.subCategory }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    init {
        loadData()
        observeChanges()
        viewModelScope.launch {
            foodBook.load()
            refreshTreatmentLinks()
            foodBook.book.collect { book ->
                _uiState.update { it.copy(recipes = book.recipes, eatenMeals = book.eaten, foodBarcodes = book.offLinks.associate { link -> link.foodId to link.barcode },
                    foodNotes = book.foodNotes.associate { note -> note.foodId to note.text },
                ) }
                loadRecipePhotos(book)
            }
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
                val images = foodImages.saved(foods.map { it.id })
                val categories = foods.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val subCategories = foods.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
                _uiState.update {
                    // A filter on a group that no longer has any food (its last food was moved to
                    // another group, or deleted) would hide every food. It is removed, so the whole
                    // list comes back.
                    val category = it.filterCategory.takeIf { c -> foods.any { f -> c.matches(f.category) } } ?: GroupFilter.All
                    // A subcategory belongs to its category: it goes with it.
                    val subCategory = it.filterSubCategory
                        .takeIf { _ -> category == it.filterCategory }
                        ?.takeIf { sub -> foods.any { f -> category.matches(f.category) && sub.matches(f.subCategory) } }
                        ?: GroupFilter.All
                    it.copy(
                        foods = foods,
                        foodImages = images,
                        allCategories = categories,
                        allSubCategories = subCategories,
                        filterCategory = category,
                        filterSubCategory = subCategory,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load foods", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Reads the photos of the dishes, meals and history that are not read yet. */
    private suspend fun loadRecipePhotos(book: FoodBook) {
        val keys = book.recipes.mapNotNull { it.photo } + book.eaten.flatMap { meal -> listOfNotNull(meal.photo) + meal.items.mapNotNull { it.photo } }
        val known = uiState.value.recipePhotos
        val missing = keys.filter { it !in known }
        if (missing.isEmpty()) return
        val read = foodImages.read(missing)
        if (read.isNotEmpty()) _uiState.update { it.copy(recipePhotos = it.recipePhotos + read) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** A new category starts with all its subcategories. */
    fun setFilterCategory(category: GroupFilter) {
        _uiState.update { it.copy(filterCategory = category, filterSubCategory = GroupFilter.All) }
    }

    fun setFilterSubCategory(subCategory: GroupFilter) {
        _uiState.update { it.copy(filterSubCategory = subCategory) }
    }

    /** The newest foods first, or A to Z. Kept for the next time the screen is opened. */
    fun setSortFoodsByName(byName: Boolean) {
        preferences.put(BooleanNonKey.FoodSortByName, byName)
        _uiState.update { it.copy(sortFoodsByName = byName) }
    }

    // ---- Category filters of the dishes ----

    /** Derived: the dishes and meals, with the dishes that do not pass the filters of the dish tab left out. */
    val filteredRecipes: StateFlow<List<Recipe>> = uiState
        .map { s -> s.recipes.filter { it.kind != RecipeKind.DISH || (s.dishCategory.matches(it.category) && s.dishSubCategory.matches(it.subCategory)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dishCategoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.recipes.filter { it.kind == RecipeKind.DISH }.map { it.category }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    val dishSubCategoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.recipes.filter { it.kind == RecipeKind.DISH && s.dishCategory.matches(it.category) }.map { it.subCategory }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    fun setDishCategory(category: GroupFilter) {
        _uiState.update { it.copy(dishCategory = category, dishSubCategory = GroupFilter.All) }
    }

    fun setDishSubCategory(subCategory: GroupFilter) {
        _uiState.update { it.copy(dishSubCategory = subCategory) }
    }

    /**
     * The categories proposed in the dish editor: the ones of the foods and of the dishes, so a
     * home-made dish can go in the same group as the foods it is close to.
     */
    fun dishCategorySuggestions(): List<String> {
        val s = uiState.value
        return (s.allCategories + s.recipes.mapNotNull { it.category?.trim() })
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { nameSortKey(it) }
    }

    /** The subcategories already used with [category], by the foods and by the dishes. */
    fun dishSubCategorySuggestions(category: String): List<String> {
        val wanted = category.trim()
        if (wanted.isEmpty()) return emptyList()
        return (subCategoriesOf(wanted) + uiState.value.recipes
            .filter { it.category?.trim().equals(wanted, ignoreCase = true) }
            .mapNotNull { it.subCategory?.trim() })
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { nameSortKey(it) }
    }

    fun updateEditCategory(v: String) = _uiState.update { it.copy(editCategory = v) }

    fun updateEditSubCategory(v: String) = _uiState.update { it.copy(editSubCategory = v) }

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
                    editorBarcode = it.foodBarcodes[food.id],
                    editorNote = it.foodNotes[food.id].orEmpty(),
                    editorSaveAttempted = false,
                    editorOffProduct = null,
                    editorScaleMode = false,
                    editorScaleBase = null,
                    editorImage = null,
                    editorUserPhoto = null,
                    editorPhotoChanged = false,
                    showPhotoCamera = false,
                    showEditor = true
                )
            }
            loadEditorPhotos(food.id)
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
                    editorBarcode = null,
                    editorNote = "",
                    editorSaveAttempted = false,
                    editorOffProduct = null,
                    editorScaleMode = false,
                    editorScaleBase = null,
                    editorImage = null,
                    editorUserPhoto = null,
                    editorPhotoChanged = false,
                    showPhotoCamera = false,
                    showEditor = true
                )
            }
        }
    }

    /**
     * The photos of the food opened in the editor: the photo of the user, and the OFF photo kept
     * under it.
     *
     * A food imported from OFF whose photo file is missing (new phone, restored backup) gets it
     * again when it is opened. Nothing is downloaded for a food typed by hand or a product without
     * a photo.
     */
    private fun loadEditorPhotos(foodId: Long) {
        if (!foodImages.available) return
        viewModelScope.launch {
            val user = foodImages.userPhoto(foodId)
            val off = foodImages.forFood(foodId)
            _uiState.update {
                // The user may have taken or removed a photo while the files were read: that wins.
                if (it.editorFood?.id != foodId) it
                else if (it.editorPhotoChanged) it.copy(editorImage = off)
                else it.copy(editorUserPhoto = user, editorImage = off)
            }
            // A downloaded OFF photo is shown on the list too, unless the food shows a photo of the user there.
            if (off != null) _uiState.update { if (foodId in it.foodImages) it else it.copy(foodImages = it.foodImages + (foodId to off)) }
        }
    }

    // ---- Photo of the user (proof of concept) ----

    fun openPhotoCamera() = _uiState.update { it.copy(showPhotoCamera = true) }

    fun closePhotoCamera() = _uiState.update { it.copy(showPhotoCamera = false) }

    /** The photo just taken. It is saved with the food, when the user presses Save. */
    fun takePhoto(bytes: ByteArray) = _uiState.update { it.copy(editorUserPhoto = bytes, editorPhotoChanged = true, showPhotoCamera = false) }

    /** The photo of the user is removed when the food is saved. The OFF photo, if any, is shown again. */
    fun removePhoto() = _uiState.update { it.copy(editorUserPhoto = null, editorPhotoChanged = true) }

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

    /**
     * Sent only when the user asks: OFF allows about 10 searches per minute.
     *
     * A barcode already in the list is shown first, without asking OFF: it is local, works without
     * network and keeps a request. [checkList] is false when the user wants another food for it anyway
     * (another portion, for example).
     */
    fun runImport(checkList: Boolean = true) {
        val state = uiState.value
        val query = state.importQuery.trim()
        if (query.isEmpty() || state.importInProgress) return
        if (checkList && OpenFoodFactsRepository.isBarcode(query)) {
            val owners = state.foodsWithBarcode(query)
            if (owners.isNotEmpty()) return _uiState.update { it.copy(barcodeOwners = owners, importResult = null) }
        }
        _uiState.update { it.copy(importInProgress = true, importResult = null) }
        viewModelScope.launch {
            val result = openFoodFacts.lookup(query)
            _uiState.update { it.copy(importInProgress = false, importResult = result, importImages = emptyMap()) }
            if (result is OffResult.Found) loadImportImages(result.products)
        }
    }

    /** The barcode is in the list: opens that food instead of asking OFF. */
    fun openBarcodeOwner(food: FD) {
        _uiState.update { it.copy(barcodeOwners = emptyList(), showImport = false, showScanner = false) }
        openEditor(food)
    }

    /** The barcode is in the list, and the user wants another food for the same product. */
    fun importAnyway() {
        _uiState.update { it.copy(barcodeOwners = emptyList()) }
        runImport(checkList = false)
    }

    fun dismissBarcodeOwners() = _uiState.update { it.copy(barcodeOwners = emptyList()) }

    /**
     * OFF does not know the barcode: a new food is typed by hand with it, so the barcode is not lost.
     * The next scan finds the food, and the product can be sent to OFF later.
     */
    fun makeFoodWithBarcode() {
        val code = uiState.value.importQuery.trim()
        if (!OpenFoodFactsRepository.isBarcode(code)) return
        closeImport()
        openEditor()
        _uiState.update { it.copy(editorBarcode = code) }
    }

    /**
     * The photos of the products found, one after the other, shown as they arrive: the package is
     * often what tells two close products apart. They are files of the OFF image server, not API
     * requests, so the OFF request limits are not used.
     */
    private suspend fun loadImportImages(products: List<OffProduct>) {
        if (!foodImages.available) return
        for (product in products.distinctBy { it.barcode }) {
            if (product.imageUrl == null) continue
            val bytes = foodImages.download(product) ?: continue
            _uiState.update { s ->
                // A new search has started: these photos are not for it.
                if ((s.importResult as? OffResult.Found)?.products?.any { it.barcode == product.barcode } != true) s
                else s.copy(
                    importImages = s.importImages + (product.barcode to bytes),
                    editorImage = if (s.editorOffProduct?.barcode == product.barcode) bytes else s.editorImage,
                )
            }
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
                editorBarcode = product.barcode,
                // An OFF product starts in the portion mode: the values follow the portion.
                editorScaleMode = true,
                editorImage = it.importImages[product.barcode],
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
        if (item.estimated) {
            _uiState.update { it.copy(showHandLine = true, handLineKey = key, handLineName = item.name, handLineCarbs = MealMath.formatAmount(item.carbs)) }
            return
        }
        val food = item.foodId?.let { foodsById()[it] }
        val recipe = item.recipeId?.let { recipesById()[it] }
        if (food == null && recipe == null) return showMessage(UiStrings.food_book_line_missing)
        // After a unit change the old number means something else: the user types the quantity again.
        val text = if (item.problem == LineProblem.UNIT_CHANGED) "" else MealMath.formatAmount(item.quantity)
        _uiState.update { it.copy(quantityFood = food, quantityRecipe = recipe, quantityLineKey = key, quantityText = text) }
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

    // ---- A line typed by hand: a name and estimated carbs, without a food (a home-made quiche) ----

    fun openHandLine() = _uiState.update { it.copy(showHandLine = true, handLineKey = null, handLineName = "", handLineCarbs = "") }

    fun updateHandLineName(v: String) = _uiState.update { it.copy(handLineName = v) }

    fun updateHandLineCarbs(v: String) = _uiState.update { it.copy(handLineCarbs = v) }

    fun dismissHandLine() = _uiState.update { it.copy(showHandLine = false, handLineKey = null) }

    /**
     * Adds the line typed by hand to the meal, or changes the line the dialog was opened from. It
     * needs a name and carbs above 0. The carbs are the estimate of the user: they are marked as
     * such in the meal, the history and the export.
     */
    fun confirmHandLine() {
        val state = uiState.value
        val name = state.handLineName.trim()
        val carbs = MealMath.parseAmount(state.handLineCarbs) ?: return
        if (name.isEmpty()) return
        val key = state.handLineKey
        val line = handLine(name, carbs, key ?: nextMealKey++)
        _uiState.update { s ->
            val items = if (key == null) s.mealItems + line else s.mealItems.map { if (it.key == key) line else it }
            s.copy(showHandLine = false, handLineKey = null, mealItems = items)
        }
    }

    /** Fat, protein and energy are not known for a line typed by hand, so the meal shows them as not known. */
    private fun handLine(name: String, carbs: Double, key: Long = nextMealKey++) =
        MealItem(key = key, name = name, quantity = 1.0, unit = "", carbs = carbs, estimated = true)

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

    /** Why the total of [recipe] is not known, or null. See [FoodBookMath.problemOf]. */
    fun problemOf(recipe: Recipe): LineProblem? = FoodBookMath.problemOf(recipe, foodsById(), recipesById())

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
            partial = nutrition.partial,
        )
    }

    /** The lines of a dish or meal as meal lines, or null when one of them cannot be computed. */
    private fun linesOf(recipe: Recipe): List<MealItem>? = recipe.items.map { item -> lineOf(item) ?: return null }

    private fun lineOf(item: RecipeItem): MealItem? =
        item.estimatedCarbs?.let { handLine(item.name, it) }
            ?: item.foodId?.let { id -> foodsById()[id]?.takeUnless { FoodBookMath.unitChanged(item, it) }?.let { foodLine(it, item.quantity) } }
            ?: item.recipeId?.let { id -> recipesById()[id]?.let { recipeLine(it, item.quantity) } }

    /**
     * The lines of a dish or meal to change it. A line that cannot be computed any more is kept,
     * marked with its [LineProblem], so the user sees it and can remove or correct it.
     */
    private fun editLinesOf(recipe: Recipe): List<MealItem> = recipe.items.map { item ->
        lineOf(item) ?: MealItem(
            key = nextMealKey++,
            name = item.name,
            quantity = item.quantity,
            unit = item.unit,
            carbs = 0.0,
            foodId = item.foodId,
            recipeId = item.recipeId,
            problem = if (item.foodId?.let { foodsById()[it] } != null) LineProblem.UNIT_CHANGED else LineProblem.DELETED,
        )
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
        // Always opens, also when a food in it was deleted: this is where it is repaired.
        val lines = editLinesOf(recipe)
        _uiState.update {
            it.copy(
                parkedItems = it.mealItems,
                parkedSource = it.mealSource,
                mealItems = lines,
                mealSource = null,
                editing = recipe,
                editName = recipe.name,
                editNote = recipe.note.orEmpty(),
                editCategory = recipe.category.orEmpty(),
                editSubCategory = recipe.subCategory.orEmpty(),
                editPhoto = recipe.photo?.let { key -> it.recipePhotos[key] },
                editPhotoChanged = false,
                showEditCamera = false,
                showMeal = true,
            )
        }
    }

    fun openEditCamera() = _uiState.update { it.copy(showEditCamera = true) }

    fun closeEditCamera() = _uiState.update { it.copy(showEditCamera = false) }

    /** The photo of the dish or meal being changed. It is saved with the dish. */
    fun takeEditPhoto(bytes: ByteArray) = _uiState.update { it.copy(editPhoto = bytes, editPhotoChanged = true, showEditCamera = false) }

    fun removeEditPhoto() = _uiState.update { it.copy(editPhoto = null, editPhotoChanged = true) }

    fun updateEditName(v: String) = _uiState.update { it.copy(editName = v) }

    fun updateEditNote(v: String) = _uiState.update { it.copy(editNote = v) }

    fun cancelEdit() = endEdit()

    /** Writes the changed dish or meal back, with the same id and kind. */
    fun saveEdit() {
        val state = uiState.value
        val recipe = state.editing ?: return
        val name = state.editName.trim()
        if (name.isEmpty() || state.mealItems.isEmpty()) return
        // A deleted food counts as 0 g: saved like this, the dish would give too few carbs.
        if (state.mealItems.any { it.problem != null }) return showMessage(UiStrings.food_book_remove_missing)
        if (nameUsed(name, recipe.kind, except = recipe.id)) return showMessage(UiStrings.food_book_name_used, name)
        val items = recipeItemsOf(state.mealItems)
        if (FoodBookMath.makesLoop(recipe.id, items, recipesById())) return showMessage(UiStrings.food_book_loop)
        val photoChanged = state.editPhotoChanged
        val newPhoto = state.editPhoto
        val note = state.editNote.trim().ifBlank { null }
        // The same letters as a group already used: the group is not written twice in two ways.
        val category = sameAs(state.editCategory, dishCategorySuggestions())
        val subCategory = category?.let { sameAs(state.editSubCategory, dishSubCategorySuggestions(it)) }
        viewModelScope.launch {
            val photo = if (!photoChanged) recipe.photo else newPhoto?.let { bytes ->
                foodImages.savePhoto(bytes)?.also { key -> _uiState.update { it.copy(recipePhotos = it.recipePhotos + (key to bytes)) } }
            }
            foodBook.saveRecipe(recipe.copy(name = name, items = items, photo = photo, note = note, category = category, subCategory = subCategory))
            // The old file stays while a meal of the history shows it.
            if (photoChanged) recipe.photo?.let { foodImages.deleteUnused(listOf(it)) }
        }
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
            editNote = "",
            editCategory = "",
            editSubCategory = "",
            editPhoto = null,
            editPhotoChanged = false,
            showEditCamera = false,
            showMeal = false,
        )
    }

    private fun recipeItemsOf(items: List<MealItem>): List<RecipeItem> =
        items.map {
            RecipeItem(name = it.name, quantity = it.quantity, unit = it.unit, foodId = it.foodId, recipeId = it.recipeId, estimatedCarbs = if (it.estimated) it.carbs else null)
        }

    private fun nameUsed(name: String, kind: RecipeKind, except: String? = null): Boolean =
        uiState.value.recipes.any { it.kind == kind && it.id != except && it.name.equals(name, ignoreCase = true) }

    fun openSave() = _uiState.update { it.copy(showSave = true, saveName = "", saveKind = it.mealSource?.kind ?: RecipeKind.MEAL, saveEaten = null) }

    /**
     * Opens the save dialog for [meal] of the history, so a meal put together on the spot can be used
     * again without typing it again. The dialog says when the total of today differs from what was
     * eaten, and when a line is kept as typed by hand.
     */
    fun openSaveEaten(meal: EatenMeal) {
        val items = FoodBookMath.recipeItemsOf(meal, foodsById(), uiState.value.recipes, rh.gs(UiStrings.food_book_servings))
        val carbsNow = nutritionOf(Recipe(id = "", name = "", kind = RecipeKind.MEAL, items = items))?.carbs?.roundToInt()
        val typed = items.count { it.estimatedCarbs != null } > meal.items.count { it.estimated }
        _uiState.update {
            it.copy(showSave = true, saveName = meal.name.orEmpty(), saveKind = RecipeKind.MEAL, saveEaten = EatenSave(meal, items, carbsNow, typed))
        }
    }

    fun updateSaveName(v: String) = _uiState.update { it.copy(saveName = v) }
    fun updateSaveKind(kind: RecipeKind) = _uiState.update { it.copy(saveKind = kind) }
    fun dismissSave() = _uiState.update { it.copy(showSave = false, saveEaten = null) }

    /**
     * Saves the meal being put together as a new dish or standard meal. A name already used is
     * refused: an existing one is changed with its pencil, so it is never replaced by mistake.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun confirmSave() {
        val state = uiState.value
        val name = state.saveName.trim()
        state.saveEaten?.let { return saveEatenMeal(it, name, state.saveKind) }
        if (name.isEmpty() || state.mealItems.isEmpty()) return
        if (state.mealItems.any { it.problem != null }) return showMessage(UiStrings.food_book_remove_missing)
        if (nameUsed(name, state.saveKind)) return showMessage(UiStrings.food_book_name_used, name)
        val recipe = Recipe(id = Uuid.random().toString(), name = name, kind = state.saveKind, items = recipeItemsOf(state.mealItems))
        viewModelScope.launch { foodBook.saveRecipe(recipe) }
        _uiState.update { it.copy(showSave = false, mealSource = recipe) }
        showMessage(UiStrings.food_book_saved, name)
    }

    /**
     * Saves a meal of the history as a new dish or standard meal, and gives the meal that name. A name
     * already used is refused, as for any new dish. The note of the meal does not change.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun saveEatenMeal(save: EatenSave, name: String, kind: RecipeKind) {
        if (name.isEmpty() || save.items.isEmpty()) return
        if (nameUsed(name, kind)) return showMessage(UiStrings.food_book_name_used, name)
        val recipe = Recipe(id = Uuid.random().toString(), name = name, kind = kind, items = save.items, photo = save.meal.photo)
        viewModelScope.launch {
            foodBook.saveRecipe(recipe)
            foodBook.nameEatenMeal(save.meal.id, name)
        }
        _uiState.update { it.copy(showSave = false, saveEaten = null) }
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
        // A standard meal whose lines were changed before sending ("croissant instead of bread").
        val changed = state.mealSource?.let { source -> linesOf(source)?.map { lineKey(it) } != state.mealItems.map { lineKey(it) } } ?: false
        val photo = state.mealSource?.let { source -> recipesById()[source.id]?.photo }
        val (id, result) = record(state.mealItems, state.mealSource?.name, target, changed, photo)
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
        return record(lines, recipe.name, target, photo = recipe.photo).second
    }

    /** Returns the id of the saved copy, and the carbs and note for the dialog. */
    @OptIn(ExperimentalUuidApi::class)
    private fun lineKey(item: MealItem) = Triple(item.name, item.quantity, item.unit)

    private fun record(items: List<MealItem>, name: String?, target: MealTarget, changed: Boolean = false, photo: String? = null): Pair<String, Pair<Int, String>> {
        val recipes = recipesById()
        val carbs = MealMath.wizardCarbs(items)
        val note = noteFor(items)
        val meal = EatenMeal(
            id = Uuid.random().toString(),
            timestamp = dateUtil.now(),
            name = name,
            note = note,
            wizardCarbs = carbs,
            items = items.map {
                EatenItem(
                    it.name, it.quantity, it.unit, it.carbs, it.fat, it.protein, it.energyKj, estimated = it.estimated, foodId = it.foodId, recipeId = it.recipeId,
                    photo = it.recipeId?.let { id -> recipes[id]?.photo }, partial = it.partial
                )
            },
            target = target,
            changed = changed,
            photo = photo,
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
                    if (item.estimated) rh.gs(UiStrings.food_meal_estimated) else rh.gs(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit),
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
        // The values mode: every field is free, a new portion changes nothing else.
        if (!s.editorScaleMode) return@update s.copy(editorPortion = v)
        if (product == null) {
            val base = s.editorScaleBase
            if (base == null || newPortion == null) return@update s.copy(editorPortion = v)
            val factor = newPortion / base.portion
            fun scaled(value: Double?) = value?.let { MealMath.formatAmount(it * factor) } ?: ""
            return@update s.copy(
                editorPortion = v,
                editorCarbs = scaled(base.carbs),
                editorFat = scaled(base.fat),
                editorProtein = scaled(base.protein),
                editorEnergy = scaled(base.energy),
            )
        }
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
    /**
     * Portion mode ([on]): only the portion is typed, and the values follow it in proportion, from
     * the values of now. Values mode: every field is typed, as on the package. A food imported from
     * OFF starts in the portion mode, a food changed starts in the values mode.
     */
    fun setScaleMode(on: Boolean) = _uiState.update { s ->
        if (!on) return@update s.copy(editorScaleMode = false, editorScaleBase = null)
        val portion = MealMath.parseAmount(s.editorPortion)
        // Without a portion there is nothing to follow: the values stay free.
        if (portion == null && s.editorOffProduct == null) return@update s
        fun value(text: String) = text.trim().replace(',', '.').toDoubleOrNull()
        s.copy(
            editorScaleMode = true,
            editorScaleBase = portion?.let { ScaleBase(it, value(s.editorCarbs), value(s.editorFat), value(s.editorProtein), value(s.editorEnergy)) },
        )
    }

    /** The unit is shown next to the portion on every card: it is kept short. */
    fun updateEditorUnit(v: String) = _uiState.update { it.copy(editorUnit = v.take(MAX_UNIT_LENGTH)) }
    fun updateEditorCarbs(v: String) = _uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = _uiState.update { it.copy(editorFat = v) }
    fun updateEditorProtein(v: String) = _uiState.update { it.copy(editorProtein = v) }
    fun updateEditorEnergy(v: String) = _uiState.update { it.copy(editorEnergy = v) }
    fun updateEditorNote(v: String) = _uiState.update { it.copy(editorNote = v) }

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
            // Values are typed as on the package ("14.8", "1,7"): they are rounded, not lost.
            carbs = MealMath.parseNutrient(state.editorCarbs) ?: 0,
            fat = MealMath.parseNutrient(state.editorFat),
            protein = MealMath.parseNutrient(state.editorProtein),
            energy = MealMath.parseNutrient(state.editorEnergy),
            // Not editable here, but must survive an edit (a food from Nightscout can have one).
            gi = state.editorFood?.gi,
        )

        val product = state.editorOffProduct
        // A new food typed by hand for a barcode OFF does not know keeps the barcode.
        val barcode = state.editorBarcode.takeIf { product == null && state.editorFood == null }
        val note = state.editorNote.trim().ifBlank { null }
        val noteChanged = note != state.editorFood?.let { state.foodNotes[it.id] }
        val photo = state.editorImage
        val userPhoto = state.editorUserPhoto
        val photoChanged = state.editorPhotoChanged
        val lastId = state.foods.maxOfOrNull { it.id } ?: 0L
        viewModelScope.launch {
            try {
                persistenceLayer.insertOrUpdateFood(food)
                _uiState.update { it.copy(showEditor = false, showPhotoCamera = false) }
                warnUnitChanged(state.editorFood, food)
                if (product == null && barcode == null && !photoChanged && !noteChanged) return@launch
                val foodId = savedFoodId(food, lastId) ?: return@launch
                // The OFF product with its photo, then the photo of the user, which is shown instead.
                if (product != null) foodImages.link(foodId, product, photo)
                else if (barcode != null) foodImages.linkBarcode(foodId, barcode)
                if (noteChanged) foodBook.saveFoodNote(foodId, note)
                if (photoChanged) foodImages.setUserPhoto(foodId, userPhoto)
                val shown = foodImages.saved(listOf(foodId))[foodId]
                _uiState.update { it.copy(foodImages = if (shown != null) it.foodImages + (foodId to shown) else it.foodImages - foodId) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save food", e)
            }
        }
    }

    /**
     * The unit of a food used in dishes or meals changed: their lines are in the old unit. They show
     * that their total is not known until each one is opened and its quantity set again.
     */
    private fun warnUnitChanged(before: FD?, after: FD) {
        if (before == null || !FoodBookMath.unitDiffers(before.unit, after.unit)) return
        val users = uiState.value.recipes.count { recipe -> recipe.items.any { it.foodId == before.id } }
        if (users > 0) showMessage(UiStrings.food_unit_changed_check, users)
    }

    /**
     * The id of a food just saved. The database does not give back the id of a new food, so it is
     * the new food with this name, read after the save.
     */
    private suspend fun savedFoodId(food: FD, lastId: Long): Long? =
        if (food.id != 0L) food.id
        else persistenceLayer.getFoods().filter { it.id > lastId && it.name == food.name }.maxOfOrNull { it.id }

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
            if (recipeIds.isNotEmpty()) {
                val photos = state.recipes.filter { it.id in recipeIds }.mapNotNull { it.photo }
                foodBook.deleteRecipes(recipeIds)
                foodImages.deleteUnused(photos)
            }
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
    val filterCategory: GroupFilter = GroupFilter.All,
    val filterSubCategory: GroupFilter = GroupFilter.All,
    /** False: the newest foods first. True: the names from A to Z. */
    val sortFoodsByName: Boolean = false,
    val dishCategory: GroupFilter = GroupFilter.All,
    val dishSubCategory: GroupFilter = GroupFilter.All,
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
    /** True in the portion mode: only the portion is typed, the values follow it. See [FoodManagementViewModel.setScaleMode]. */
    val editorScaleMode: Boolean = false,
    /** The values the portion mode follows, for a food not filled from OFF. */
    val editorScaleBase: ScaleBase? = null,
    /** The OFF product photo of the editor food, or null when there is none (yet). */
    val editorImage: ByteArray? = null,
    /** The photo taken by the user, shown instead of [editorImage]. Null when there is none. */
    val editorUserPhoto: ByteArray? = null,
    /** True when the user took or removed a photo in the editor: it is saved with the food. */
    val editorPhotoChanged: Boolean = false,
    /** The barcode of the editor food, shown small at the bottom of the editor. Null when it has none. */
    val editorBarcode: String? = null,
    /** The comment of the editor food. It is shown only here, never on the card. */
    val editorNote: String = "",
    val showPhotoCamera: Boolean = false,
    /** The photo shown for each food on the list, by food id: the photo of the user, or else the OFF photo. */
    val foodImages: Map<Long, ByteArray> = emptyMap(),
    /** The photos of the dishes, meals and history, by key. */
    val recipePhotos: Map<String, ByteArray> = emptyMap(),
    // Open Food Facts import (proof of concept)
    val showImport: Boolean = false,
    val importQuery: String = "",
    val importInProgress: Boolean = false,
    val importResult: OffResult? = null,
    /** The photos of the products found, by barcode, added as they are downloaded. */
    val importImages: Map<String, ByteArray> = emptyMap(),
    val showScanner: Boolean = false,
    /** The foods that already have the barcode just scanned or typed. The dialog is shown while not empty. */
    val barcodeOwners: List<FD> = emptyList(),
    /** The barcode of each food that has one, by food id. The POC keeps it in the OFF links of the food book. */
    val foodBarcodes: Map<Long, String> = emptyMap(),
    /** The comment of each food that has one, by food id. The POC keeps it in the food book. */
    val foodNotes: Map<Long, String> = emptyMap(),
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
    /** The meal of the history the save dialog is open for, or null when it saves the meal being put together. */
    val saveEaten: EatenSave? = null,
    /** Eaten meal id to the treatment found for it. */
    val treatmentLinks: Map<String, TreatmentLink> = emptyMap(),
    /** The line of the meal the quantity dialog changes, or null when the dialog adds a new line. */
    val quantityLineKey: Long? = null,
    /** True while the dialog for a line typed by hand (name and estimated carbs) is open. */
    val showHandLine: Boolean = false,
    /** The hand-typed line the dialog changes, or null when the dialog adds a new line. */
    val handLineKey: Long? = null,
    val handLineName: String = "",
    val handLineCarbs: String = "",
    /** The dish or meal being changed (pencil). While set, the meal lines are its lines. */
    val editing: Recipe? = null,
    val editName: String = "",
    /** The comment of the dish or meal being changed. Shown only in its pencil, never on the card. */
    val editNote: String = "",
    /** The category and subcategory of the dish being changed, as typed. Empty: none. */
    val editCategory: String = "",
    val editSubCategory: String = "",
    /** The photo of the dish or meal being changed, and whether the user changed it. */
    val editPhoto: ByteArray? = null,
    val editPhotoChanged: Boolean = false,
    val showEditCamera: Boolean = false,
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

    /** The photo of a line of a meal or a dish: the photo of its food, or of its dish. */
    fun imageOf(item: MealItem): ByteArray? =
        item.foodId?.let { foodImages[it] }
            ?: item.recipeId?.let { id -> recipes.firstOrNull { it.id == id }?.photo }?.let { recipePhotos[it] }

    /** The foods of the list that have [barcode]. The same code with zeros in front counts ("0301..." is "301..."). */
    fun foodsWithBarcode(barcode: String): List<FD> {
        val code = barcode.trimStart('0')
        return foods.filter { food -> foodBarcodes[food.id]?.trimStart('0') == code }
    }
}

/**
 * A meal of the history about to be saved as a dish or a standard meal.
 *
 * @param items the lines of the new dish or meal
 * @param carbsNow its carbs with the foods of today, or null when one line cannot be computed
 * @param keptAsTyped true when a line becomes a line typed by hand with the carbs eaten, because its
 *   food or dish is not in the list any more, or its food is counted in another unit now
 */
@Immutable
data class EatenSave(val meal: EatenMeal, val items: List<RecipeItem>, val carbsNow: Int?, val keptAsTyped: Boolean)

enum class FoodTab { FOODS, DISHES, MEALS, HISTORY }

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** A bolus without the note of the calculation belongs to it only when given this soon after it. */
private const val BOLUS_TIME_WINDOW_MS = 5 * 60 * 1000L


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
