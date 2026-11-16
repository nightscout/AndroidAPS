package app.aaps.di.metro

import androidx.lifecycle.ViewModel
import app.aaps.plugins.sync.di.OpenHumansMetroBridge
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Each view model carries its own registration - `@ContributesIntoMap` plus `@ViewModelKey` on the
 * class - so adding one touches no graph and no factory. This class only merges the maps.
 * Two maps, because there are two graphs. Open Humans is still a root of its own rather than an
 * extension (its view models are `internal`, see `OpenHumansMetroGraph`), so its contributions land in
 * a separate map. The keys are view model classes, so the two cannot collide.
 */
class AapsViewModelFactory(
    root: AppRootGraph,
    openHumans: OpenHumansMetroBridge
) : MetroViewModelFactory() {

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> =
        root.viewModelProviders + openHumans.viewModelProviders

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory> =
        root.assistedFactoryProviders + openHumans.assistedFactoryProviders

    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> =
        root.manualAssistedFactoryProviders + openHumans.manualAssistedFactoryProviders
}
