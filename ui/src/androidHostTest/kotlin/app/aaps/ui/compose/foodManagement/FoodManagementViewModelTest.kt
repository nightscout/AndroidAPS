package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class FoodManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: FoodManagementViewModel
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} loadData() launch (no advanceUntilIdle), so construction
        // stays clean and we test the synchronous update methods against the default state. The observeChanges()
        // cold flow is still built synchronously during init, so it must be stubbed non-null.
        Dispatchers.setMain(dispatcher)
        whenever(persistenceLayer.observeChanges(FD::class)).thenReturn(emptyFlow())
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading with empty filters`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.filterCategory).isEqualTo(GroupFilter.All)
        assertThat(state.filterSubCategory).isEqualTo(GroupFilter.All)
        assertThat(state.sortFoodsByName).isFalse()
        assertThat(state.showEditor).isFalse()
    }

    @Test
    fun `setSearchQuery updates the state`() {
        sut.setSearchQuery("apple")

        assertThat(sut.uiState.value.searchQuery).isEqualTo("apple")
    }

    @Test
    fun `setFilterCategory sets category and clears subcategory`() {
        sut.setFilterSubCategory(GroupFilter.Named("Sub"))
        sut.setFilterCategory(GroupFilter.Named("Fruit"))

        val state = sut.uiState.value
        assertThat(state.filterCategory).isEqualTo(GroupFilter.Named("Fruit"))
        assertThat(state.filterSubCategory).isEqualTo(GroupFilter.All)
    }

    @Test
    fun `setFilterSubCategory updates the state`() {
        sut.setFilterSubCategory(GroupFilter.Named("Berries"))

        assertThat(sut.uiState.value.filterSubCategory).isEqualTo(GroupFilter.Named("Berries"))
    }

    @Test
    fun `editor update setters update the state`() {
        sut.updateEditorName("Banana")
        sut.updateEditorCategory("Fruit")
        sut.updateEditorSubCategory("Tropical")
        sut.updateEditorPortion("120")
        sut.updateEditorUnit("g")
        sut.updateEditorCarbs("27")
        sut.updateEditorFat("0")
        sut.updateEditorProtein("1")
        sut.updateEditorEnergy("105")

        val state = sut.uiState.value
        assertThat(state.editorName).isEqualTo("Banana")
        assertThat(state.editorCategory).isEqualTo("Fruit")
        assertThat(state.editorSubCategory).isEqualTo("Tropical")
        assertThat(state.editorPortion).isEqualTo("120")
        assertThat(state.editorUnit).isEqualTo("g")
        assertThat(state.editorCarbs).isEqualTo("27")
        assertThat(state.editorFat).isEqualTo("0")
        assertThat(state.editorProtein).isEqualTo("1")
        assertThat(state.editorEnergy).isEqualTo("105")
    }

    @Test
    fun `openEditor with null resets editor fields to defaults and shows editor`() {
        sut.updateEditorName("stale")
        sut.openEditor(null)

        val state = sut.uiState.value
        assertThat(state.showEditor).isTrue()
        assertThat(state.editorFood).isNull()
        assertThat(state.editorName).isEmpty()
        assertThat(state.editorUnit).isEqualTo("g")
        assertThat(state.editorSaveAttempted).isFalse()
    }

    @Test
    fun `closeEditor hides the editor`() {
        sut.openEditor(null)
        sut.closeEditor()

        assertThat(sut.uiState.value.showEditor).isFalse()
    }

    @Test
    fun `clearUndo clears the undo food`() {
        sut.clearUndo()

        assertThat(sut.uiState.value.undoFood).isNull()
    }

    // ---- Filters "All / None / a group", the sort, and a filter left on an empty group ----

    private val apple = FD(id = 50, name = "apple", category = "Fruit", subCategory = "Fresh", portion = 100.0, unit = "g", carbs = 12)
    private val prune = FD(id = 51, name = "Prune", category = "Fruit", portion = 100.0, unit = "g", carbs = 60)
    private val nut = FD(id = 52, name = "Écrou salé", portion = 100.0, unit = "g", carbs = 1)

    /** A screen that has loaded [foods] (the database gives the newest first). */
    private fun screenWith(vararg foods: FD, changes: Flow<List<FD>> = emptyFlow()): FoodManagementViewModel {
        runBlocking { whenever(persistenceLayer.getFoods()).thenReturn(foods.toList()) }
        whenever(persistenceLayer.observeChanges(FD::class)).thenReturn(changes)
        val vm = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
        dispatcher.scheduler.runCurrent()
        return vm
    }

    /** Keeps [flow] collected, as the screen does: the derived lists are only computed while shown. */
    private fun <T> shown(flow: StateFlow<T>): StateFlow<T> {
        CoroutineScope(dispatcher).launch { flow.collect {} }
        dispatcher.scheduler.runCurrent()
        return flow
    }

    @Test
    fun `the None category shows only the foods without a category`() {
        val vm = screenWith(nut, prune, apple)
        val foods = shown(vm.filteredFoods)

        vm.setFilterCategory(GroupFilter.None)
        dispatcher.scheduler.runCurrent()

        assertThat(foods.value.map { it.name }).containsExactly("Écrou salé")
        assertThat(shown(vm.categoryChoices).value).isEqualTo(GroupChoices(listOf("Fruit"), hasNone = true))
    }

    @Test
    fun `the None subcategory shows the foods of the category without a subcategory`() {
        val vm = screenWith(nut, prune, apple)
        val foods = shown(vm.filteredFoods)
        val subChoices = shown(vm.subCategoryChoices)

        vm.setFilterCategory(GroupFilter.Named("Fruit"))
        dispatcher.scheduler.runCurrent()
        assertThat(subChoices.value).isEqualTo(GroupChoices(listOf("Fresh"), hasNone = true))

        vm.setFilterSubCategory(GroupFilter.None)
        dispatcher.scheduler.runCurrent()
        assertThat(foods.value.map { it.name }).containsExactly("Prune")
    }

    @Test
    fun `the foods are newest first or A to Z, and the choice is kept`() {
        val vm = screenWith(nut, prune, apple)
        val foods = shown(vm.filteredFoods)
        assertThat(foods.value.map { it.name }).containsExactly("Écrou salé", "Prune", "apple").inOrder()

        vm.setSortFoodsByName(true)
        dispatcher.scheduler.runCurrent()

        assertThat(foods.value.map { it.name }).containsExactly("apple", "Écrou salé", "Prune").inOrder()
        verify(preferences).put(BooleanNonKey.FoodSortByName, true)
    }

    @Test
    fun `the sort chosen before is used when the screen opens`() {
        whenever(preferences.get(BooleanNonKey.FoodSortByName)).thenReturn(true)

        val vm = screenWith(nut, prune, apple)

        assertThat(vm.uiState.value.sortFoodsByName).isTrue()
        assertThat(shown(vm.filteredFoods).value.map { it.name }).containsExactly("apple", "Écrou salé", "Prune").inOrder()
    }

    @Test
    fun `a category filter is removed when its last food moves to another category`() {
        val changes = MutableSharedFlow<List<FD>>(extraBufferCapacity = 1)
        val cake = FD(id = 40, name = "Cake", category = "Bakery", subCategory = "Sweet", portion = 100.0, unit = "g", carbs = 50)
        val vm = screenWith(cake, nut, changes = changes)
        vm.setFilterCategory(GroupFilter.Named("Bakery"))
        vm.setFilterSubCategory(GroupFilter.Named("Sweet"))

        runBlocking { whenever(persistenceLayer.getFoods()).thenReturn(listOf(cake.copy(category = "Dessert"), nut)) }
        changes.tryEmit(emptyList())
        dispatcher.scheduler.advanceTimeBy(1_100)
        dispatcher.scheduler.runCurrent()

        assertThat(vm.uiState.value.filterCategory).isEqualTo(GroupFilter.All)
        assertThat(vm.uiState.value.filterSubCategory).isEqualTo(GroupFilter.All)
    }

    @Test
    fun `a category filter stays while it still has foods`() {
        val changes = MutableSharedFlow<List<FD>>(extraBufferCapacity = 1)
        val cake = FD(id = 40, name = "Cake", category = "Bakery", portion = 100.0, unit = "g", carbs = 50)
        val bun = FD(id = 41, name = "Bun", category = "Bakery", portion = 100.0, unit = "g", carbs = 55)
        val vm = screenWith(cake, bun, changes = changes)
        vm.setFilterCategory(GroupFilter.Named("Bakery"))

        runBlocking { whenever(persistenceLayer.getFoods()).thenReturn(listOf(cake.copy(category = "Dessert"), bun)) }
        changes.tryEmit(emptyList())
        dispatcher.scheduler.advanceTimeBy(1_100)
        dispatcher.scheduler.runCurrent()

        assertThat(vm.uiState.value.filterCategory).isEqualTo(GroupFilter.Named("Bakery"))
    }

    // ---- Editor: the groups already used are proposed, and kept with their spelling ----

    @Test
    fun `the categories proposed are the ones used, once each, A to Z`() {
        val vm = screenWith(
            apple, prune, nut,
            FD(id = 53, name = "Bun", category = "Bakery", portion = 100.0, unit = "g", carbs = 55),
            FD(id = 54, name = "Kiwi", category = "fruit ", portion = 100.0, unit = "g", carbs = 10),
            FD(id = 55, name = "Pepper", category = "Épices", portion = 100.0, unit = "g", carbs = 2),
        )

        assertThat(vm.categorySuggestions()).containsExactly("Bakery", "Épices", "Fruit").inOrder()
    }

    @Test
    fun `only the subcategories of the chosen category are proposed`() {
        val vm = screenWith(apple, prune, FD(id = 53, name = "Bun", category = "Bakery", subCategory = "Sweet", portion = 100.0, unit = "g", carbs = 55))

        assertThat(vm.subCategoriesOf(" fruit")).containsExactly("Fresh")
        assertThat(vm.subCategoriesOf("")).isEmpty()
    }

    @Test
    fun `a group typed with other capitals is saved with the spelling already used`() {
        val vm = screenWith(apple)
        vm.openEditor(null)
        vm.updateEditorName("Pear")
        vm.updateEditorCategory("fruit ")
        vm.updateEditorSubCategory("FRESH")
        vm.updateEditorCarbs("10")

        vm.saveFood()
        dispatcher.scheduler.runCurrent()

        val saved = argumentCaptor<FD>()
        runBlocking { verify(persistenceLayer).insertOrUpdateFood(saved.capture()) }
        assertThat(saved.firstValue.category).isEqualTo("Fruit")
        assertThat(saved.firstValue.subCategory).isEqualTo("Fresh")
    }

    @Test
    fun `a new group is saved as typed, and an empty one as no group`() {
        val vm = screenWith(apple)
        vm.openEditor(null)
        vm.updateEditorName("Crisps")
        vm.updateEditorCategory(" Snacks ")
        vm.updateEditorSubCategory(" ")

        vm.saveFood()
        dispatcher.scheduler.runCurrent()

        val saved = argumentCaptor<FD>()
        runBlocking { verify(persistenceLayer).insertOrUpdateFood(saved.capture()) }
        assertThat(saved.firstValue.category).isEqualTo("Snacks")
        assertThat(saved.firstValue.subCategory).isNull()
    }
}
