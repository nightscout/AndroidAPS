package com.nightscout.eversense.models

import com.nightscout.eversense.enums.CalibrationMode
import com.nightscout.eversense.enums.CalibrationPhase
import com.nightscout.eversense.enums.CalibrationReadiness
import kotlinx.serialization.Serializable

@Serializable
class EversenseState {
    var lastSync: Long = 0
    var useSmoothing: Boolean = false
    var insertionDate: Long = 0
    var calibrationPhase: CalibrationPhase = CalibrationPhase.UNKNOWN
    var calibrationReadiness: CalibrationReadiness = CalibrationReadiness.UNKNOWN
    var calibrationMode: CalibrationMode = CalibrationMode.DEFAULT
    var nextCalibrationDate: Long = 0
    var lastCalibrationDate: Long = 0
    var batteryPercentage: Int = 0
    var recentGlucoseDatetime: Long = 0
    var recentGlucoseValue: Int = 0
    var lastGlucoseRaw: Int = 0
    var placementSignalRssi: Int = 0
    var sensorSignalStrength: Int = 0
    var activeAlarms: List<ActiveAlarm> = emptyList()
    var firmwareVersion: String = ""
    var mmaFeatures: Int = 0
    var extFirmwareVersion: String = ""
    var settings = EversenseTransmitterSettings()
}

@Serializable
class EversenseTransmitterSettings {
    var vibrateEnabled: Boolean = true
    var glucoseHighAlarmEnabled: Boolean = true
    var glucoseHighAlarmThreshold: Int = 250
    var glucoseLowAlarmThreshold: Int = 60
    var rateFallingAlarmEnabled: Boolean = true
    var rateFallingAlarmThreshold: Double = 1.5
    var rateRisingAlarmEnabled: Boolean = true
    var rateRisingAlarmThreshold: Double = 1.5
    var predictiveHighAlarmEnabled: Boolean = true
    var predictiveHighAlarmThreshold: Int = 180
    var predictiveHighAlarmMinutes: Int = 5
    var predictiveLowAlarmEnabled: Boolean = true
    var predictiveLowAlarmThreshold: Int = 70
    var predictiveLowAlarmMinutes: Int = 5
}
