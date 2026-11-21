package app.aaps.plugins.eversense.models

import app.aaps.plugins.eversense.enums.CalibrationMode
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.enums.CalibrationPhase
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import kotlinx.serialization.Serializable

@Serializable
class EversenseState {
    var lastSync: Long = 0
    var insertionDate: Long = 0
    var calibrationPhase: CalibrationPhase = CalibrationPhase.UNKNOWN
    var calibrationReadiness: CalibrationReadiness = CalibrationReadiness.REASON_UNKNOWN
    var calibrationMode: CalibrationMode = CalibrationMode.DEFAULT
    var nextCalibrationDate: Long = 0
    var lastCalibrationDate: Long = 0
    var batteryPercentage: Int = 0
    var recentGlucoseDatetime: Long = 0
    var recentGlucoseValue: Int = 0
    var lastGlucoseRaw: Int = 0
    var sensorSignalStrength: Int = 0
    var activeAlarms: List<ActiveAlarm> = emptyList()
    var firmwareVersion: String = ""
    var mmaFeatures: Int = 0
    var extFirmwareVersion: String = ""
    var transmitterSerialNumber: String = ""
    var transmitterName: String = ""
    var sensorId: String = ""
    var calibrationHistory: List<CalibrationHistoryItem> = emptyList()
}

