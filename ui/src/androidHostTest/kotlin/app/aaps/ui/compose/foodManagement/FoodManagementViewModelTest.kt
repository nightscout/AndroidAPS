package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.FD
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.compose.foodManagement.book.EatenMeal
import app.aaps.ui.compose.foodManagement.book.ExportFile
import app.aaps.ui.compose.foodManagement.book.FoodBackupMath
import app.aaps.ui.compose.foodManagement.book.FoodBook
import app.aaps.ui.compose.foodManagement.book.FoodBookRepository
import app.aaps.ui.compose.foodManagement.book.FoodCsv
import app.aaps.ui.compose.foodManagement.book.FoodImages
import app.aaps.ui.compose.foodManagement.book.MealTarget
import app.aaps.ui.compose.foodManagement.book.MemoryFoodBookStore
import app.aaps.ui.compose.foodManagement.book.NoFoodExportFiles
import app.aaps.ui.compose.foodManagement.book.NoFoodImageStore
import app.aaps.ui.compose.foodManagement.book.Recipe
import app.aaps.ui.compose.foodManagement.book.RecipeItem
import app.aaps.ui.compose.foodManagement.book.RecipeKind
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import app.aaps.ui.compose.foodManagement.openfoodfacts.OpenFoodFactsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class FoodManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var openFoodFacts: OpenFoodFactsRepository
    @Mock private lateinit var rh: TextResolver
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter

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
        whenever(persistenceLayer.observeChanges(BCR::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.observeChanges(CA::class)).thenReturn(emptyFlow())
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, openFoodFacts, rh, FoodBookRepository(MemoryFoodBookStore(), aapsLogger), dateUtil, decimalFormatter, FoodCsv(rh, dateUtil, decimalFormatter, NoFoodExportFiles()), NoFoodExportFiles(), FoodImages(NoFoodImageStore(), openFoodFacts, FoodBookRepository(MemoryFoodBookStore(), aapsLogger), aapsLogger))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading with empty filters`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.filterCategory).isNull()
        assertThat(state.filterSubCategory).isNull()
        assertThat(state.showEditor).isFalse()
    }

    @Test
    fun `setSearchQuery updates the state`() {
        sut.setSearchQuery("apple")

        assertThat(sut.uiState.value.searchQuery).isEqualTo("apple")
    }

    @Test
    fun `setFilterCategory sets category and clears subcategory`() {
        sut.setFilterSubCategory("Sub")
        sut.setFilterCategory("Fruit")

        val state = sut.uiState.value
        assertThat(state.filterCategory).isEqualTo("Fruit")
        assertThat(state.filterSubCategory).isNull()
    }

    @Test
    fun `setFilterSubCategory updates the state`() {
        sut.setFilterSubCategory("Berries")

        assertThat(sut.uiState.value.filterSubCategory).isEqualTo("Berries")
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

    // ---- Meal (proof of concept) ----

    private val bread = FD(id = 1, name = "Bread", portion = 100.0, carbs = 50)

    @Test
    fun `askQuantity proposes the food portion`() {
        sut.askQuantity(bread)

        assertThat(sut.uiState.value.quantityFood).isEqualTo(bread)
        assertThat(sut.uiState.value.quantityText).isEqualTo("100")
    }

    @Test
    fun `confirmQuantity adds the food with carbs for that quantity`() {
        sut.askQuantity(bread)
        sut.updateQuantity("80")
        sut.confirmQuantity()

        val state = sut.uiState.value
        assertThat(state.quantityFood).isNull()
        val item = state.mealItems.single()
        assertThat(item.name).isEqualTo("Bread")
        assertThat(item.quantity).isEqualTo(80.0)
        assertThat(item.unit).isEqualTo("g")
        assertThat(item.carbs).isWithin(0.0001).of(40.0)
    }

    @Test
    fun `an invalid quantity keeps the dialog open and adds nothing`() {
        sut.askQuantity(bread)
        sut.updateQuantity("0")
        sut.confirmQuantity()

        assertThat(sut.uiState.value.quantityFood).isEqualTo(bread)
        assertThat(sut.uiState.value.mealItems).isEmpty()
    }

    @Test
    fun `the same food can be added twice and removed one at a time`() {
        repeat(2) { sut.askQuantity(bread); sut.confirmQuantity() }
        val items = sut.uiState.value.mealItems
        assertThat(items.map { it.key }.distinct()).hasSize(2)

        sut.removeMealItem(items.first().key)

        assertThat(sut.uiState.value.mealItems.map { it.key }).containsExactly(items.last().key)
    }

    @Test
    fun `removing the last item closes the meal sheet`() {
        sut.askQuantity(bread); sut.confirmQuantity()
        sut.openMeal()
        assertThat(sut.uiState.value.showMeal).isTrue()

        sut.removeMealItem(sut.uiState.value.mealItems.single().key)

        assertThat(sut.uiState.value.showMeal).isFalse()
    }

    @Test
    fun `the meal can be saved as a dish, and a line keeps its food`() {
        sut.askQuantity(bread); sut.confirmQuantity()
        sut.openSave()
        assertThat(sut.uiState.value.showSave).isTrue()
        assertThat(sut.uiState.value.saveKind).isEqualTo(RecipeKind.MEAL) // the default

        sut.updateSaveName(" Toast ")
        sut.updateSaveKind(RecipeKind.DISH)
        sut.confirmSave()

        val state = sut.uiState.value
        assertThat(state.showSave).isFalse()
        val saved = state.mealSource!!
        assertThat(saved.name).isEqualTo("Toast")
        assertThat(saved.kind).isEqualTo(RecipeKind.DISH)
        assertThat(saved.items.single().foodId).isEqualTo(1L)
        assertThat(saved.items.single().quantity).isEqualTo(100.0)
    }

    @Test
    fun `a meal without a name is not saved`() {
        sut.askQuantity(bread); sut.confirmQuantity()
        sut.openSave()
        sut.updateSaveName("  ")
        sut.confirmSave()

        assertThat(sut.uiState.value.showSave).isTrue()
        assertThat(sut.uiState.value.mealSource).isNull()
    }

    @Test
    fun `clearing the meal forgets where it came from`() {
        sut.askQuantity(bread); sut.confirmQuantity()
        sut.openSave(); sut.updateSaveName("Toast"); sut.confirmSave()

        sut.clearMeal()

        assertThat(sut.uiState.value.mealSource).isNull()
    }

    @Test
    fun `a meal line carries the food nutrition for its quantity`() {
        val milk = FD(id = 2, name = "Milk", portion = 250.0, unit = "ml", carbs = 12, fat = 4, protein = 8, energy = 600)
        sut.askQuantity(milk); sut.updateQuantity("125"); sut.confirmQuantity()

        val item = sut.uiState.value.mealItems.single()
        assertThat(item.foodId).isEqualTo(2L)
        assertThat(item.fat!!).isWithin(0.0001).of(2.0)
        assertThat(item.energyKj!!).isWithin(0.0001).of(300.0)
    }

    /** Loads [foods] into the screen, as the database would. */
    private fun loadFoods(vararg foods: FD) {
        runBlocking { whenever(persistenceLayer.getFoods()).thenReturn(foods.toList()) }
        // Only loadData() runs here: the food book waits on the IO dispatcher, which is not advanced.
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `tapping a meal line changes its quantity in place`() {
        loadFoods(bread)
        val milk = FD(id = 2, name = "Milk", portion = 250.0, unit = "ml", carbs = 12)
        sut.askQuantity(bread); sut.updateQuantity("80"); sut.confirmQuantity()
        sut.askQuantity(milk); sut.confirmQuantity()
        val key = sut.uiState.value.mealItems.first().key

        sut.editLine(key)
        assertThat(sut.uiState.value.quantityFood).isEqualTo(bread)
        assertThat(sut.uiState.value.quantityText).isEqualTo("80")
        sut.updateQuantity("60")
        sut.confirmQuantity()

        val items = sut.uiState.value.mealItems
        assertThat(items).hasSize(2)
        assertThat(items.first().key).isEqualTo(key)
        assertThat(items.first().quantity).isEqualTo(60.0)
        assertThat(items.first().carbs).isWithin(0.0001).of(30.0)
        assertThat(sut.uiState.value.quantityLineKey).isNull()
    }

    private val toast = Recipe(id = "toast", name = "Toast", kind = RecipeKind.DISH, items = listOf(RecipeItem("Bread", 30.0, "g", foodId = 1)))

    @Test
    fun `changing a dish puts the meal aside and cancel brings it back`() {
        loadFoods(bread)
        sut.askQuantity(bread); sut.updateQuantity("80"); sut.confirmQuantity()

        sut.startEdit(toast)
        var state = sut.uiState.value
        assertThat(state.editing).isEqualTo(toast)
        assertThat(state.showMeal).isTrue()
        assertThat(state.mealItems.single().quantity).isEqualTo(30.0)

        // Removing the last line is a step of the change: the sheet stays open.
        sut.removeMealItem(state.mealItems.single().key)
        assertThat(sut.uiState.value.showMeal).isTrue()

        sut.cancelEdit()
        state = sut.uiState.value
        assertThat(state.editing).isNull()
        assertThat(state.mealItems.single().quantity).isEqualTo(80.0)
    }

    @Test
    fun `saving a changed dish keeps its id and kind and brings the meal back`() {
        loadFoods(bread)
        sut.askQuantity(bread); sut.updateQuantity("80"); sut.confirmQuantity()
        sut.startEdit(toast)
        sut.editLine(sut.uiState.value.mealItems.single().key); sut.updateQuantity("40"); sut.confirmQuantity()

        sut.updateEditName(" ")
        sut.saveEdit()
        assertThat(sut.uiState.value.editing).isEqualTo(toast) // no name: not saved

        sut.updateEditName("Big toast")
        sut.saveEdit()
        assertThat(sut.uiState.value.editing).isNull()
        assertThat(sut.uiState.value.mealItems.single().quantity).isEqualTo(80.0)
    }

    @Test
    fun `sending the meal waits for its treatment and keeps the meal`() {
        sut.askQuantity(bread); sut.updateQuantity("80"); sut.confirmQuantity()
        sut.openMeal()

        val (carbs, _) = sut.send(MealTarget.CARBS)

        val state = sut.uiState.value
        assertThat(carbs).isEqualTo(40)
        assertThat(state.pendingMealId).isNotNull()
        assertThat(state.showMeal).isFalse()
        assertThat(state.mealItems).hasSize(1) // cleared only when the treatment is found
        assertThat(state.confirmedMeals).isEmpty()
    }

    // ---- Delete with a long press, categories, unit ----

    /** A screen on [book], with [foods] in the database. Runs the start of the screen. */
    private fun screenWith(book: FoodBookRepository, vararg foods: FD): FoodManagementViewModel {
        runBlocking { whenever(persistenceLayer.getFoods()).thenReturn(foods.toList()) }
        val vm = FoodManagementViewModel(persistenceLayer, aapsLogger, openFoodFacts, rh, book, dateUtil, decimalFormatter, FoodCsv(rh, dateUtil, decimalFormatter, NoFoodExportFiles()), NoFoodExportFiles(), FoodImages(NoFoodImageStore(), openFoodFacts, FoodBookRepository(MemoryFoodBookStore(), aapsLogger), aapsLogger))
        dispatcher.scheduler.runCurrent()
        return vm
    }

    /** Runs the screen until [done]: the food book writes its file on the real IO dispatcher. */
    private fun runUntil(done: () -> Boolean) {
        repeat(200) {
            dispatcher.scheduler.runCurrent()
            if (done()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a long press selects, a tap toggles, another tab ends the selection`() {
        val milk = FD(id = 2, name = "Milk", portion = 250.0, unit = "ml", carbs = 12)

        sut.startSelection(bread)
        sut.toggleSelection(milk)
        assertThat(sut.uiState.value.selecting).isTrue()
        assertThat(sut.uiState.value.selectedFoodIds).containsExactly(1L, 2L)

        sut.toggleSelection(bread)
        assertThat(sut.uiState.value.selectedFoodIds).containsExactly(2L)

        sut.selectTab(FoodTab.DISHES)
        assertThat(sut.uiState.value.selecting).isFalse()
        assertThat(sut.uiState.value.selectedFoodIds).isEmpty()
    }

    @Test
    fun `no selection while a dish is changed`() {
        loadFoods(bread)
        sut.startEdit(toast)

        sut.startSelection(bread)

        assertThat(sut.uiState.value.selecting).isFalse()
    }

    @Test
    fun `the delete dialog names the dishes that use a deleted food`() {
        val book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        runBlocking { book.saveRecipe(toast) }
        val vm = screenWith(book, bread)

        vm.startSelection(bread)
        vm.requestDelete()

        assertThat(vm.uiState.value.showDeleteConfirm).isTrue()
        val summary = vm.deleteSummary()
        assertThat(summary.names).containsExactly("Bread")
        assertThat(summary.usedIn).containsExactly("Bread" to listOf("Toast"))

        // A dish deleted with the food it uses gives no warning: nothing that stays uses the food.
        vm.toggleSelection(toast)
        assertThat(vm.deleteSummary().usedIn).isEmpty()
    }

    @Test
    fun `confirming deletes the selected foods and dishes`() {
        val book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        runBlocking { book.saveRecipe(toast) }
        val vm = screenWith(book, bread)
        vm.startSelection(bread)
        vm.toggleSelection(toast)

        vm.confirmDelete()
        runUntil { book.book.value.recipes.isEmpty() }

        assertThat(book.book.value.recipes).isEmpty()
        runBlocking { verify(persistenceLayer).invalidateFood(eq(1L), eq(Action.FOOD_REMOVED), eq(Sources.Food)) }
        assertThat(vm.uiState.value.foods).isEmpty()
        assertThat(vm.uiState.value.selecting).isFalse()
    }

    @Test
    fun `nothing selected means no delete dialog`() {
        sut.startSelection(bread)
        sut.toggleSelection(bread)

        sut.requestDelete()

        assertThat(sut.uiState.value.showDeleteConfirm).isFalse()
    }

    @Test
    fun `a category typed with other capitals is saved as the existing one`() {
        val apple = FD(id = 3, name = "Apple", portion = 100.0, carbs = 12, category = "Fruits", subCategory = "Fresh")
        loadFoods(apple)
        sut.openEditor()
        sut.updateEditorName("Pear")
        sut.updateEditorCategory(" fruits ")
        sut.updateEditorSubCategory("FRESH")
        sut.updateEditorCarbs("10")

        sut.saveFood()
        dispatcher.scheduler.runCurrent()

        val saved = argumentCaptor<FD>()
        runBlocking { verify(persistenceLayer).insertOrUpdateFood(saved.capture()) }
        assertThat(saved.firstValue.category).isEqualTo("Fruits")
        assertThat(saved.firstValue.subCategory).isEqualTo("Fresh")
    }

    @Test
    fun `decimal values typed from the package are saved rounded, not as zero`() {
        loadFoods()
        sut.openEditor()
        sut.updateEditorName("Biscuit")
        sut.updateEditorPortion("30")
        sut.updateEditorCarbs("14.8")
        sut.updateEditorFat("1,7")
        sut.updateEditorProtein("0.4")
        sut.updateEditorEnergy("520.6")

        sut.saveFood()
        dispatcher.scheduler.runCurrent()

        val saved = argumentCaptor<FD>()
        runBlocking { verify(persistenceLayer).insertOrUpdateFood(saved.capture()) }
        assertThat(saved.firstValue.carbs).isEqualTo(15)
        assertThat(saved.firstValue.fat).isEqualTo(2)
        assertThat(saved.firstValue.protein).isEqualTo(0)
        assertThat(saved.firstValue.energy).isEqualTo(521)
    }

    @Test
    fun `only the subcategories of the chosen category are proposed`() {
        loadFoods(
            FD(id = 3, name = "Apple", portion = 100.0, carbs = 12, category = "Fruits", subCategory = "Fresh"),
            FD(id = 4, name = "Raisins", portion = 100.0, carbs = 70, category = "fruits", subCategory = "Dried"),
            FD(id = 5, name = "Bean", portion = 100.0, carbs = 5, category = "Vegetables", subCategory = "Green"),
        )

        assertThat(sut.subCategoriesOf("FRUITS")).containsExactly("Dried", "Fresh").inOrder()
        assertThat(sut.subCategoriesOf("")).isEmpty()
    }

    @Test
    fun `a long unit is cut`() {
        sut.updateEditorUnit("x".repeat(MAX_UNIT_LENGTH + 5))

        assertThat(sut.uiState.value.editorUnit).hasLength(MAX_UNIT_LENGTH)
    }

    // ---- The history follows the treatments ----

    private fun eaten(id: String, target: MealTarget, treatmentId: Long?, validatedAt: Long?) =
        EatenMeal(id = id, timestamp = 1_000_000, name = null, note = "Bread 80 g (40 g)", wizardCarbs = 40, items = emptyList(), target = target, validatedAt = validatedAt, treatmentId = treatmentId)

    @Test
    fun `a meal whose treatment was deleted is removed, the others stay`() {
        val book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        runBlocking {
            book.addEatenMeal(eaten("deleted", MealTarget.CARBS, treatmentId = 10, validatedAt = 1_000_000))
            book.addEatenMeal(eaten("kept", MealTarget.CARBS, treatmentId = 11, validatedAt = 1_000_000))
            book.addEatenMeal(eaten("not found", MealTarget.CARBS, treatmentId = 12, validatedAt = 1_000_000))
            whenever(persistenceLayer.getCarbsById(10)).thenReturn(CA(id = 10, timestamp = 1_000_000, duration = 0, amount = 40.0, isValid = false))
            whenever(persistenceLayer.getCarbsById(11)).thenReturn(CA(id = 11, timestamp = 1_000_000, duration = 0, amount = 40.0, notes = "Bread 80 g (40 g)"))
        }
        whenever(dateUtil.now()).thenReturn(2_000_000)

        val vm = screenWith(book)
        runUntil { book.book.value.eaten.size == 2 && vm.uiState.value.treatmentLinks.isNotEmpty() }

        // Deleted in the treatments history: gone. Not found at all: kept, nothing is removed on a guess.
        assertThat(book.book.value.eaten.map { it.id }).containsExactly("kept", "not found")
        assertThat(vm.uiState.value.treatmentLinks.keys).containsExactly("kept")
        assertThat(vm.uiState.value.treatmentLinks.getValue("kept").sameNote).isTrue()
    }

    @Test
    fun `a meal keeps the id of the treatment found for it`() {
        val book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        runBlocking {
            book.addEatenMeal(eaten("waiting", MealTarget.CARBS, treatmentId = null, validatedAt = null))
            whenever(persistenceLayer.getBolusCalculatorResultsFromTime(any(), eq(true))).thenReturn(emptyList())
            whenever(persistenceLayer.getCarbsFromTime(any(), eq(true)))
                .thenReturn(listOf(CA(id = 7, dateCreated = 1_010_000, timestamp = 900_000, duration = 0, amount = 40.0, notes = "Bread 80 g (40 g)")))
        }
        whenever(dateUtil.now()).thenReturn(1_020_000)

        screenWith(book)
        runUntil { book.book.value.eaten.single().treatmentId != null }

        val meal = book.book.value.eaten.single()
        assertThat(meal.treatmentId).isEqualTo(7L)
        assertThat(meal.validatedAt).isEqualTo(900_000L)
    }

    // ---- Backup and restore (proof of concept, personal use) ----

    @Test
    fun `restore adds the missing food, points the dish to it and checks the treatment ids`() {
        val yogurt = FD(id = 30, name = "Yogurt", portion = 1.0, unit = "pot", carbs = 6)
        val dish = Recipe("dish", "Sweet yogurt", RecipeKind.DISH, listOf(RecipeItem("Yogurt", 1.0, "pot", foodId = 30)))
        val text = FoodBackupMath.encode(
            listOf(bread, yogurt),
            FoodBook(
                recipes = listOf(dish),
                // "same" points to the same carbs entry here; "other" to a record of another meal.
                eaten = listOf(eaten("same", MealTarget.CARBS, treatmentId = 7, validatedAt = 900_000), eaten("other", MealTarget.CARBS, treatmentId = 8, validatedAt = 950_000)),
            ),
            now = 0
        )
        val files = object : NoFoodExportFiles() {
            override val available = true
            override fun read(file: ExportFile): String = text
        }
        // Only bread is in this database. A new food gets the next id.
        val inDb = mutableListOf(bread)
        runBlocking {
            whenever(persistenceLayer.getFoods()).thenAnswer { inDb.toList() }
            whenever(persistenceLayer.insertOrUpdateFood(any())).thenAnswer {
                inDb += (it.arguments[0] as FD).copy(id = 100)
                PersistenceLayer.TransactionResult<FD>()
            }
            whenever(persistenceLayer.getCarbsById(7)).thenReturn(CA(id = 7, timestamp = 900_000, duration = 0, amount = 40.0))
            whenever(persistenceLayer.getCarbsById(8)).thenReturn(CA(id = 8, timestamp = 123, duration = 0, amount = 10.0))
            whenever(persistenceLayer.getBolusCalculatorResultsFromTime(any(), eq(true))).thenReturn(emptyList())
            whenever(persistenceLayer.getCarbsFromTime(any(), eq(true))).thenReturn(emptyList())
        }
        whenever(dateUtil.now()).thenReturn(1_000_000)
        val book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        val vm = FoodManagementViewModel(persistenceLayer, aapsLogger, openFoodFacts, rh, book, dateUtil, decimalFormatter, FoodCsv(rh, dateUtil, decimalFormatter, files), files, FoodImages(NoFoodImageStore(), openFoodFacts, FoodBookRepository(MemoryFoodBookStore(), aapsLogger), aapsLogger))
        dispatcher.scheduler.runCurrent()

        vm.restore(ExportFile("backup", "ref"))
        runUntil { book.book.value.eaten.size == 2 }

        assertThat(inDb.map { it.name }).containsExactly("Bread", "Yogurt")
        assertThat(book.book.value.recipes.single().items.single().foodId).isEqualTo(100L)
        val meals = book.book.value.eaten.associateBy { it.id }
        assertThat(meals.getValue("same").treatmentId).isEqualTo(7L)
        assertThat(meals.getValue("other").treatmentId).isNull()
        assertThat(meals.getValue("other").validatedAt).isEqualTo(950_000L)
    }

    @Test
    fun `an empty meal cannot be opened`() {
        sut.openMeal()

        assertThat(sut.uiState.value.showMeal).isFalse()
    }

    // ---- Open Food Facts import (proof of concept) ----

    @Test
    fun `selecting an OFF product fills a new food in the editor without saving it`() {
        val product = OffProduct(
            barcode = "123", name = "Milk", brand = null, carbsPer100 = 4.7, sugarsPer100 = 4.7, fiberPer100 = 0.0,
            fatPer100 = 1.6, proteinPer100 = 3.3, energyKjPer100 = 197.0, isLiquid = true
        )
        sut.openImport()

        sut.selectOffProduct(product)

        val state = sut.uiState.value
        assertThat(state.showImport).isFalse()
        assertThat(state.showEditor).isTrue()
        assertThat(state.editorFood).isNull() // a new food, nothing saved yet
        assertThat(state.editorName).isEqualTo("Milk")
        assertThat(state.editorPortion).isEqualTo("100")
        assertThat(state.editorUnit).isEqualTo("ml")
        assertThat(state.editorCarbs).isEqualTo("5")
        assertThat(state.editorCategory).isEqualTo(OFF_CATEGORY)
        assertThat(state.editorOffProduct).isEqualTo(product)
    }

    @Test
    fun `opening the editor by hand clears the OFF banner`() {
        val product = OffProduct(
            barcode = "123", name = "Milk", brand = null, carbsPer100 = null, sugarsPer100 = null, fiberPer100 = null,
            fatPer100 = null, proteinPer100 = null, energyKjPer100 = null, isLiquid = false
        )
        sut.selectOffProduct(product)
        assertThat(sut.uiState.value.editorCarbs).isEmpty() // missing carbs stay empty, not 0

        sut.openEditor()

        assertThat(sut.uiState.value.editorOffProduct).isNull()
    }

    private val yogurt = OffProduct(
        barcode = "1", name = "Yaourt", brand = null, carbsPer100 = 8.8, sugarsPer100 = null, fiberPer100 = null,
        fatPer100 = 3.2, proteinPer100 = 4.0, energyKjPer100 = 300.0, isLiquid = false,
        servingSize = "1 pot (125 g)", servingQuantity = 125.0
    )

    @Test
    fun `an OFF product with a serving opens on that serving`() {
        sut.selectOffProduct(yogurt)

        val state = sut.uiState.value
        assertThat(state.editorPortion).isEqualTo("1")
        assertThat(state.editorUnit).isEqualTo("pot (125 g)")
        assertThat(state.editorCarbs).isEqualTo("11")
        assertThat(state.editorOffPerServing).isTrue()
    }

    @Test
    fun `the user can switch between the serving and 100 g`() {
        sut.selectOffProduct(yogurt)
        sut.setOffPerServing(false)
        assertThat(sut.uiState.value.editorPortion).isEqualTo("100")
        assertThat(sut.uiState.value.editorUnit).isEqualTo("g")
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("9")
        assertThat(sut.uiState.value.editorOffPerServing).isFalse()

        sut.setOffPerServing(true)
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("11")
    }

    @Test
    fun `changing the portion of an OFF food recalculates the values`() {
        sut.selectOffProduct(yogurt)
        sut.setOffPerServing(false)

        sut.updateEditorPortion("125")

        assertThat(sut.uiState.value.editorCarbs).isEqualTo("11")
        assertThat(sut.uiState.value.editorEnergy).isEqualTo("375")
    }

    @Test
    fun `a value typed by the user is kept when the portion changes`() {
        sut.selectOffProduct(yogurt)
        sut.updateEditorCarbs("12")

        sut.updateEditorPortion("2")

        assertThat(sut.uiState.value.editorCarbs).isEqualTo("12") // typed, kept
        assertThat(sut.uiState.value.editorFat).isEqualTo("8")    // computed, follows: 3.2 x 2.5
    }

    @Test
    fun `clearing the portion and typing a new one still recalculates`() {
        sut.selectOffProduct(yogurt)
        sut.updateEditorPortion("")
        sut.updateEditorPortion("2")

        assertThat(sut.uiState.value.editorCarbs).isEqualTo("22")
    }

    @Test
    fun `a food typed by hand is never recalculated`() {
        sut.openEditor()
        sut.updateEditorPortion("100")
        sut.updateEditorCarbs("50")

        sut.updateEditorPortion("200")

        assertThat(sut.uiState.value.editorCarbs).isEqualTo("50")
    }

    @Test
    fun `a barcode read while the camera is closed is ignored`() {
        sut.onBarcodeScanned("3017620422003")

        assertThat(sut.uiState.value.importQuery).isEmpty()
    }
}
