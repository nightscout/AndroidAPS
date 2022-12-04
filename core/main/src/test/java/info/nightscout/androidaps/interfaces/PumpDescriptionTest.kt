package info.nightscout.androidaps.interfaces

import info.nightscout.interfaces.pump.defs.PumpCapability
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.pump.defs.PumpTempBasalType
import info.nightscout.interfaces.pump.defs.PumpType
import org.junit.Assert
import org.junit.jupiter.api.Test

class PumpDescriptionTest {

    @Test fun setPumpDescription() {
        val pumpDescription = PumpDescription()
        pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
        Assert.assertEquals(pumpDescription.bolusStep, PumpType.ACCU_CHEK_COMBO.bolusSize, 0.1)
        Assert.assertEquals(pumpDescription.basalMinimumRate, PumpType.ACCU_CHEK_COMBO.baseBasalStep, 0.1)
        Assert.assertEquals(pumpDescription.basalStep, PumpType.ACCU_CHEK_COMBO.baseBasalStep, 0.1)
        Assert.assertEquals(pumpDescription.extendedBolusDurationStep, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.durationStep?.toDouble())
        Assert.assertEquals(pumpDescription.extendedBolusMaxDuration, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.maxDuration?.toDouble())
        Assert.assertEquals(pumpDescription.extendedBolusStep, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.step)
        Assert.assertEquals(pumpDescription.isExtendedBolusCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.ExtendedBolus))
        Assert.assertEquals(pumpDescription.isBolusCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Bolus))
        Assert.assertEquals(pumpDescription.isRefillingCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Refill))
        Assert.assertEquals(pumpDescription.isSetBasalProfileCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.BasalProfileSet))
        Assert.assertEquals(pumpDescription.isTempBasalCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.TempBasal))
        Assert.assertEquals(pumpDescription.maxTempPercent.toDouble(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.maxDose)
        Assert.assertEquals(pumpDescription.tempPercentStep.toDouble(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.step)
        Assert.assertEquals(pumpDescription.tempBasalStyle, if (PumpType.ACCU_CHEK_COMBO.pumpTempBasalType == PumpTempBasalType.Percent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE)
        Assert.assertEquals(pumpDescription.tempDurationStep.toLong(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.durationStep?.toLong())
        Assert.assertEquals(pumpDescription.tempDurationStep15mAllowed, PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration15minAllowed))
        Assert.assertEquals(pumpDescription.tempDurationStep30mAllowed, PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration30minAllowed))
    }
}