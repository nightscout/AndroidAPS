package info.nightscout.androidaps.plugins.general.smsCommunicator;

import android.telephony.SmsManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({L.class, SP.class, MainApp.class, DateUtil.class, ProfileFunctions.class, TreatmentsPlugin.class, SmsManager.class, IobCobCalculatorPlugin.class})

public class SmsCommunicatorPluginTest {

    SmsCommunicatorPlugin smsCommunicatorPlugin;
    LoopPlugin loopPlugin;

    @Test
    public void processSettingsTest() {
        // called from constructor
        Assert.assertEquals(smsCommunicatorPlugin.allowedNumbers.get(0), "1234");
        Assert.assertEquals(smsCommunicatorPlugin.allowedNumbers.get(1), "5678");
        Assert.assertEquals(smsCommunicatorPlugin.allowedNumbers.size(), 2);
    }

    @Test
    public void isAllowedNumberTest() {
        Assert.assertTrue(smsCommunicatorPlugin.isAllowedNumber("5678"));
        Assert.assertFalse(smsCommunicatorPlugin.isAllowedNumber("56"));
    }

    @Test
    public void processSmsTest() {
        Sms sms;

        // SMS from not allowed number should be ignored
        sms = new Sms("12", "aText");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertTrue(sms.ignored);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "aText");

        // test remote control disabled
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "LOOP STATUS");
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("Remote command is not allowed"));

        // enable remote control for next tests
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //BG
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BG");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "BG");
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("IOB:"));
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("Last BG: 100"));

        //LOOP STATUS : disabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "LOOP STATUS");
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(1).text, "Loop is disabled");

        //LOOP STATUS : suspended
        when(loopPlugin.minutesToEndOfSuspend()).thenReturn(10);
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "LOOP STATUS");
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(1).text, "Suspended (10 m)");

        //LOOP STATUS : enabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(0).text, "LOOP STATUS");
        Assert.assertEquals(smsCommunicatorPlugin.messages.get(1).text, "Loop is enabled");
    }

    @Before
    public void prepareTests() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockStrings();
        AAPSMocker.mockBus();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockTreatmentPlugin();
        AAPSMocker.mockIobCobCalculatorPlugin();

        BgReading reading = new BgReading();
        reading.value = 100;
        List<BgReading> bgList = new ArrayList<>();
        bgList.add(reading);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getBgReadings()).thenReturn(bgList);

        mockStatic(DateUtil.class);
        mockStatic(SmsManager.class);
        SmsManager smsManager = mock(SmsManager.class);
        when(SmsManager.getDefault()).thenReturn(smsManager);

        when(SP.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678");
        smsCommunicatorPlugin = new SmsCommunicatorPlugin();
        smsCommunicatorPlugin.setPluginEnabled(PluginType.GENERAL, true);

        loopPlugin = mock(LoopPlugin.class);
        when(MainApp.getSpecificPlugin(LoopPlugin.class)).thenReturn(loopPlugin);
    }

}
