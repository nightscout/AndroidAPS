package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.pump.common.defs.PumpCapability
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import org.junit.Assert
import org.junit.Test

/**
 * Created by andy on 5/13/18.
 */
class PumpDescritpionTest {

    @Test fun setPumpDescription() {
        val pumpDescription = PumpDescription()
        pumpDescription.setPumpDescription(PumpType.AccuChekCombo)
        Assert.assertEquals(pumpDescription.bolusStep, PumpType.AccuChekCombo.bolusSize, 0.1)
        Assert.assertEquals(pumpDescription.basalMinimumRate, PumpType.AccuChekCombo.baseBasalStep, 0.1)
        Assert.assertEquals(pumpDescription.basalStep, PumpType.AccuChekCombo.baseBasalStep, 0.1)
        Assert.assertEquals(pumpDescription.extendedBolusDurationStep, PumpType.AccuChekCombo.extendedBolusSettings.durationStep.toDouble(), 0.1)
        Assert.assertEquals(pumpDescription.extendedBolusMaxDuration, PumpType.AccuChekCombo.extendedBolusSettings.maxDuration.toDouble(), 0.1)
        Assert.assertEquals(pumpDescription.extendedBolusStep, PumpType.AccuChekCombo.extendedBolusSettings.step, 0.1)
        Assert.assertEquals(pumpDescription.isExtendedBolusCapable, PumpType.AccuChekCombo.pumpCapability.hasCapability(PumpCapability.ExtendedBolus))
        Assert.assertEquals(pumpDescription.isBolusCapable, PumpType.AccuChekCombo.pumpCapability.hasCapability(PumpCapability.Bolus))
        Assert.assertEquals(pumpDescription.isRefillingCapable, PumpType.AccuChekCombo.pumpCapability.hasCapability(PumpCapability.Refill))
        Assert.assertEquals(pumpDescription.isSetBasalProfileCapable, PumpType.AccuChekCombo.pumpCapability.hasCapability(PumpCapability.BasalProfileSet))
        Assert.assertEquals(pumpDescription.isTempBasalCapable, PumpType.AccuChekCombo.pumpCapability.hasCapability(PumpCapability.TempBasal))
        Assert.assertEquals(pumpDescription.maxTempPercent.toDouble(), PumpType.AccuChekCombo.tbrSettings.maxDose, 0.1)
        Assert.assertEquals(pumpDescription.tempPercentStep.toDouble(), PumpType.AccuChekCombo.tbrSettings.step, 0.1)
        Assert.assertEquals(pumpDescription.tempBasalStyle, if (PumpType.AccuChekCombo.pumpTempBasalType == PumpTempBasalType.Percent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE)
        Assert.assertEquals(pumpDescription.tempDurationStep.toLong(), PumpType.AccuChekCombo.tbrSettings.durationStep.toLong())
        Assert.assertEquals(pumpDescription.tempDurationStep15mAllowed, PumpType.AccuChekCombo.specialBasalDurations.hasCapability(PumpCapability.BasalRate_Duration15minAllowed))
        Assert.assertEquals(pumpDescription.tempDurationStep30mAllowed, PumpType.AccuChekCombo.specialBasalDurations.hasCapability(PumpCapability.BasalRate_Duration30minAllowed))
    }
}