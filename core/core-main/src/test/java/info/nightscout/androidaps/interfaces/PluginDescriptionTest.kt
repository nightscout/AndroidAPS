package info.nightscout.androidaps.interfaces

import androidx.fragment.app.Fragment
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import org.junit.Assert
import org.junit.jupiter.api.Test

class PluginDescriptionTest {

    @Test fun mainTypeTest() {
        val pluginDescription = PluginDescription().mainType(PluginType.PUMP)
        Assert.assertEquals(PluginType.PUMP, pluginDescription.mainType)
    }

    @Test fun fragmentClassTest() {
        val pluginDescription = PluginDescription().fragmentClass(Fragment::class.java.name)
        Assert.assertEquals(Fragment::class.java.name, pluginDescription.fragmentClass)
    }

    @Test fun alwaysEnabledTest() {
        val pluginDescription = PluginDescription().alwaysEnabled(true)
        Assert.assertEquals(true, pluginDescription.alwaysEnabled)
    }

    @Test fun alwaysVisibleTest() {
        val pluginDescription = PluginDescription().alwaysVisible(true)
        Assert.assertEquals(true, pluginDescription.alwaysVisible)
    }

    @Test fun neverVisibleTest() {
        val pluginDescription = PluginDescription().neverVisible(true)
        Assert.assertEquals(true, pluginDescription.neverVisible)
    }

    @Test fun showInListTest() {
        val pluginDescription = PluginDescription().showInList(false)
        Assert.assertEquals(false, pluginDescription.showInList)
    }

    @Test fun pluginIcon() {
        val pluginDescription = PluginDescription().pluginIcon(10)
        Assert.assertEquals(10, pluginDescription.pluginIcon.toLong())
    }

    @Test fun pluginName() {
        val pluginDescription = PluginDescription().pluginName(10)
        Assert.assertEquals(10, pluginDescription.pluginName.toLong())
    }

    @Test fun shortNameTest() {
        val pluginDescription = PluginDescription().shortName(10)
        Assert.assertEquals(10, pluginDescription.shortName.toLong())
    }

    @Test fun preferencesIdTest() {
        val pluginDescription = PluginDescription().preferencesId(10)
        Assert.assertEquals(10, pluginDescription.preferencesId.toLong())
    }

    @Test fun enableByDefault() {
        val pluginDescription = PluginDescription().enableByDefault(true)
        Assert.assertEquals(true, pluginDescription.enableByDefault)
    }

    @Test fun visibleByDefault() {
        val pluginDescription = PluginDescription().visibleByDefault(true)
        Assert.assertEquals(true, pluginDescription.visibleByDefault)
    }
}