package info.nightscout.androidaps.plugins.PumpCommon.utils;

import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpCapability;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpTempBasalType;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;

/**
 * Created by andy on 02/05/2018.
 */

public class PumpUtil {


    public static void setPumpDescription(PumpDescription pumpDescription, PumpType pumpType)
    {
        // reset
        pumpDescription.resetSettings();

        PumpCapability pumpCapability = pumpType.getPumpCapability();

        pumpDescription.isBolusCapable = pumpCapability.hasCapability(PumpCapability.Bolus);
        pumpDescription.bolusStep = pumpType.getBolusSize();

        pumpDescription.isExtendedBolusCapable = pumpCapability.hasCapability(PumpCapability.ExtendedBolus);
        pumpDescription.extendedBolusStep = pumpType.getExtendedBolusSettings().getStep();
        pumpDescription.extendedBolusDurationStep = pumpType.getExtendedBolusSettings().getDurationStep();
        pumpDescription.extendedBolusMaxDuration = pumpType.getExtendedBolusSettings().getMaxDuration();

        pumpDescription.isTempBasalCapable = pumpCapability.hasCapability(PumpCapability.TempBasal);

        if (pumpType.getPumpTempBasalType()==PumpTempBasalType.Percent)
        {
            pumpDescription.tempBasalStyle = PumpDescription.PERCENT;
            pumpDescription.maxTempPercent = pumpType.getTbrSettings().getMaxDose().intValue();
            pumpDescription.tempPercentStep = (int)pumpType.getTbrSettings().getStep();
        }
        else
        {
            pumpDescription.tempBasalStyle = PumpDescription.ABSOLUTE;
            pumpDescription.maxTempAbsolute = pumpType.getTbrSettings().getMaxDose();
            pumpDescription.tempAbsoluteStep = pumpType.getTbrSettings().getStep();
        }

        pumpDescription.tempDurationStep = pumpType.getTbrSettings().getDurationStep();
        pumpDescription.tempMaxDuration = pumpType.getTbrSettings().getMaxDuration();

        pumpDescription.tempDurationStep15mAllowed = pumpType.getSpecialBasalDurations().hasCapability(PumpCapability.BasalRate_Duration15minAllowed);
        pumpDescription.tempDurationStep30mAllowed = pumpType.getSpecialBasalDurations().hasCapability(PumpCapability.BasalRate_Duration30minAllowed);

        pumpDescription.isSetBasalProfileCapable = pumpCapability.hasCapability(PumpCapability.BasalProfileSet);
        pumpDescription.basalStep = pumpType.getBaseBasalStep();
        pumpDescription.basalMinimumRate = pumpType.getBaseBasalMinValue();

        pumpDescription.isRefillingCapable = pumpCapability.hasCapability(PumpCapability.Refill);
        pumpDescription.storesCarbInfo = pumpCapability.hasCapability(PumpCapability.StoreCarbInfo);

        pumpDescription.supportsTDDs = pumpCapability.hasCapability(PumpCapability.TDD);
        pumpDescription.needsManualTDDLoad = pumpCapability.hasCapability(PumpCapability.ManualTDDLoad);

        pumpDescription.is30minBasalRatesCapable = pumpCapability.hasCapability(PumpCapability.BasalRate30min);

    }


}
