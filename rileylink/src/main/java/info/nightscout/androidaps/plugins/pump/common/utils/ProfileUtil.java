package info.nightscout.androidaps.plugins.pump.common.utils;

import java.util.Locale;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

public class ProfileUtil {


    public static String getProfileDisplayable(Profile profile, PumpType pumpType) {

        StringBuilder stringBuilder = new StringBuilder();

        for (Profile.ProfileValue basalValue : profile.getBasalValues()) {

            double basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value);

            int hour = basalValue.timeAsSeconds / (60 * 60);

            stringBuilder.append((hour < 10 ? "0" : "") + hour + ":00");

            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue));
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() > 3)
            return stringBuilder.substring(0, stringBuilder.length() - 2);
        else
            return stringBuilder.toString();
    }

    public static String getBasalProfilesDisplayable(Profile.ProfileValue[] profiles, PumpType pumpType) {

        StringBuilder stringBuilder = new StringBuilder();

        for (Profile.ProfileValue basalValue : profiles) {

            double basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value);

            int hour = basalValue.timeAsSeconds / (60 * 60);

            stringBuilder.append((hour < 10 ? "0" : "") + hour + ":00");

            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue));
            stringBuilder.append(", ");
        }
        if (stringBuilder.length() > 3)
            return stringBuilder.substring(0, stringBuilder.length() - 2);
        else
            return stringBuilder.toString();
    }


}
