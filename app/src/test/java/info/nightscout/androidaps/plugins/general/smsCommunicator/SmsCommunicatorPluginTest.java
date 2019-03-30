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
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.profile.simple.SimpleProfilePlugin;
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.XdripCalibrations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        L.class, SP.class, MainApp.class, DateUtil.class, ProfileFunctions.class,
        TreatmentsPlugin.class, SmsManager.class, IobCobCalculatorPlugin.class,
        CommandQueue.class, ConfigBuilderPlugin.class, NSUpload.class, ProfileInterface.class,
        SimpleProfilePlugin.class, XdripCalibrations.class, VirtualPumpPlugin.class
})

public class SmsCommunicatorPluginTest {

    private SmsCommunicatorPlugin smsCommunicatorPlugin;
    private LoopPlugin loopPlugin;

    private boolean hasBeenRun = false;

    @Test
    public void processSettingsTest() {
        // called from constructor
        Assert.assertEquals("1234", smsCommunicatorPlugin.allowedNumbers.get(0));
        Assert.assertEquals("5678", smsCommunicatorPlugin.allowedNumbers.get(1));
        Assert.assertEquals(2, smsCommunicatorPlugin.allowedNumbers.size());
    }

    @Test
    public void isCommandTest() {
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BOLUS", ""));
        smsCommunicatorPlugin.messageToConfirm = null;
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", ""));
        smsCommunicatorPlugin.messageToConfirm = new AuthRequest(smsCommunicatorPlugin, new Sms("1234", "ddd"), "RequestText", "ccode", new SmsAction() {
            @Override
            public void run() {
            }
        });
        Assert.assertTrue(smsCommunicatorPlugin.isCommand("BLB", "1234"));
        Assert.assertFalse(smsCommunicatorPlugin.isCommand("BLB", "2345"));
        smsCommunicatorPlugin.messageToConfirm = null;
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
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("12", "aText");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertTrue(sms.ignored);
        Assert.assertEquals("aText", smsCommunicatorPlugin.messages.get(0).text);

        //UNKNOWN
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "UNKNOWN");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("UNKNOWN", smsCommunicatorPlugin.messages.get(0).text);

        //BG
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BG");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BG", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("IOB:"));
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("Last BG: 100"));
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("COB: 10(2)g"));

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
        // ignore from other number
        smsCommunicatorPlugin.processSms(new Sms("5678", passCode));
        smsCommunicatorPlugin.processSms(new Sms("1234", "XXXX"));
        Assert.assertEquals("XXXX", smsCommunicatorPlugin.messages.get(3).text);
        Assert.assertEquals("Wrong code. Command cancelled.", smsCommunicatorPlugin.messages.get(4).text);
        //then correct code should not work
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(5).text);
        Assert.assertEquals(6, smsCommunicatorPlugin.messages.size()); // processed as common message

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

    @Test
    public void processProfileTest() {
        Sms sms;

        //PROFILE
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages.get(1).text);

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //PROFILE
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE LIST (no profile interface)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Not configured", smsCommunicatorPlugin.messages.get(1).text);

        ProfileInterface profileInterface = mock(SimpleProfilePlugin.class);
        when(ConfigBuilderPlugin.getPlugin().getActiveProfileInterface()).thenReturn(profileInterface);

        //PROFILE LIST (no profile defined)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Not configured", smsCommunicatorPlugin.messages.get(1).text);

        when(profileInterface.getProfile()).thenReturn(AAPSMocker.getValidProfileStore());

        //PROFILE STATUS
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE STATUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE STATUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals(AAPSMocker.TESTPROFILENAME, smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE LIST
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE LIST");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE LIST", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("1. " + AAPSMocker.TESTPROFILENAME, smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE 2 (non existing)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE 2");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 2", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE 1 0(wrong percentage)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE 0(wrong index)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //PROFILE 1(OK)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To switch profile to someProfile 100% reply with code"));

        //PROFILE 1 90(OK)
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "PROFILE 1 90");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("PROFILE 1 90", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To switch profile to someProfile 90% reply with code"));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Profile switch created", smsCommunicatorPlugin.messages.get(3).text);
    }

    @Test
    public void processBasalTest() {
        Sms sms;

        //BASAL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages.get(1).text);

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //BASAL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //BASAL CANCEL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL CANCEL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL CANCEL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To stop temp basal reply with code"));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Temp basal canceled\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);

        //BASAL a%
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL a%");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL a%", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //BASAL 10% 0
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL 10% 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 10% 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyBasalPercentConstraints(any(), any())).thenReturn(new Constraint<>(20));

        //BASAL 20% 20
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL 20% 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 20% 20", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To start basal 20% for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Temp basal 20% for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);

        //BASAL a
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL a", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //BASAL 1 0
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 1 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyBasalConstraints(any(), any())).thenReturn(new Constraint<>(1d));

        //BASAL 1 20
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BASAL 1 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BASAL 1 20", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To start basal 1.00U/h for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Temp basal 1.00U/h for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);

    }

    @Test
    public void processExtendedTest() {
        Sms sms;

        //EXTENDED
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages.get(1).text);

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //EXTENDED
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //EXTENDED CANCEL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED CANCEL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED CANCEL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To stop extended bolus reply with code"));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Extended bolus canceled\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);

        //EXTENDED a%
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED a%");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED a%", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyExtendedBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        //EXTENDED 1 0
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED 1 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED 1 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //EXTENDED 1 20
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "EXTENDED 1 20");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("EXTENDED 1 20", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To start extended bolus 1.00U for 20 min reply with code"));
        passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Extended bolus 1.00U for 20 min started successfully\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);
    }

    @Test
    public void processBolusTest() {
        Sms sms;

        //BOLUS
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages.get(1).text);

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //BOLUS
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        when(DateUtil.now()).thenReturn(1000L);
        //BOLUS 1
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote bolus not available. Try again later.", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(0d));

        when(DateUtil.now()).thenReturn(Constants.remoteBolusMinDistance + 1002L);

        //BOLUS 0
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //BOLUS a
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS a");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS a", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(MainApp.getConstraintChecker().applyExtendedBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        when(MainApp.getConstraintChecker().applyBolusConstraints(any())).thenReturn(new Constraint<>(1d));

        //BOLUS 1
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To deliver bolus 1.00U reply with code"));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Bolus 1.00U delivered successfully\nVirtual Pump", smsCommunicatorPlugin.messages.get(3).text);

        //BOLUS 1 (Suspended pump)
        smsCommunicatorPlugin.lastRemoteBolusTime = 0;
        PumpInterface pump = mock(VirtualPumpPlugin.class);
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(pump);
        when(pump.isSuspended()).thenReturn(true);
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "BOLUS 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("BOLUS 1", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Pump suspended", smsCommunicatorPlugin.messages.get(1).text);
        when(pump.isSuspended()).thenReturn(false);
    }

    @Test
    public void processCalTest() {
        Sms sms;

        //CAL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "CAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Remote command is not allowed", smsCommunicatorPlugin.messages.get(1).text);

        when(SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)).thenReturn(true);

        //CAL
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "CAL");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        //CAL 0
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "CAL 0");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL 0", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("Wrong format", smsCommunicatorPlugin.messages.get(1).text);

        when(XdripCalibrations.sendIntent(any())).thenReturn(true);
        //CAL 1
        smsCommunicatorPlugin.messages = new ArrayList<>();
        sms = new Sms("1234", "CAL 1");
        smsCommunicatorPlugin.processSms(sms);
        Assert.assertEquals("CAL 1", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertTrue(smsCommunicatorPlugin.messages.get(1).text.contains("To send calibration 1.00 reply with code"));
        String passCode = smsCommunicatorPlugin.messageToConfirm.confirmCode;
        smsCommunicatorPlugin.processSms(new Sms("1234", passCode));
        Assert.assertEquals(passCode, smsCommunicatorPlugin.messages.get(2).text);
        Assert.assertEquals("Calibration sent. Receiving must be enabled in xDrip.", smsCommunicatorPlugin.messages.get(3).text);
    }

    @Test
    public void sendNotificationToAllNumbers() {
        smsCommunicatorPlugin.messages = new ArrayList<>();
        smsCommunicatorPlugin.sendNotificationToAllNumbers("abc");
        Assert.assertEquals("abc", smsCommunicatorPlugin.messages.get(0).text);
        Assert.assertEquals("abc", smsCommunicatorPlugin.messages.get(1).text);
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
        AAPSMocker.mockConstraintsChecker();

        BgReading reading = new BgReading();
        reading.value = 100;
        List<BgReading> bgList = new ArrayList<>();
        bgList.add(reading);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getBgReadings()).thenReturn(bgList);
        PowerMockito.when(IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "SMS COB")).thenReturn(new CobInfo(10d, 2d));

        mockStatic(XdripCalibrations.class);
        mockStatic(DateUtil.class);
        mockStatic(SmsManager.class);
        SmsManager smsManager = mock(SmsManager.class);
        when(SmsManager.getDefault()).thenReturn(smsManager);

        when(SP.getString(R.string.key_smscommunicator_allowednumbers, "")).thenReturn("1234;5678");
        smsCommunicatorPlugin = SmsCommunicatorPlugin.getPlugin();
        smsCommunicatorPlugin.setPluginEnabled(PluginType.GENERAL, true);

        loopPlugin = mock(LoopPlugin.class);
        when(MainApp.getSpecificPlugin(LoopPlugin.class)).thenReturn(loopPlugin);

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

        VirtualPumpPlugin virtualPumpPlugin = VirtualPumpPlugin.getPlugin();
        when(ConfigBuilderPlugin.getPlugin().getActivePump()).thenReturn(virtualPumpPlugin);
    }

}
