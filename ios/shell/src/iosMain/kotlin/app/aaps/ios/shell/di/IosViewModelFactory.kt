package app.aaps.ios.shell.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Builds screens' view models, the iOS counterpart of `AapsViewModelFactory` in `:app`.
 *
 * Each view model registers itself with `@ContributesIntoMap` and `@ViewModelKey`, so adding one
 * touches no graph and not this class either - all it does is hand Metro's maps to the factory.
 *
 * Simpler than the Android one, which merges a second map because Open Humans is still a root graph
 * of its own. That plugin is not built for iOS, so there is one map here rather than two.
 */
class IosViewModelFactory(
    private val graph: IosAppGraph
) : MetroViewModelFactory() {

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
        get() = graph.viewModelProviders

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = graph.assistedFactoryProviders

    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = graph.manualAssistedFactoryProviders
}
