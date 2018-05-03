package info.nightscout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;


/**
 * Created by mike on 28.03.2018.
 */

@RunWith(RobolectricTestRunner.class)
public class MainAppTest {
    MainApp mainApp = new MainApp();

    @Test
    public void busTest() {
        Assert.assertNotNull(mainApp.bus());
    }

    @Test
    public void gsTest() {
        Assert.assertNotNull(mainApp.gs(R.string.app_name));
        Assert.assertNotNull(mainApp.gs(R.string.app_name, ""));
    }

    @Test
    public void gcTest() {
        Assert.assertEquals(-16711681, mainApp.gc(R.color.basal));
    }

    @Test
    public void instanceTest() {
        Assert.assertNotNull(mainApp.instance());
    }

    @Test
    public void getDbHelperTest() {
        Assert.assertNotNull(mainApp.getDbHelper());
    }

    @Test
    public void closeDbHelperTest() {
        mainApp.closeDbHelper();
        Assert.assertNull(mainApp.getDbHelper());
    }

    @Test
    public void getConfigBuilderTest() {
        Assert.assertNotNull(mainApp.getConfigBuilder());
    }

    @Test
    public void getConstraintCheckerTest() {
        Assert.assertNotNull(mainApp.getConstraintChecker());
    }

    @Test
    public void getPluginsListTest() {
        Assert.assertNotNull(mainApp.getPluginsList());
    }

    @Test
    public void getSpecificPluginsListTest() {
        // currently MDI, VP, R, Rv2, KoreanR, RS
        int expected;
        if (Config.NSCLIENT || Config.G5UPLOADER)
            expected = 1; // VirtualPump only
        else
            expected = 7;
        Assert.assertEquals(expected, mainApp.getSpecificPluginsList(PluginType.PUMP).size());
    }

    @Test
    public void getSpecificPluginsVisibleInListTest() {
        // currently MDI, VP, R, Rv2, KoreanR, RS
        int expected;
        if (Config.NSCLIENT || Config.G5UPLOADER)
            expected = 1; // VirtualPump only
        else
            expected = 7;
        Assert.assertEquals(expected, mainApp.getSpecificPluginsVisibleInList(PluginType.PUMP).size());
    }

    @Test
    public void getSpecificPluginsListByInterfaceTest() {
        // currently MDI, VP, R, Rv2, KoreanR, RS
        int expected;
        if (Config.NSCLIENT || Config.G5UPLOADER)
            expected = 1; // VirtualPump only
        else
            expected = 7;
        Assert.assertEquals(expected, mainApp.getSpecificPluginsListByInterface(PumpInterface.class).size());
    }

    @Test
    public void getSpecificPluginsVisibleInListByInterfaceTest() {
        // currently MDI, VP, R, Rv2, KoreanR, RS
        int expected;
        if (Config.NSCLIENT || Config.G5UPLOADER)
            expected = 1; // VirtualPump only
        else
            expected = 7;
        Assert.assertEquals(expected, mainApp.getSpecificPluginsVisibleInListByInterface(PumpInterface.class, PluginType.PUMP).size());
    }

    @Test
    public void getSpecificPluginTest() {
        // currently MDI, VP, R, Rv2, KoreanR, RS
        Assert.assertEquals("Overview", mainApp.getSpecificPlugin(OverviewPlugin.class).getName());
    }

    @Test
    public void isEngineeringModeOrReleaseTest() {
        Assert.assertEquals(!BuildConfig.APS, mainApp.isEngineeringModeOrRelease());
    }

    @Test
    public void getLogDirectoryTest() {
        // logger not initialized in Roboelectric
        Assert.assertNull(mainApp.getLogDirectory());
    }

}
