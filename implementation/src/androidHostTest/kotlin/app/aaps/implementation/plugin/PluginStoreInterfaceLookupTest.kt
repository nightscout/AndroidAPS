package app.aaps.implementation.plugin

import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.aps.Sensitivity
import app.aaps.core.interfaces.smoothing.Smoothing
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import kotlin.reflect.KClass

/**
 * What [PluginStore.getSpecificPluginsListByInterface] answers, pinned before it is rewritten.
 *
 * The method currently asks `interfaceClass.java.isAssignableFrom(ConfigBuilder::class.java)`, which
 * is JVM reflection and the last thing keeping this class off iOS. These tests exist to prove the
 * replacement answers identically rather than to argue that it should: they were written against the
 * reflection version and must keep passing unchanged afterwards.
 *
 * The interesting cases are the two the guard can actually match. `ConfigBuilder` declares no
 * supertypes, so `X.isAssignableFrom(ConfigBuilder)` is true only when `X` is `ConfigBuilder`
 * itself or `Any` - nothing else in the hierarchy can satisfy it.
 */
class PluginStoreInterfaceLookupTest : TestBase() {

    @Mock lateinit var preferences: Preferences

    private val apsPlugin: PluginBase = mock(extraInterfaces = arrayOf(APS::class))
    private val pumpPlugin: PluginBase = mock(extraInterfaces = arrayOf(Pump::class))
    private val sensitivityPlugin: PluginBase = mock(extraInterfaces = arrayOf(Sensitivity::class))
    private val smoothingPlugin: PluginBase = mock(extraInterfaces = arrayOf(Smoothing::class))
    private val bgSourcePlugin: PluginBase = mock(extraInterfaces = arrayOf(BgSource::class))

    private fun store(): PluginStore =
        PluginStore(aapsLogger, preferences) { mock() }.also {
            it.plugins = listOf(apsPlugin, pumpPlugin, sensitivityPlugin, smoothingPlugin, bgSourcePlugin)
        }

    private fun lookup(type: KClass<*>) = store().getSpecificPluginsListByInterface(type)

    @Test
    fun `each interface returns the plugin that implements it`() {
        assertThat(lookup(APS::class)).containsExactly(apsPlugin)
        assertThat(lookup(Pump::class)).containsExactly(pumpPlugin)
        assertThat(lookup(Sensitivity::class)).containsExactly(sensitivityPlugin)
        assertThat(lookup(Smoothing::class)).containsExactly(smoothingPlugin)
        assertThat(lookup(BgSource::class)).containsExactly(bgSourcePlugin)
    }

    @Test
    fun `an interface no plugin implements returns nothing`() {
        assertThat(lookup(Runnable::class)).isEmpty()
    }

    @Test
    fun `asking for ConfigBuilder returns nothing`() {
        // The guard. Asking for the builder itself must not hand back the plugin list.
        assertThat(lookup(ConfigBuilder::class)).isEmpty()
    }

    @Test
    fun `asking for Any returns nothing`() {
        // The only other class the guard matches, since every plugin is an Any. No caller passes
        // this, but it is the case a naive equality check would get wrong, so it is pinned.
        assertThat(lookup(Any::class)).isEmpty()
    }

    @Test
    fun `the lookup does not depend on plugin order`() {
        val reversed = PluginStore(aapsLogger, preferences) { mock() }.also {
            it.plugins = listOf(bgSourcePlugin, smoothingPlugin, sensitivityPlugin, pumpPlugin, apsPlugin)
        }

        assertThat(reversed.getSpecificPluginsListByInterface(Sensitivity::class)).containsExactly(sensitivityPlugin)
    }
}
