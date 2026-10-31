package app.aaps.implementation.sharedPreferences

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.KeyCategory
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The split this makes is what "import everything except the pump settings" means in practice, so a
 * key landing in the wrong bucket is either a pump that stops working after an import, or a setting
 * the user asked to keep that gets overwritten anyway.
 *
 * `pluginDescription.mainType == PluginType.PUMP` is the rule (Miloš, 2026-09-22), matching what
 * `ConfigBuilderImpl` uses to elect the active pump rather than the `@PumpDriver` DI bucket.
 */
class PreferenceKeyResolverFactoryTest {

    private val pumpKey = StringKey.GeneralUnits
    private val otherKey = IntKey.ApsDynIsfAdjustmentFactor
    private val unownedKey = BooleanKey.GeneralSimpleMode

    private fun pluginWith(type: PluginType, owned: List<NonPreferenceKey>): PluginBaseWithPreferences {
        val plugin = mock<PluginBaseWithPreferences>()
        whenever(plugin.pluginDescription).thenReturn(PluginDescription().mainType(type))
        whenever(plugin.ownPreferences).thenReturn(owned)
        return plugin
    }

    private fun factory(vararg plugins: PluginBase): PreferenceKeyResolverFactory {
        val activePlugin = mock<ActivePlugin>()
        whenever(activePlugin.getPluginsList()).thenReturn(ArrayList(plugins.toList()))
        val preferences = mock<Preferences>()
        whenever(preferences.getAllKeys()).thenReturn(
            BooleanKey.entries + StringKey.entries + IntKey.entries + BooleanComposedKey.entries
        )
        return PreferenceKeyResolverFactory(preferences, activePlugin)
    }

    @Test fun `a key owned by a pump driver is pump internal`() {
        val sut = factory(
            pluginWith(PluginType.PUMP, listOf(pumpKey)),
            pluginWith(PluginType.GENERAL, listOf(otherKey))
        ).create()

        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.PumpInternal)
    }

    @Test fun `a key owned by a non-pump plugin is other plugin internal`() {
        val sut = factory(
            pluginWith(PluginType.PUMP, listOf(pumpKey)),
            pluginWith(PluginType.GENERAL, listOf(otherKey))
        ).create()

        assertThat(sut.resolve(otherKey.key)?.category).isEqualTo(KeyCategory.OtherPluginInternal)
    }

    @Test fun `a key no plugin claims is general`() {
        val sut = factory(pluginWith(PluginType.PUMP, listOf(pumpKey))).create()

        assertThat(sut.resolve(unownedKey.key)?.category).isEqualTo(KeyCategory.General)
    }

    /**
     * If two plugins claim the same key and one is a pump, the pump wins. That is the direction that
     * PRESERVES the value when the user asked to keep their pump settings; the other way round would
     * overwrite a pump key from the file, which is the failure this category exists to prevent.
     */
    @Test fun `a key claimed by both a pump and another plugin counts as the pump's`() {
        val sut = factory(
            pluginWith(PluginType.PUMP, listOf(pumpKey)),
            pluginWith(PluginType.GENERAL, listOf(pumpKey))
        ).create()

        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.PumpInternal)
    }

    @Test fun `plugins without preferences are ignored rather than throwing`() {
        val sut = factory(mock<PluginBase>(), pluginWith(PluginType.PUMP, listOf(pumpKey))).create()

        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.PumpInternal)
    }

    @Test fun `only the pump driver's keys are pump internal`() {
        val sut = factory(
            pluginWith(PluginType.PUMP, listOf(pumpKey)),
            pluginWith(PluginType.GENERAL, listOf(otherKey))
        ).create()

        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.PumpInternal)
        assertThat(sut.resolve(otherKey.key)?.category).isEqualTo(KeyCategory.OtherPluginInternal)
    }

    /**
     * The AAPSCLIENT / pumpcontrol shape: `MetroGraphs.allPlugins` never builds the pump bucket, so
     * there is no pump driver to walk and nothing can be classified as preservable. Recorded as a
     * known limit of owner-based classification, not as acceptable behaviour - it is why an
     * unresolved key must not simply be dropped (plan 4.1.6).
     */
    @Test fun `with no pump plugin nothing is pump internal`() {
        val sut = factory(pluginWith(PluginType.GENERAL, listOf(otherKey))).create()

        assertThat(sut.resolve(pumpKey.key)?.category).isEqualTo(KeyCategory.General)
    }
}
