package app.aaps.plugins.eversense.packets

import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.eversense.connection.EversenseLink
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import kotlinx.coroutines.CancellationException
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.CalibrationMode
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.packets.e3.GetBatteryPercentagePacket
import app.aaps.plugins.eversense.packets.e3.GetVersionPacket
import app.aaps.plugins.eversense.packets.e3.GetVersionExtendedPacket
import app.aaps.plugins.eversense.packets.e3.GetMmaFeaturesPacket
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
import app.aaps.plugins.eversense.packets.e3.SendCalibrationPacket
import app.aaps.plugins.eversense.packets.e3.SetCurrentDatetimePacket
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.serialization.json.Json

class EversenseE3Communicator {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        suspend fun readGlucose(gatt: EversenseLink, preferences: EversenseStore, watchers: List<EversenseWatcher>) {
            val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
            val state = JSON.decodeFromString<EversenseState>(stateJson)
            val recentCutoffTime = PacketTime.nowMillis() - 60_000L

            if (recentCutoffTime < state.recentGlucoseDatetime) {
                gatt.aapsLogger.warn(LTag.BGSOURCE, "Glucose data is still recent - lastReading: ${state.recentGlucoseDatetime}")
                return
            }

            try {
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Reading current glucose..." }
                val glucoseData = gatt.writePacket<GetCurrentGlucosePacket.Response>(GetCurrentGlucosePacket())
                if (glucoseData.datetime <= state.recentGlucoseDatetime) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Glucose data is still recent after reading - currentReading: ${glucoseData.datetime}, lastReading: ${state.recentGlucoseDatetime}")
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

                // TODO: read history for backfill - deliberately not implemented. Eversense is
                // phasing out E3 in favor of 365 (its EU/OUS successor), so this isn't worth
                // building for a shrinking user base.

                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }


                run {
                    watchers.forEach {
                        it.onCGMRead(EversenseType.EVERSENSE_E3, result)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                gatt.aapsLogger.error(LTag.BGSOURCE, "Got exception during readGlucose - exception $exception")
            }
        }

        suspend fun fullSync(gatt: EversenseLink, preferences: EversenseStore, watchers: List<EversenseWatcher>, force: Boolean = false) {
            try {
                val stateJson = preferences.getString(StorageKeys.STATE, null) ?: "{}"
                val state = JSON.decodeFromString<EversenseState>(stateJson)
                val prevLastCalibrationDate = state.lastCalibrationDate

                val freshnessThreshold = if (state.calibrationReadiness == CalibrationReadiness.WAITING_POST_CALIBRATION)
                    60_000L else 180_000L
                val freshnessCutoff = PacketTime.nowMillis() - freshnessThreshold

                if (!force && freshnessCutoff < state.lastSync) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "State is still fresh - lastSync: ${state.lastSync}")
                    return
                }

                // Send ping first - the official app calls postPingRequest() before any
                // ReadSingleByte commands. Without it the transmitter rejects 0x2A with
                // InvalidMessageLength (error 5) for every address.
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Pinging transmitter..." }
                try {
                    gatt.writePacket<PingPacket.Response>(PingPacket())
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Ping successful" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Ping failed (non-fatal): $e")
                }

                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Reading current datetime..." }
                val currentDatetime = gatt.writePacket<GetCurrentDatetimePacket.Response>(GetCurrentDatetimePacket())
                if (currentDatetime.needsTimeSync) {
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Send SetCurrentDatetimePacket..." }
                    gatt.writePacket<SetCurrentDatetimePacket.Response>(SetCurrentDatetimePacket())
                }

                // The E3 battery register returns an enum index (0-11) mapped to display percentages.
                // Mapping sourced from official Eversense app BATTERY_LEVEL enum (fromStrength).
                try {
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Reading battery percentage..." }
                    val batteryRaw = gatt.writePacket<GetBatteryPercentagePacket.Response>(GetBatteryPercentagePacket())
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Battery raw register value: ${batteryRaw.percentage}" }
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
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Battery percentage mapped: ${state.batteryPercentage}%" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Battery read failed (non-fatal): $e")
                }

                // All flash register reads below are wrapped in try/catch.
                // Paolo's E3 firmware 6.04 rejects ReadTwoByte/ReadFourByte commands
                // with InvalidMessageLength (error 5) - these must be non-fatal so
                // fullSync completes and lastSync updates even if reads fail.

                try {
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Reading insertion datetime..." }
                    val insertionDate = gatt.writePacket<GetInsertionDatePacket.Response>(GetInsertionDatePacket())
                    val insertionTime = gatt.writePacket<GetInsertionTimePacket.Response>(GetInsertionTimePacket())
                    val combined = insertionDate.date + insertionTime.time
                    val minDate = 1577836800000L  // 2020-01-01
                    val maxDate = 1893456000000L  // 2030-01-01
                    if (combined in minDate..maxDate) {
                        state.insertionDate = combined
                        gatt.aapsLogger.debug(LTag.BGSOURCE) { "Insertion date accepted: $combined" }
                    } else {
                        gatt.aapsLogger.warn(LTag.BGSOURCE, "Insertion date out of plausible range, ignoring: $combined")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Insertion datetime read failed (non-fatal): $e")
                }

                try {
                    gatt.aapsLogger.debug(LTag.BGSOURCE) { "Reading calibration info..." }
                    val calibrationPhase = gatt.writePacket<GetCalibrationPhasePacket.Response>(GetCalibrationPhasePacket())
                    val calibrationReadiness = gatt.writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                    val lastCalibrationDate = gatt.writePacket<GetLastCalibrationDatePacket.Response>(GetLastCalibrationDatePacket())
                    val lastCalibrationTime = gatt.writePacket<GetLastCalibrationTimePacket.Response>(GetLastCalibrationTimePacket())
                    state.calibrationPhase = calibrationPhase.phase
                    state.calibrationReadiness = calibrationReadiness.readiness
                    val minCalDate = 1577836800000L  // 2020-01-01
                    val maxCalDate = 1893456000000L  // 2030-01-01
                    val newLastCal = lastCalibrationDate.date + lastCalibrationTime.time
                    if (newLastCal in minCalDate..maxCalDate) {
                        if (newLastCal != prevLastCalibrationDate && prevLastCalibrationDate != 0L) {
                            gatt.aapsLogger.debug(LTag.BGSOURCE) { "Calibration detected from external source: lastCalibrationDate changed from $prevLastCalibrationDate to $newLastCal" }
                        }
                        state.lastCalibrationDate = newLastCal
                        // E3's calibration cadence is a fixed 24h - derive nextCalibrationDate from
                        // lastCalibrationDate instead of querying the transmitter for it. The device's
                        // own GetNextCalibrationDate/TimePacket registers proved unreliable in practice
                        // (matches the upstream iOS EversenseKit fix, which dropped the same two BLE
                        // reads for the identical reason - see EversenseKit commit d71d14564f).
                        state.nextCalibrationDate = newLastCal + 24 * 60 * 60_000L
                        gatt.aapsLogger.debug(LTag.BGSOURCE) { "lastCalibrationDate accepted: $newLastCal, nextCalibrationDate derived: ${state.nextCalibrationDate}" }
                    } else {
                        gatt.aapsLogger.warn(LTag.BGSOURCE, "lastCalibrationDate out of plausible range, ignoring: $newLastCal")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    gatt.aapsLogger.warn(LTag.BGSOURCE, "Calibration info read failed (non-fatal): $e")
                }

                try {
                    val isDailyCalibration = gatt.writePacket<GetCalibrationDailyPacket.Response>(GetCalibrationDailyPacket())
                    state.calibrationMode = if (isDailyCalibration.isDaily) CalibrationMode.DAILY_SINGLE else CalibrationMode.DAILY_DUAL
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state.calibrationMode = CalibrationMode.DEFAULT
                }

                // Get firmware version - aligns with iOS GetVersionPacket
                try {
                    val version = gatt.writePacket<GetVersionPacket.Response>(GetVersionPacket())
                    if (version != null) state.firmwareVersion = version.version
                } catch (e: CancellationException) { throw e } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "GetVersion failed: $e") }

                // Get extended firmware version
                try {
                    val extVersion = gatt.writePacket<GetVersionExtendedPacket.Response>(GetVersionExtendedPacket())
                    if (extVersion != null) state.extFirmwareVersion = extVersion.extVersion
                } catch (e: CancellationException) { throw e } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "GetVersionExtended failed: $e") }

                // Get MMA features
                try {
                    val mma = gatt.writePacket<GetMmaFeaturesPacket.Response>(GetMmaFeaturesPacket())
                    if (mma != null) state.mmaFeatures = mma.value
                } catch (e: CancellationException) { throw e } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "GetMmaFeatures failed: $e") }

                // Set app version - iOS sends 8.0.4 in every fullSync
                try { gatt.writePacket<SetAppVersionE3Packet.Response>(SetAppVersionE3Packet()) } catch (e: CancellationException) { throw e } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "SetAppVersionE3 failed: $e") }

                // Set BLE disconnect timeout - 10s
                try { gatt.writePacket<SetBleDisconnectPacket.Response>(SetBleDisconnectPacket(10)) } catch (e: CancellationException) { throw e } catch (e: Exception) { gatt.aapsLogger.warn(LTag.BGSOURCE, "SetBleDisconnect E3 failed: $e") }

                state.lastSync = PacketTime.nowMillis()
                gatt.aapsLogger.debug(LTag.BGSOURCE) { "Completed full sync - datetime: ${state.lastSync}" }
                preferences.edit(commit = true) {
                    putString(StorageKeys.STATE, JSON.encodeToString(state))
                }

                run {
                    watchers.forEach {
                        it.onStateChanged(state)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                gatt.aapsLogger.error(LTag.BGSOURCE, "Failed to do full sync: $exception")
            }
        }


        // Send a blood glucose calibration value to the E3 transmitter.
        // The transmitter must be in CalibrationReadiness.READY state.
        // Throws EversenseWriteException if the packet fails.
        suspend fun sendCalibration(gatt: EversenseLink, glucoseMgDl: Int) {
            gatt.aapsLogger.debug(LTag.BGSOURCE) { "Sending calibration value: $glucoseMgDl mg/dL" }
            gatt.writePacket<SendCalibrationPacket.Response>(SendCalibrationPacket(glucoseMgDl), 15000L)
            gatt.aapsLogger.debug(LTag.BGSOURCE) { "Calibration sent successfully" }
        }
    }
}
