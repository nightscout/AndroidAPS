package info.nightscout.androidaps.plugins.PumpCommon.utils;

import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpTempBasalType;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;

/**
 * Created by andy on 02/05/2018.
 */

public class PumpUtil {

    // for now used only by VirtualPump, but with small changes could be used by any constructor
    public static void setPumpDescription(PumpDescription pumpDescription, PumpType pumpType)
    {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = pumpType.getBolusSize();

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = pumpType.getExtendedBolusSettings().getStep();
        pumpDescription.extendedBolusDurationStep = pumpType.getExtendedBolusSettings().getDurationStep();
        pumpDescription.extendedBolusMaxDuration = pumpType.getExtendedBolusSettings().getMaxDuration();

        pumpDescription.isTempBasalCapable = true;

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


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = pumpType.getBaseBasalStep();
        pumpDescription.basalMinimumRate = pumpType.getBaseBasalMinValue();
    }


}
