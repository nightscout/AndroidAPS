package info.nightscout.androidaps.plugins.general.smsCommunicator;

import android.telephony.SmsManager;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        L.class, SP.class, MainApp.class, DateUtil.class, ProfileFunctions.class,
        TreatmentsPlugin.class, SmsManager.class, IobCobCalculatorPlugin.class,
        CommandQueue.class, ConfigBuilderPlugin.class, NSUpload.class
})

public class SmsCommunicatorPluginTest {

    SmsCommunicatorPlugin smsCommunicatorPlugin;
    LoopPlugin loopPlugin;

    boolean hasBeenRun = false;

    @Test
    public void processSettingsTest() {
        // called from constructor
        Assert.assertEquals("1234", smsCommunicatorPlugin.allowedNumbers.get(0));
        Assert.assertEquals("5678", smsCommunicatorPlugin.allowedNumbers.get(1));
        Assert.assertEquals(2, smsCommunicatorPlugin.allowedNumbers.size());
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
        Assert.assertEquals("aText", smsCommunicatorPlugin.messages.get(0).text);

        //UNKNOWN
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "UNKNOWN");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("UNKNOWN", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Uknown command or wrong reply", smsCommunicatorPlugin.messages.get(1).text);

        //BG
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BG");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BG", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("IOB:"));
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("Last BG: 100"));

        // LOOP : test remote control disabled
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("Remote command is not allowed"));
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //LOOP STATUS : disabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP STATUS : suspended
        when(loopPlugin.minutesToEndOfSuspend()).thenReturn(10);
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Suspended (10 m)", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP STATUS : enabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP : wrong format
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP DISABLE : already disabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP DISABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP DISABLE : from enabled
        hasBeenRun = false;
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        doAnswer((Answer) invocation -> {
            hasBeenRun = true;
            return null;
        }).when(loopPlugin).setPluginEnabled(PluginType.LOOP, false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP DISABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop has been disabled Temp basal canceled", smsCommunicatorPlugin.messages.get(1).text);
        Assert.assertTrue(hasBeenRun);

        //LOOP ENABLE : already enabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP ENABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP ENABLE : from disabled
        hasBeenRun = false;
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        doAnswer((Answer) invocation -> {
            hasBeenRun = true;
            return null;
        }).when(loopPlugin).setPluginEnabled(PluginType.LOOP, true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP ENABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop has been enabled", smsCommunicatorPlugin.messages.get(1).text);
        Assert.assertTrue(hasBeenRun);

        //LOOP RESUME : already enabled
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP RESUME");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP RESUME", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Loop resumed", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP SUSPEND 1 2: wrong format
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP SUSPEND 1 2");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP SUSPEND 1 2", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP SUSPEND 0 : wrong duration
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP SUSPEND 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP SUSPEND 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong duration", smsCommunicatorPlugin.messages.get(1).text);

        //LOOP SUSPEND 100 : suspend for 100 min + correct answer
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP SUSPEND 100");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP SUSPEND 100", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To suspend loop for 100 minutes reply with code "));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Loop suspended Temp basal canceled", smsCommunicatorPlugin.messages.get(3).text);

        //LOOP SUSPEND 200 : limit to 180 min + wrong answer
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP SUSPEND 200");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP SUSPEND 200", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To suspend loop for 180 minutes reply with code "));
        passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", "XXXX"));
        Assert.assertEquals("XXXX", smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Wrong code. Command cancelled.", smsCommunicatorPlugin.messages.get(3).text);
        //then correct code should not work
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(4).text);
        Assert.assertEquals("Uknown command or wrong reply", smsCommunicatorPlugin.messages.get(5).text);

        //LOOP BLABLA
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "LOOP BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("LOOP BLABLA", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //TREATMENTS REFRESH
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "TREATMENTS REFRESH");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("TREATMENTS REFRESH", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("TREATMENTS REFRESH"));

        //TREATMENTS BLA BLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "TREATMENTS BLA BLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("TREATMENTS BLA BLA", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //TREATMENTS BLABLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "TREATMENTS BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("TREATMENTS BLABLA", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //NSCLIENT RESTART
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "NSCLIENT RESTART");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("NSCLIENT RESTART", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("NSCLIENT RESTART"));

        //NSCLIENT BLA BLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "NSCLIENT BLA BLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("NSCLIENT BLA BLA", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //NSCLIENT BLABLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "NSCLIENT BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.ignored);
        Assert.assertEquals("NSCLIENT BLABLA", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //PUMP
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PUMP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PUMP", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Virtual Pump", smsCommunicatorPlugin.messages.get(1).text);

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
        AAPSMocker.mockTreatmentService();
        AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockCommandQueue();
        AAPSMocker.mockNSUpload();

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
        smsCommunicatorPlugin = SmsCommunicatorPlugin.getPlugin();
        smsCommunicatorPlugin.setPluginEnabled(PluginType.GENERAL, true);

        loopPlugin = mock(LoopPlugin.class);
        when(MainApp.getSpecificPlugin(LoopPlugin.class)).thenReturn(loopPlugin);

        Mockito.doAnswer((Answer) invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.result = new PumpEnactResult().success(true);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).cancelTempBasal(anyBoolean(), any(Callback.class));

        Mockito.doAnswer((Answer) invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.result = new PumpEnactResult().success(true);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).readStatus(anyString(), any(Callback.class));

        VirtualPumpPlugin virtualPumpPlugin = VirtualPumpPlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(virtualPumpPlugin);
    }

}
