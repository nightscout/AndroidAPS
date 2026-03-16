package app.aaps.core.interfaces.pump.defs

import app.aaps.core.data.pump.defs.Capability
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType

fun PumpDescription.fillFor(pumpType: PumpType): PumpDescription {
    resetSettings()
    this.pumpType = pumpType
    val pumpCapability = pumpType.pumpCapability() ?: return this
    isBolusCapable = pumpCapability.hasCapability(Capability.Bolus)
    bolusStep = pumpType.bolusSize()
    isExtendedBolusCapable = pumpCapability.hasCapability(Capability.ExtendedBolus)
    pumpType.extendedBolusSettings()?.step?.let { extendedBolusStep = it }
    pumpType.extendedBolusSettings()?.durationStep?.let { extendedBolusDurationStep = it.toDouble() }
    pumpType.extendedBolusSettings()?.maxDuration?.let { extendedBolusMaxDuration = it.toDouble() }
    isTempBasalCapable = pumpCapability.hasCapability(Capability.TempBasal)
    if (pumpType.pumpTempBasalType() == PumpTempBasalType.Percent) {
        tempBasalStyle = PumpDescription.PERCENT
        pumpType.tbrSettings()?.maxDose?.let { maxTempPercent = it.toInt() }
        pumpType.tbrSettings()?.step?.let { tempPercentStep = it.toInt() }
    } else {
        tempBasalStyle = PumpDescription.ABSOLUTE
        pumpType.tbrSettings()?.maxDose?.let { maxTempAbsolute = it }
        pumpType.tbrSettings()?.step?.let { tempAbsoluteStep = it }
    }
    pumpType.tbrSettings()?.durationStep?.let { tempDurationStep = it }
    pumpType.tbrSettings()?.maxDuration?.let { tempMaxDuration = it }
    tempDurationStep15mAllowed = pumpType.specialBasalDurations().contains(Capability.BasalRate_Duration15minAllowed)
    tempDurationStep30mAllowed = pumpType.specialBasalDurations().contains(Capability.BasalRate_Duration30minAllowed)
    isSetBasalProfileCapable = pumpCapability.hasCapability(Capability.BasalProfileSet)
    basalStep = pumpType.baseBasalStep()
    basalMinimumRate = pumpType.baseBasalMinValue()
    isRefillingCapable = pumpCapability.hasCapability(Capability.Refill)
    isBatteryReplaceable = pumpCapability.hasCapability(Capability.ReplaceBattery)
    supportsTDDs = pumpCapability.hasCapability(Capability.TDD)
    needsManualTDDLoad = pumpCapability.hasCapability(Capability.ManualTDDLoad)
    is30minBasalRatesCapable = pumpCapability.hasCapability(Capability.BasalRate30min)
    hasCustomUnreachableAlertCheck = pumpType.hasCustomUnreachableAlertCheck
    isPatchPump = pumpType.isPatchPump()
    maxReservoirReading = pumpType.maxReservoirReading()
    useHardwareLink = pumpType.useHardwareLink

    return this
}