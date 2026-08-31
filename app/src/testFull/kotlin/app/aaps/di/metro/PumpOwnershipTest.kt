package app.aaps.di.metro

import android.content.Context
import androidx.lifecycle.ViewModel
import app.aaps.shared.tests.metroScopedProviderTypes
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Every pump object a view model injects has exactly one owner.
 *
 * Two instances of a pump state holder is not a slow screen, it is a screen watching an object the pump
 * never writes to. That cost CI shards A and C - "Pump never reported initialized", "Command queue
 * never went idle" - across ten types, while shard B drove the same transport with no UI and stayed green.
 *
 * **This test has been rewritten twice as the premise moved.** It began as `PumpLeavesTest`, demanding
 * that every such type be *handed over by `PumpLeaves`*, because Dagger owned the pump objects. Then
 * `PumpLeaves` was deleted and it asked that they be Metro owned. It selected candidates by javax
 * `@Singleton`, which was the mark of "buildable by both frameworks" - and javax is gone now, so that
 * filter would select nothing and the test would pass over anything.
 *
 * What it asks now does not depend on either framework: **every concrete class a pump view model
 * injects must have an owner.** Either `@SingleIn` on the class, or a scoped `@Provides` in a binding
 * container - the second is how classes Metro cannot generate a factory for are owned. Interfaces are
 * skipped: they have to be bound somewhere explicit, so they cannot be duplicated by accident.
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

    /** The pump view models Metro builds, taken from the graph itself so a new one is covered for free. */
    private fun pumpViewModels(): List<KClass<out ViewModel>> =
        testRoot().viewModelProviders.keys.filter {
            val name = it.java.name
            name.startsWith("app.aaps.pump.") || name.startsWith("info.nightscout.pump.")
        }
}
