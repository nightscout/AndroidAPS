package info.nightscout.androidaps.interfaces;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.profile.ns.NSProfileFragment;

@RunWith(PowerMockRunner.class)
public class PluginDescriptionTest {

    @Test
    public void mainTypeTest() {
        PluginDescription pluginDescription = new PluginDescription().mainType(PluginType.PUMP);
        Assert.assertEquals(PluginType.PUMP, pluginDescription.getType());
    }

    @Test
    public void fragmentClassTest() {
        PluginDescription pluginDescription = new PluginDescription().fragmentClass(NSProfileFragment.class.getName());
        Assert.assertEquals(NSProfileFragment.class.getName(), pluginDescription.getFragmentClass());
    }

    @Test
    public void alwaysEnabledTest() {
        PluginDescription pluginDescription = new PluginDescription().alwaysEnabled(true);
        Assert.assertEquals(true, pluginDescription.alwaysEnabled);
    }

    @Test
    public void alwayVisibleTest() {
        PluginDescription pluginDescription = new PluginDescription().alwaysVisible(true);
        Assert.assertEquals(true, pluginDescription.alwaysVisible);
    }

    @Test
    public void neverVisibleTest() {
        PluginDescription pluginDescription = new PluginDescription().neverVisible(true);
        Assert.assertEquals(true, pluginDescription.neverVisible);
    }

    @Test
    public void showInListTest() {
        PluginDescription pluginDescription = new PluginDescription().showInList(false);
        Assert.assertEquals(false, pluginDescription.showInList);
    }

    @Test
    public void pluginName() {
        PluginDescription pluginDescription = new PluginDescription().pluginName(10);
        Assert.assertEquals(10, pluginDescription.pluginName);
    }

    @Test
    public void shortNameTest() {
        PluginDescription pluginDescription = new PluginDescription().shortName(10);
        Assert.assertEquals(10, pluginDescription.shortName);
    }

    @Test
    public void preferencesIdTest() {
        PluginDescription pluginDescription = new PluginDescription().preferencesId(10);
        Assert.assertEquals(10, pluginDescription.preferencesId);
    }

    @Test
    public void enableByDefault() {
        PluginDescription pluginDescription = new PluginDescription().enableByDefault(true);
        Assert.assertEquals(true, pluginDescription.enableByDefault);
    }

    @Test
    public void visibleByDefault() {
        PluginDescription pluginDescription = new PluginDescription().visibleByDefault(true);
        Assert.assertEquals(true, pluginDescription.visibleByDefault);
    }

}