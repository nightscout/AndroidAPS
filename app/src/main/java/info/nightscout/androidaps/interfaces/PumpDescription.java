package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpCapability;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by mike on 08.12.2016.
 */

public class PumpDescription {
    public PumpType pumpType = PumpType.GenericAAPS;

    public PumpDescription () {
        resetSettings();
    }

    public static final int NONE = 0;
    public static final int PERCENT = 0x01;
    public static final int ABSOLUTE = 0x02;

    public boolean isBolusCapable;
    public double bolusStep;

    public boolean isExtendedBolusCapable;
    public double extendedBolusStep;
    public double extendedBolusDurationStep;
    public double extendedBolusMaxDuration;

    public boolean isTempBasalCapable;
    public int tempBasalStyle;

    public int maxTempPercent;
    public int tempPercentStep;

    public double maxTempAbsolute;
    public double tempAbsoluteStep;

    public int tempDurationStep;
    public boolean tempDurationStep15mAllowed;
    public boolean tempDurationStep30mAllowed;
    public int tempMaxDuration;

    public boolean isSetBasalProfileCapable;
    public double basalStep;
    public double basalMinimumRate;
    public double basalMaximumRate;

    public boolean isRefillingCapable;

    public boolean storesCarbInfo;

    public boolean is30minBasalRatesCapable;

    public boolean supportsTDDs;
    public boolean needsManualTDDLoad;


    public void resetSettings() {
        isBolusCapable = true;
        bolusStep = 0.1d;

        isExtendedBolusCapable = true;
        extendedBolusStep = 0.1d;
        extendedBolusDurationStep = 30;
        extendedBolusMaxDuration = 12 * 60;

        isTempBasalCapable = true;
        tempBasalStyle = PERCENT;
        maxTempPercent = 200;
        tempPercentStep = 10;
        maxTempAbsolute = 10;
        tempAbsoluteStep = 0.05d;
        tempDurationStep = 60;
        tempMaxDuration = 12 * 60;
        tempDurationStep15mAllowed = false;
        tempDurationStep30mAllowed = false;

        isSetBasalProfileCapable = true;
        basalStep = 0.01d;
        basalMinimumRate = 0.04d;
        basalMaximumRate = 25d;
        is30minBasalRatesCapable = false;

        isRefillingCapable = false;
        storesCarbInfo = true;

        supportsTDDs = false;
        needsManualTDDLoad = true;
    }

    public void setPumpDescription(PumpType pumpType) {
        resetSettings();
        this.pumpType = pumpType;

        PumpCapability pumpCapability = pumpType.getPumpCapability();

        isBolusCapable = pumpCapability.hasCapability(PumpCapability.Bolus);
        bolusStep = pumpType.getBolusSize();

        isExtendedBolusCapable = pumpCapability.hasCapability(PumpCapability.ExtendedBolus);
        extendedBolusStep = pumpType.getExtendedBolusSettings().getStep();
        extendedBolusDurationStep = pumpType.getExtendedBolusSettings().getDurationStep();
        extendedBolusMaxDuration = pumpType.getExtendedBolusSettings().getMaxDuration();

        isTempBasalCapable = pumpCapability.hasCapability(PumpCapability.TempBasal);

        if (pumpType.getPumpTempBasalType() == PumpTempBasalType.Percent) {
            tempBasalStyle = PERCENT;
            maxTempPercent = pumpType.getTbrSettings().getMaxDose().intValue();
            tempPercentStep = (int) pumpType.getTbrSettings().getStep();
        } else {
            tempBasalStyle = ABSOLUTE;
            maxTempAbsolute = pumpType.getTbrSettings().getMaxDose();
            tempAbsoluteStep = pumpType.getTbrSettings().getStep();
        }

        tempDurationStep = pumpType.getTbrSettings().getDurationStep();
        tempMaxDuration = pumpType.getTbrSettings().getMaxDuration();

        tempDurationStep15mAllowed = pumpType.getSpecialBasalDurations()
                .hasCapability(PumpCapability.BasalRate_Duration15minAllowed);
        tempDurationStep30mAllowed = pumpType.getSpecialBasalDurations()
                .hasCapability(PumpCapability.BasalRate_Duration30minAllowed);

        isSetBasalProfileCapable = pumpCapability.hasCapability(PumpCapability.BasalProfileSet);
        basalStep = pumpType.getBaseBasalStep();
        basalMinimumRate = pumpType.getBaseBasalMinValue();

        isRefillingCapable = pumpCapability.hasCapability(PumpCapability.Refill);
        storesCarbInfo = pumpCapability.hasCapability(PumpCapability.StoreCarbInfo);

        supportsTDDs = pumpCapability.hasCapability(PumpCapability.TDD);
        needsManualTDDLoad = pumpCapability.hasCapability(PumpCapability.ManualTDDLoad);

        is30minBasalRatesCapable = pumpCapability.hasCapability(PumpCapability.BasalRate30min);
    }

}
