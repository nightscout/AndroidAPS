package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Where the food POC keeps its text file. One file, read once and written after each change.
 *
 * PROOF OF CONCEPT: on Android this is a private file of the app. It is not in the settings export,
 * not in the logs, and not sent to Nightscout.
 */
interface FoodBookStore {

    /** The saved text, or null when nothing was saved yet. */
    fun read(): String?

    fun write(text: String)
}

/**
 * Dishes, standard meals and eaten meals of the food POC.
 *
 * PROOF OF CONCEPT. Kept apart from the AAPS database on purpose: the database version does not
 * change, and deleting the file removes the POC data and nothing else.
 */
@SingleIn(AppScope::class)
@Inject
class FoodBookRepository(
    private val store: FoodBookStore,
    private val aapsLogger: AAPSLogger,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false

    private val _book = MutableStateFlow(FoodBook())
    val book: StateFlow<FoodBook> = _book.asStateFlow()

    /** Reads the file once. A file that cannot be read is logged and replaced by an empty book. */
    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val text = withContext(aapsIoDispatcher) { runCatching { store.read() }.getOrNull() }
        _book.value = text?.let {
            runCatching { json.decodeFromString(FoodBook.serializer(), it) }
                .onFailure { e -> aapsLogger.error(LTag.UI, "Food POC: cannot read the saved dishes and meals", e) }
                .getOrNull()
        } ?: FoodBook()
        loaded = true
    }

    /** Adds [recipe], or replaces the recipe with the same id. */
    suspend fun saveRecipe(recipe: Recipe) = change { book ->
        val others = book.recipes.filterNot { it.id == recipe.id }
        book.copy(recipes = (others + recipe).sortedBy { it.name.lowercase() })
    }

    suspend fun deleteRecipes(ids: Set<String>) = change { book -> book.copy(recipes = book.recipes.filterNot { it.id in ids }) }

    suspend fun addEatenMeal(meal: EatenMeal) = change { book -> book.copy(eaten = listOf(meal) + book.eaten) }

    /** Adds what a restore found missing. Nothing already there is changed. */
    suspend fun addRestored(merge: BackupMerge) = change { book ->
        book.copy(
            recipes = (book.recipes + merge.recipes).sortedBy { it.name.lowercase() },
            eaten = (book.eaten + merge.meals).sortedByDescending { it.timestamp },
        )
    }

    /**
     * Ties the meals of [linked] (meal id to the treatment found for it) to their treatment, and
     * removes the meals of [removed].
     */
    suspend fun updateEatenMeals(linked: Map<String, TreatmentLink>, removed: Set<String>) = change { book ->
        book.copy(eaten = book.eaten.filterNot { it.id in removed }.map { meal ->
            linked[meal.id]?.let { meal.copy(validatedAt = meal.validatedAt ?: it.timestamp, treatmentId = it.id) } ?: meal
        })
    }

    private suspend fun change(block: (FoodBook) -> FoodBook) {
        load()
        mutex.withLock {
            _book.update(block)
            val text = json.encodeToString(FoodBook.serializer(), _book.value)
            withContext(aapsIoDispatcher) {
                runCatching { store.write(text) }
                    .onFailure { e -> aapsLogger.error(LTag.UI, "Food POC: cannot save the dishes and meals", e) }
            }
        }
    }
}
