package app.aaps.core.interfaces.pump.defs

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpDescriptionTest {

    @Test fun setPumpDescription() {
        val pumpDescription = PumpDescription()
        pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO)
        assertThat(pumpDescription.bolusStep).isWithin(0.1).of(PumpType.ACCU_CHEK_COMBO.bolusSize)
        assertThat(pumpDescription.basalMinimumRate).isWithin(0.1).of(PumpType.ACCU_CHEK_COMBO.baseBasalStep)
        assertThat(pumpDescription.basalStep).isWithin(0.1).of(PumpType.ACCU_CHEK_COMBO.baseBasalStep)
        assertThat(pumpDescription.extendedBolusDurationStep).isEqualTo(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.durationStep?.toDouble())
        assertThat(pumpDescription.extendedBolusMaxDuration).isEqualTo(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.maxDuration?.toDouble())
        assertThat(pumpDescription.extendedBolusStep).isEqualTo(PumpType.ACCU_CHEK_COMBO.extendedBolusSettings?.step)
        assertThat(pumpDescription.isExtendedBolusCapable).isEqualTo(PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.ExtendedBolus))
        assertThat(pumpDescription.isBolusCapable).isEqualTo(PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Bolus))
        assertThat(pumpDescription.isRefillingCapable).isEqualTo(PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.Refill))
        assertThat(pumpDescription.isSetBasalProfileCapable).isEqualTo(PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.BasalProfileSet))
        assertThat(pumpDescription.isTempBasalCapable).isEqualTo(PumpType.ACCU_CHEK_COMBO.pumpCapability?.hasCapability(PumpCapability.TempBasal))
        assertThat(pumpDescription.maxTempPercent.toDouble()).isEqualTo(PumpType.ACCU_CHEK_COMBO.tbrSettings?.maxDose)
        assertThat(pumpDescription.tempPercentStep.toDouble()).isEqualTo(PumpType.ACCU_CHEK_COMBO.tbrSettings?.step)
        assertThat(pumpDescription.tempBasalStyle).isEqualTo(if (PumpType.ACCU_CHEK_COMBO.pumpTempBasalType == PumpTempBasalType.Percent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE)
        assertThat(pumpDescription.tempDurationStep.toLong()).isEqualTo(PumpType.ACCU_CHEK_COMBO.tbrSettings?.durationStep?.toLong())
        assertThat(pumpDescription.tempDurationStep15mAllowed).isEqualTo(PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration15minAllowed))
        assertThat(pumpDescription.tempDurationStep30mAllowed).isEqualTo(PumpType.ACCU_CHEK_COMBO.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration30minAllowed))
    }
}
