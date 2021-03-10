package info.nightscout.androidaps.plugins.pump.omnipod.eros;

import android.os.Looper;

import org.joda.time.DateTimeZone;
import org.joda.time.tz.UTCProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;

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
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.rx.TestAapsSchedulers;

import static info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants.BASAL_STEP_DURATION;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@RunWith(PowerMockRunner.class)
public class OmnipodErosPumpPluginTest {

    @Mock HasAndroidInjector injector;
    AAPSLogger aapsLogger = new AAPSLoggerTest();
    RxBusWrapper rxBusWrapper = new RxBusWrapper(new TestAapsSchedulers());
    @Mock ResourceHelper resourceHelper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) ActivePluginProvider activePluginProvider;
    @Mock AapsOmnipodErosManager aapsOmnipodErosManager;
    @Mock CommandQueueProvider commandQueueProvider;
    @Mock RileyLinkUtil rileyLinkUtil;

    @Before
    public void prepare() {
        when(resourceHelper.gs(anyInt(), anyLong())).thenReturn("");
    }

    @Test
    @PrepareForTest(Looper.class)
    public <T> void testSetTempBasalPercent() {

        DateTimeZone.setProvider(new UTCProvider());

        // mock all the things
        PowerMockito.mockStatic(Looper.class);
        OmnipodErosPumpPlugin plugin = new OmnipodErosPumpPlugin(injector, aapsLogger, new TestAapsSchedulers(), rxBusWrapper, null,
                resourceHelper, activePluginProvider, null, null, aapsOmnipodErosManager, commandQueueProvider,
                null, null, null, null,
                rileyLinkUtil, null, null, null
        );
        when(activePluginProvider.getActiveTreatments().getTempBasalFromHistory(anyLong())).thenReturn(null);
        when(rileyLinkUtil.getRileyLinkHistory()).thenReturn(new ArrayList<>());
        when(injector.androidInjector()).thenReturn(instance -> {
        });
        Profile profile = mock(Profile.class);


        // always return a PumpEnactResult containing same rate and duration as input
        when(aapsOmnipodErosManager.setTemporaryBasal(any(TempBasalPair.class))).thenAnswer(
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
        assertEquals(result1.getAbsolute(), 0.4d, 0.01d);
        assertEquals(result1.getDuration(), 30);
        assertEquals(result2.getAbsolute(), 25d, 0.01d);
        assertEquals(result2.getDuration(), 30000);
        assertEquals(result3.getAbsolute(), 0d, 0.01d);
        assertEquals(result3.getDuration(), 30);
        assertEquals(result4.getAbsolute(), -1d, 0.01d);
        assertEquals(result4.getDuration(), -1);
        // this is validated downstream, see TempBasalExtraCommand
        assertEquals(result5.getAbsolute(), -0.25d, 0.01d);
        assertEquals(result5.getDuration(), 60);

        // Given zero basal
        when(profile.getBasal()).thenReturn(0d);
        // When
        result1 = plugin.setTempBasalPercent(8000, 90, profile, false);
        result2 = plugin.setTempBasalPercent(0, 0, profile, false);
        // Then return zero values
        assertEquals(result1.getAbsolute(), 0d, 0.01d);
        assertEquals(result1.getDuration(), 90);
        assertEquals(result2.getAbsolute(), -1d, 0.01d);
        assertEquals(result2.getDuration(), -1);

        // Given unhealthy basal
        when(profile.getBasal()).thenReturn(500d);
        // When treatment
        result1 = plugin.setTempBasalPercent(80, 30, profile, false);
        // Then return sane values
        assertEquals(result1.getAbsolute(), PumpType.Omnipod_Eros.determineCorrectBasalSize(500d * 0.8), 0.01d);
        assertEquals(result1.getDuration(), 30);

        // Given weird basal
        when(profile.getBasal()).thenReturn(1.234567d);
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 600, profile, false);
        // Then return sane values
        assertEquals(result1.getAbsolute(), 3.4567876, 0.01d);
        assertEquals(result1.getDuration(), 600);

        // Given negative basal
        when(profile.getBasal()).thenReturn(-1.234567d);
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 510, profile, false);
        // Then return negative value (this is validated further downstream, see TempBasalExtraCommand)
        assertEquals(result1.getAbsolute(), -3.4567876, 0.01d);
        assertEquals(result1.getDuration(), 510);
    }

}
