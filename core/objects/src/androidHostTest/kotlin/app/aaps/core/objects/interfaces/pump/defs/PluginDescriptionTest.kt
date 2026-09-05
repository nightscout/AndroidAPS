package app.aaps.core.objects.interfaces.pump.defs

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.TextRef
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
        val ref = TextRef.AndroidRes(10)
        assertThat(PluginDescription().pluginName(ref).pluginName).isEqualTo(ref)
    }

    @Test fun shortNameTest() {
        val ref = TextRef.AndroidRes(10)
        assertThat(PluginDescription().shortName(ref).shortName).isEqualTo(ref)
    }

    @Test fun enableByDefault() {
        val pluginDescription = PluginDescription().enableByDefault(true)
        assertThat(pluginDescription.enableByDefault).isTrue()
    }
}
