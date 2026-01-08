package com.nightscout.eversense.models

import kotlinx.serialization.Serializable

@Serializable
class EversenseState {
    var lastSync: Long = 0
    var insertionDate: Long = 0

    var batteryPercentage: Int = 0

    var recentGlucoseDatetime: Long = 0
    var recentGlucoseValue: Int = 0

    var settings = EversenseTransmitterSettings()
}

@Serializable
class EversenseTransmitterSettings {
    var vibrateEnabled: Boolean = true

    var glucoseHighAlarmEnabled: Boolean = true
    var glucoseHighAlarmThreshold: Int = 250
    var glucoseLowAlarmThreshold: Int = 60

    var rateAlarmEnabled: Boolean = true
    var rateFallingAlarmEnabled: Boolean = true
    var rateFallingAlarmThreshold: Double = 1.5
    var rateRisingAlarmEnabled: Boolean = true
    var rateRisingAlarmThreshold: Double = 1.5

    var predictiveAlarmEnabled: Boolean = true
    var predictiveHighAlarmEnabled: Boolean = true
    var predictiveHighAlarmThreshold: Int = 180
    var predictiveHighAlarmMinutes: Int = 5
    var predictiveLowAlarmEnabled: Boolean = true
    var predictiveLowAlarmThreshold: Int = 70
    var predictiveLowAlarmMinutes: Int = 5
}