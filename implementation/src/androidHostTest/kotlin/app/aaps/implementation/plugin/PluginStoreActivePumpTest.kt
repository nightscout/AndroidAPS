package app.aaps.implementation.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * That reading the active pump before one has been elected neither throws nor writes.
 *
 * This pins the fix for the "No pump selected" crash (`PluginStore.getActivePumpInternal`, live in
 * Crashlytics on 4.0.0-dev..dev-c). `ConfigBuilderImpl.initialize` starts the plugins and only then
 * calls `verifySelectionInCategories`, so between those two a plugin's `onStart` can read the active
 * pump while `activePumpStore` is still null. Two things make that safe, and BOTH are easy to remove
 * by accident:
 *
 * 1. the fallback to the first enabled PUMP plugin, instead of throwing;
 * 2. that the fallback is a **pure read**. It used to call `getTheOneEnabledInArray`, which disables
 *    every other enabled pump in the category - a write, and scheduled `onStop` jobs nobody could
 *    wait for, from inside a property getter.
 */
class PluginStoreActivePumpTest : TestBase() {

    /** A plugin that is also a `Pump`, without hand-writing the ~33 members. */
    private class TestPumpPlugin(
        aapsLogger: AAPSLogger,
        rh: TextResolver,
        notificationManager: NotificationManager,
        delegate: Pump,
        private val pumpName: String
    ) : PluginBase(PluginDescription().mainType(PluginType.PUMP), aapsLogger, rh, notificationManager), Pump by delegate {

        override val name: String get() = pumpName
    }

    private fun pump(name: String) = TestPumpPlugin(aapsLogger, mock<TextResolver>(), mock<NotificationManager>(), mock<Pump>(), name)

    private fun storeWith(vararg plugins: PluginBase): PluginStore =
        PluginStore(aapsLogger, mock<Preferences>(), { mock<PumpWithConcentration>() }).also { it.plugins = plugins.toList() }

    @Test
    fun `an enabled pump is returned before any election has happened`() {
        val only = pump("Only pump")
        only.setPluginEnabledBlocking(PluginType.PUMP, true)
        val sut = storeWith(only)

        // verifySelectionInCategories has NOT run, so activePumpStore is still null.
        assertThat(sut.activePumpInternal).isSameInstanceAs(only)
    }

    /**
     * The part that regressed before: reading the property must not change anything. If this getter
     * disables the runners-up again, it does so during plugin start, where nobody can await the
     * `onStop` jobs it would schedule.
     */
    @Test
    fun `reading the active pump does not disable the other enabled pumps`() {
        val first = pump("First pump")
        val second = pump("Second pump")
        first.setPluginEnabledBlocking(PluginType.PUMP, true)
        second.setPluginEnabledBlocking(PluginType.PUMP, true)
        val sut = storeWith(first, second)

        assertThat(sut.activePumpInternal).isSameInstanceAs(first)

        // Both are still enabled. A read is a read.
        assertThat(first.isEnabled(PluginType.PUMP)).isTrue()
        assertThat(second.isEnabled(PluginType.PUMP)).isTrue()
    }

    /** With nothing enabled at all there is genuinely no answer, and it must say so rather than guess. */
    @Test
    fun `with no enabled pump it still fails loudly`() {
        val disabled = pump("Disabled pump")
        val sut = storeWith(disabled)

        val e = runCatching { sut.activePumpInternal }.exceptionOrNull()

        assertThat(e).isInstanceOf(IllegalStateException::class.java)
        assertThat(e).hasMessageThat().isEqualTo("No pump selected")
    }
}
