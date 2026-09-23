package app.aaps.di.metro

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Inject
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * The pump entry points Android constructs must have a member injector entry.
 *
 * Same failure mode as the receivers, and the same reason it needs a test: a missing entry is not a
 * build error. `MetroService.onCreate` looks the injector up by `target::class`, throws when it is not
 * there, and that only happens when the service actually starts - which for these is when the phone
 * connects to the pump. A DanaR user would see the driver fail at connect time.
 *
 * The lookup uses the runtime class, so the entry has to name each concrete class. An entry for a base
 * class is never found: the three DanaR execution services all subclass `AbstractDanaRExecutionService`
 * and still need one entry each. That is the trap `ReceiverInjectorsTest` spells out for `SmsReceiver`.
 *
 * ## The expectation is found, not written down
 *
 * It used to name thirteen classes from seven pump modules. That made those modules a compile-time
 * dependency of this test - removing `:pump:dana:danar` from `settings.gradle` stopped
 * `:app:testFullDebugUnitTest` compiling - and it only checked the thirteen. A new pump service that
 * forgot its entry is exactly the case this file exists to catch, and a list cannot catch it.
 *
 * It now looks for them: every concrete activity, service or receiver in a pump package that has a
 * field Metro fills, declared on the class or on any of its bases. Such a class gets those fields only
 * through this map, so one without an entry fails when Android starts it.
 */
class PumpEntryPointInjectorsTest {

    private val androidEntryPoints = listOf(Activity::class.java, Service::class.java, BroadcastReceiver::class.java)

    /** Concrete Android entry points belonging to a pump module that need their fields filled. */
    private fun pumpEntryPoints(): List<Class<*>> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { it.name.startsWith("app.aaps.pump.") || it.name.startsWith("info.nightscout.pump.") }
            .filter { type -> androidEntryPoints.any { it.isAssignableFrom(type) } }
            // Android never builds a base class on its own; only the concrete one is looked up.
            .filterNot { it.isInterface || Modifier.isAbstract(it.modifiers) }
            .filter { it.hasInjectedField() }

    private fun Class<*>.hasInjectedField(): Boolean =
        generateSequence(this) { it.superclass }
            .any { type -> type.declaredFields.any { it.isAnnotationPresent(Inject::class.java) } }

    @Test
    fun `every pump entry point has its own injector`() {
        val expected = pumpEntryPoints()
        check(expected.isNotEmpty()) { "Found no pump entry points on the classpath - the scan broke" }

        val contributed = testRoot().contributedMemberInjectors.keys.map { it.java }

        assertThat(contributed).containsAtLeastElementsIn(expected)
    }
}
