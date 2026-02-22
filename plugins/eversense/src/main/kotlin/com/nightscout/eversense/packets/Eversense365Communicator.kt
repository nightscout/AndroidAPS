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
import com.nightscout.eversense.packets.e365.GetCalibrationInfoPacket
import com.nightscout.eversense.packets.e365.GetGlucoseDataPacket
import com.nightscout.eversense.packets.e365.GetPatientSettingsPacket
import com.nightscout.eversense.packets.e365.GetSensorInformationPacket
import com.nightscout.eversense.packets.e365.SetCurrentDateTimePacket
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

            val result = mutableListOf<EversenseCGMResult>()
            state.recentGlucoseDatetime = glucoseData.datetime
            state.recentGlucoseValue = glucoseData.glucoseInMgDl
            result += EversenseCGMResult(
                glucoseInMgDl = glucoseData.glucoseInMgDl,
                datetime = glucoseData.datetime,
                trend = glucoseData.trend
            )

            // TODO: read history for backfill

            preferences.edit(commit = true) {
                putString(StorageKeys.STATE, JSON.encodeToString(state))
            }

            handler.post {
                watchers.forEach {
                    it.onCGMRead(EversenseType.EVERSENSE_365, result)
                }
            }
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)

                var sensorInformation = gatt.writePacket<GetSensorInformationPacket.Response>(GetSensorInformationPacket())

                // Allow time drift <10s
                if (abs(System.currentTimeMillis() - sensorInformation.transmitterDatetime) > 10_000) {
                    EversenseLogger.debug(TAG, "Updating transmitter datetime")
                    val setTime = gatt.writePacket<SetCurrentDateTimePacket.Response>(SetCurrentDateTimePacket())

                    // Re-read sensor information based on updated time
                    sensorInformation = gatt.writePacket(GetSensorInformationPacket())
                }

                sensorIdLength = sensorInformation.sensorIdLength
                state.insertionDate = sensorInformation.insertionDate
                state.batteryPercentage = sensorInformation.batteryLevel

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

                state.lastSync = System.currentTimeMillis()
                EversenseLogger.info(TAG, "Completed full sync - datetime: ${state.lastSync}")
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                handler.post {
                    watchers.forEach {
                        it.onStateChanged(state)
                    }
                }
            } catch(exception: Exception) {
                EversenseLogger.error(TAG, "Failed to do full sync: $exception")
                exception.printStackTrace()
            }
        }

        fun writeSettings(gatt: EversenseGattCallback, preferences: SharedPreferences, settings: EversenseTransmitterSettings): Boolean {
            // TODO:
            return false
        }
    }
}