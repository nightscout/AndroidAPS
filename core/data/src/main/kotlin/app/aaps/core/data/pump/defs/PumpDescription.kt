package app.aaps.core.data.pump.defs

class PumpDescription {

    var pumpType = PumpType.GENERIC_AAPS
    var isBolusCapable = false
    var bolusStep = 0.0
    var isExtendedBolusCapable = true
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

    //var storesCarbInfo = false
    var is30minBasalRatesCapable = false
    var supportsTDDs = false
    var needsManualTDDLoad = false
    var hasCustomUnreachableAlertCheck = false
    var isPatchPump = false
    var maxReservoirReading = 50
    var useHardwareLink = false

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
        //storesCarbInfo = false
        supportsTDDs = false
        needsManualTDDLoad = true
        hasCustomUnreachableAlertCheck = false
        useHardwareLink = false
    }

    fun clone(): PumpDescription =
        PumpDescription().also { it ->
            it.pumpType = this.pumpType
            it.isBolusCapable = this.isBolusCapable
            it.bolusStep = this.bolusStep
            it.isExtendedBolusCapable = this.isExtendedBolusCapable
            it.extendedBolusStep = this.extendedBolusStep
            it.extendedBolusDurationStep = this.extendedBolusDurationStep
            it.extendedBolusMaxDuration = this.extendedBolusMaxDuration
            it.isTempBasalCapable = this.isTempBasalCapable
            it.tempBasalStyle = this.tempBasalStyle
            it.maxTempPercent = this.maxTempPercent
            it.tempPercentStep = this.tempPercentStep
            it.maxTempAbsolute = this.maxTempAbsolute
            it.tempAbsoluteStep = this.tempAbsoluteStep
            it.tempDurationStep = this.tempDurationStep
            it.tempDurationStep15mAllowed = this.tempDurationStep15mAllowed
            it.tempDurationStep30mAllowed = this.tempDurationStep30mAllowed
            it.tempMaxDuration = this.tempMaxDuration
            it.isSetBasalProfileCapable = this.isSetBasalProfileCapable
            it.basalStep = this.basalStep
            it.basalMinimumRate = this.basalMinimumRate
            it.basalMaximumRate = this.basalMaximumRate
            it.isRefillingCapable = this.isRefillingCapable
            it.isBatteryReplaceable = this.isBatteryReplaceable

            //it.storesCarbInfo = this.storesCarbInfo
            it.is30minBasalRatesCapable = this.is30minBasalRatesCapable
            it.supportsTDDs = this.supportsTDDs
            it.needsManualTDDLoad = this.needsManualTDDLoad
            it.hasCustomUnreachableAlertCheck = this.hasCustomUnreachableAlertCheck
            it.isPatchPump = this.isPatchPump
            it.maxReservoirReading = this.maxReservoirReading
            it.useHardwareLink = this.useHardwareLink
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