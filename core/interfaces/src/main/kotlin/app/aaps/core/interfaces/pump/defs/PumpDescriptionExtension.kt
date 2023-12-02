package app.aaps.core.interfaces.pump.defs

import app.aaps.core.data.pump.defs.PumpCapability
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType

fun PumpDescription.fillFor(pumpType: PumpType): PumpDescription {
    resetSettings()
    this.pumpType = pumpType
    val pumpCapability = pumpType.pumpCapability ?: return this
    isBolusCapable = pumpCapability.hasCapability(PumpCapability.Bolus)
    bolusStep = pumpType.bolusSize
    isExtendedBolusCapable = pumpCapability.hasCapability(PumpCapability.ExtendedBolus)
    pumpType.extendedBolusSettings?.step?.let { extendedBolusStep = it }
    pumpType.extendedBolusSettings?.durationStep?.let { extendedBolusDurationStep = it.toDouble() }
    pumpType.extendedBolusSettings?.maxDuration?.let { extendedBolusMaxDuration = it.toDouble() }
    isTempBasalCapable = pumpCapability.hasCapability(PumpCapability.TempBasal)
    if (pumpType.pumpTempBasalType == PumpTempBasalType.Percent) {
        tempBasalStyle = PumpDescription.PERCENT
        pumpType.tbrSettings?.maxDose?.let { maxTempPercent = it.toInt() }
        pumpType.tbrSettings?.step?.let { tempPercentStep = it.toInt() }
    } else {
        tempBasalStyle = PumpDescription.ABSOLUTE
        pumpType.tbrSettings?.maxDose?.let { maxTempAbsolute = it }
        pumpType.tbrSettings?.step?.let { tempAbsoluteStep = it }
    }
    pumpType.tbrSettings?.durationStep?.let { tempDurationStep = it }
    pumpType.tbrSettings?.maxDuration?.let { tempMaxDuration = it }
    pumpType.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration15minAllowed)?.let { tempDurationStep15mAllowed = it }
    pumpType.specialBasalDurations?.hasCapability(PumpCapability.BasalRate_Duration30minAllowed)?.let { tempDurationStep30mAllowed = it }
    isSetBasalProfileCapable = pumpCapability.hasCapability(PumpCapability.BasalProfileSet)
    basalStep = pumpType.baseBasalStep
    basalMinimumRate = pumpType.baseBasalMinValue
    isRefillingCapable = pumpCapability.hasCapability(PumpCapability.Refill)
    isBatteryReplaceable = pumpCapability.hasCapability(PumpCapability.ReplaceBattery)
    //storesCarbInfo = pumpCapability.hasCapability(PumpCapability.StoreCarbInfo)
    supportsTDDs = pumpCapability.hasCapability(PumpCapability.TDD)
    needsManualTDDLoad = pumpCapability.hasCapability(PumpCapability.ManualTDDLoad)
    is30minBasalRatesCapable = pumpCapability.hasCapability(PumpCapability.BasalRate30min)
    hasCustomUnreachableAlertCheck = pumpType.hasCustomUnreachableAlertCheck
    isPatchPump = pumpType.isPatchPump
    maxResorvoirReading = pumpType.maxReservoirReading
    useHardwareLink = pumpType.useHardwareLink

    return this
}