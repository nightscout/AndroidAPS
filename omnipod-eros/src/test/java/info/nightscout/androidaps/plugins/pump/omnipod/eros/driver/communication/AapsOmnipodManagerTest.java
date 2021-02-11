package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication;

import org.joda.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.powermock.api.mockito.PowerMockito;

import java.util.List;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class AapsOmnipodManagerTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void validProfile() {
        Profile profile = mock(Profile.class);

        Profile.ProfileValue value1 = mock(Profile.ProfileValue.class);
        value1.timeAsSeconds = 0;
        value1.value = 0.5D;

        Profile.ProfileValue value2 = mock(Profile.ProfileValue.class);
        value2.timeAsSeconds = 18000;
        value2.value = 1.0D;

        Profile.ProfileValue value3 = mock(Profile.ProfileValue.class);
        value3.timeAsSeconds = 50400;
        value3.value = 3.05D;

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                value1,
                value2,
                value3
        });

        BasalSchedule basalSchedule = AapsOmnipodManager.mapProfileToBasalSchedule(profile);

        List<BasalScheduleEntry> entries = basalSchedule.getEntries();
        assertEquals(3, entries.size());

        BasalScheduleEntry entry1 = entries.get(0);
        assertEquals(Duration.standardSeconds(0), entry1.getStartTime());
        assertEquals(0.5D, entry1.getRate(), 0.000001);

        BasalScheduleEntry entry2 = entries.get(1);
        assertEquals(Duration.standardSeconds(18000), entry2.getStartTime());
        assertEquals(1.0D, entry2.getRate(), 0.000001);

        BasalScheduleEntry entry3 = entries.get(2);
        assertEquals(Duration.standardSeconds(50400), entry3.getStartTime());
        assertEquals(3.05D, entry3.getRate(), 0.000001);
    }

    @Test
    public void invalidProfileNullProfile() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Profile can not be null");
        AapsOmnipodManager.mapProfileToBasalSchedule(null);
    }

    @Test
    public void invalidProfileNullEntries() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Basal values can not be null");
        AapsOmnipodManager.mapProfileToBasalSchedule(mock(Profile.class));
    }

    @Test
    public void invalidProfileZeroEntries() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Entries can not be empty");
        Profile profile = mock(Profile.class);

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[0]);

        AapsOmnipodManager.mapProfileToBasalSchedule(profile);
    }

    @Test
    public void invalidProfileNonZeroOffset() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("First basal schedule entry should have 0 offset");

        Profile profile = mock(Profile.class);

        Profile.ProfileValue value = mock(Profile.ProfileValue.class);
        value.timeAsSeconds = 1800;
        value.value = 0.5D;

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                value,
        });

        AapsOmnipodManager.mapProfileToBasalSchedule(profile);
    }

    @Test
    public void invalidProfileMoreThan24Hours() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid start time");

        Profile profile = mock(Profile.class);

        Profile.ProfileValue value1 = mock(Profile.ProfileValue.class);
        value1.timeAsSeconds = 0;
        value1.value = 0.5D;

        Profile.ProfileValue value2 = mock(Profile.ProfileValue.class);
        value2.timeAsSeconds = 86400;
        value2.value = 0.5D;

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                value1,
                value2
        });

        AapsOmnipodManager.mapProfileToBasalSchedule(profile);
    }

    @Test
    public void invalidProfileNegativeOffset() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid start time");

        Profile profile = mock(Profile.class);

        Profile.ProfileValue value = mock(Profile.ProfileValue.class);
        value.timeAsSeconds = -1;
        value.value = 0.5D;

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                value,
        });

        AapsOmnipodManager.mapProfileToBasalSchedule(profile);
    }

    @Test
    public void roundsToSupportedPrecision() {
        Profile profile = mock(Profile.class);

        Profile.ProfileValue value = mock(Profile.ProfileValue.class);
        value.timeAsSeconds = 0;
        value.value = 0.04D;

        PowerMockito.when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                value,
        });

        BasalSchedule basalSchedule = AapsOmnipodManager.mapProfileToBasalSchedule(profile);
        BasalScheduleEntry basalScheduleEntry = basalSchedule.getEntries().get(0);

        assertEquals(0.05D, basalScheduleEntry.getRate(), 0.000001);
    }
}