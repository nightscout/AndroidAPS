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
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.XdripCalibrations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        L.class, SP.class, MainApp.class, DateUtil.class, ProfileFunctions.class,
        TreatmentsPlugin.class, SmsManager.class, IobCobCalculatorPlugin.class,
        CommandQueue.class, ConfigBuilderPlugin.class, NSUpload.class, ProfileInterface.class,
        LocalProfilePlugin.class, XdripCalibrations.class, VirtualPumpPlugin.class, LoopPlugin.class
})

public class SmsCommunicatorPluginTest {

    private SmsCommunicatorPlugin smsCommunicatorPlugin;
    private LoopPlugin loopPlugin;

    private boolean hasBeenRun = false;

    private VirtualPumpPlugin virtualPumpPlugin;

    @Test
    public void processSettingsTest() {
        // called from constructor
        Assert.assertEquals("1234", smsCommunicatorPlugin.getAllowedNumbers().get(0));
        Assert.assertEquals("5678", smsCommunicatorPlugin.getAllowedNumbers().get(1));
        Assert.assertEquals(2, smsCommunicatorPlugin.getAllowedNumbers().size());
    }

    @Test
    public void isCommandTest() {
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BOLUS", ""));
        smsCommunicatorPlugin.setMessageToConfirm(null);
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", ""));
        smsCommunicatorPlugin.setMessageToConfirm(new AuthRequest(smsCommunicatorPlugin, new Sms("1234", "ddd"), "RequestText", "ccode", new SmsAction() {
            @Override
            public void run() {
            }
        }));
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BLB", "1234"));
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", "2345"));
        smsCommunicatorPlugin.setMessageToConfirm(null);
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
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("12", "aText");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertTrue(sms.getIgnored());
        Assert.assertEquals("aText", smsCommunicatorPlugin.getMessages().get(0).getText());

        //UNKNOWN
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "UNKNOWN");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("UNKNOWN", smsCommunicatorPlugin.getMessages().get(0).getText());

        //BG
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BG");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BG", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("IOB:"));
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("Last BG: 100"));
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("COB: 10(2)g"));

        // LOOP : test remote control disabled
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("Remote command is not allowed"));
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //LOOP STATUS : disabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP STATUS : suspended
        when(loopPlugin.minutesToEndOfSuspend()).thenReturn(10);
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(true);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Suspended (10 m)", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP STATUS : enabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP STATUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP : wrong format
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP DISABLE : already disabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP DISABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop is disabled", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP DISABLE : from enabled
        hasBeenRun = false;
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        doAnswer((Answer) invocation -> {
            hasBeenRun = true;
            return null;
        }).when(loopPlugin).setPluginEnabled(PluginType.LOOP, false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP DISABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP DISABLE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop has been disabled Temp basal canceled", smsCommunicatorPlugin.getMessages().get(1).getText());
        Assert.assertTrue(hasBeenRun);

        //LOOP ENABLE : already enabled
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP ENABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop is enabled", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP ENABLE : from disabled
        hasBeenRun = false;
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(false);
        doAnswer((Answer) invocation -> {
            hasBeenRun = true;
            return null;
        }).when(loopPlugin).setPluginEnabled(PluginType.LOOP, true);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP ENABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP ENABLE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop has been enabled", smsCommunicatorPlugin.getMessages().get(1).getText());
        Assert.assertTrue(hasBeenRun);

        //LOOP RESUME : already enabled
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP RESUME");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP RESUME", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Loop resumed", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP SUSPEND 1 2: wrong format
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP SUSPEND 1 2");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP SUSPEND 1 2", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP SUSPEND 0 : wrong duration
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP SUSPEND 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP SUSPEND 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong duration", smsCommunicatorPlugin.getMessages().get(1).getText());

        //LOOP SUSPEND 100 : suspend for 100 min + correct answer
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP SUSPEND 100");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP SUSPEND 100", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To suspend loop for 100 minutes reply with code "));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Loop suspended Temp basal canceled", smsCommunicatorPlugin.getMessages().get(3).getText());

        //LOOP SUSPEND 200 : limit to 180 min + wrong answer
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP SUSPEND 200");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP SUSPEND 200", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To suspend loop for 180 minutes reply with code "));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        // ignore from other number
        smsCommunicatorPlugin.processSms(new Sms("5678", passCode));
        smsCommunicatorPlugin.processSms(new Sms("1234", "XXXX"));
        Assert.assertEquals("XXXX", smsCommunicatorPlugin.getMessages().get(3).getText());
        Assert.assertEquals("Wrong code. Command cancelled.", smsCommunicatorPlugin.getMessages().get(4).getText());
        //then correct code should not work
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(5).getText());
        Assert.assertEquals(6, smsCommunicatorPlugin.getMessages().size()); // processed as common message

        //LOOP BLABLA
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "LOOP BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("LOOP BLABLA", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //TREATMENTS REFRESH
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TREATMENTS REFRESH");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("TREATMENTS REFRESH", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("TREATMENTS REFRESH"));

        //TREATMENTS BLA BLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TREATMENTS BLA BLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("TREATMENTS BLA BLA", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //TREATMENTS BLABLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TREATMENTS BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("TREATMENTS BLABLA", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //NSCLIENT RESTART
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "NSCLIENT RESTART");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("NSCLIENT RESTART", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("NSCLIENT RESTART"));

        //NSCLIENT BLA BLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "NSCLIENT BLA BLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("NSCLIENT BLA BLA", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //NSCLIENT BLABLA
        when(loopPlugin.isEnabled(PluginType.LOOP)).thenReturn(true);
        when(loopPlugin.isSuspended()).thenReturn(false);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "NSCLIENT BLABLA");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("NSCLIENT BLABLA", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //PUMP
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PUMP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PUMP", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Virtual Pump", smsCommunicatorPlugin.getMessages().get(1).getText());

        //HELP
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "HELP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("HELP", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("PUMP"));

        //HELP PUMP
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "HELP PUMP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("HELP PUMP", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("PUMP"));

        //SMS : wrong format
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "SMS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("SMS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //SMS STOP
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "SMS DISABLE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("SMS DISABLE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To disable the SMS Remote Service reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("SMS Remote Service stopped. To reactivate it, use AAPS on master smartphone."));

        //TARGET : wrong format
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TARGET");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertFalse(sms.getIgnored());
        Assert.assertEquals("TARGET", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //TARGET MEAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TARGET MEAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("TARGET MEAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To set the Temp Target"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("set successfully"));

        //TARGET STOP/CANCEL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "TARGET STOP");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("TARGET STOP", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To cancel Temp Target reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("Temp Target canceled successfully"));
    }

    @Test
    public void processProfileTest() {
        Sms sms;

        //PROFILE
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //PROFILE
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE LIST (no profile interface)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Not configured", smsCommunicatorPlugin.getMessages().get(1).getText());

        ProfileInterface profileInterface = mock(LocalProfilePlugin.class);
        when(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface()).thenReturn(profileInterface);

        //PROFILE LIST (no profile defined)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Not configured", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(profileInterface.getProfile()).thenReturn(AAPSMocker.getValidProfileStore());

        //PROFILE STATUS
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE STATUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals(AAPSMocker.TESTPROFILENAME, smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE LIST
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("1. " + AAPSMocker.TESTPROFILENAME, smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE 2 (non existing)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE 2");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 2", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE 1 0(wrong percentage)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE 0(wrong index)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //PROFILE 1(OK)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To switch profile to someProfile 100% reply with code"));

        //PROFILE 1 90(OK)
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "PROFILE 1 90");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1 90", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To switch profile to someProfile 90% reply with code"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Profile switch created", smsCommunicatorPlugin.getMessages().get(3).getText());
    }

    @Test
    public void processBasalTest() {
        Sms sms;

        //BASAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //BASAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //BASAL CANCEL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL CANCEL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL CANCEL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To stop temp basal reply with code"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("Temp basal canceled"));

        //BASAL a%
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL a%");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL a%", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //BASAL 10% 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL 10% 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 10% 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyBasalPercentConstraints(any(), any())).thenReturn(new Constraint<>(20));

        //BASAL 20% 20
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL 20% 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 20% 20", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To start basal 20% for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Temp basal 20% for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.getMessages().get(3).getText());

        //BASAL a
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL a", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //BASAL 1 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 1 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyBasalConstraints(any(), any())).thenReturn(new Constraint<>(1d));

        //BASAL 1 20
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BASAL 1 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 1 20", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To start basal 1.00U/h for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Temp basal 1.00U/h for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.getMessages().get(3).getText());

    }

    @Test
    public void processExtendedTest() {
        Sms sms;

        //EXTENDED
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //EXTENDED
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //EXTENDED CANCEL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED CANCEL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED CANCEL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To stop extended bolus reply with code"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("Extended bolus canceled"));

        //EXTENDED a%
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED a%");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED a%", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyExtendedBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        //EXTENDED 1 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED 1 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //EXTENDED 1 20
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "EXTENDED 1 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED 1 20", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To start extended bolus 1.00U for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Extended bolus 1.00U for 20 min started successfully\nnull\nVirtual Pump", smsCommunicatorPlugin.getMessages().get(3).getText());
    }

    @Test
    public void processBolusTest() {
        Sms sms;

        //BOLUS
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //BOLUS
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        when(DateUtil.now()).thenReturn(1000L);
        when(SP.getLong(R.string.key_smscommunicator_remotebolusmindistance, T.msecs(Constants.remoteBolusMinDistance).mins())).thenReturn(15L);
        //BOLUS 1
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote bolus not available. Try again later.", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(0d));

        when(DateUtil.now()).thenReturn(Constants.remoteBolusMinDistance + 1002L);

        //BOLUS 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //BOLUS a
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS a", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyExtendedBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        //BOLUS 1
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To deliver bolus 1.00U reply with code"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().contains("Bolus 1.00U delivered successfully"));

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.setLastRemoteBolusTime(0);
        when(virtualPumpPlugin.isSuspended()).thenReturn(true);
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Pump suspended", smsCommunicatorPlugin.getMessages().get(1).getText());
        when(virtualPumpPlugin.isSuspended()).thenReturn(false);

        //BOLUS 1 a
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 1 a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1 a", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //BOLUS 1 MEAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "BOLUS 1 MEAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1 MEAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To deliver meal bolus 1.00U reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Meal Bolus 1.00U delivered successfully\nVirtual Pump\nTarget 5.0 for 45 minutes", smsCommunicatorPlugin.getMessages().get(3).getText());
    }

    @Test
    public void processCalTest() {
        Sms sms;

        //CAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //CAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        //CAL 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CAL 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(XdripCalibrations.sendIntent(any())).thenReturn(true);
        //CAL 1
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CAL 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To send calibration 1.00 reply with code"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertEquals("Calibration sent. Receiving must be enabled in xDrip.", smsCommunicatorPlugin.getMessages().get(3).getText());
    }

    @Test
    public void processCarbsTest() {
        Sms sms;

        when(DateUtil.now()).thenReturn(1000000L);
        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(false);
        //CAL
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //CARBS
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyCarbsConstraints(any())).thenReturn(new Constraint<>(0));

        //CARBS 0
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 0", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.getMessages().get(1).getText());

        when(MainApp.getConstraintChecker().applyCarbsConstraints(any())).thenReturn(new Constraint<>(1));

        //CARBS 1
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 1", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To enter 1g at"));
        String passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().startsWith("Carbs 1g entered successfully"));

        //CARBS 1 a
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 1 a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 1 a", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("Wrong format"));

        //CARBS 1 00
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 1 00");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 1 00", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("Wrong format"));

        //CARBS 1 12:01
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 1 12:01");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 1 12:01", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To enter 1g at 12:01PM reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().startsWith("Carbs 1g entered successfully"));

        //CARBS 1 3:01AM
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        sms = new Sms("1234", "CARBS 1 3:01AM");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CARBS 1 3:01AM", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(1).getText().contains("To enter 1g at 03:01AM reply with code"));
        passCode = smsCommunicatorPlugin.getMessageToConfirm().getConfirmCode();
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.getMessages().get(2).getText());
        Assert.assertTrue(smsCommunicatorPlugin.getMessages().get(3).getText().startsWith("Carbs 1g entered successfully"));
    }

    @Test
    public void sendNotificationToAllNumbers() {
        smsCommunicatorPlugin.setMessages(new ArrayList<>());
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc");
        Assert.assertEquals("abc", smsCommunicatorPlugin.getMessages().get(0).getText());
        Assert.assertEquals("abc", smsCommunicatorPlugin.getMessages().get(1).getText());
    }

    @Before
    public void prepareTests() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockApplicationContext();
        AAPSMocker.mockSP();
        AAPSMocker.mockL();
        AAPSMocker.mockStrings();
        AAPSMocker.mockProfileFunctions();
        AAPSMocker.mockTreatmentPlugin();
        AAPSMocker.mockTreatmentService();
        AAPSMocker.mockIobCobCalculatorPlugin();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockCommandQueue();
        AAPSMocker.mockNSUpload();
        AAPSMocker.mockConstraintsChecker();

        BgReading reading = new BgReading();
        reading.value = 100;
        List<BgReading> bgList = new ArrayList<>();
        bgList.add(reading);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getBgReadings()).thenReturn(bgList);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "SMS COB")).thenReturn(new CobInfo(10d, 2d));

        mockStatic(XdripCalibrations.class);
        spy(DateUtil.class);
        mockStatic(SmsManager.class);
        SmsManager smsManager = mock(SmsManager.class);
        when(SmsManager.getDefault()).thenReturn(smsManager);

        when(SP.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678");
        smsCommunicatorPlugin = SmsCommunicatorPlugin.INSTANCE;
        smsCommunicatorPlugin.setPluginEnabled(PluginType.GENERAL, true);

        mockStatic(LoopPlugin.class);
        loopPlugin = mock(LoopPlugin.class);
        when(LoopPlugin.getPlugin()).thenReturn(loopPlugin);

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.result = new PumpEnactResult().success(true);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).cancelTempBasal(anyBoolean(), any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(0);
            callback.result = new PumpEnactResult().success(true);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).cancelExtended(any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.result = new PumpEnactResult().success(true);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).readStatus(anyString(), any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(1);
            callback.result = new PumpEnactResult().success(true).bolusDelivered(1);
            callback.run();
            return null;
        }).when(AAPSMocker.queue).bolus(any(DetailedBolusInfo.class), any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(4);
            callback.result = new PumpEnactResult().success(true).isPercent(true).percent(invocation.getArgument(0)).duration(invocation.getArgument(1));
            callback.run();
            return null;
        }).when(AAPSMocker.queue).tempBasalPercent(anyInt(), anyInt(), anyBoolean(), any(), any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(4);
            callback.result = new PumpEnactResult().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1));
            callback.run();
            return null;
        }).when(AAPSMocker.queue).tempBasalAbsolute(anyDouble(), anyInt(), anyBoolean(), any(), any(Callback.class));

        Mockito.doAnswer(invocation -> {
            Callback callback = invocation.getArgument(2);
            callback.result = new PumpEnactResult().success(true).isPercent(false).absolute(invocation.getArgument(0)).duration(invocation.getArgument(1));
            callback.run();
            return null;
        }).when(AAPSMocker.queue).extendedBolus(anyDouble(), anyInt(), any(Callback.class));

        virtualPumpPlugin = mock(VirtualPumpPlugin.class);
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(virtualPumpPlugin);
        when(virtualPumpPlugin.shortStatus(anyBoolean())).thenReturn("Virtual Pump");
        when(virtualPumpPlugin.isSuspended()).thenReturn(false);
    }

}
