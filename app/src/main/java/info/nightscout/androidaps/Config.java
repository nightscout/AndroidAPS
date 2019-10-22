package info.nightscout.androidaps;

/**
 * Created by mike on 07.06.2016.
 */
public class Config {
    public static int SUPPORTEDNSVERSION = 1002; // 0.10.00

    public static final boolean APS = BuildConfig.FLAVOR.equals("full");

    public static final boolean NSCLIENT = BuildConfig.FLAVOR.equals("nsclient") || BuildConfig.FLAVOR.equals("nsclient2");
    public static final boolean PUMPCONTROL = BuildConfig.FLAVOR.equals("pumpcontrol");

    public static final boolean PUMPDRIVERS = BuildConfig.FLAVOR.equals("full") || BuildConfig.FLAVOR.equals("pumpcontrol");
}
