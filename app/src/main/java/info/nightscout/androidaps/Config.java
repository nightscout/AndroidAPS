package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Config {
    // MAIN FUCTIONALITY
    public static final boolean APS = BuildConfig.APS;
    // PLUGINS
    public static final boolean LOWSUSPEDENABLED = APS;
    public static final boolean OPENAPSMAENABLED = APS;
    public static final boolean LOOPENABLED = APS;
    public static final boolean OBJECTIVESENABLED = APS && BuildConfig.OBJECTIVES;

    public static final boolean CAREPORTALENABLED = true;
    public static final boolean SMSCOMMUNICATORENABLED = true;

    public static final boolean DANAR = true && BuildConfig.PUMPDRIVERS;
    public static final boolean MM640G = false && BuildConfig.PUMPDRIVERS;

    public static final boolean detailedLog = true;
    public static final boolean logFunctionCalls = true;
    public static final boolean logIncommingBG = true;
    public static final boolean logIncommingData = true;
    public static final boolean logAPSResult = true;
    public static final boolean logPumpComm = true;
    public static final boolean logPrefsChange = true;
    public static final boolean logConfigBuilder = true;
    public static final boolean logConstraintsChanges = true;
    public static final boolean logTempBasalsCut = true;
    public static final boolean logNSUpload = true;
    public static final boolean logPumpActions = true;
    public static final boolean logSMSComm = true;
    public static final boolean logCongigBuilderActions = true;

    // DanaR specific
    public static final boolean logDanaBTComm = true;
    public static final boolean logDanaMessageDetail = true;
    public static final boolean logDanaSerialEngine = true;
}
