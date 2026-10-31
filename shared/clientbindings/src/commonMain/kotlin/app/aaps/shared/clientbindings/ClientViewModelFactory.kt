package app.aaps.shared.clientbindings

import androidx.lifecycle.ViewModel
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Turns a graph's view model maps into the factory `metroViewModel()` looks for.
 *
 * Android finds its factory through the `Application` object. Neither a desktop nor an iOS app has
 * an ambient equivalent, so each host provides one as `LocalMetroViewModelFactory` around the shared
 * UI.
 *
 * Typed against [MetroViewModelMultibindings] rather than against a particular graph, which is what
 * lets one class serve both. `DesktopAppGraph` and `IosAppGraph` each implement that interface, and
 * the two shells previously carried copies of this class that differed only in the graph type they
 * named.
 *
 * Adding a view model touches none of this: it registers itself with `@ContributesIntoMap` and
 * `@ViewModelKey`, and all this does is hand Metro's maps to the factory.
 */
class ClientViewModelFactory(
    private val graph: MetroViewModelMultibindings
) : MetroViewModelFactory() {

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
        get() = graph.viewModelProviders

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = graph.assistedFactoryProviders

    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = graph.manualAssistedFactoryProviders
}
