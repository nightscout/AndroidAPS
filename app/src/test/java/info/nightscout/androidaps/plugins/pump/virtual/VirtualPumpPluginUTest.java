package info.nightscout.androidaps.plugins.pump.virtual;

import android.content.Context;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.ToastUtils;

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
    public void getPumpType() {
    }

    @Test
    public void refreshConfiguration() {

        when(SP.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        Assert.assertEquals(PumpType.AccuChekCombo, virtualPumpPlugin.getPumpType());
    }


    @Test
    public void refreshConfigurationTwice() {

        when(SP.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        when(SP.getString(R.string.key_virtualpump_type, "Generic AAPS")).thenReturn("Accu-Chek Combo");

        virtualPumpPlugin.refreshConfiguration();

        Assert.assertEquals(PumpType.AccuChekCombo, virtualPumpPlugin.getPumpType());
    }

}