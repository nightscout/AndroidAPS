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


}
