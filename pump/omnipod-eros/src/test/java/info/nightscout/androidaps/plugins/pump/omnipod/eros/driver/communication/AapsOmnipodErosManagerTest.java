package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalScheduleEntry;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager;
import info.nightscout.interfaces.profile.Profile;

public class AapsOmnipodErosManagerTest {

    @Test
    public void validProfile() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                new Profile.ProfileValue(0, 0.5),
                new Profile.ProfileValue(18000, 1.0),
                new Profile.ProfileValue(50400, 3.05)
        });

        BasalSchedule basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile);

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
        assertThrows("Profile can not be null", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(null));
    }

    @Test
    public void invalidProfileNullEntries() {
        assertThrows("Basal values can not be null", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(mock(Profile.class)));
    }

    @Test
    public void invalidProfileZeroEntries() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[0]);

        assertThrows("Entries can not be empty", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(profile));
    }

    @Test
    public void invalidProfileNonZeroOffset() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                new Profile.ProfileValue(1800, 0.5)
        });

        assertThrows("First basal schedule entry should have 0 offset", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(profile));
    }

    @Test
    public void invalidProfileMoreThan24Hours() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                new Profile.ProfileValue(0, 0.5),
                new Profile.ProfileValue(86400, 0.5)
        });

        assertThrows("Invalid start time", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(profile));
    }

    @Test
    public void invalidProfileNegativeOffset() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                new Profile.ProfileValue(-1, 0.5)
        });

        assertThrows("Invalid start time", IllegalArgumentException.class, () -> AapsOmnipodErosManager.mapProfileToBasalSchedule(profile));
    }

    @Test
    public void roundsToSupportedPrecision() {
        Profile profile = mock(Profile.class);

        when(profile.getBasalValues()).thenReturn(new Profile.ProfileValue[]{
                new Profile.ProfileValue(0, 0.04),
        });

        BasalSchedule basalSchedule = AapsOmnipodErosManager.mapProfileToBasalSchedule(profile);
        BasalScheduleEntry basalScheduleEntry = basalSchedule.getEntries().get(0);

        assertEquals(0.05D, basalScheduleEntry.getRate(), 0.000001);
    }
}