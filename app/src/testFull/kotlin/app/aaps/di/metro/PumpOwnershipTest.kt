package app.aaps.di.metro

import androidx.lifecycle.ViewModel
import app.aaps.shared.tests.metroScopedProviderTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Every pump object a view model injects has exactly one owner.
 *
 * A class with a javax `@Singleton` and an `@Inject` constructor is buildable by Dagger AND, because
 * `:app` runs Metro with Dagger interop, by Metro. A javax scope does not cross the two, so each gets
 * its own instance and neither is wrong on its own. The damage only shows at runtime: whichever
 * framework the pump service writes through fills one object while a Compose view model reads the
 * other, and the screen watches a pump that never connects.
 *
 * That is not theory. It turned CI shards A and C red - "Pump never reported initialized", "Command
 * queue never went idle" - across ten types in every converted pump driver, while shard B drove the
 * same transport with no UI and stayed green.
 *
 * **This test was called `PumpLeavesTest` and asserted the opposite.** Its rule was that every such
 * type must be *handed over by `PumpLeaves`*, because Dagger owned the pump objects: the services were
 * `DaggerService`s, so Dagger's copy was the real one and Metro had to borrow it. `dagger.android` is
 * off the phone now, every pump service is filled by a Metro member injector, and `PumpLeaves` was
 * deleted once the last binding left it. The rule that survives the reversal is the one that mattered
 * all along: **someone owns it, and there is one.**
 *
 * Ownership can be either annotation on the class or a scoped `@Provides` in a Metro binding container.
 * The second is not a loophole, it is how a class Metro cannot generate a factory for gets owned - the
 * Java eros managers, and the Omnipod common BLE classes whose module has no Metro plugin. A container
 * that calls the constructor itself needs no generated factory, and a scope on that provider means one
 * instance, which is all this test is really about.
 */
class PumpOwnershipTest {

    @Test
    fun `every singleton a pump view model injects is owned by Metro`() {
        // Anchored on AppRootGraph rather than a pump class: `:app` compiles `src/main` and the flavour
        // source set into one output, so this reaches every withPumps binding container.
        val owned = metroScopedProviderTypes(anchors = listOf(AppRootGraph::class.java))
        check(owned.isNotEmpty()) { "Found no scoped container providers - the scan broke" }

        val offenders = mutableListOf<String>()
        for (vm in pumpViewModels()) {
            val parameters = vm.primaryConstructor?.parameters ?: continue
            for (parameter in parameters) {
                val type = (parameter.type.classifier as? KClass<*>)?.java ?: continue
                // Only concrete classes carrying a javax scope can be built twice. An interface has to be
                // bound somewhere explicit, so it cannot be duplicated by accident.
                if (!type.isAnnotationPresent(Singleton::class.java)) continue
                if (type !in owned) offenders += "${vm.simpleName} injects ${type.simpleName}, which nobody owns"
            }
        }

        assertThat(offenders).isEmpty()
    }

    /** The pump view models Metro builds, taken from the graph itself so a new one is covered for free. */
    private fun pumpViewModels(): List<KClass<out ViewModel>> =
        testRoot().viewModelProviders.keys.filter {
            val name = it.java.name
            name.startsWith("app.aaps.pump.") || name.startsWith("info.nightscout.pump.")
        }
}
