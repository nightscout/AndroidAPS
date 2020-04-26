package info.nightscout.androidaps.interfaces;

import org.junit.Assert;
import org.junit.Test;

import info.nightscout.androidaps.plugins.pump.common.defs.PumpCapability;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;

/**
 * Created by andy on 5/13/18.
 */
public class PumpDescritpionTest {

    @Test
    public void setPumpDescription() {

        PumpDescription pumpDescription = new PumpDescription();

        pumpDescription.setPumpDescription(PumpType.AccuChekCombo);

        Assert.assertEquals(pumpDescription.bolusStep, PumpType.AccuChekCombo.getBolusSize(), 0.1d);
        Assert.assertEquals(pumpDescription.basalMinimumRate, PumpType.AccuChekCombo.getBaseBasalStep(), 0.1d);
        Assert.assertEquals(pumpDescription.basalStep, PumpType.AccuChekCombo.getBaseBasalStep(), 0.1d);
        Assert.assertEquals(pumpDescription.extendedBolusDurationStep, PumpType.AccuChekCombo.getExtendedBolusSettings().getDurationStep(), 0.1d);
        Assert.assertEquals(pumpDescription.extendedBolusMaxDuration, PumpType.AccuChekCombo.getExtendedBolusSettings().getMaxDuration(), 0.1d);
        Assert.assertEquals(pumpDescription.extendedBolusStep, PumpType.AccuChekCombo.getExtendedBolusSettings().getStep(), 0.1d);
        Assert.assertEquals(pumpDescription.isExtendedBolusCapable, PumpType.AccuChekCombo.getPumpCapability().hasCapability(PumpCapability.ExtendedBolus));
        Assert.assertEquals(pumpDescription.isBolusCapable, PumpType.AccuChekCombo.getPumpCapability().hasCapability(PumpCapability.Bolus));
        Assert.assertEquals(pumpDescription.isRefillingCapable, PumpType.AccuChekCombo.getPumpCapability().hasCapability(PumpCapability.Refill));
        Assert.assertEquals(pumpDescription.isSetBasalProfileCapable, PumpType.AccuChekCombo.getPumpCapability().hasCapability(PumpCapability.BasalProfileSet));
        Assert.assertEquals(pumpDescription.isTempBasalCapable, PumpType.AccuChekCombo.getPumpCapability().hasCapability(PumpCapability.TempBasal));
        Assert.assertEquals(pumpDescription.maxTempPercent, PumpType.AccuChekCombo.getTbrSettings().getMaxDose(), 0.1d);
        Assert.assertEquals(pumpDescription.tempPercentStep, PumpType.AccuChekCombo.getTbrSettings().getStep(), 0.1d);
        Assert.assertEquals(pumpDescription.tempBasalStyle, PumpType.AccuChekCombo.getPumpTempBasalType()== PumpTempBasalType.Percent ? PumpDescription.PERCENT : PumpDescription.ABSOLUTE);
        Assert.assertEquals(pumpDescription.tempDurationStep, PumpType.AccuChekCombo.getTbrSettings().getDurationStep());
        Assert.assertEquals(pumpDescription.tempDurationStep15mAllowed, PumpType.AccuChekCombo.getSpecialBasalDurations().hasCapability(PumpCapability.BasalRate_Duration15minAllowed));
        Assert.assertEquals(pumpDescription.tempDurationStep30mAllowed, PumpType.AccuChekCombo.getSpecialBasalDurations().hasCapability(PumpCapability.BasalRate_Duration30minAllowed));

    }

}