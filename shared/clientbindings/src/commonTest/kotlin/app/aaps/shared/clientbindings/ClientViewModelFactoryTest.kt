package app.aaps.shared.clientbindings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelMultibindings
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The factory must read the graph's own view model map, and read it on every call.
 *
 * Worth pinning because the failure is silent. Every screen asks `metroViewModel()` for its view
 * model, so a factory wired to the wrong graph member - or one that froze an empty map at
 * construction - still builds the graph and still starts the app. Screens then fail one at a time,
 * as a user opens them.
 *
 * Tested through `create`, the public entry point, rather than the maps themselves: those are
 * `protected` in `MetroViewModelFactory`, and this is the path a screen actually takes.
 *
 * This is also the first test in `:shared:clientbindings`. The module is pure wiring shared by the
 * desktop and iOS shells, so nothing else covers it.
 */
class ClientViewModelFactoryTest {

    private class FakeViewModel : ViewModel()

    private val provided: Map<KClass<out ViewModel>, () -> ViewModel> =
        mapOf(FakeViewModel::class to { FakeViewModel() })
    private val noAssisted: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory> = emptyMap()
    private val noManual: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory> = emptyMap()

    private fun bindings(models: () -> Map<KClass<out ViewModel>, () -> ViewModel>) =
        object : MetroViewModelMultibindings {
            override val viewModelProviders get() = models()
            override val assistedFactoryProviders = noAssisted
            override val manualAssistedFactoryProviders = noManual
        }

    @Test
    fun buildsAViewModelTheGraphOffers() {
        val factory = ClientViewModelFactory(bindings { provided })

        val first = factory.create(FakeViewModel::class, CreationExtras.Empty)
        val second = factory.create(FakeViewModel::class, CreationExtras.Empty)

        assertTrue(first is FakeViewModel)
        // A provider, not a singleton: each screen gets its own.
        assertTrue(first !== second)
    }

    @Test
    fun refusesAViewModelTheGraphDoesNotOffer() {
        val factory = ClientViewModelFactory(bindings { emptyMap() })

        assertFailsWith<IllegalArgumentException> {
            factory.create(FakeViewModel::class, CreationExtras.Empty)
        }
    }

    @Test
    fun readsTheMapOnEveryCallSoALaterContributionIsSeen() {
        // The overrides delegate with `get()` on purpose: a graph may build its multibinding map
        // lazily, and caching it here once would freeze whatever existed at construction time.
        var current: Map<KClass<out ViewModel>, () -> ViewModel> = emptyMap()
        val factory = ClientViewModelFactory(bindings { current })

        assertFailsWith<IllegalArgumentException> {
            factory.create(FakeViewModel::class, CreationExtras.Empty)
        }

        current = provided

        assertTrue(factory.create(FakeViewModel::class, CreationExtras.Empty) is FakeViewModel)
    }
}
