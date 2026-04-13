package com.nightscout.eversense.packets

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.nightscout.eversense.EversenseGattCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.models.EversenseTransmitterSettings
import com.nightscout.eversense.packets.e365.GetActiveAlarmsPacket
import com.nightscout.eversense.packets.e365.Ping365Packet
import com.nightscout.eversense.packets.e365.SetAppVersion365Packet
import com.nightscout.eversense.packets.e365.SetBleDisconnect365Packet
import com.nightscout.eversense.packets.e365.GetGlucoseLogValuesPacket
import com.nightscout.eversense.packets.e365.GetLogRangePacket365
import com.nightscout.eversense.packets.e365.SetHighGlucoseAlarm365Packet
import com.nightscout.eversense.packets.e365.SetHighGlucoseAlarmEnabled365Packet
import com.nightscout.eversense.packets.e365.SetLowGlucoseAlarm365Packet
import com.nightscout.eversense.packets.e365.SetPredictionHighEnabled365Packet
import com.nightscout.eversense.packets.e365.SetPredictionHighThreshold365Packet
import com.nightscout.eversense.packets.e365.SetPredictionHighTime365Packet
import com.nightscout.eversense.packets.e365.SetPredictionLowEnabled365Packet
import com.nightscout.eversense.packets.e365.SetPredictionLowThreshold365Packet
import com.nightscout.eversense.packets.e365.SetPredictionLowTime365Packet
import com.nightscout.eversense.packets.e365.SetRateFallingEnabled365Packet
import com.nightscout.eversense.packets.e365.SetRateFallingThreshold365Packet
import com.nightscout.eversense.packets.e365.SetRateRisingEnabled365Packet
import com.nightscout.eversense.packets.e365.SetRateRisingThreshold365Packet
import com.nightscout.eversense.packets.e365.SetRepeatHighGlucose365Packet
import com.nightscout.eversense.packets.e365.SetRepeatLowGlucose365Packet
import com.nightscout.eversense.packets.e365.SetVibrateMode365Packet
import com.nightscout.eversense.packets.e365.LogType
import com.nightscout.eversense.packets.e365.GetCalibrationInfoPacket
import com.nightscout.eversense.packets.e365.GetGlucoseDataPacket
import com.nightscout.eversense.packets.e365.GetPatientSettingsPacket
import com.nightscout.eversense.packets.e365.GetSensorInformationPacket
import com.nightscout.eversense.packets.e365.SetCurrentDateTimePacket
import com.nightscout.eversense.util.EselSmoothing
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
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

            if (glucoseData.glucoseInMgDl > 1000) {
                EversenseLogger.error(TAG, "recentGlucose exceeds range - received: ${glucoseData.glucoseInMgDl}")
                return
            }

            var currentGlucose = glucoseData.glucoseInMgDl
            if (state.useSmoothing && state.recentGlucoseValue > 0 && state.lastGlucoseRaw > 0) {
                currentGlucose = EselSmoothing.smooth(currentGlucose, state.recentGlucoseValue, state.lastGlucoseRaw)
            }

            val result = mutableListOf<EversenseCGMResult>()
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

            // Read glucose history for backfill
            try {
                val logRange = gatt.writePacket<GetLogRangePacket365.Response>(GetLogRangePacket365(LogType.GLUCOSE))
                val range = com.nightscout.eversense.util.RangeCalculator.calculateGlucoseRange(
                    logRange.rangeFrom, logRange.rangeTo, state.recentGlucoseDatetime
                )
                val history = gatt.writePacket<GetGlucoseLogValuesPacket.Response>(
                    GetGlucoseLogValuesPacket(from = range.from, to = range.to, sensorIdLength = sensorIdLength)
                )
                val backfill = history.glucoseHistory
                    .filter { it.datetime > state.recentGlucoseDatetime }
                    .map { item -> EversenseCGMResult(glucoseInMgDl = item.valueInMgDl, datetime = item.datetime, trend = item.trend) }
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

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
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

                // Set BLE disconnect timeout to 5 minutes matching iOS default
                try { gatt.writePacket<SetBleDisconnect365Packet.Response>(SetBleDisconnect365Packet(300)) } catch (e: Exception) { EversenseLogger.warning(TAG, "SetBleDisconnect failed: $e") }

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
