package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 08.12.2016.
 */

public class PumpDescription {
    public static final int NONE = 0;
    public static final int PERCENT = 1;
    public static final int ABSOLUTE = 2;
    public static final int EXTENDED = 4;

    public boolean isBolusCapable = true;
    public double bolusStep = 0.1d;

    public boolean isExtendedBolusCapable = true;
    public double extendedBolusStep = 0.1d;

    public boolean isTempBasalCapable = true;
    public int lowTempBasalStyle = PERCENT;
    public int highTempBasalStyle = PERCENT;
    public double maxHighTempPercent = 200;
    public double maxHighTempAbsolute = 0; // zero = no limit
    public double lowTempPercentStep = 10;
    public double lowTempAbsoluteStep = 0.05d;
    public int lowTempPercentDuration = 30;
    public int lowTempAbsoluteDuration = 30;
    public double highTempPercentStep = 10;
    public double highTempAbsoluteStep = 0.05d;
    public int highTempPercentDuration = 30;
    public int highTempAbsoluteDuration = 30;

    public boolean isSetBasalProfileCapable = true;
    public double basalStep = 0.01d;
    public double basalMinimumRate = 0.04d;

    public boolean isRefillingCapable = false;
}
