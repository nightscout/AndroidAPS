package info.nightscout.androidaps.interfaces;

/**
 * Created by mike on 08.12.2016.
 */

public class PumpDescription {
    public static final int PERCENT = 0;
    public static final int ABSOLUTE = 1;
    public static final int EXTENDED = 2;

    public boolean isBolusCapable = true;
    public double bolusStep = 0.1d;

    public boolean isExtendedBolusCapable = true;
    public double extendedBolusStep = 0.1d;

    public boolean isTempBasalCapable = true;
    public int lowTempBasalStyle = PERCENT;
    public int highTempBasalStyle = PERCENT;
    public double maxHighTemp = 200;
    public double lowTempStep = 10;
    public double highTempStep = 10;

    public boolean isSetBasalProfileCapable = true;
    public double basalStep = 0.01d;
    public double basalMinimumRate = 0.04d;

    public boolean isRefillingCapable = false;
}
