package app.aaps.ui.compose.foodManagement.book

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/** PROOF OF CONCEPT: iOS keeps the food POC in memory only. See [MemoryFoodBookStore]. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<FoodBookStore>())
@Inject
class IosFoodBookStore : MemoryFoodBookStore()
