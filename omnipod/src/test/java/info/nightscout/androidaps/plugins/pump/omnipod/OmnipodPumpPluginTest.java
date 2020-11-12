package info.nightscout.androidaps.plugins.pump.omnipod;

import android.os.Looper;

import org.joda.time.DateTimeZone;
import org.joda.time.tz.UTCProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

import dagger.android.AndroidInjector;
import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.AAPSLoggerTest;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
public class OmnipodPumpPluginTest {

    @Mock HasAndroidInjector injector;
    AAPSLogger aapsLogger = new AAPSLoggerTest();
    RxBusWrapper rxBusWrapper = new RxBusWrapper();
    @Mock ResourceHelper resourceHelper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) ActivePluginProvider activePluginProvider;
    @Mock AapsOmnipodManager aapsOmnipodManager;
    @Mock CommandQueueProvider commandQueueProvider;
    @Mock RileyLinkUtil rileyLinkUtil;


    @Test
    @PrepareForTest(Looper.class)
    public <T> void testSetTempBasalPercent() {

        DateTimeZone.setProvider(new UTCProvider());

        // mock all the things
        PowerMockito.mockStatic(Looper.class);
        OmnipodPumpPlugin plugin = new OmnipodPumpPlugin(injector, aapsLogger, rxBusWrapper, null,
                resourceHelper, activePluginProvider, null, null, aapsOmnipodManager, commandQueueProvider,
                null, null, null, null, null,
                rileyLinkUtil, null, null
        );
        when(activePluginProvider.getActiveTreatments().getTempBasalFromHistory(anyLong())).thenReturn(null);
        when(rileyLinkUtil.getRileyLinkHistory()).thenReturn(new ArrayList<>());
        when(injector.androidInjector()).thenReturn(new AndroidInjector<Object>() {
            @Override public void inject(Object instance) {
            }
        });
        Profile profile = mock(Profile.class);


        // always return a PumpEnactResult containing same rate and duration as input
        when(aapsOmnipodManager.setTemporaryBasal(any(TempBasalPair.class))).thenAnswer(
                invocation -> {
                    TempBasalPair pair = invocation.getArgument(0);
                    PumpEnactResult result = new PumpEnactResult(injector);
                    result.absolute(pair.getInsulinRate());
                    result.duration(pair.getDurationMinutes());
                    return result;
                });


        // Given standard basal
        when(profile.getBasal()).thenReturn(0.5d);
        // When
        PumpEnactResult result1 = plugin.setTempBasalPercent(80, 30, profile, false);
        PumpEnactResult result2 = plugin.setTempBasalPercent(5000, 30000, profile, false);
        PumpEnactResult result3 = plugin.setTempBasalPercent(0, 30, profile, false);
        PumpEnactResult result4 = plugin.setTempBasalPercent(0, 0, profile, false);
        PumpEnactResult result5 = plugin.setTempBasalPercent(-50, 60, profile, false);
        // Then return correct values
        assertEquals(result1.absolute, 0.4d, 0.01d);
        assertEquals(result1.duration, 30);
        assertEquals(result2.absolute, 25d, 0.01d);
        assertEquals(result2.duration, 30000);
        assertEquals(result3.absolute, 0d, 0.01d);
        assertEquals(result3.duration, 30);
        assertEquals(result4.absolute, -1d, 0.01d);
        assertEquals(result4.duration, -1);
        // this is validated downstream, see TempBasalExtraCommand
        assertEquals(result5.absolute, -0.25d, 0.01d);
        assertEquals(result5.duration, 60);

        // Given zero basal
        when(profile.getBasal()).thenReturn(0d);
        // When
        result1 = plugin.setTempBasalPercent(8000, 90, profile, false);
        result2 = plugin.setTempBasalPercent(0, 0, profile, false);
        // Then return zero values
        assertEquals(result1.absolute, 0d, 0.01d);
        assertEquals(result1.duration, 90);
        assertEquals(result2.absolute, -1d, 0.01d);
        assertEquals(result2.duration, -1);

        // Given unhealthy basal
        when(profile.getBasal()).thenReturn(500d);
        // When treatment
        result1 = plugin.setTempBasalPercent(80, 30, profile, false);
        // Then return sane values
        assertEquals(result1.absolute, PumpType.Insulet_Omnipod.determineCorrectBasalSize(500d * 0.8), 0.01d);
        assertEquals(result1.duration, 30);

        // Given weird basal
        when(profile.getBasal()).thenReturn(1.234567d);
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 600, profile, false);
        // Then return sane values
        assertEquals(result1.absolute, 3.4567876, 0.01d);
        assertEquals(result1.duration, 600);

        // Given negative basal
        when(profile.getBasal()).thenReturn(-1.234567d);
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 510, profile, false);
        // Then return negative value (this is validated further downstream, see TempBasalExtraCommand)
        assertEquals(result1.absolute, -3.4567876, 0.01d);
        assertEquals(result1.duration, 510);
    }

}
