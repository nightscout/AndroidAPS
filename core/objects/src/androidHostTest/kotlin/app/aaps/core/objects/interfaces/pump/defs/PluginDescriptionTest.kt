package app.aaps.core.objects.interfaces.pump.defs

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PluginDescriptionTest {

    @Test fun mainTypeTest() {
        val pluginDescription = PluginDescription().mainType(PluginType.PUMP)
        assertThat(pluginDescription.mainType).isEqualTo(PluginType.PUMP)
    }

    @Test fun `enforce records the state and its condition`() {
        val enabled = PluginDescription().enforce(EnforcedState.Enabled)
        assertThat(enabled.enforcements).hasSize(1)
        assertThat(enabled.enforcements.first().state).isEqualTo(EnforcedState.Enabled)
        assertThat(enabled.enforcements.first().applies.invoke()).isTrue()

        var flag = false
        val conditional = PluginDescription().enforce(EnforcedState.Disabled) { flag }
        assertThat(conditional.enforcements.first().applies.invoke()).isFalse()
        flag = true
        assertThat(conditional.enforcements.first().applies.invoke()).isTrue()
    }

    @Test fun `no enforcement means the user decides`() {
        assertThat(PluginDescription().enforcements).isEmpty()
    }

    @Test fun `enforceEnabledOnlyWhen declares both directions`() {
        val description = PluginDescription().enforceEnabledOnlyWhen { false }
        assertThat(description.enforcements).hasSize(2)
        val applying = description.enforcements.filter { it.applies.invoke() }
        assertThat(applying).hasSize(1)
        assertThat(applying.first().state).isEqualTo(EnforcedState.Disabled)
    }

    @Test fun showInListTest() {
        val pluginDescription = PluginDescription().showInList { false }
        assertThat(pluginDescription.showInList.invoke()).isFalse()
    }

    @Test fun pluginName() {
        val ref = TextRef.AndroidRes(10)
        assertThat(PluginDescription().pluginName(ref).pluginName).isEqualTo(ref)
    }

    @Test fun shortNameTest() {
        val ref = TextRef.AndroidRes(10)
        assertThat(PluginDescription().shortName(ref).shortName).isEqualTo(ref)
    }
}
