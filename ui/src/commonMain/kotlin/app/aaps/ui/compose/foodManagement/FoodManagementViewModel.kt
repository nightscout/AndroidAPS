package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class FoodManagementViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
) : ViewModel() {

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

    /** Derived: the choices of the category filter. */
    val categoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.foods.map { it.category }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    /** Derived: the choices of the subcategory filter, for the chosen category. */
    val subCategoryChoices: StateFlow<GroupChoices> = uiState
        .map { s -> GroupChoices.of(s.foods.filter { s.filterCategory.matches(it.category) }.map { it.subCategory }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChoices())

    init {
        loadData()
        observeChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        persistenceLayer.observeChanges<FD>()
            .debounce(1000L)
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val foods = persistenceLayer.getFoods()
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
                    showEditor = true
                )
            }
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false) }
    }

    fun updateEditorName(v: String) = _uiState.update { it.copy(editorName = v) }
    fun updateEditorCategory(v: String) = _uiState.update { it.copy(editorCategory = v) }
    fun updateEditorSubCategory(v: String) = _uiState.update { it.copy(editorSubCategory = v) }
    fun updateEditorPortion(v: String) = _uiState.update { it.copy(editorPortion = v) }
    fun updateEditorUnit(v: String) = _uiState.update { it.copy(editorUnit = v) }
    fun updateEditorCarbs(v: String) = _uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = _uiState.update { it.copy(editorFat = v) }
    fun updateEditorProtein(v: String) = _uiState.update { it.copy(editorProtein = v) }
    fun updateEditorEnergy(v: String) = _uiState.update { it.copy(editorEnergy = v) }

    fun saveFood() {
        _uiState.update { it.copy(editorSaveAttempted = true) }
        val state = uiState.value
        val name = state.editorName.trim()
        if (name.isBlank()) return
        val category = sameAs(state.editorCategory, categorySuggestions())

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
            subCategory = category?.let { sameAs(state.editorSubCategory, subCategoriesOf(it)) },
            portion = state.editorPortion.toDoubleOrNull() ?: 0.0,
            unit = state.editorUnit.trim().ifBlank { "g" },
            carbs = state.editorCarbs.toIntOrNull() ?: 0,
            fat = state.editorFat.toIntOrNull(),
            protein = state.editorProtein.toIntOrNull(),
            energy = state.editorEnergy.toIntOrNull(),
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

    /** The categories already used, A to Z, each once whatever its capitals. */
    fun categorySuggestions(): List<String> = distinctNames(uiState.value.foods.map { it.category })

    /** The subcategories already used with [category] (same letters, any case), A to Z. */
    fun subCategoriesOf(category: String): List<String> {
        val wanted = category.trim()
        if (wanted.isEmpty()) return emptyList()
        return distinctNames(uiState.value.foods.filter { it.category?.trim().equals(wanted, ignoreCase = true) }.map { it.subCategory })
    }

    private fun distinctNames(values: List<String?>): List<String> =
        values.mapNotNull { it?.trim()?.takeIf { v -> v.isNotEmpty() } }.distinctBy { it.lowercase() }.sortedBy { nameSortKey(it) }

    /** [typed] trimmed, written as in [existing] when it is there with other capitals. Null when empty. */
    private fun sameAs(typed: String, existing: List<String>): String? {
        val text = typed.trim()
        if (text.isEmpty()) return null
        return existing.firstOrNull { it.equals(text, ignoreCase = true) } ?: text
    }

    fun deleteFood(food: FD) {
        viewModelScope.launch {
            try {
                persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
                _uiState.update {
                    it.copy(
                        foods = it.foods.filter { f -> f.id != food.id },
                        undoFood = food
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete food", e)
            }
        }
    }

    fun deleteAndCloseEditor() {
        val food = uiState.value.editorFood ?: return
        deleteFood(food)
        closeEditor()
    }

    fun undoDelete() {
        val food = uiState.value.undoFood ?: return
        viewModelScope.launch {
            try {
                persistenceLayer.insertOrUpdateFood(food.copy(isValid = true))
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to undo food delete", e)
            } finally {
                _uiState.update { it.copy(undoFood = null) }
            }
        }
    }

    fun clearUndo() {
        _uiState.update { it.copy(undoFood = null) }
    }
}

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
    // Undo
    val undoFood: FD? = null,
)
