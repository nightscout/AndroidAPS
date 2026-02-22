package com.nightscout.eversense.packets

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.nightscout.eversense.EversenseGattCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.enums.CalibrationMode
import com.nightscout.eversense.enums.EversenseType
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseState
import com.nightscout.eversense.models.EversenseTransmitterSettings
import com.nightscout.eversense.packets.e3.GetBatteryPercentagePacket
import com.nightscout.eversense.packets.e3.GetCalibrationDailyPacket
import com.nightscout.eversense.packets.e3.GetCalibrationPhasePacket
import com.nightscout.eversense.packets.e3.GetCalibrationReadinessPacket
import com.nightscout.eversense.packets.e3.GetCurrentDatetimePacket
import com.nightscout.eversense.packets.e3.GetCurrentGlucosePacket
import com.nightscout.eversense.packets.e3.GetInsertionDatePacket
import com.nightscout.eversense.packets.e3.GetInsertionTimePacket
import com.nightscout.eversense.packets.e3.GetLastCalibrationDatePacket
import com.nightscout.eversense.packets.e3.GetLastCalibrationTimePacket
import com.nightscout.eversense.packets.e3.GetNextCalibrationDatePacket
import com.nightscout.eversense.packets.e3.GetNextCalibrationTimePacket
import com.nightscout.eversense.packets.e3.GetSettingGlucoseHighEnabled
import com.nightscout.eversense.packets.e3.GetSettingGlucoseHighThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingGlucoseLowThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveHighEnabledPacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveHighThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveHighTimePacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveLowEnabledPacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveLowThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingPredictiveLowTimePacket
import com.nightscout.eversense.packets.e3.GetSettingRateFallingEnabledPacket
import com.nightscout.eversense.packets.e3.GetSettingRateFallingThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingRateRisingEnabledPacket
import com.nightscout.eversense.packets.e3.GetSettingRateRisingThresholdPacket
import com.nightscout.eversense.packets.e3.GetSettingVibratePacket
import com.nightscout.eversense.packets.e3.SetCurrentDatetimePacket
import com.nightscout.eversense.packets.e3.SetSettingGlucoseHighEnablePacket
import com.nightscout.eversense.packets.e3.SetSettingGlucoseHighThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingGlucoseLowThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveHighAlarmEnabledPacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveHighThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveHighTimePacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveLowAlarmEnabledPacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveLowThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingPredictiveLowTimePacket
import com.nightscout.eversense.packets.e3.SetSettingRateFallingEnabledPacket
import com.nightscout.eversense.packets.e3.SetSettingRateFallingThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingRateRisingEnabledPacket
import com.nightscout.eversense.packets.e3.SetSettingRateRisingThresholdPacket
import com.nightscout.eversense.packets.e3.SetSettingVibratePacket
import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class EversenseE3Communicator {
    companion object {
        private const val TAG = "EversenseE3Communicator"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val handler = Handler(Looper.getMainLooper())

        fun readGlucose(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)
            val fourHalfMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(270)

            if (fourHalfMinAgo < state.recentGlucoseDatetime) {
                EversenseLogger.warning(TAG, "Glucose data is still recent - lastReading: ${state.recentGlucoseDatetime}")
                return
            }

            try {
                EversenseLogger.debug(TAG, "Reading current glucose...")
                val currentGlucose = gatt.writePacket<GetCurrentGlucosePacket.Response>(GetCurrentGlucosePacket())
                if (currentGlucose.datetime <= state.recentGlucoseDatetime) {
                    EversenseLogger.warning(TAG, "Glucose data is still recent after reading - currentReading: ${currentGlucose.datetime}, lastReading: ${state.recentGlucoseDatetime}")
                    return
                }

                if (currentGlucose.glucoseInMgDl > 1000) {
                    EversenseLogger.error(TAG, "recentGlucose exceeds range - received: ${currentGlucose.glucoseInMgDl}")
                    return
                }

                val result = mutableListOf<EversenseCGMResult>()
                state.recentGlucoseDatetime = currentGlucose.datetime
                state.recentGlucoseValue = currentGlucose.glucoseInMgDl
                result += EversenseCGMResult(
                    glucoseInMgDl = currentGlucose.glucoseInMgDl,
                    datetime = currentGlucose.datetime,
                    trend = currentGlucose.trend
                )

                // TODO: read history for backfill

                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                handler.post {
                    watchers.forEach {
                        it.onCGMRead(EversenseType.EVERSENSE_E3, result)
                    }
                }
            } catch (exception: Exception) {
                EversenseLogger.error(TAG, "Got exception during readGlucose - exception $exception")
            }
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)
                val fourHalfMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(270)

                if (fourHalfMinAgo < state.lastSync) {
                    EversenseLogger.warning(TAG, "State is still fresh - lastSync: ${state.lastSync}")
                    return
                }

                EversenseLogger.debug(TAG, "Reading current datetime...")
                val currentDatetime = gatt.writePacket<GetCurrentDatetimePacket.Response>(GetCurrentDatetimePacket())
                if (currentDatetime.needsTimeSync) {
                    EversenseLogger.debug(TAG, "Send SetCurrentDatetimePacket...")
                    gatt.writePacket<SetCurrentDatetimePacket.Response>(SetCurrentDatetimePacket())
                }

                EversenseLogger.debug(TAG, "Reading battery percentage...")
                val batteryPercentage = gatt.writePacket<GetBatteryPercentagePacket.Response>(GetBatteryPercentagePacket())
                state.batteryPercentage = batteryPercentage.percentage

                EversenseLogger.debug(TAG, "Reading insertion datetime...")
                val insertionDate = gatt.writePacket<GetInsertionDatePacket.Response>(GetInsertionDatePacket())
                val insertionTime = gatt.writePacket<GetInsertionTimePacket.Response>(GetInsertionTimePacket())
                state.insertionDate = insertionDate.date + insertionTime.time

                EversenseLogger.debug(TAG, "Reading calibration info...")
                val calibrationPhase = gatt.writePacket<GetCalibrationPhasePacket.Response>(GetCalibrationPhasePacket())
                val calibrationReadiness = gatt.writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                val nextCalibrationDate = gatt.writePacket<GetNextCalibrationDatePacket.Response>(GetNextCalibrationDatePacket())
                val nextCalibrationTime = gatt.writePacket<GetNextCalibrationTimePacket.Response>(GetNextCalibrationTimePacket())
                val lastCalibrationDate = gatt.writePacket<GetLastCalibrationDatePacket.Response>(GetLastCalibrationDatePacket())
                val lastCalibrationTime = gatt.writePacket<GetLastCalibrationTimePacket.Response>(GetLastCalibrationTimePacket())

                try {
                    // Older E3 transmitters might not have this data point, thus we are allowed to ignore the expection
                    val isDailyCalibration = gatt.writePacket<GetCalibrationDailyPacket.Response>(GetCalibrationDailyPacket())
                    state.calibrationMode = if (isDailyCalibration.isDaily) CalibrationMode.DAILY_SINGLE else CalibrationMode.DAILY_DUAL
                } catch(exception: Exception) {
                    state.calibrationMode = CalibrationMode.DEFAULT
                }

                state.calibrationPhase = calibrationPhase.phase
                state.calibrationReadiness = calibrationReadiness.readiness
                state.nextCalibrationDate = nextCalibrationDate.date + nextCalibrationTime.time
                state.lastCalibrationDate = lastCalibrationDate.date + lastCalibrationTime.time

                // Transmitter settings
                EversenseLogger.debug(TAG, "Reading transmitter settings...")
                val vibrateEnabled = gatt.writePacket<GetSettingVibratePacket.Response>(GetSettingVibratePacket())
                val glucoseHighEnabled = gatt.writePacket<GetSettingGlucoseHighEnabled.Response>(GetSettingGlucoseHighEnabled())
                val glucoseHighThreshold = gatt.writePacket<GetSettingGlucoseHighThresholdPacket.Response>(GetSettingGlucoseHighThresholdPacket())
                val glucoseLowThreshold = gatt.writePacket<GetSettingGlucoseLowThresholdPacket.Response>(GetSettingGlucoseLowThresholdPacket())
                val rateFallingEnabled = gatt.writePacket<GetSettingRateFallingEnabledPacket.Response>(GetSettingRateFallingEnabledPacket())
                val rateFallingThreshold = gatt.writePacket<GetSettingRateFallingThresholdPacket.Response>(GetSettingRateFallingThresholdPacket())
                val rateRisingEnabled = gatt.writePacket<GetSettingRateRisingEnabledPacket.Response>(GetSettingRateRisingEnabledPacket())
                val rateRisingThreshold = gatt.writePacket<GetSettingRateRisingThresholdPacket.Response>(GetSettingRateRisingThresholdPacket())
                val predictiveHighEnabled = gatt.writePacket<GetSettingPredictiveHighEnabledPacket.Response>(GetSettingPredictiveHighEnabledPacket())
                val predictiveHighTime = gatt.writePacket<GetSettingPredictiveHighTimePacket.Response>(GetSettingPredictiveHighTimePacket())
                val predictiveHighThreshold = gatt.writePacket<GetSettingPredictiveHighThresholdPacket.Response>(GetSettingPredictiveHighThresholdPacket())
                val predictiveLowEnabled = gatt.writePacket<GetSettingPredictiveLowEnabledPacket.Response>(GetSettingPredictiveLowEnabledPacket())
                val predictiveLowTime = gatt.writePacket<GetSettingPredictiveLowTimePacket.Response>(GetSettingPredictiveLowTimePacket())
                val predictiveLowThreshold = gatt.writePacket<GetSettingPredictiveLowThresholdPacket.Response>(GetSettingPredictiveLowThresholdPacket())

                state.settings.vibrateEnabled = vibrateEnabled.enabled
                state.settings.glucoseHighAlarmEnabled = glucoseHighEnabled.enabled
                state.settings.glucoseHighAlarmThreshold = glucoseHighThreshold.threshold
                state.settings.glucoseLowAlarmThreshold = glucoseLowThreshold.threshold
                state.settings.rateFallingAlarmEnabled = rateFallingEnabled.enabled
                state.settings.rateFallingAlarmThreshold = rateFallingThreshold.threshold
                state.settings.rateRisingAlarmEnabled = rateRisingEnabled.enabled
                state.settings.rateRisingAlarmThreshold = rateRisingThreshold.threshold
                state.settings.predictiveHighAlarmEnabled = predictiveHighEnabled.enabled
                state.settings.predictiveHighAlarmMinutes = predictiveHighTime.minutes
                state.settings.predictiveHighAlarmThreshold = predictiveHighThreshold.threshold
                state.settings.predictiveLowAlarmEnabled = predictiveLowEnabled.enabled
                state.settings.predictiveLowAlarmMinutes = predictiveLowTime.minutes
                state.settings.predictiveLowAlarmThreshold = predictiveLowThreshold.threshold

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
            } catch (exception: Exception) {
                EversenseLogger.error(TAG, "Failed to do full sync: $exception")
            }
        }

        fun writeSettings(gatt: EversenseGattCallback, preferences: SharedPreferences, settings: EversenseTransmitterSettings): Boolean {
            try {
                gatt.writePacket<SetSettingVibratePacket.Response>(SetSettingVibratePacket(settings.vibrateEnabled))

                gatt.writePacket<SetSettingGlucoseHighEnablePacket.Response>(SetSettingGlucoseHighEnablePacket(settings.glucoseHighAlarmEnabled))
                gatt.writePacket<SetSettingGlucoseHighThresholdPacket.Response>(SetSettingGlucoseHighThresholdPacket(settings.glucoseHighAlarmThreshold))
                gatt.writePacket<SetSettingGlucoseLowThresholdPacket.Response>(SetSettingGlucoseLowThresholdPacket(settings.glucoseLowAlarmThreshold))

                gatt.writePacket<SetSettingRateFallingEnabledPacket.Response>(SetSettingRateFallingEnabledPacket(settings.rateFallingAlarmEnabled))
                gatt.writePacket<SetSettingRateFallingThresholdPacket.Response>(SetSettingRateFallingThresholdPacket(settings.rateFallingAlarmThreshold))
                gatt.writePacket<SetSettingRateRisingEnabledPacket.Response>(SetSettingRateRisingEnabledPacket(settings.rateRisingAlarmEnabled))
                gatt.writePacket<SetSettingRateRisingThresholdPacket.Response>(SetSettingRateRisingThresholdPacket(settings.rateRisingAlarmThreshold))

                gatt.writePacket<SetSettingPredictiveHighAlarmEnabledPacket.Response>(SetSettingPredictiveHighAlarmEnabledPacket(settings.predictiveHighAlarmEnabled))
                gatt.writePacket<SetSettingPredictiveHighTimePacket.Response>(SetSettingPredictiveHighTimePacket(settings.predictiveHighAlarmMinutes))
                gatt.writePacket<SetSettingPredictiveHighThresholdPacket.Response>(SetSettingPredictiveHighThresholdPacket(settings.predictiveHighAlarmThreshold))
                gatt.writePacket<SetSettingPredictiveLowAlarmEnabledPacket.Response>(SetSettingPredictiveLowAlarmEnabledPacket(settings.predictiveLowAlarmEnabled))
                gatt.writePacket<SetSettingPredictiveLowTimePacket.Response>(SetSettingPredictiveLowTimePacket(settings.predictiveLowAlarmMinutes))
                gatt.writePacket<SetSettingPredictiveLowThresholdPacket.Response>(SetSettingPredictiveLowThresholdPacket(settings.predictiveLowAlarmThreshold))

                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)
                state.settings = settings
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                return true
            } catch (exception: Exception) {
                EversenseLogger.error(TAG, "Failed to do full sync: $exception")
                return false
            }
        }
    }
}