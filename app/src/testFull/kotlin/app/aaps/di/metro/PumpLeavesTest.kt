package app.aaps.di.metro

import app.aaps.shared.tests.metroScopedProviderTypes
import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * No pump object is built by both frameworks at once.
 *
 * A class with a javax `@Singleton` and an `@Inject` constructor is buildable by Dagger AND, because
 * `:app` runs Metro with Dagger interop, by Metro. A javax scope does not cross the two, so each gets its
 * own instance and neither is wrong on its own. The damage only shows at runtime: the pump services are
 * still `DaggerService`s, so Dagger's copy is the one the pump writes to, while a Compose view model
 * reads Metro's copy and sees a pump that never connects.
 *
 * That is not theory. It turned CI shards A and C red - "Pump never reported initialized", "Command queue
 * never went idle" - across ten types in every converted pump driver, while shard B drove the same
 * transport with no UI and stayed green. The fix is to name an owner: [PumpLeaves] hands Dagger's instance
 * to Metro.
 *
 * This test re-runs that audit on every build. It walks each pump view model's constructor and fails if a
 * parameter is a `@Singleton` class that [PumpLeaves] does not hand over - which is what adding one new
 * dependency to one view model would otherwise do, silently.
 */
class PumpLeavesTest {

    @Test
    fun `every singleton a pump view model injects is handed over by PumpLeaves`() {
        // Handed over by a leaf, OR owned by a scoped `@Provides` in a Metro binding container. The
        // second case is how the Java eros classes are owned: Metro cannot generate a factory from an
        // `@Inject` constructor it has no Kotlin IR for, so a container calls the constructor instead.
        // Either way there is exactly one instance, which is all this test is really about.
        val handedOver = PumpLeaves::class.java.declaredMethods.map { it.returnType }.toSet() +
            metroScopedProviderTypes(anchors = listOf(PumpLeaves::class.java))

        val offenders = mutableListOf<String>()
        for (vm in pumpViewModels()) {
            val parameters = vm.primaryConstructor?.parameters ?: continue
            for (parameter in parameters) {
                val type = (parameter.type.classifier as? KClass<*>)?.java ?: continue
                // Only concrete classes carrying a javax scope can be built twice. An interface has to be
                // bound somewhere explicit, so it cannot be duplicated by accident.
                if (!type.isAnnotationPresent(Singleton::class.java)) continue
                if (type !in handedOver) offenders += "${vm.simpleName} injects ${type.simpleName}"
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
