package info.nightscout.androidaps.plugins.PumpVirtual;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpInsight.InsightPlugin;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

import static org.junit.Assert.*;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Created by andy on 5/13/18.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, ToastUtils.class, Context.class, SP.class})
public class VirtualPumpPluginUTest {


    VirtualPumpPlugin virtualPumpPlugin;



    @Before
    public void prepareMocks() throws Exception {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockBus();
        AAPSMocker.mockStrings();
        AAPSMocker.mockCommandQueue();
        AAPSMocker.mockSP();

        virtualPumpPlugin = VirtualPumpPlugin.getPlugin();
    }


    @Test
    public void getPumpType() throws Exception {
    }

    @Test
    public void refreshConfiguration() throws Exception {

        when(SP.getString("virtualpump_type", "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        Assert.assertEquals(PumpType.AccuChekCombo, virtualPumpPlugin.getPumpType());
    }


    @Test
    public void refreshConfigurationTwice() throws Exception {

        when(SP.getString("virtualpump_type", "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        when(SP.getString("virtualpump_type", "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        Assert.assertEquals(PumpType.AccuChekCombo, virtualPumpPlugin.getPumpType());
    }

}