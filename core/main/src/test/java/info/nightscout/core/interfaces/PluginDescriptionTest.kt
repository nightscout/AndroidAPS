package info.nightscout.core.interfaces

import androidx.fragment.app.Fragment
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PluginDescriptionTest {

    @Test fun mainTypeTest() {
        val pluginDescription = PluginDescription().mainType(PluginType.PUMP)
        Assertions.assertEquals(PluginType.PUMP, pluginDescription.mainType)
    }

    @Test fun fragmentClassTest() {
        val pluginDescription = PluginDescription().fragmentClass(Fragment::class.java.name)
        Assertions.assertEquals(Fragment::class.java.name, pluginDescription.fragmentClass)
    }

    @Test fun alwaysEnabledTest() {
        val pluginDescription = PluginDescription().alwaysEnabled(true)
        Assertions.assertEquals(true, pluginDescription.alwaysEnabled)
    }

    @Test fun alwaysVisibleTest() {
        val pluginDescription = PluginDescription().alwaysVisible(true)
        Assertions.assertEquals(true, pluginDescription.alwaysVisible)
    }

    @Test fun neverVisibleTest() {
        val pluginDescription = PluginDescription().neverVisible(true)
        Assertions.assertEquals(true, pluginDescription.neverVisible)
    }

    @Test fun showInListTest() {
        val pluginDescription = PluginDescription().showInList(false)
        Assertions.assertEquals(false, pluginDescription.showInList)
    }

    @Test fun pluginIcon() {
        val pluginDescription = PluginDescription().pluginIcon(10)
        Assertions.assertEquals(10, pluginDescription.pluginIcon.toLong())
    }

    @Test fun pluginName() {
        val pluginDescription = PluginDescription().pluginName(10)
        Assertions.assertEquals(10, pluginDescription.pluginName.toLong())
    }

    @Test fun shortNameTest() {
        val pluginDescription = PluginDescription().shortName(10)
        Assertions.assertEquals(10, pluginDescription.shortName.toLong())
    }

    @Test fun preferencesIdTest() {
        val pluginDescription = PluginDescription().preferencesId(10)
        Assertions.assertEquals(10, pluginDescription.preferencesId.toLong())
    }

    @Test fun enableByDefault() {
        val pluginDescription = PluginDescription().enableByDefault(true)
        Assertions.assertEquals(true, pluginDescription.enableByDefault)
    }

    @Test fun visibleByDefault() {
        val pluginDescription = PluginDescription().visibleByDefault(true)
        Assertions.assertEquals(true, pluginDescription.visibleByDefault)
    }
}