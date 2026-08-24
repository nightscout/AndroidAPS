package app.aaps.plugins.eversense.packets

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import app.aaps.plugins.eversense.EversenseGattCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.models.EversenseTransmitterSettings
import app.aaps.plugins.eversense.packets.e365.GetActiveAlarmsPacket
import app.aaps.plugins.eversense.packets.e365.Ping365Packet
import app.aaps.plugins.eversense.packets.e365.SetAppVersion365Packet
import app.aaps.plugins.eversense.packets.e365.SetBleDisconnect365Packet
import app.aaps.plugins.eversense.packets.e365.GetCalibrationLogValuesPacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseLogValuesPacket
import app.aaps.plugins.eversense.packets.e365.GetLogRangePacket365
import app.aaps.plugins.eversense.packets.e365.SetHighGlucoseAlarm365Packet
import app.aaps.plugins.eversense.packets.e365.SetHighGlucoseAlarmEnabled365Packet
import app.aaps.plugins.eversense.packets.e365.SetLowGlucoseAlarm365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionHighEnabled365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionHighThreshold365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionHighTime365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionLowEnabled365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionLowThreshold365Packet
import app.aaps.plugins.eversense.packets.e365.SetPredictionLowTime365Packet
import app.aaps.plugins.eversense.packets.e365.SetRateFallingEnabled365Packet
import app.aaps.plugins.eversense.packets.e365.SetRateFallingThreshold365Packet
import app.aaps.plugins.eversense.packets.e365.SetRateRisingEnabled365Packet
import app.aaps.plugins.eversense.packets.e365.SetRateRisingThreshold365Packet
import app.aaps.plugins.eversense.packets.e365.SetRepeatHighGlucose365Packet
import app.aaps.plugins.eversense.packets.e365.SetRepeatLowGlucose365Packet
import app.aaps.plugins.eversense.packets.e365.SetVibrateMode365Packet
import app.aaps.plugins.eversense.packets.e365.LogType
import app.aaps.plugins.eversense.packets.e365.GetCalibrationInfoPacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseDataPacket
import app.aaps.plugins.eversense.packets.e365.GetPatientSettingsPacket
import app.aaps.plugins.eversense.packets.e365.GetSensorInformationPacket
import app.aaps.plugins.eversense.packets.e365.SetCurrentDateTimePacket
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class Eversense365Communicator {
    companion object {
        private const val TAG = "EversenseE3Communicator"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val handler = Handler(Looper.getMainLooper())

        private var sensorIdLength = 10

        fun readGlucose(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)

            val glucoseData = gatt.writePacket<GetGlucoseDataPacket.Response>(GetGlucoseDataPacket(sensorIdLength))
            if (glucoseData.datetime <= state.recentGlucoseDatetime) {
                EversenseLogger.warning(TAG, "Glucose data is still recent after reading - currentReading: ${glucoseData.datetime}, lastReading: ${state.recentGlucoseDatetime}")
                return
            }

            var currentGlucose = glucoseData.glucoseInMgDl

            val result = mutableListOf<EversenseCGMResult>()
            val previousGlucoseDatetime = state.recentGlucoseDatetime
            state.recentGlucoseDatetime = glucoseData.datetime
            state.recentGlucoseValue = currentGlucose
            state.lastGlucoseRaw = glucoseData.glucoseInMgDl
            state.sensorSignalStrength = glucoseData.signalStrength
            EversenseLogger.info(TAG, "Sensor signal strength from glucose packet: ${glucoseData.signalStrength}")

            state.sensorId = glucoseData.sensorId

            result += EversenseCGMResult(
                glucoseInMgDl = currentGlucose,
                datetime = glucoseData.datetime,
                trend = glucoseData.trend,
                sensorId = glucoseData.sensorId,
                rawResponseHex = glucoseData.rawResponseHex
            )

            // Read last calibration entry for DMS upload (must run while GATT is connected)
            try {
                val calibLogRange = gatt.writePacket<GetLogRangePacket365.Response>(
                    GetLogRangePacket365(LogType.CALIBRATIONS)
                )
                if (calibLogRange.rangeTo > calibLogRange.rangeFrom) {
                    val calibFrom = maxOf(calibLogRange.rangeTo - 1, calibLogRange.rangeFrom)
                    val calibHistory = gatt.writePacket<GetCalibrationLogValuesPacket.Response>(
                        GetCalibrationLogValuesPacket(from = calibFrom, to = calibLogRange.rangeTo)
                    )
                    state.calibrationHistory = calibHistory.calibrationHistory
                    EversenseLogger.info(TAG, "Calibration: ${calibHistory.count} entries read (range $calibFrom..${calibLogRange.rangeTo})")
                }
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "Could not read calibration history: $e")
            }

            // Read glucose history for backfill — use previousGlucoseDatetime so gap readings are included
            try {
                val logRange = gatt.writePacket<GetLogRangePacket365.Response>(GetLogRangePacket365(LogType.GLUCOSE))
                val range = app.aaps.plugins.eversense.util.RangeCalculator.calculateGlucoseRange(
                    logRange.rangeFrom, logRange.rangeTo, previousGlucoseDatetime
                )
                val history = gatt.writePacket<GetGlucoseLogValuesPacket.Response>(
                    GetGlucoseLogValuesPacket(from = range.from, to = range.to, sensorIdLength = sensorIdLength)
                )
                val backfill = history.glucoseHistory
                    .filter { it.datetime > previousGlucoseDatetime && it.datetime < glucoseData.datetime }
                    .map { item -> EversenseCGMResult(glucoseInMgDl = item.valueInMgDl, datetime = item.datetime, trend = item.trend, rawResponseHex = item.rawResponseHex) }
                if (backfill.isNotEmpty()) {
                    result.addAll(0, backfill)
                    EversenseLogger.info(TAG, "Backfill: added ${backfill.size} historical readings")
                }
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "Could not read glucose history: $e")
            }

            preferences.edit(commit = true) {
                putString(StorageKeys.STATE, JSON.encodeToString(state))
            }

            handler.post {
                watchers.forEach { it.onCGMRead(EversenseType.EVERSENSE_365, result) }
                watchers.forEach { it.onStateChanged(state) }
            }
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>, force: Boolean = false) {
            val stateJsonCheck = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val stateCheck = JSON.decodeFromString<EversenseState>(stateJsonCheck)
            val fourMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(240)
            if (!force && stateCheck.lastSync > fourMinAgo) {
                EversenseLogger.debug(TAG, "365 fullSync skipped — last sync was recent (${(System.currentTimeMillis() - stateCheck.lastSync) / 1000}s ago)")
                return
            }
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)

                var sensorInformation = gatt.writePacket<GetSensorInformationPacket.Response>(GetSensorInformationPacket())

                // Ping transmitter first — matches iOS fullSync order
                try { gatt.writePacket<Ping365Packet.Response>(Ping365Packet()) } catch (e: Exception) { EversenseLogger.warning(TAG, "Ping failed: $e") }

                if (abs(System.currentTimeMillis() - sensorInformation.transmitterDatetime) > 10_000) {
                    EversenseLogger.debug(TAG, "Updating transmitter datetime")
                    gatt.writePacket<SetCurrentDateTimePacket.Response>(SetCurrentDateTimePacket())
                    sensorInformation = gatt.writePacket(GetSensorInformationPacket())
                }

                sensorIdLength = sensorInformation.sensorIdLength
                state.insertionDate = sensorInformation.insertionDate
                state.batteryPercentage = sensorInformation.batteryLevel
                state.firmwareVersion = sensorInformation.version
                state.extFirmwareVersion = sensorInformation.extVersion
                state.transmitterSerialNumber = sensorInformation.serialNumber
                state.transmitterName = sensorInformation.transmitterName
                EversenseLogger.info(TAG, "Transmitter serialNumber='${sensorInformation.serialNumber}' transmitterName='${sensorInformation.transmitterName}'")
                EversenseLogger.info(TAG, "Firmware version: ${sensorInformation.version} / ${sensorInformation.extVersion}")

                val calibrationInfo = gatt.writePacket<GetCalibrationInfoPacket.Response>(GetCalibrationInfoPacket())
                state.calibrationPhase = calibrationInfo.currentPhase
                state.calibrationReadiness = calibrationInfo.calibrationReadiness
                state.calibrationMode = calibrationInfo.calibrationMode
                state.nextCalibrationDate = calibrationInfo.nextCalibration
                state.lastCalibrationDate = calibrationInfo.lastCalibration

                val patientSettings = gatt.writePacket<GetPatientSettingsPacket.Response>(GetPatientSettingsPacket())
                state.settings.vibrateEnabled = patientSettings.vibrateMode
                state.settings.glucoseHighAlarmEnabled = patientSettings.highGlucoseEnabled
                state.settings.glucoseHighAlarmThreshold = patientSettings.highGlucoseAlarmInMgDl
                state.settings.glucoseLowAlarmThreshold = patientSettings.lowGlucoseAlarmInMgDl
                state.settings.rateFallingAlarmEnabled = patientSettings.rateFallingEnabled
                state.settings.rateFallingAlarmThreshold = patientSettings.rateFallingThreshold
                state.settings.rateRisingAlarmEnabled = patientSettings.rateRisingEnabled
                state.settings.rateRisingAlarmThreshold = patientSettings.rateRisingThreshold
                state.settings.predictiveHighAlarmEnabled = patientSettings.predictionHighEnabled
                state.settings.predictiveHighAlarmMinutes = patientSettings.predictionRisingInterval
                state.settings.predictiveHighAlarmThreshold = patientSettings.predictionRisingThreshold
                state.settings.predictiveLowAlarmEnabled = patientSettings.predictionLowEnabled
                state.settings.predictiveLowAlarmMinutes = patientSettings.predictionFallingInterval
                state.settings.predictiveLowAlarmThreshold = patientSettings.predictionFallingThreshold

                // Send app version — iOS sends "8.0.4" in every fullSync
                try { gatt.writePacket<SetAppVersion365Packet.Response>(SetAppVersion365Packet()) } catch (e: Exception) { EversenseLogger.warning(TAG, "SetAppVersion failed: $e") }

                // Set BLE disconnect timeout to 0 = never disconnect
                try { gatt.writePacket<SetBleDisconnect365Packet.Response>(SetBleDisconnect365Packet(0)) } catch (e: Exception) { EversenseLogger.warning(TAG, "SetBleDisconnect failed: $e") }

                // Read active alarms
                try {
                    val activeAlarms = gatt.writePacket<GetActiveAlarmsPacket.Response>(GetActiveAlarmsPacket())
                    state.activeAlarms = activeAlarms.alarms
                    EversenseLogger.info(TAG, "Active alarms: ${activeAlarms.alarms.map { it.code.title }}")
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Could not read active alarms: $e")
                }

                state.lastSync = System.currentTimeMillis()
                EversenseLogger.info(TAG, "Completed full sync - datetime: ${state.lastSync}")
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                handler.post {
                    watchers.forEach { it.onStateChanged(state) }
                }
            } catch (exception: Exception) {
                EversenseLogger.error(TAG, "Failed to do full sync: $exception")
                exception.printStackTrace()
                // Disconnect on fullSync failure so BLE session resets cleanly rather than looping
                // with broken GATT state. Must use disconnectAndScheduleReconnect() here, not plain
                // disconnect() - see that method's doc comment for why a self-initiated disconnect
                // doesn't reliably re-trigger onConnectionStateChange() (and therefore its reconnect
                // scheduling) on its own.
                EversenseLogger.warning(TAG, "Disconnecting after fullSync failure to reset BLE session")
                gatt.disconnectAndScheduleReconnect()
            }
        }

        fun writeSettings(gatt: EversenseGattCallback, preferences: SharedPreferences, settings: EversenseTransmitterSettings): Boolean {
            return try {
                gatt.writePacket<SetVibrateMode365Packet.Response>(SetVibrateMode365Packet(settings.vibrateEnabled))
                gatt.writePacket<SetHighGlucoseAlarmEnabled365Packet.Response>(SetHighGlucoseAlarmEnabled365Packet(settings.glucoseHighAlarmEnabled))
                gatt.writePacket<SetHighGlucoseAlarm365Packet.Response>(SetHighGlucoseAlarm365Packet(settings.glucoseHighAlarmThreshold))
                gatt.writePacket<SetLowGlucoseAlarm365Packet.Response>(SetLowGlucoseAlarm365Packet(settings.glucoseLowAlarmThreshold))
                gatt.writePacket<SetRateFallingEnabled365Packet.Response>(SetRateFallingEnabled365Packet(settings.rateFallingAlarmEnabled))
                gatt.writePacket<SetRateFallingThreshold365Packet.Response>(SetRateFallingThreshold365Packet(settings.rateFallingAlarmThreshold))
                gatt.writePacket<SetRateRisingEnabled365Packet.Response>(SetRateRisingEnabled365Packet(settings.rateRisingAlarmEnabled))
                gatt.writePacket<SetRateRisingThreshold365Packet.Response>(SetRateRisingThreshold365Packet(settings.rateRisingAlarmThreshold))
                gatt.writePacket<SetPredictionLowEnabled365Packet.Response>(SetPredictionLowEnabled365Packet(settings.predictiveLowAlarmEnabled))
                gatt.writePacket<SetPredictionLowThreshold365Packet.Response>(SetPredictionLowThreshold365Packet(settings.predictiveLowAlarmThreshold))
                gatt.writePacket<SetPredictionLowTime365Packet.Response>(SetPredictionLowTime365Packet(settings.predictiveLowAlarmMinutes))
                gatt.writePacket<SetPredictionHighEnabled365Packet.Response>(SetPredictionHighEnabled365Packet(settings.predictiveHighAlarmEnabled))
                gatt.writePacket<SetPredictionHighThreshold365Packet.Response>(SetPredictionHighThreshold365Packet(settings.predictiveHighAlarmThreshold))
                gatt.writePacket<SetPredictionHighTime365Packet.Response>(SetPredictionHighTime365Packet(settings.predictiveHighAlarmMinutes))
                EversenseLogger.info(TAG, "365 settings written successfully")
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(JSON.decodeFromString<EversenseState>(preferences.getString(StorageKeys.STATE, null) ?: "{}").also { it.settings = settings }))
                }
                true
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Failed to write 365 settings: $e")
                false
            }
        }
    }
}

