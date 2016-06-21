package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Constants {
    public static final String MGDL = "mg/dl"; // This is Nightscout representation
    public static final String MMOL = "mmol";

    public static final double MMOLL_TO_MGDL = 18.0182;
    public static final double MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL;

    public static final int hoursToKeepInDatabase = 24;
}
