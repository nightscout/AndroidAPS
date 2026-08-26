package app.aaps.di.metro

import app.aaps.implementation.plugin.PluginStore
import app.aaps.plugins.sync.tidepool.TidepoolPlugin
import app.aaps.pump.medtronic.MedtronicPumpPlugin
import app.aaps.shared.tests.unbridgedSingletons
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * No class is built by both frameworks at once.
 *
 * `PumpLeavesTest` asks this question about pump view models. This asks it about everything, because
 * the same mistake is possible anywhere: a class carrying javax `@Singleton` is buildable by Dagger and,
 * since the graph in `:app` runs without Dagger interop, by Metro too - which ignores the javax scope and
 * builds a fresh one per injection point. Both halves look correct and the build passes; the object just
 * stops being shared.
 *
 * Three of these shipped in a single day before this test existed, each found by hand:
 * `ProfileSwitchSilentGate` (a scene profile switch showed the notification its gate suppresses),
 * `ReceiverDelegate` (Tidepool read an upload gate nothing updated) and `RateLimit` (nothing was ever
 * rate limited). None of them failed a build, a unit test or CI.
 *
 * To fix a report, name an owner - `@SingleIn` plus a `CoreObjectsModule` delegate so Dagger borrows
 * Metro's, or leave it Dagger's and add a leaf so Metro borrows Dagger's. Do not add it to an ignore
 * list unless the class genuinely holds no state, and say why.
 */
class SplitBrainTest {

    @Test
    fun `nothing javax-scoped is rebuilt by Metro`() {
        val reports = unbridgedSingletons(anchors = ANCHORS, daggerOwned = daggerOwnedTypes())
            .filterNot { report -> STATELESS.any { report.startsWith(it) } }

        assertWithMessage(
            "These are javax @Singleton, so exactly one was intended, but Metro builds its own copy. " +
                "Give the class @SingleIn and a CoreObjectsModule delegate, or hand it over with a leaf."
        ).that(reports).isEmpty()
    }

    /**
     * Classes a second copy of does no harm, each checked to hold no mutable field: they take their
     * dependencies in the constructor and compute, so two instances behave identically.
     *
     * A duplicate is still a wasted allocation, so this is a tolerance rather than an endorsement - but
     * scoping them would add a delegate that carries no meaning. **Only add an entry after reading the
     * class**: the whole point of this test is that "looks harmless" is exactly how the three real ones
     * got through.
     */
    private val STATELESS = listOf(
        // Branches on config and forwards to the dispatcher. No fields.
        "app.aaps.implementation.bolus.RoleBranch",
        // Encrypts and decrypts what it is handed; every var in it is a local.
        "app.aaps.implementation.maintenance.formats.EncryptedPrefsFormat",
        // Builds upload payloads from its arguments. No fields.
        "app.aaps.plugins.sync.tidepool.comm.UploadChunk",
        // Holds one Intent built from Context in its constructor; two are interchangeable.
        "app.aaps.persistentNotification.DummyServiceHelper"
    )

    /**
     * One class per compiled output to scan. A module contributes nothing unless something in it is
     * named here, so add an anchor when a new module starts contributing to the graph.
     */
    private val ANCHORS
        get() = listOf(
            AapsLeaves::class.java,          // :app
            PluginStore::class.java,         // :implementation
            TidepoolPlugin::class.java,      // :plugins:sync
            MedtronicPumpPlugin::class.java  // :pump:medtronic
        )

    /**
     * The types Dagger owns and lends to Metro, read from the leaves themselves rather than listed here
     * - a hand-written copy would drift the moment someone adds one.
     *
     * `CoreObjectsModule`'s `provide*(graphs: MetroGraphs)` delegates are deliberately **not** included.
     * They run the other way, and a delegate alone does not make a type safe: it returns whatever Metro
     * built at that moment, so an unscoped Metro side still hands every other Metro consumer its own
     * copy while Dagger caches one. Such a class must also carry `@SingleIn`, and letting the delegate
     * excuse it is exactly the hole that let this test pass over a reverted ProfileSwitchSilentGate.
     */
    private fun daggerOwnedTypes(): Set<Class<*>> {
        val leaves = (AapsLeaves::class.java.declaredMethods + PumpLeaves::class.java.declaredMethods)
            .filter { it.parameterCount == 0 }
            .map { it.returnType }
            .toSet()

        check(leaves.size > 50) { "Only ${leaves.size} leaf types found - the reflection broke" }
        return leaves
    }
}
