package info.nightscout.androidaps;

import com.j256.ormlite.stmt.query.In;

/**
 * Created by mike on 07.06.2016.
 */
public class Constants {
    public static final String MGDL = "mg/dl"; // This is Nightscout's representation
    public static final String MMOL = "mmol";

    public static final double MMOLL_TO_MGDL = 18; // 18.0182;
    public static final double MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL;

    public static final double basalAbsoluteOnlyForCheckLimit = 10101010d;
    public static final Integer basalPercentOnlyForCheckLimit = 10101010;
    public static final double bolusOnlyForCheckLimit = 10101010d;
    public static final Integer carbsOnlyForCheckLimit = 10101010;

    public static final Integer notificationID = 556677;

    public static final int hoursToKeepInDatabase = 72;
    public static final int daysToKeepHistoryInDatabase = 30;

    public static final long keepAliveMsecs = 5 * 60 * 1000L;

    // SMS COMMUNICATOR

    public static final long remoteBolusMinDistance = 15 * 60 * 1000L;


    // AMA
    public static final int MAX_DAILY_SAFETY_MULTIPLIER = 3;
    public static final int CURRENT_BASAL_SAFETY_MULTIPLIER = 4;

    public static final int BOLUSSNOOZE_DIA_ADVISOR = 2;
    public static final double AUTOSENS_MAX = 1.2d;
    public static final double AUTOSENS_MIN = 0.7d;
    public static final boolean AUTOSENS_ADJUST_TARGETS = false;
    public static final double MIN_5M_CARBIMPACT = 3d;

    // Circadian Percentage Profile
    public static final int CPP_MIN_PERCENTAGE = 50;
    public static final int CPP_MAX_PERCENTAGE = 200;
}
