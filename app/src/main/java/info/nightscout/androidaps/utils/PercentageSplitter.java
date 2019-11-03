package info.nightscout.androidaps.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by mike on 22.12.2017.
 */

public class PercentageSplitter {
    // Matches "Profile name (200%,-2h)", "Profile name (50%)
    private static final Pattern splitPattern = Pattern.compile("(.+)\\(\\d+%(,-?\\d+h)?\\)");

    /** Removes the suffix for percentage and timeshift from a profile name. This is the inverse of what
     * {@link info.nightscout.androidaps.db.ProfileSwitch#getCustomizedName()} does.
     * Since the customized name is used for the PS upload to NS, this is needed get the original profile name
     * when retrieving the PS from NS again. */
    public static String pureName(String name) {
        Matcher percentageMatch = splitPattern.matcher(name);
        return percentageMatch.find() ? percentageMatch.group(1).trim() : name;
    }
}
