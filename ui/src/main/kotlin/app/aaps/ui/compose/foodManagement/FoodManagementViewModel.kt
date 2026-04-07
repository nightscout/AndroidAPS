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
import app.aaps.core.interfaces.logging.LTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoodManagementViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
) : ViewModel() {

    val uiState: StateFlow<FoodManagementUiState> field = MutableStateFlow(FoodManagementUiState())

    /** Derived: foods filtered by current search query and category/subcategory filters */
    val filteredFoods: StateFlow<List<FD>> = uiState
        .map { s ->
            s.foods.filter { food ->
                (s.searchQuery.isBlank() || food.name.contains(s.searchQuery, ignoreCase = true))
                    && (s.filterCategory == null || food.category == s.filterCategory)
                    && (s.filterSubCategory == null || food.subCategory == s.filterSubCategory)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Derived: subcategories available for the currently selected category filter */
    val filteredSubCategories: StateFlow<List<String>> = uiState
        .map { s ->
            val foods = s.foods
            val cat = s.filterCategory
            val relevant = if (cat != null) foods.filter { it.category == cat } else foods
            relevant.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadData()
        observeChanges()
    }

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
                val categories = foods.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val subCategories = foods.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
                uiState.update {
                    it.copy(
                        foods = foods,
                        allCategories = categories,
                        allSubCategories = subCategories,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load foods", e)
                uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterCategory(category: String?) {
        uiState.update { it.copy(filterCategory = category, filterSubCategory = null) }
    }

    fun setFilterSubCategory(subCategory: String?) {
        uiState.update { it.copy(filterSubCategory = subCategory) }
    }

    fun openEditor(food: FD? = null) {
        if (food != null) {
            uiState.update {
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
            uiState.update {
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
        uiState.update { it.copy(showEditor = false) }
    }

    fun updateEditorName(v: String) = uiState.update { it.copy(editorName = v) }
    fun updateEditorCategory(v: String) = uiState.update { it.copy(editorCategory = v) }
    fun updateEditorSubCategory(v: String) = uiState.update { it.copy(editorSubCategory = v) }
    fun updateEditorPortion(v: String) = uiState.update { it.copy(editorPortion = v) }
    fun updateEditorUnit(v: String) = uiState.update { it.copy(editorUnit = v) }
    fun updateEditorCarbs(v: String) = uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = uiState.update { it.copy(editorFat = v) }
    fun updateEditorProtein(v: String) = uiState.update { it.copy(editorProtein = v) }
    fun updateEditorEnergy(v: String) = uiState.update { it.copy(editorEnergy = v) }

    fun saveFood() {
        uiState.update { it.copy(editorSaveAttempted = true) }
        val state = uiState.value
        val name = state.editorName.trim()
        if (name.isBlank()) return

        val food = FD(
            id = state.editorFood?.id ?: 0,
            version = state.editorFood?.version ?: 0,
            dateCreated = state.editorFood?.dateCreated ?: -1,
            isValid = true,
            referenceId = state.editorFood?.referenceId,
            ids = state.editorFood?.ids ?: IDs(),
            name = name,
            category = state.editorCategory.trim().ifBlank { null },
            subCategory = state.editorSubCategory.trim().ifBlank { null },
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
                uiState.update { it.copy(showEditor = false) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save food", e)
            }
        }
    }

    fun deleteFood(food: FD) {
        viewModelScope.launch {
            try {
                persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
                uiState.update {
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
                uiState.update { it.copy(undoFood = null) }
            }
        }
    }

    fun clearUndo() {
        uiState.update { it.copy(undoFood = null) }
    }
}

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
    // Undo
    val undoFood: FD? = null,
)
