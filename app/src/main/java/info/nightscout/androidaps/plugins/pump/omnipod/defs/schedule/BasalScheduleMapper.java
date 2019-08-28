package info.nightscout.androidaps.plugins.pump.omnipod.defs.schedule;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.data.Profile;

public class BasalScheduleMapper {
    // TODO add tests
    public static BasalSchedule mapProfileToBasalSchedule(Profile profile) {
        Profile.ProfileValue[] basalValues = profile.getBasalValues();
        List<BasalScheduleEntry> entries = new ArrayList<>();
        for(Profile.ProfileValue basalValue : basalValues) {
            entries.add(new BasalScheduleEntry(basalValue.value, Duration.standardSeconds(basalValue.timeAsSeconds)));
        }

        return new BasalSchedule(entries);
    }
}
