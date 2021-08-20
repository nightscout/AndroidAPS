package info.nightscout.androidaps;

import info.nightscout.androidaps.utils.T;

/**
 * Created by mike on 07.06.2016.
 */
public class Constants {
    public static final String MGDL = "mg/dl"; // This is Nightscout's representation
    public static final String MMOL = "mmol";

    public static final double MMOLL_TO_MGDL = 18; // 18.0182;
    public static final double MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL;

    public static final double defaultDIA = 5d;

    public static final Double REALLYHIGHBASALRATE = 1111111d;
    public static final Integer REALLYHIGHPERCENTBASALRATE = 1111111;
    public static final double REALLYHIGHBOLUS = 1111111d;
    public static final Integer REALLYHIGHCARBS = 1111111;
    public static final double REALLYHIGHIOB = 1111111d;

    public static final Integer notificationID = 556677;

    public static final int hoursToKeepInDatabase = 72;
    public static final int daysToKeepHistoryInDatabase = 30;

    // SMS COMMUNICATOR
    public static final long remoteBolusMinDistance = 15 * 60 * 1000L;

    // Circadian Percentage Profile
    public static final int CPP_MIN_PERCENTAGE = 30;
    public static final int CPP_MAX_PERCENTAGE = 250;
    public static final int CPP_MIN_TIMESHIFT = -6;
    public static final int CPP_MAX_TIMESHIFT = 23;

    public static final double MAX_PROFILE_SWITCH_DURATION = 7 * 24 * 60; // [min] ~ 7 days

    //DanaR
    public static final double dailyLimitWarning = 0.95d;

    // Temp targets
    public static final int defaultActivityTTDuration = 90; // min
    public static final double defaultActivityTTmgdl = 140d;
    public static final double defaultActivityTTmmol = 8d;
    public static final int defaultEatingSoonTTDuration = 45; // min
    public static final double defaultEatingSoonTTmgdl = 90d;
    public static final double defaultEatingSoonTTmmol = 5d;
    public static final int defaultHypoTTDuration = 30; // min
    public static final double defaultHypoTTmgdl = 120d;
    public static final double defaultHypoTTmmol = 6.5d;

    public static final double MIN_TT_MGDL = 72d;
    public static final double MAX_TT_MGDL = 180d;
    public static final double MIN_TT_MMOL = 4d;
    public static final double MAX_TT_MMOL = 10d;

    //NSClientInternal
    public static final int MAX_LOG_LINES = 100;

    //Screen: Threshold for width/height to go into small width/height layout
    public static final int SMALL_WIDTH = 320;
    public static final int SMALL_HEIGHT = 480;

    //Autosens
    public static final double DEVIATION_TO_BE_EQUAL = 2.0;
    public static final double DEFAULT_MAX_ABSORPTION_TIME = 6.0;

    // Pump
    public static final int PUMP_MAX_CONNECTION_TIME_IN_SECONDS = 120 - 1;
    public static final int MIN_WATCHDOG_INTERVAL_IN_SECONDS = 12 * 60;

    //SMS Communicator
    public static final long SMS_CONFIRM_TIMEOUT = T.mins(5).msecs();

    //Storage [MB]
    public static final long MINIMUM_FREE_SPACE = 200;

    // Overview
    public static final double LOWMARK = 76.0;
    public static final double HIGHMARK = 180.0;

    // STATISTICS
    public static final double STATS_TARGET_LOW_MMOL = 3.9;
    public static final double STATS_TARGET_HIGH_MMOL = 7.8;
    public static final double STATS_RANGE_LOW_MMOL = 3.9;
    public static final double STATS_RANGE_HIGH_MMOL = 10.0;

    // Local profile
    public static final String LOCAL_PROFILE = "LocalProfile";

    // Local Alerts
    public static final int DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES = 30;
    public static final int DEFAULT_MISSED_BG_READINGS_THRESHOLD_MINUTES = 30;

    // One Time Password

    /**
     * Size of generated key for TOTP Authenticator token, in bits
     * rfc6238 suggest at least 160 for SHA1 based TOTP, but it ts too weak
     * with 512 generated QRCode to provision authenticator is too detailed
     * 256 is chosen as both secure enough and small enough for easy-scannable QRCode
     */
    public static final int OTP_GENERATED_KEY_LENGTH_BITS = 256;

    /**
     * How many old TOTP tokens still accept.
     * Each token is 30s valid, but copying and SMS transmision of it can take additional seconds,
     * so we add leeway to still accept given amount of older tokens
     */
    public static final int OTP_ACCEPT_OLD_TOKENS_COUNT = 1;
}
