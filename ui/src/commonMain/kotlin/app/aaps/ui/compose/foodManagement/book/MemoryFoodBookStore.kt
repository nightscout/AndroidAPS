package app.aaps.ui.compose.foodManagement.book

/**
 * Keeps the food POC text in memory only: it is gone when the app stops.
 *
 * PROOF OF CONCEPT: used on desktop and iOS, where the POC is not tested. The Android build saves
 * to a file. A real feature must save on every platform.
 */
open class MemoryFoodBookStore : FoodBookStore {

    private var text: String? = null

    override fun read(): String? = text

    override fun write(text: String) {
        this.text = text
    }
}
