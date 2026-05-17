package app.aaps.core.objects.interfaces.pump.defs

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginDescription
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PluginDescriptionTest {

    @Test fun mainTypeTest() {
        val pluginDescription = PluginDescription().mainType(PluginType.PUMP)
        assertThat(pluginDescription.mainType).isEqualTo(PluginType.PUMP)
    }

    @Test fun alwaysEnabledTest() {
        val pluginDescription = PluginDescription().alwaysEnabled(true)
        assertThat(pluginDescription.alwaysEnabled).isTrue()
    }

    @Test fun neverVisibleTest() {
        val pluginDescription = PluginDescription().neverVisible(true)
        assertThat(pluginDescription.neverVisible).isTrue()
    }

    @Test fun showInListTest() {
        val pluginDescription = PluginDescription().showInList { false }
        assertThat(pluginDescription.showInList.invoke()).isFalse()
    }

    @Test fun pluginName() {
        val pluginDescription = PluginDescription().pluginName(10)
        assertThat(pluginDescription.pluginName.toLong()).isEqualTo(10)
    }

    @Test fun shortNameTest() {
        val pluginDescription = PluginDescription().shortName(10)
        assertThat(pluginDescription.shortName.toLong()).isEqualTo(10)
    }

    @Test fun enableByDefault() {
        val pluginDescription = PluginDescription().enableByDefault(true)
        assertThat(pluginDescription.enableByDefault).isTrue()
    }
}
