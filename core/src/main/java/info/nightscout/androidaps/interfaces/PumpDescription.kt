package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.pump.common.defs.PumpCapability
import info.nightscout.androidaps.plugins.pump.common.defs.PumpTempBasalType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

class PumpDescription() {

    constructor(pumpType: PumpType) : this() {
        setPumpDescription(pumpType)
    }

    var pumpType = PumpType.GenericAAPS
    var isBolusCapable = false
    var bolusStep = 0.0
    var isExtendedBolusCapable = false
    var extendedBolusStep = 0.0
    var extendedBolusDurationStep = 0.0
    var extendedBolusMaxDuration = 0.0
    var isTempBasalCapable = false
    var tempBasalStyle = 0
    var maxTempPercent = 0
    var tempPercentStep = 0
    var maxTempAbsolute = 0.0
    var tempAbsoluteStep = 0.0
    var tempDurationStep = 0
    var tempDurationStep15mAllowed = false
    var tempDurationStep30mAllowed = false
    var tempMaxDuration = 0
    var isSetBasalProfileCapable = false
    var basalStep = 0.0
    var basalMinimumRate = 0.0
    var basalMaximumRate = 0.0
    var isRefillingCapable = false
    var isBatteryReplaceable = false
    var storesCarbInfo = false
    var is30minBasalRatesCapable = false
    var supportsTDDs = false
    var needsManualTDDLoad = false
    var hasCustomUnreachableAlertCheck = false

    fun resetSettings() {
        isBolusCapable = true
        bolusStep = 0.1
        isExtendedBolusCapable = true
        extendedBolusStep = 0.1
        extendedBolusDurationStep = 30.0
        extendedBolusMaxDuration = (12 * 60).toDouble()
        isTempBasalCapable = true
        tempBasalStyle = PERCENT
        maxTempPercent = 200
        tempPercentStep = 10
        maxTempAbsolute = 10.0
        tempAbsoluteStep = 0.05
        tempDurationStep = 60
        tempMaxDuration = 12 * 60
        tempDurationStep15mAllowed = false
        tempDurationStep30mAllowed = false
        isSetBasalProfileCapable = true
        basalStep = 0.01
        basalMinimumRate = 0.04
        basalMaximumRate = 25.0
        is30minBasalRatesCapable = false
        isRefillingCapable = true
        isBatteryReplaceable = true
        storesCarbInfo = true
        supportsTDDs = false
        needsManualTDDLoad = true
        hasCustomUnreachableAlertCheck = false
    }

    fun setPumpDescription(pumpType: PumpType) {
        resetSettings()
        this.pumpType = pumpType
        val pumpCapability = pumpType.pumpCapability
        isBolusCapable = pumpCapability.hasCapability(PumpCapability.Bolus)
        bolusStep = pumpType.bolusSize
        isExtendedBolusCapable = pumpCapability.hasCapability(PumpCapability.ExtendedBolus)
        extendedBolusStep = pumpType.extendedBolusSettings.step
        extendedBolusDurationStep = pumpType.extendedBolusSettings.durationStep.toDouble()
        extendedBolusMaxDuration = pumpType.extendedBolusSettings.maxDuration.toDouble()
        isTempBasalCapable = pumpCapability.hasCapability(PumpCapability.TempBasal)
        if (pumpType.pumpTempBasalType == PumpTempBasalType.Percent) {
            tempBasalStyle = PERCENT
            maxTempPercent = pumpType.tbrSettings.maxDose.toInt()
            tempPercentStep = pumpType.tbrSettings.step.toInt()
        } else {
            tempBasalStyle = ABSOLUTE
            maxTempAbsolute = pumpType.tbrSettings.maxDose
            tempAbsoluteStep = pumpType.tbrSettings.step
        }
        tempDurationStep = pumpType.tbrSettings.durationStep
        tempMaxDuration = pumpType.tbrSettings.maxDuration
        tempDurationStep15mAllowed = pumpType.specialBasalDurations
            .hasCapability(PumpCapability.BasalRate_Duration15minAllowed)
        tempDurationStep30mAllowed = pumpType.specialBasalDurations
            .hasCapability(PumpCapability.BasalRate_Duration30minAllowed)
        isSetBasalProfileCapable = pumpCapability.hasCapability(PumpCapability.BasalProfileSet)
        basalStep = pumpType.baseBasalStep
        basalMinimumRate = pumpType.baseBasalMinValue
        isRefillingCapable = pumpCapability.hasCapability(PumpCapability.Refill)
        isBatteryReplaceable = pumpCapability.hasCapability(PumpCapability.ReplaceBattery)
        storesCarbInfo = pumpCapability.hasCapability(PumpCapability.StoreCarbInfo)
        supportsTDDs = pumpCapability.hasCapability(PumpCapability.TDD)
        needsManualTDDLoad = pumpCapability.hasCapability(PumpCapability.ManualTDDLoad)
        is30minBasalRatesCapable = pumpCapability.hasCapability(PumpCapability.BasalRate30min)
        hasCustomUnreachableAlertCheck = pumpType.hasCustomUnreachableAlertCheck
    }

    companion object {

        const val NONE = 0
        const val PERCENT = 0x01
        const val ABSOLUTE = 0x02
    }

    init {
        resetSettings()
    }
}