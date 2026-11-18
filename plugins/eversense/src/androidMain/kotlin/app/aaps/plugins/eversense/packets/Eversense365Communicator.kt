package app.aaps.plugins.eversense.packets

import app.aaps.plugins.eversense.storage.EversenseStore
import android.os.Handler
import android.os.Looper
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.EversenseGattCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.E365GlucoseSync
import app.aaps.plugins.eversense.models.E365LiveReading
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
        private const val TAG = "Eversense365Communicator"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val handler = Handler(Looper.getMainLooper())

        private var sensorIdLength = 10

        private const val LOG_PAGE_SIZE = 20
        private const val MAX_PENDING_LIVE_READINGS = 288
        private val liveMaxAgeMs = TimeUnit.MINUTES.toMillis(6)
        private val futureToleranceMs = TimeUnit.MINUTES.toMillis(5)
        private val logMatchToleranceMs = TimeUnit.SECONDS.toMillis(90)
        private val samplingIntervalMs = TimeUnit.MINUTES.toMillis(5)

        fun readGlucose(gatt: EversenseGattCallback, preferences: EversenseStore, watchers: List<EversenseWatcher>) {
            val state = JSON.decodeFromString<EversenseState>(preferences.getString(StorageKeys.STATE, null) ?: "{}")
            val now = System.currentTimeMillis()
            val savedSync = state.e365GlucoseSync
            val sameTransmitter = savedSync.transmitter == state.transmitterSerialNumber
            var sync = if (sameTransmitter) savedSync else E365GlucoseSync(transmitter = state.transmitterSerialNumber)
            val previousTime = state.recentGlucoseDatetime.takeIf {
                (sameTransmitter || savedSync.transmitter.isEmpty()) && it in 1..(now + futureToleranceMs)
            } ?: 0L
            if (savedSync.transmitter.isEmpty() && sync.lastRecord == null && sync.initialGlucoseDatetime == null &&
                previousTime > 0 && state.sensorId.isNotEmpty()
            ) {
                sync = sync.copy(liveReadings = listOf(E365LiveReading(previousTime, state.recentGlucoseValue, state.sensorId)))
            }
            if (sync.lastRecord == null && sync.initialGlucoseDatetime == null) {
                sync = sync.copy(initialGlucoseDatetime = previousTime)
                state.e365GlucoseSync = sync
                saveState(preferences, state)
            }

            val live = try {
                gatt.writePacket<GetGlucoseDataPacket.Response>(GetGlucoseDataPacket(sensorIdLength))
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "Could not read current glucose: $e")
                null
            }
            if (live != null && live.glucoseInMgDl in 40..400 &&
                live.datetime in (now - liveMaxAgeMs)..(now + futureToleranceMs) &&
                (live.datetime > previousTime || (previousTime > now && live.datetime <= now) ||
                    (state.sensorId.isNotEmpty() && live.sensorId != state.sensorId))
            ) {
                state.recentGlucoseDatetime = live.datetime
                state.recentGlucoseValue = live.glucoseInMgDl
                state.lastGlucoseRaw = live.glucoseInMgDl
                state.sensorSignalStrength = live.signalStrength
                state.sensorId = live.sensorId
                val marker = E365LiveReading(live.datetime, live.glucoseInMgDl, live.sensorId)
                sync = sync.copy(liveReadings = (sync.liveReadings + marker).takeLast(MAX_PENDING_LIVE_READINGS))
                // Hand off the usable value before any optional log command can end this link.
                watchers.forEach {
                    it.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(live.glucoseInMgDl, live.datetime, live.trend, live.sensorId)))
                }
                state.e365GlucoseSync = sync
                saveState(preferences, state)
            }

            try {
                syncGlucoseLog(gatt, preferences, watchers, state, sync, previousTime, now)
                syncCalibrationLog(gatt, preferences, state)
            } catch (e: Exception) {
                EversenseLogger.warning(TAG, "Could not sync transmitter logs: $e")
            }

            handler.post { watchers.forEach { it.onStateChanged(state) } }
        }

        private fun syncGlucoseLog(
            gatt: EversenseGattCallback,
            preferences: EversenseStore,
            watchers: List<EversenseWatcher>,
            state: EversenseState,
            sync: E365GlucoseSync,
            previousTime: Long,
            now: Long
        ) {
            val range = gatt.writePacket<GetLogRangePacket365.Response>(GetLogRangePacket365(LogType.GLUCOSE))
            if (range.rangeFrom < 0 || range.rangeTo < range.rangeFrom) return

            val cursor = sync.lastRecord?.takeIf { it <= range.rangeTo }
            if (cursor == range.rangeTo) return
            val initialTime = sync.initialGlucoseDatetime ?: previousTime
            val initialLookback = if (initialTime > 0) {
                ((now - initialTime).coerceAtLeast(0) / samplingIntervalMs + 2).coerceAtMost(range.rangeTo.toLong())
            } else (LOG_PAGE_SIZE - 1).toLong()
            val from = maxOf(range.rangeFrom, cursor?.plus(1) ?: (range.rangeTo - initialLookback).toInt())
            val to = minOf(range.rangeTo.toLong(), from.toLong() + LOG_PAGE_SIZE - 1).toInt()
            if (cursor == null) {
                // Keep the initial position even if the first page times out.
                state.e365GlucoseSync = sync.copy(lastRecord = from - 1, initialGlucoseDatetime = null)
                saveState(preferences, state)
            }
            val page = gatt.writePacket<GetGlucoseLogValuesPacket.Response>(
                GetGlucoseLogValuesPacket(from, to, sensorIdLength)
            )

            // Only acknowledge consecutive records. A missing or truncated response leaves the
            // next record eligible for the next sync, even after a newer live reading arrives.
            val received = page.recordNumbers.toSet()
            val lastRecord = (from..to).takeWhile { it in received }.lastOrNull() ?: return
            val remainingMarkers = sync.liveReadings.toMutableList()
            val readings = page.glucoseHistory
                .filter { (it.recordNumber ?: -1) in from..lastRecord && it.valueInMgDl in 40..400 && it.datetime in 1..(now + futureToleranceMs) }
                .distinctBy { it.recordNumber }
                .sortedBy { it.recordNumber }
                .map { item ->
                    val matchingLive = remainingMarkers.singleOrNull {
                        it.sensorId.equals(item.sensorId, ignoreCase = true) && it.glucoseInMgDl == item.valueInMgDl &&
                            abs(it.datetime - item.datetime) <= logMatchToleranceMs
                    }
                    if (matchingLive != null) remainingMarkers.remove(matchingLive)
                    EversenseCGMResult(
                        glucoseInMgDl = item.valueInMgDl,
                        datetime = item.datetime,
                        trend = item.trend,
                        sensorId = item.sensorId,
                        rawResponseHex = item.rawResponseHex,
                        recordNumber = item.recordNumber,
                        cloudOnly = matchingLive != null
                    )
                }
            if (readings.isNotEmpty()) watchers.forEach { it.onCGMRead(EversenseType.EVERSENSE_365, readings) }

            state.e365GlucoseSync = sync.copy(lastRecord = lastRecord, initialGlucoseDatetime = null, liveReadings = remainingMarkers)
            saveState(preferences, state)
        }

        private fun syncCalibrationLog(gatt: EversenseGattCallback, preferences: EversenseStore, state: EversenseState) {
            val range = gatt.writePacket<GetLogRangePacket365.Response>(GetLogRangePacket365(LogType.CALIBRATIONS))
            if (range.rangeTo <= range.rangeFrom) return
            val history = gatt.writePacket<GetCalibrationLogValuesPacket.Response>(
                GetCalibrationLogValuesPacket(from = maxOf(range.rangeTo - 1, range.rangeFrom), to = range.rangeTo)
            )
            state.calibrationHistory = history.calibrationHistory
            saveState(preferences, state)
        }

        private fun saveState(preferences: EversenseStore, state: EversenseState) {
            preferences.edit(commit = true) { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: EversenseStore, watchers: List<EversenseWatcher>, force: Boolean = false) {
            val stateJsonCheck = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val stateCheck = JSON.decodeFromString<EversenseState>(stateJsonCheck)
            val fourMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(240)
            if (!force && stateCheck.lastSync > fourMinAgo) {
                EversenseLogger.debug(TAG) { "365 fullSync skipped — last sync was recent (${(System.currentTimeMillis() - stateCheck.lastSync) / 1000}s ago)" }
                return
            }
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)

                var sensorInformation = gatt.writePacket<GetSensorInformationPacket.Response>(GetSensorInformationPacket())

                // Ping transmitter first — matches iOS fullSync order
                try { gatt.writePacket<Ping365Packet.Response>(Ping365Packet()) } catch (e: Exception) { EversenseLogger.warning(TAG, "Ping failed: $e") }

                if (abs(System.currentTimeMillis() - sensorInformation.transmitterDatetime) > 10_000) {
                    EversenseLogger.debug(TAG) { "Updating transmitter datetime" }
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
                EversenseLogger.debug(TAG) { "Transmitter serialNumber='${sensorInformation.serialNumber}' transmitterName='${sensorInformation.transmitterName}'" }
                EversenseLogger.debug(TAG) { "Firmware version: ${sensorInformation.version} / ${sensorInformation.extVersion}" }

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

                // Test the packet's standard five-minute interval; this firmware rejects zero.
                try {
                    gatt.writePacket<SetBleDisconnect365Packet.Response>(SetBleDisconnect365Packet(300))
                    EversenseLogger.info(TAG, "SetBleDisconnect accepted: 300 seconds")
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "SetBleDisconnect (300 seconds) failed: $e")
                }

                // Read active alarms
                try {
                    val activeAlarms = gatt.writePacket<GetActiveAlarmsPacket.Response>(GetActiveAlarmsPacket())
                    // Drop unrecognized codes (e.g. the removed TxDocked/TxUndocked 68/69) so they
                    // can't masquerade as a real ongoing alarm - matches the upstream iOS EversenseKit fix.
                    val filteredAlarms = activeAlarms.alarms.filter { it.code != EversenseAlarm.UNKNOWN }
                    state.activeAlarms = filteredAlarms
                    EversenseLogger.debug(TAG) { "Active alarms: ${filteredAlarms.map { it.code.title }}" }
                } catch (e: Exception) {
                    EversenseLogger.warning(TAG, "Could not read active alarms: $e")
                }

                state.lastSync = System.currentTimeMillis()
                EversenseLogger.debug(TAG) { "Completed full sync - datetime: ${state.lastSync}" }
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                handler.post {
                    watchers.forEach { it.onStateChanged(state) }
                }
            } catch (exception: Exception) {
                EversenseLogger.error(TAG, "Failed to do full sync: $exception")
                EversenseLogger.debug(TAG) { exception.stackTraceToString() }
                // Disconnect on fullSync failure so BLE session resets cleanly rather than looping
                // with broken GATT state. Must use disconnectAndScheduleReconnect() here, not plain
                // disconnect() - see that method's doc comment for why a self-initiated disconnect
                // doesn't reliably re-trigger onConnectionStateChange() (and therefore its reconnect
                // scheduling) on its own.
                EversenseLogger.warning(TAG, "Disconnecting after fullSync failure to reset BLE session")
                gatt.disconnectAndScheduleReconnect()
            }
        }

        fun writeSettings(gatt: EversenseGattCallback, preferences: EversenseStore, settings: EversenseTransmitterSettings): Boolean {
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
                EversenseLogger.debug(TAG) { "365 settings written successfully" }
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

