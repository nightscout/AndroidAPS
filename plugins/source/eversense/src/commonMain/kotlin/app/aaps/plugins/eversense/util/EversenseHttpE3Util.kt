package app.aaps.plugins.eversense.util

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.protocol.BinaryFields
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/**
 * DMS cloud upload for Eversense E3 (EU/OUS) transmitters.
 *
 * Endpoints confirmed from decompiled Eversense EU app v7.1.1:
 *   Token : https://ousiamapialpha.eversensedms.com/
 *   Care  : https://ousalphaapiservices.eversensedms.com/
 *
 * Note: E3 does not use the diagnostic-log upload endpoint (PostEssentialLogs)
 * because it does not expose raw BLE response bytes. Only PutCurrentValues and
 * PutDeviceEvents are supported.
 */
class EversenseHttpE3Util {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }


        // EU/OUS endpoints, confirmed from decompiled Eversense EU app v7.1.1
        internal var tokenBaseUrl = "https://ousiamapialpha.eversensedms.com/"
        internal var careBaseUrl  = "https://ousalphaapiservices.eversensedms.com/"


        // -- Auth --------------------------------------------------------------

        suspend fun login(aapsLogger: AAPSLogger, preferences: EversenseStore): LoginResponseModel? {
            val state = getState(preferences)
            return try {
                val reply = DmsHttpClient.post("${tokenBaseUrl}connect/token", DmsHttpClient.passwordGrant(state.username, state.password), form = true)
                DmsSignIn.remember(preferences, DmsSignIn.outcome(reply))
                if (reply.status >= 400) {
                    aapsLogger.error(LTag.BGSOURCE, "Login failed: HTTP ${reply.status}")
                    null
                } else {
                    aapsLogger.info(LTag.BGSOURCE, "Login succeeded: HTTP ${reply.status}")
                    Json.decodeFromString(LoginResponseModel.serializer(), reply.body)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "Login failed: $error")
                null
            }
        }

        suspend fun getOrRefreshToken(aapsLogger: AAPSLogger, preferences: EversenseStore): String? = DmsAccessTokens.resolve(preferences) {
            login(aapsLogger, preferences)?.let { DmsAccessTokens.Login(it.access_token, it.expires_in) }
        }

        // -- Portal sync -------------------------------------------------------

        /**
         * POST api/care/PutCurrentValues - updates Last Sync Date on portal and feeds AGP reports.
         */
        suspend fun putCurrentValues(
            aapsLogger: AAPSLogger,
            preferences: EversenseStore,
            glucose: Int,
            timestamp: Long,
            trend: EversenseTrendArrow,
            signalStrength: Int,
            batteryPercentage: Int
        ): Boolean {
            val token = getOrRefreshToken(aapsLogger, preferences) ?: run {
                aapsLogger.error(LTag.BGSOURCE, "E3 putCurrentValues - no valid token")
                return false
            }
            return try {
                val ts = DmsHttpClient.timestamp(timestamp)
                val body = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${EversenseDmsBinaryCodec.trendOrdinal(trend)},"SignalStrength":${EversenseDmsBinaryCodec.signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

                val reply = DmsHttpClient.post("${careBaseUrl}api/care/PutCurrentValues", body, token, readBody = false)
                val code = reply.status
                if (code >= 400) {
                    val err = reply.body
                    aapsLogger.error(LTag.BGSOURCE, "E3 PutCurrentValues failed - status: $code")
                    false
                } else {
                    aapsLogger.debug(LTag.BGSOURCE) { "E3 PutCurrentValues ok - status: $code, glucose=$glucose" }
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "E3 PutCurrentValues exception: $e")
                false
            }
        }

        /**
         * POST api/care/PutDeviceEvents - populates Sensor Glucose history table on portal.
         */
        suspend fun putDeviceEvents(
            aapsLogger: AAPSLogger,
            preferences: EversenseStore,
            readings: List<EversenseCGMResult>,
            transmitterSerialNumber: String,
            calibrations: List<CalibrationHistoryItem> = emptyList(),
            alerts: List<app.aaps.plugins.eversense.models.ActiveAlarm> = emptyList()
        ): Boolean {
            if (readings.isEmpty()) return true
            val token = getOrRefreshToken(aapsLogger, preferences) ?: run {
                aapsLogger.error(LTag.BGSOURCE, "E3 putDeviceEvents - no valid token")
                return false
            }
            return try {
                val sensorId    = readings.firstOrNull { it.sensorId.isNotEmpty() }?.sensorId ?: ""
                val tzOffsetSec = BinaryFields.zoneOffsetMillis(PacketTime.nowMillis()) / 1000
                val offsetBytes = Base64.encode(EversenseDmsBinaryCodec.int32LE(tzOffsetSec))
                val sgBytes     = EversenseDmsBinaryCodec.buildSgBytes(readings)
                val mgBytes     = if (calibrations.isNotEmpty()) EversenseDmsBinaryCodec.buildMgBytes(calibrations) else EversenseDmsBinaryCodec.buildEmptyMgBytes()
                val patientBytes = EversenseDmsBinaryCodec.buildEmptyPatientBytes()
                val alertBytes  = EversenseDmsBinaryCodec.buildAlertBytes(sensorId, alerts, readings.lastOrNull()?.datetime ?: PacketTime.nowMillis(), readings.lastOrNull()?.glucoseInMgDl ?: 0)

                aapsLogger.debug(LTag.BGSOURCE) { "E3 PutDeviceEvents: ${readings.size} reading(s), txId='$transmitterSerialNumber'" }

                val body = """{"deviceType":"SMSIMeter","deviceName":"Smart Transmitter (Android)","deviceID":"$transmitterSerialNumber","offsetBytes":"$offsetBytes","sgBytes":"$sgBytes","mgBytes":"$mgBytes","patientBytes":"$patientBytes","alertBytes":"$alertBytes","algorithmVersion":"10"}"""

                val reply = DmsHttpClient.post("${careBaseUrl}api/care/PutDeviceEvents", body, token, readBody = false)
                val code = reply.status
                if (code >= 400) {
                    val err = reply.body
                    aapsLogger.error(LTag.BGSOURCE, "E3 PutDeviceEvents failed - status: $code")
                    false
                } else {
                    aapsLogger.info(LTag.BGSOURCE, "E3 PutDeviceEvents ok - status: $code, readings: ${readings.size}")
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "E3 PutDeviceEvents exception: $e")
                false
            }
        }

        private fun getState(preferences: EversenseStore): EversenseSecureState {
            val json = preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
            return JSON.decodeFromString<EversenseSecureState>(json)
        }
    }

    @Serializable
    data class LoginResponseModel(
        val access_token: String,
        val expires_in: Int,
        val token_type: String,
        val expires: String,
        val lastLogin: String
    )
}
