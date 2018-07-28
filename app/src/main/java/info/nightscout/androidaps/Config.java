package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Config {
    public static int SUPPORTEDNSVERSION = 1002; // 0.10.00

    // MAIN FUCTIONALITY
    public static final boolean APS = BuildConfig.APS;
    // PLUGINS
    public static final boolean NSCLIENT = BuildConfig.NSCLIENTOLNY;
    public static final boolean G5UPLOADER = BuildConfig.G5UPLOADER;
    public static final boolean PUMPCONTROL = BuildConfig.PUMPCONTROL;

    public static final boolean HWPUMPS = BuildConfig.PUMPDRIVERS;

    public static final boolean ACTION = !BuildConfig.NSCLIENTOLNY && !BuildConfig.G5UPLOADER;
    public static final boolean MDI = !BuildConfig.NSCLIENTOLNY && !BuildConfig.G5UPLOADER;
    public static final boolean OTHERPROFILES = !BuildConfig.NSCLIENTOLNY && !BuildConfig.G5UPLOADER;
    public static final boolean SAFETY = !BuildConfig.NSCLIENTOLNY && !BuildConfig.G5UPLOADER;

    public static final boolean SMSCOMMUNICATORENABLED = !BuildConfig.NSCLIENTOLNY && !BuildConfig.G5UPLOADER;


    public static boolean logFunctionCalls = true;
    public static boolean logAPSResult = true;
    public static boolean logPrefsChange = true;
    public static boolean logConfigBuilder = true;
    public static boolean logCongigBuilderActions = true;
    public static boolean logAutosensData = false;
    public static boolean logEvents = false;
    public static boolean logQueue = true;
    public static boolean logBgSource = true;
    public static boolean logOverview = true;
    public static boolean logNotification = true;
    public static boolean logAlarm = false;
    public static boolean logDataService = true;
    public static boolean logDataFood = true;
    public static boolean logDataTreatments = true;
    public static boolean logDatabase = true;
    public static boolean logNsclient = true;
    public static boolean logObjectives = false;
    public static boolean logPump = true;
    public static boolean logPumpComm = true;
    public static boolean logPumpBtComm = false;

}
