package app.aaps.ui.compose.foodManagement.book

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/** PROOF OF CONCEPT: desktop has no exports folder yet, so export and backup are not offered. */
@ContributesBinding(AppScope::class, binding = binding<FoodExportFiles>())
@Inject
class DesktopFoodExportFiles : NoFoodExportFiles()
