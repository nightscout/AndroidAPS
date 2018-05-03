package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 08.12.2016.
 */

public class PumpDescription {
    public static final int NONE = 0;
    public static final int PERCENT = 0x01;
    public static final int ABSOLUTE = 0x02;

    public boolean isBolusCapable = true;
    public double bolusStep = 0.1d;

    public boolean isExtendedBolusCapable = true;
    public double extendedBolusStep = 0.1d;
    public double extendedBolusDurationStep = 30;
    public double extendedBolusMaxDuration = 12 * 60;

    public boolean isTempBasalCapable = true;
    public int tempBasalStyle = PERCENT;

    public int maxTempPercent = 200;
    public int tempPercentStep = 10;

    public double maxTempAbsolute = 10;
    public double tempAbsoluteStep = 0.05d;

    public int tempDurationStep = 60;
    public boolean tempDurationStep15mAllowed = false;
    public boolean tempDurationStep30mAllowed = false;
    public int tempMaxDuration = 12 * 60;


    public boolean isSetBasalProfileCapable = true;
    public double basalStep = 0.01d;
    public double basalMinimumRate = 0.04d;

    public boolean isRefillingCapable = false;

    public boolean storesCarbInfo = true;

    public boolean is30minBasalRatesCapable = false;

    public boolean supportsTDDs = false;
    public boolean needsManualTDDLoad = true;
}
