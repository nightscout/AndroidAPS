package info.nightscout.androidaps.data;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.List;

import info.AAPSMocker;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 26.03.2018.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class, ConfigBuilderPlugin.class, TreatmentsPlugin.class, TreatmentService.class})
public class ProfileIntervalsTest {
    private final long startDate = DateUtil.now();
    ProfileIntervals<ProfileSwitch> list = new ProfileIntervals<>();

    @Test
    public void doTests() {
        // create one 10h interval and test value in and out
        list.add(new ProfileSwitch().date(startDate).duration((int) T.hours(10).mins()).profileName("1").profile(AAPSMocker.getValidProfile()));
        // for older date first record should be returned only if has zero duration
        Assert.assertEquals(null, list.getValueToTime(startDate - T.secs(1).msecs()));
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate)).profileName);
        Assert.assertEquals(null, list.getValueToTime(startDate + T.hours(10).msecs() + 1));

        list.reset();
        list.add(new ProfileSwitch().date(startDate).profileName("1").profile(AAPSMocker.getValidProfile()));
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate - T.secs(1).msecs())).profileName);
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate)).profileName);
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(10).msecs() + 1)).profileName);

        // switch to different profile after 5h
        list.add(new ProfileSwitch().date(startDate + T.hours(5).msecs()).duration(0).profileName("2").profile(AAPSMocker.getValidProfile()));
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate - T.secs(1).msecs())).profileName);
        Assert.assertEquals("1", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(5).msecs() - 1)).profileName);
        Assert.assertEquals("2", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(5).msecs() + 1)).profileName);

        // insert 1h interval inside
        list.add(new ProfileSwitch().date(startDate + T.hours(6).msecs()).duration((int) T.hours(1).mins()).profileName("3").profile(AAPSMocker.getValidProfile()));
        Assert.assertEquals("2", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(6).msecs() - 1)).profileName);
        Assert.assertEquals("3", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(6).msecs() + 1)).profileName);
        Assert.assertEquals("2", ((ProfileSwitch) list.getValueToTime(startDate + T.hours(7).msecs() + 1)).profileName);
    }

    @Test
    public void testCopyConstructor() {
        list.reset();
        list.add(new ProfileSwitch().date(startDate).duration((int) T.hours(10).mins()).profileName("4").profile(AAPSMocker.getValidProfile()));
        ProfileIntervals<ProfileSwitch> list2 = new ProfileIntervals<>(list);
        Assert.assertEquals(1, list2.getList().size());
    }

    @Test
    public void invalidProfilesShouldNotBeReturned() {
        list.reset();
        list.add(new ProfileSwitch().date(startDate + T.hours(1).msecs()).profileName("6"));
        Assert.assertEquals(null, list.get(0));
    }

    @Test
    public void testReversingArrays() {
        List<ProfileSwitch> someList = new ArrayList<>();
        someList.add(new ProfileSwitch().date(startDate).duration((int) T.hours(3).mins()).profileName("5").profile(AAPSMocker.getValidProfile()));
        someList.add(new ProfileSwitch().date(startDate + T.hours(1).msecs()).duration((int) T.hours(1).mins()).profileName("6").profile(AAPSMocker.getValidProfile()));
        list.reset();
        list.add(someList);
        Assert.assertEquals(startDate, list.get(0).date);
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversed(0).date);
        Assert.assertEquals(startDate + T.hours(1).msecs(), list.getReversedList().get(0).date);

    }

    @Before
    public void prepareMock() {
        AAPSMocker.mockMainApp();
        AAPSMocker.mockStrings();
        AAPSMocker.mockConfigBuilder();
        AAPSMocker.mockTreatmentPlugin();
    }
}
