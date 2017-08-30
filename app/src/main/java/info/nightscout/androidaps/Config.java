package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Config {
    public static int SUPPORTEDNSVERSION = 1000; // 0.10.00

    // MAIN FUCTIONALITY
    public static final boolean APS = BuildConfig.APS;
    // PLUGINS
    public static final boolean OPENAPSENABLED = APS;
    public static final boolean LOOPENABLED = APS;
    public static final boolean WEAR = BuildConfig.WEAR;

    public static final boolean NSCLIENT = BuildConfig.NSCLIENTOLNY;

    public static final boolean COMBO = true && BuildConfig.PUMPDRIVERS;
    public static final boolean DANAR = true && BuildConfig.PUMPDRIVERS;
    public static final boolean DANARv2 = true && BuildConfig.PUMPDRIVERS;

    public static final boolean ACTION = !BuildConfig.NSCLIENTOLNY;
    public static final boolean VIRTUALPUMP = !BuildConfig.NSCLIENTOLNY;
    public static final boolean MDI = !BuildConfig.NSCLIENTOLNY;
    public static final boolean OTHERPROFILES = !BuildConfig.NSCLIENTOLNY;
    public static final boolean SAFETY = !BuildConfig.NSCLIENTOLNY;

    public static final boolean SMSCOMMUNICATORENABLED = !BuildConfig.NSCLIENTOLNY;


    public static final boolean ALLPREFERENCES = !BuildConfig.NSCLIENTOLNY;

    public static final boolean detailedLog = true;
    public static final boolean logFunctionCalls = true;
    public static final boolean logIncommingData = true;
    public static final boolean logAPSResult = true;
    public static final boolean logPumpComm = true;
    public static final boolean logPrefsChange = true;
    public static final boolean logConfigBuilder = true;
    public static final boolean logConstraintsChanges = true;
    public static final boolean logNSUpload = true;
    public static final boolean logPumpActions = true;
    public static final boolean logCongigBuilderActions = true;
    public static final boolean logAutosensData = true;

    // DanaR specific
    public static final boolean logDanaBTComm = true;
    public static final boolean logDanaMessageDetail = true;
    public static final boolean logDanaSerialEngine = true;

    // Combo specific
    // TODO try turning this into preferences
    public static final boolean comboExperimentalFeatures = true;

    /** enable the UNFINISHED and currently BROKEN bolus cammand that reports progress and can be cancelled */
    public static final boolean comboExperimentalBolus = false && comboExperimentalFeatures;

    /** Very quick hack to split up bolus into 2 U parts, spaced roughly 45s apart.
     * If there's an error during bolusing, no record is created in AAPS.
     * Don't combine with experimental bolus! */
    public static final boolean comboExperimentalSplitBoluses = false && comboExperimentalFeatures && !comboExperimentalBolus;
}
