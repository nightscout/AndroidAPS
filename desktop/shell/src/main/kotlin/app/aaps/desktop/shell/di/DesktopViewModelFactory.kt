package app.aaps.desktop.shell.di

import androidx.lifecycle.ViewModel
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/**
 * Turns the graph's view model maps into the factory `metroViewModel()` looks for.
 *
 * Android finds this through the `Application` object. A desktop has no ambient equivalent, so the
 * host provides it as `LocalMetroViewModelFactory` around the shared UI - the same arrangement the
 * Apple shell uses.
 */
internal class DesktopViewModelFactory(
    private val graph: DesktopAppGraph
) : MetroViewModelFactory() {

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
        get() = graph.viewModelProviders

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = graph.assistedFactoryProviders

    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = graph.manualAssistedFactoryProviders
}
