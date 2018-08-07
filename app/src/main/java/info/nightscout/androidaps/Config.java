package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Config {
    public static int SUPPORTEDNSVERSION = 1002; // 0.10.00

    // MAIN FUCTIONALITY
    public static final boolean APS = BuildConfig.APS;
    // PLUGINS
    public static final boolean NSCLIENT = BuildConfig.NSCLIENT || BuildConfig.NSCLIENT2;
    public static final boolean G5UPLOADER = BuildConfig.G5UPLOADER;
    public static final boolean PUMPCONTROL = BuildConfig.PUMPCONTROL;

    public static final boolean PUMPDRIVERS = BuildConfig.PUMPDRIVERS;

    public static final boolean ACTION = !NSCLIENT && !G5UPLOADER;
    public static final boolean MDI = !NSCLIENT && !G5UPLOADER;
    public static final boolean OTHERPROFILES = !NSCLIENT && !G5UPLOADER;
    public static final boolean SAFETY = !NSCLIENT && !G5UPLOADER;

    public static final boolean SMSCOMMUNICATORENABLED = !NSCLIENT && !G5UPLOADER;


}
