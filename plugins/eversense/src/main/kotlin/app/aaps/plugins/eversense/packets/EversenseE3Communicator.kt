package app.aaps.plugins.eversense.packets

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import app.aaps.plugins.eversense.EversenseGattCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.CalibrationMode
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.models.EversenseTransmitterSettings
import app.aaps.plugins.eversense.packets.e3.GetBatteryPercentagePacket
import app.aaps.plugins.eversense.packets.e3.GetVersionPacket
import app.aaps.plugins.eversense.packets.e3.GetVersionExtendedPacket
import app.aaps.plugins.eversense.packets.e3.GetMmaFeaturesPacket
import app.aaps.plugins.eversense.packets.e3.GetHighGlucoseRepeatIntervalPacket
import app.aaps.plugins.eversense.packets.e3.GetLowGlucoseRepeatIntervalPacket
import app.aaps.plugins.eversense.packets.e3.SetBleDisconnectPacket
import app.aaps.plugins.eversense.packets.e3.SetAppVersionE3Packet
import app.aaps.plugins.eversense.packets.e3.GetCalibrationDailyPacket
import app.aaps.plugins.eversense.packets.e3.GetCalibrationPhasePacket
import app.aaps.plugins.eversense.packets.e3.GetCalibrationReadinessPacket
import app.aaps.plugins.eversense.packets.e3.GetCurrentDatetimePacket
import app.aaps.plugins.eversense.packets.e3.PingPacket
import app.aaps.plugins.eversense.packets.e3.GetCurrentGlucosePacket
import app.aaps.plugins.eversense.packets.e3.GetInsertionDatePacket
import app.aaps.plugins.eversense.packets.e3.GetInsertionTimePacket
import app.aaps.plugins.eversense.packets.e3.GetLastCalibrationDatePacket
import app.aaps.plugins.eversense.packets.e3.GetLastCalibrationTimePacket
import app.aaps.plugins.eversense.packets.e3.GetNextCalibrationDatePacket
import app.aaps.plugins.eversense.packets.e3.GetNextCalibrationTimePacket
import app.aaps.plugins.eversense.packets.e3.GetSettingGlucoseHighEnabled
import app.aaps.plugins.eversense.packets.e3.GetSettingGlucoseHighThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingGlucoseLowThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveHighEnabledPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveHighThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveHighTimePacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveLowEnabledPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveLowThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingPredictiveLowTimePacket
import app.aaps.plugins.eversense.packets.e3.GetSettingRateFallingEnabledPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingRateFallingThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingRateRisingEnabledPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingRateRisingThresholdPacket
import app.aaps.plugins.eversense.packets.e3.GetSettingVibratePacket
import app.aaps.plugins.eversense.packets.e3.SendCalibrationPacket
import app.aaps.plugins.eversense.packets.e3.SetCurrentDatetimePacket
import app.aaps.plugins.eversense.packets.e3.SetSettingGlucoseHighEnablePacket
import app.aaps.plugins.eversense.packets.e3.SetSettingGlucoseHighThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingGlucoseLowThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveHighAlarmEnabledPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveHighThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveHighTimePacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveLowAlarmEnabledPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveLowThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingPredictiveLowTimePacket
import app.aaps.plugins.eversense.packets.e3.SetSettingRateFallingEnabledPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingRateFallingThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingRateRisingEnabledPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingRateRisingThresholdPacket
import app.aaps.plugins.eversense.packets.e3.SetSettingVibratePacket
import app.aaps.plugins.eversense.util.EversenseLogger
import app.aaps.plugins.eversense.util.StorageKeys
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
                val glucoseData = gatt.writePacket<GetCurrentGlucosePacket.Response>(GetCurrentGlucosePacket())
                if (glucoseData.datetime <= state.recentGlucoseDatetime) {
                    EversenseLogger.warning(TAG, "Glucose data is still recent after reading - currentReading: ${glucoseData.datetime}, lastReading: ${state.recentGlucoseDatetime}")
                    return
                }

                if (glucoseData.glucoseInMgDl > 1000) {
                    EversenseLogger.error(TAG, "recentGlucose exceeds range - received: ${glucoseData.glucoseInMgDl}")
                    return
                }

                var currentGlucose = glucoseData.glucoseInMgDl

                val result = mutableListOf<EversenseCGMResult>()
                state.recentGlucoseDatetime = glucoseData.datetime
                state.recentGlucoseValue = currentGlucose
                state.lastGlucoseRaw = glucoseData.glucoseInMgDl
                result += EversenseCGMResult(
                    glucoseInMgDl = currentGlucose,
                    datetime = glucoseData.datetime,
                    trend = glucoseData.trend,
                    sensorId = state.sensorId,
                    rawResponseHex = glucoseData.rawResponseHex
                )

                // TODO: read history for backfill

                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                // Read RSSI to update placement signal after each glucose reading
                try {
                    EversenseLogger.debug(TAG, "Reading RSSI for placement signal...")
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Failed to read RSSI: $e")
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

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>, force: Boolean = false) {
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)
                val prevLastCalibrationDate = state.lastCalibrationDate

                val freshnessThreshold = if (state.calibrationReadiness == CalibrationReadiness.WAITING_POST_CALIBRATION)
                    TimeUnit.SECONDS.toMillis(60) else TimeUnit.SECONDS.toMillis(270)
                val freshnessCutoff = System.currentTimeMillis() - freshnessThreshold

                if (!force && freshnessCutoff < state.lastSync) {
                    EversenseLogger.warning(TAG, "State is still fresh - lastSync: ${state.lastSync}")
                    return
                }

                // Send ping first ΓÇö the official app calls postPingRequest() before any
                // ReadSingleByte commands. Without it the transmitter rejects 0x2A with
                // InvalidMessageLength (error 5) for every address.
                EversenseLogger.debug(TAG, "Pinging transmitter...")
                try {
                    gatt.writePacket<PingPacket.Response>(PingPacket())
                    EversenseLogger.info(TAG, "Ping successful")
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Ping failed (non-fatal): $e")
                }

                EversenseLogger.debug(TAG, "Reading current datetime...")
                val currentDatetime = gatt.writePacket<GetCurrentDatetimePacket.Response>(GetCurrentDatetimePacket())
                if (currentDatetime.needsTimeSync) {
                    EversenseLogger.debug(TAG, "Send SetCurrentDatetimePacket...")
                    gatt.writePacket<SetCurrentDatetimePacket.Response>(SetCurrentDatetimePacket())
                }

                // The E3 battery register returns an enum index (0-11) mapped to display percentages.
                // Mapping sourced from official Eversense app BATTERY_LEVEL enum (fromStrength).
                try {
                    EversenseLogger.debug(TAG, "Reading battery percentage...")
                    val batteryRaw = gatt.writePacket<GetBatteryPercentagePacket.Response>(GetBatteryPercentagePacket())
                    EversenseLogger.info(TAG, "Battery raw register value: ${batteryRaw.percentage}")
                    state.batteryPercentage = when (batteryRaw.percentage) {
                        0  -> 0
                        1  -> 5
                        2  -> 10
                        3  -> 25
                        4  -> 35
                        5  -> 45
                        6  -> 55
                        7  -> 65
                        8  -> 75
                        9  -> 85
                        10 -> 95
                        11 -> 100
                        else -> batteryRaw.percentage
                    }
                    EversenseLogger.info(TAG, "Battery percentage mapped: ${state.batteryPercentage}%")
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Battery read failed (non-fatal): $e")
                }

                // All flash register reads below are wrapped in try/catch.
                // Paolo's E3 firmware 6.04 rejects ReadTwoByte/ReadFourByte commands
                // with InvalidMessageLength (error 5) ΓÇö these must be non-fatal so
                // fullSync completes and lastSync updates even if reads fail.

                try {
                    EversenseLogger.debug(TAG, "Reading insertion datetime...")
                    val insertionDate = gatt.writePacket<GetInsertionDatePacket.Response>(GetInsertionDatePacket())
                    val insertionTime = gatt.writePacket<GetInsertionTimePacket.Response>(GetInsertionTimePacket())
                    val combined = insertionDate.date + insertionTime.time
                    val minDate = 1577836800000L  // 2020-01-01
                    val maxDate = 1893456000000L  // 2030-01-01
                    if (combined in minDate..maxDate) {
                        state.insertionDate = combined
                        EversenseLogger.info(TAG, "Insertion date accepted: $combined")
                    } else {
                        EversenseLogger.warning(TAG, "Insertion date out of plausible range, ignoring: $combined")
                    }
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Insertion datetime read failed (non-fatal): $e")
                }

                try {
                    EversenseLogger.debug(TAG, "Reading calibration info...")
                    val calibrationPhase = gatt.writePacket<GetCalibrationPhasePacket.Response>(GetCalibrationPhasePacket())
                    val calibrationReadiness = gatt.writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                    val nextCalibrationDate = gatt.writePacket<GetNextCalibrationDatePacket.Response>(GetNextCalibrationDatePacket())
                    val nextCalibrationTime = gatt.writePacket<GetNextCalibrationTimePacket.Response>(GetNextCalibrationTimePacket())
                    val lastCalibrationDate = gatt.writePacket<GetLastCalibrationDatePacket.Response>(GetLastCalibrationDatePacket())
                    val lastCalibrationTime = gatt.writePacket<GetLastCalibrationTimePacket.Response>(GetLastCalibrationTimePacket())
                    state.calibrationPhase = calibrationPhase.phase
                    state.calibrationReadiness = calibrationReadiness.readiness
                    val minCalDate = 1577836800000L  // 2020-01-01
                    val maxCalDate = 1893456000000L  // 2030-01-01
                    val newNextCal = nextCalibrationDate.date + nextCalibrationTime.time
                    val newLastCal = lastCalibrationDate.date + lastCalibrationTime.time
                    if (newNextCal in minCalDate..maxCalDate) {
                        state.nextCalibrationDate = newNextCal
                        EversenseLogger.info(TAG, "nextCalibrationDate accepted: $newNextCal")
                    } else {
                        EversenseLogger.warning(TAG, "nextCalibrationDate out of plausible range, ignoring: $newNextCal")
                    }
                    if (newLastCal in minCalDate..maxCalDate) {
                        if (newLastCal != prevLastCalibrationDate && prevLastCalibrationDate != 0L) {
                            EversenseLogger.info(TAG, "Calibration detected from external source: lastCalibrationDate changed from $prevLastCalibrationDate to $newLastCal")
                        }
                        state.lastCalibrationDate = newLastCal
                        EversenseLogger.info(TAG, "lastCalibrationDate accepted: $newLastCal")
                    } else {
                        EversenseLogger.warning(TAG, "lastCalibrationDate out of plausible range, ignoring: $newLastCal")
                    }
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Calibration info read failed (non-fatal): $e")
                }

                try {
                    val isDailyCalibration = gatt.writePacket<GetCalibrationDailyPacket.Response>(GetCalibrationDailyPacket())
                    state.calibrationMode = if (isDailyCalibration.isDaily) CalibrationMode.DAILY_SINGLE else CalibrationMode.DAILY_DUAL
                } catch (e: Exception) {
                    state.calibrationMode = CalibrationMode.DEFAULT
                }

                // Transmitter settings ΓÇö all non-fatal
                try {
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
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Settings read failed (non-fatal): $e")
                }

                // Get firmware version ΓÇö aligns with iOS GetVersionPacket
                try {
                    val version = gatt.writePacket<GetVersionPacket.Response>(GetVersionPacket())
                    state.firmwareVersion = version.version
                } catch (e: Exception) { EversenseLogger.warning(TAG, "GetVersion failed: $e") }

                // Get extended firmware version
                try {
                    val extVersion = gatt.writePacket<GetVersionExtendedPacket.Response>(GetVersionExtendedPacket())
                    state.extFirmwareVersion = extVersion.extVersion
                } catch (e: Exception) { EversenseLogger.warning(TAG, "GetVersionExtended failed: $e") }

                // Get MMA features
                try {
                    val mma = gatt.writePacket<GetMmaFeaturesPacket.Response>(GetMmaFeaturesPacket())
                    state.mmaFeatures = mma.value
                } catch (e: Exception) { EversenseLogger.warning(TAG, "GetMmaFeatures failed: $e") }

                // Set app version ΓÇö iOS sends 8.0.4 in every fullSync
                try { gatt.writePacket<SetAppVersionE3Packet.Response>(SetAppVersionE3Packet()) } catch (e: Exception) { EversenseLogger.warning(TAG, "SetAppVersionE3 failed: $e") }

                // Set BLE disconnect timeout ΓÇö 300s matching iOS default
                try { gatt.writePacket<SetBleDisconnectPacket.Response>(SetBleDisconnectPacket(300)) } catch (e: Exception) { EversenseLogger.warning(TAG, "SetBleDisconnect E3 failed: $e") }

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
                EversenseLogger.error(TAG, "Failed to write settings: $exception")
                return false
            }
        }

        // Send a blood glucose calibration value to the E3 transmitter.
        // The transmitter must be in CalibrationReadiness.READY state.
        // Throws EversenseWriteException if the packet fails.
        fun sendCalibration(gatt: EversenseGattCallback, glucoseMgDl: Int) {
            EversenseLogger.info(TAG, "Sending calibration value: $glucoseMgDl mg/dL")
            gatt.writePacket<SendCalibrationPacket.Response>(SendCalibrationPacket(glucoseMgDl), 15000L)
            EversenseLogger.info(TAG, "Calibration sent successfully")
        }
    }
}
