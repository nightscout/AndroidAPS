package info.nightscout.core.interfaces

import info.nightscout.interfaces.pump.defs.PumpCapability
import info.nightscout.interfaces.pump.defs.PumpDescription
import info.nightscout.interfaces.pump.defs.PumpTempBasalType
import info.nightscout.interfaces.pump.defs.PumpType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PumpDescriptionTest {

    @Test fun setPumpDescription() {
        val pumpDescription = PumpDescription()
        pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
        Assertions.assertEquals(pumpDescription.bolusStep, PumpType.ACCU_CHEK_COMBO.bolusSize, 0.1)
        Assertions.assertEquals(pumpDescription.basalMinimumRate, PumpType.ACCU_CHEK_COMBO.baseBasalStep, 0.1)
        Assertions.assertEquals(pumpDescription.basalStep, PumpType.ACCU_CHEK_COMBO.baseBasalStep, 0.1)
        Assertions.assertEquals(pumpDescription.extendedBolusDurationStep, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.durationStep?.toDouble())
        Assertions.assertEquals(pumpDescription.extendedBolusMaxDuration, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.maxDuration?.toDouble())
        Assertions.assertEquals(pumpDescription.extendedBolusStep, PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.step)
        Assertions.assertEquals(pumpDescription.isExtendedBolusCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.ExtendedBolus))
        Assertions.assertEquals(pumpDescription.isBolusCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Bolus))
        Assertions.assertEquals(pumpDescription.isRefillingCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Refill))
        Assertions.assertEquals(pumpDescription.isSetBasalProfileCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.BasalProfileSet))
        Assertions.assertEquals(pumpDescription.isTempBasalCapable, PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.TempBasal))
        Assertions.assertEquals(pumpDescription.maxTempPercent.toDouble(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.maxDose)
        Assertions.assertEquals(pumpDescription.tempPercentStep.toDouble(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.step)
        Assertions.assertEquals(pumpDescription.tempBasalStyle, if (PumpType.ACCU_CHEK_COMBO.pumpTempBasalType == PumpTempBasalType.Percent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE)
        Assertions.assertEquals(pumpDescription.tempDurationStep.toLong(), PumpType.ACCU_CHEK_COMBO.tbrSettings?.durationStep?.toLong())
        Assertions.assertEquals(pumpDescription.tempDurationStep15mAllowed, PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration15minAllowed))
        Assertions.assertEquals(pumpDescription.tempDurationStep30mAllowed, PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration30minAllowed))
    }
}