package app.aaps.plugins.eversense.packets

import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.eversense.storage.EversenseStore
import android.os.Handler
import android.os.Looper
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.EversenseGattCallback
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.packets.e365.GetActiveAlarmsPacket
import app.aaps.plugins.eversense.packets.e365.Ping365Packet
import app.aaps.plugins.eversense.packets.e365.SetAppVersion365Packet
import app.aaps.plugins.eversense.packets.e365.SetBleDisconnect365Packet
import app.aaps.plugins.eversense.packets.e365.GetCalibrationLogValuesPacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseLogValuesPacket
import app.aaps.plugins.eversense.packets.e365.GetLogRangePacket365
import app.aaps.plugins.eversense.packets.e365.LogType
import app.aaps.plugins.eversense.packets.e365.GetCalibrationInfoPacket
import app.aaps.plugins.eversense.packets.e365.GetGlucoseDataPacket
import app.aaps.plugins.eversense.packets.e365.GetSensorInformationPacket
import app.aaps.plugins.eversense.packets.e365.SetCurrentDateTimePacket
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class Eversense365Communicator {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        private val handler = Handler(Looper.getMainLooper())

        private var sensorIdLength = 10

        // The transmitter's history log and its "current glucose" characteristic timestamp the
        // same physical measurement a few seconds apart (log entry first, live characteristic a
        // moment later) - on a fast, already-connected keep-alive round trip that gap is usually
        // too small for the log to have caught up yet, but after a slower full reconnect (BLE
        // connect + service discovery + auth) there's enough elapsed time for the log to already
        // contain it. The backfill filter below excludes any history entry within this tolerance
        // of the live reading's timestamp so it isn't double-counted as a distinct reading -
        // comfortably under the ~5-minute reading cadence, so a real gap still backfills correctly.
        //
        // The same slip happens at the OTHER end too: the history log can timestamp a reading we
        // already stored last cycle (previousGlucoseDatetime) a second or two later than the live
        // characteristic did back then. A bare ">" comparison against previousGlucoseDatetime has
        // zero tolerance for that, so the already-seen reading re-backfills as "new" the moment the
        // log catches up to it. Apply the same tolerance on this end too.
        private val BACKFILL_DEDUP_TOLERANCE_MS = TimeUnit.SECONDS.toMillis(90)

        fun readGlucose(gatt: EversenseGattCallback, preferences: EversenseStore, watchers: List<EversenseWatcher>) {
            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)

            val glucoseData = gatt.writePacket<GetGlucoseDataPacket.Response>(GetGlucoseDataPacket(sensorIdLength))
            if (glucoseData.datetime <= state.recentGlucoseDatetime) {
                gatt.aapsLogger.warn(LTag.BGSOURCE, "Glucose data is still recent after reading - currentReading: ${glucoseData.datetime}, lastReading: ${state.recentGlucoseDatetime}")
                return
            }

            var currentGlucose = glucoseData.glucoseInMgDl

            val result = mutableListOf<EversenseCGMResult>()
            val previousGlucoseDatetime = state.recentGlucoseDatetime
            state.recentGlucoseDatetime = glucoseData.datetime
            state.recentGlucoseValue = currentGlucose
            state.lastGlucoseRaw = glucoseData.glucoseInMgDl
            state.sensorSignalStrength = glucoseData.signalStrength
            gatt.aapsLogger.debug(LTag.BGSOURCE) { "Sensor signal strength from glucose packet: ${glucoseData.signalStrength}" }

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
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Calibration: ${calibHistory.count} entries read (range $calibFrom..${calibLogRange.rangeTo})" }
                }
            } catch (e: Exception) {
                gatt.aapsLogger.warn(LTag.BGSOURCE, "Could not read calibration history: $e")
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
                    .filter { it.datetime > previousGlucoseDatetime + BACKFILL_DEDUP_TOLERANCE_MS && it.datetime < glucoseData.datetime - BACKFILL_DEDUP_TOLERANCE_MS }
                    .map { item -> EversenseCGMResult(glucoseInMgDl = item.valueInMgDl, datetime = item.datetime, trend = item.trend, rawResponseHex = item.rawResponseHex) }
                if (backfill.isNotEmpty()) {
                    result.addAll(0, backfill)
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Backfill: added ${backfill.size} historical readings" }
                }
            } catch (e: Exception) {
                gatt.aapsLogger.warn(LTag.BGSOURCE, "Could not read glucose history: $e")
            }

            preferences.edit(commit = true) {
                putString(StorageKeys.STATE, JSON.encodeToString(state))
            }

            handler.post {
                watchers.forEach { it.onCGMRead(EversenseType.EVERSENSE_365, result) }
                watchers.forEach { it.onStateChanged(state) }
            }
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: EversenseStore, watchers: List<EversenseWatcher>, force: Boolean = false) {
            val stateJsonCheck = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val stateCheck = JSON.decodeFromString<EversenseState>(stateJsonCheck)
            val fourMinAgo = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(240)
            if (!force && stateCheck.lastSync > fourMinAgo) {
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "365 fullSync skipped — last sync was recent (${(System.currentTimeMillis() - stateCheck.lastSync) / 1000}s ago)" }
                return
            }
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)

                var sensorInformation = gatt.writePacket<GetSensorInformationPacket.Response>(GetSensorInformationPacket())

                // Ping transmitter first — matches iOS fullSync order
                try { gatt.writePacket<Ping365Packet.Response>(Ping365Packet()) } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "Ping failed: $e") }

                if (abs(System.currentTimeMillis() - sensorInformation.transmitterDatetime) > 10_000) {
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Updating transmitter datetime" }
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
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Transmitter serialNumber='${sensorInformation.serialNumber}' transmitterName='${sensorInformation.transmitterName}'" }
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Firmware version: ${sensorInformation.version} / ${sensorInformation.extVersion}" }

                val calibrationInfo = gatt.writePacket<GetCalibrationInfoPacket.Response>(GetCalibrationInfoPacket())
                state.calibrationPhase = calibrationInfo.currentPhase
                state.calibrationReadiness = calibrationInfo.calibrationReadiness
                state.calibrationMode = calibrationInfo.calibrationMode
                state.nextCalibrationDate = calibrationInfo.nextCalibration
                state.lastCalibrationDate = calibrationInfo.lastCalibration

                // Send app version — iOS sends "8.0.4" in every fullSync
                try { gatt.writePacket<SetAppVersion365Packet.Response>(SetAppVersion365Packet()) } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "SetAppVersion failed: $e") }

                // Test the packet's standard five-minute interval; this firmware rejects zero.
                try {
                    gatt.writePacket<SetBleDisconnect365Packet.Response>(SetBleDisconnect365Packet(300))
                    gatt.aapsLogger.info(LTag.BGSOURCE, "SetBleDisconnect accepted: 300 seconds")
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "SetBleDisconnect (300 seconds) failed: $e")
                }

                // Read active alarms
                try {
                    val activeAlarms = gatt.writePacket<GetActiveAlarmsPacket.Response>(GetActiveAlarmsPacket())
                    // Drop unrecognized codes (e.g. the removed TxDocked/TxUndocked 68/69) so they
                    // can't masquerade as a real ongoing alarm - matches the upstream iOS EversenseKit fix.
                    val filteredAlarms = activeAlarms.alarms.filter { it.code != EversenseAlarm.UNKNOWN }
                    state.activeAlarms = filteredAlarms
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Active alarms: ${filteredAlarms.map { it.code.title }}" }
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Could not read active alarms: $e")
                }

                state.lastSync = System.currentTimeMillis()
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Completed full sync - datetime: ${state.lastSync}" }
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                handler.post {
                    watchers.forEach { it.onStateChanged(state) }
                }
            } catch (exception: Exception) {
                gatt.aapsLogger.error(LTag.BGSOURCE, "Failed to do full sync: $exception")
                gatt.aapsLogger.debug(LTag.BGSOURCE) { exception.stackTraceToString() }
                // Disconnect on fullSync failure so BLE session resets cleanly rather than looping
                // with broken GATT state. Must use disconnectAndScheduleReconnect() here, not plain
                // disconnect() - see that method's doc comment for why a self-initiated disconnect
                // doesn't reliably re-trigger onConnectionStateChange() (and therefore its reconnect
                // scheduling) on its own.
                gatt.aapsLogger.warn(LTag.BGSOURCE, "Disconnecting after fullSync failure to reset BLE session")
                gatt.disconnectAndScheduleReconnect()
            }
        }

    }
}

