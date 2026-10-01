package app.aaps.di.metro

import androidx.lifecycle.ViewModel
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * Every pump view model really reaches the factory.
 *
 * A pump screen is the one place where a wrong annotation can hide for a long time. Only one pump is
 * ever set up on a phone, so a broken map entry is invisible until the one user who owns that pump
 * opens that screen - and then it is a crash, on a device that is running a loop. The rest of the app
 * gets opened during any test pass; a Diaconn dialog does not.
 *
 * This test only asks whether the binding exists. It cannot build the view models: they are `full`
 * flavour classes, which is why the file is in `src/testFull` and not beside the other graph tests.
 *
 * ## The expectation is found, not written down
 *
 * It used to list twenty-six view model classes by name, which had two costs. It made the contents of
 * `settings.gradle` a compile-time dependency of this test - removing `:pump:equil` stopped
 * `:app:testFullDebugUnitTest` compiling rather than failing an assertion. And it only ever checked the
 * twenty-six: a *new* pump view model that forgot its annotation was exactly the case this file exists
 * to catch, and exactly the case a hand-written list cannot catch.
 *
 * Both sides now come from the build - the map from the graph, the expectation from the view models
 * actually compiled into it.
 */
class PumpViewModelsTest {

    /** Concrete `ViewModel`s belonging to a pump module, in whatever build this is. */
    private fun pumpViewModels(): List<Class<*>> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { it.name.startsWith("app.aaps.pump.") || it.name.startsWith("info.nightscout.pump.") }
            .filter { ViewModel::class.java.isAssignableFrom(it) }
            // A base class is never contributed on its own; only the screens are.
            .filterNot { it.isInterface || Modifier.isAbstract(it.modifiers) }

    @Test
    fun `every pump view model is contributed to the root graph`() {
        val expected = pumpViewModels()
        check(expected.isNotEmpty()) { "Found no pump view models on the classpath - the scan broke" }

        val contributed = testRoot().viewModelProviders.keys.map { it.java }

        assertThat(contributed).containsAtLeastElementsIn(expected)
    }
}
