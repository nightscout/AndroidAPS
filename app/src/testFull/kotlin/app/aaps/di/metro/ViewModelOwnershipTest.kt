package app.aaps.di.metro

import android.content.Context
import androidx.lifecycle.ViewModel
import app.aaps.core.objects.di.CoreObjectsGraph
import app.aaps.shared.tests.metroScopedProviderTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Every object a view model injects has exactly one owner.
 * Two instances of a pump state holder is not a slow screen, it is a screen watching an object the pump
 * never writes to. That cost CI shards A and C - "Pump never reported initialized", "Command queue
 * never went idle" - across ten types, while shard B drove the same transport with no UI and stayed green.
 * What it asks now does not depend on either framework: **every concrete class a pump view model
 * injects must have an owner.** Either `@SingleIn` on the class, or a scoped `@Provides` in a binding
 * container - the second is how classes Metro cannot generate a factory for are owned. Interfaces are
 * skipped: they have to be bound somewhere explicit, so they cannot be duplicated by accident.
 */
class ViewModelOwnershipTest {

    @Test
    fun `every singleton a view model injects is owned by Metro`() {
        // Anchored on AppRootGraph rather than a pump class: `:app` compiles `src/main` and the flavour
        // source set into one output, so this reaches every withPumps binding container.
        // Both graphs, not just the root. `CoreObjectsGraph` is reached through `@Includes` on the
        // factory rather than from `AppRootGraph` itself, so a scan anchored only on the root cannot
        // see its scoped providers - it reported `QuickWizard` as unowned when it is `@SingleIn` at
        // `CoreObjectsGraph`. The old pump-only filter never reached a type bound there, which is why
        // the blind spot survived.
        val owned = metroScopedProviderTypes(anchors = listOf(AppRootGraph::class.java, CoreObjectsGraph::class.java))
        check(owned.isNotEmpty()) { "Found no scoped container providers - the scan broke" }

        val offenders = mutableListOf<String>()
        for (vm in allViewModels()) {
            val parameters = vm.primaryConstructor?.parameters ?: continue
            for (parameter in parameters) {
                val type = (parameter.type.classifier as? KClass<*>)?.java ?: continue
                // Only concrete classes carrying a javax scope can be built twice. An interface has to be
                // bound somewhere explicit, so it cannot be duplicated by accident.
                // A graph INPUT, not a binding: the Application hands it to `AppRootGraph.Factory`, so
                // there is exactly one and no annotation says so. It is the only one of these.
                if (type == Context::class.java) continue
                // Interfaces are bound somewhere explicit, so they cannot be duplicated by accident.
                if (type.isInterface) continue
                // Owned by an annotation on the class itself - the common case.
                if (type.isAnnotationPresent(SingleIn::class.java)) continue
                // Otherwise the only other owner is a scoped @Provides in a binding container.
                if (type !in owned) offenders += "${vm.simpleName} injects ${type.simpleName}, which nobody owns"
            }
        }

        assertThat(offenders).isEmpty()
    }

    /**
     * Every view model Metro builds, taken from the graph itself so a new one is covered for free.
     *
     * This used to be filtered to `app.aaps.pump.` because that is where the failures were. The rule
     * is not pump specific though, and run against all of them it passes - so the filter was hiding
     * coverage rather than earning anything. Classes it now guards that nothing else does include
     * `DataSyncSelectorV3`, whose second copy would re-upload from a stale Nightscout cursor.
     */
    private fun allViewModels(): List<KClass<out ViewModel>> = testRoot().viewModelProviders.keys.toList()
}
