package app.aaps.plugins.eversense.util

import android.annotation.SuppressLint
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.storage.EversenseStore
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        private const val TAG = "EversenseHttpE3Util"
        private val JSON = Json { ignoreUnknownKeys = true }


        // EU/OUS endpoints � confirmed from decompiled Eversense EU app v7.1.1
        internal var tokenBaseUrl = "https://ousiamapialpha.eversensedms.com/"
        internal var careBaseUrl  = "https://ousalphaapiservices.eversensedms.com/"

        // SimpleDateFormat is not thread-safe. A ThreadLocal gives each thread its own
        // instance, so a scheduled sync and a manual sync running at the same time cannot
        // corrupt the formatted timestamp. Output is byte-for-byte identical to before.
        private val dateFormatter: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        // -- Auth --------------------------------------------------------------

        fun login(preferences: EversenseStore): LoginResponseModel? {
            val state = getState(preferences)
            return try {
                val reply = DmsHttpClient.post("${tokenBaseUrl}connect/token", DmsHttpClient.passwordGrant(state.username, state.password), form = true)
                if (reply.status >= 400) {
                    EversenseLogger.error(TAG, "Login failed: HTTP ${reply.status}")
                    null
                } else {
                    EversenseLogger.info(TAG, "Login succeeded: HTTP ${reply.status}")
                    Json.decodeFromString(LoginResponseModel.serializer(), reply.body)
                }
            } catch (error: Exception) {
                EversenseLogger.error(TAG, "Login failed: $error")
                null
            }
        }

        fun getOrRefreshToken(preferences: EversenseStore): String? = DmsAccessTokens.resolve(preferences) {
            login(preferences)?.let { DmsAccessTokens.Login(it.access_token, it.expires_in) }
        }

        // -- Portal sync -------------------------------------------------------

        /**
         * POST api/care/PutCurrentValues � updates Last Sync Date on portal and feeds AGP reports.
         */
        fun putCurrentValues(
            preferences: EversenseStore,
            glucose: Int,
            timestamp: Long,
            trend: EversenseTrendArrow,
            signalStrength: Int,
            batteryPercentage: Int
        ): Boolean {
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "E3 putCurrentValues � no valid token")
                return false
            }
            return try {
                val ts = dateFormatter.get().format(Date(timestamp))
                val body = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${EversenseDmsBinaryCodec.trendOrdinal(trend)},"SignalStrength":${EversenseDmsBinaryCodec.signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

                val reply = DmsHttpClient.post("${careBaseUrl}api/care/PutCurrentValues", body, token, readBody = false)
                val code = reply.status
                if (code >= 400) {
                    val err = reply.body
                    EversenseLogger.error(TAG, "E3 PutCurrentValues failed � status: $code")
                    false
                } else {
                    EversenseLogger.debug(TAG) { "E3 PutCurrentValues ok � status: $code, glucose=$glucose" }
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "E3 PutCurrentValues exception: $e")
                false
            }
        }

        /**
         * POST api/care/PutDeviceEvents � populates Sensor Glucose history table on portal.
         */
        fun putDeviceEvents(
            preferences: EversenseStore,
            readings: List<EversenseCGMResult>,
            transmitterSerialNumber: String,
            calibrations: List<CalibrationHistoryItem> = emptyList(),
            alerts: List<app.aaps.plugins.eversense.models.ActiveAlarm> = emptyList()
        ): Boolean {
            if (readings.isEmpty()) return true
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "E3 putDeviceEvents � no valid token")
                return false
            }
            return try {
                val sensorId    = readings.firstOrNull { it.sensorId.isNotEmpty() }?.sensorId ?: ""
                val tzOffsetSec = TimeZone.getDefault().getOffset(Date().time) / 1000
                val offsetBytes = Base64.getEncoder().encodeToString(EversenseDmsBinaryCodec.int32LE(tzOffsetSec))
                val sgBytes     = EversenseDmsBinaryCodec.buildSgBytes(readings)
                val mgBytes     = if (calibrations.isNotEmpty()) EversenseDmsBinaryCodec.buildMgBytes(calibrations) else EversenseDmsBinaryCodec.buildEmptyMgBytes()
                val patientBytes = EversenseDmsBinaryCodec.buildEmptyPatientBytes()
                val alertBytes  = EversenseDmsBinaryCodec.buildAlertBytes(sensorId, alerts, readings.lastOrNull()?.datetime ?: System.currentTimeMillis(), readings.lastOrNull()?.glucoseInMgDl ?: 0)

                EversenseLogger.debug(TAG) { "E3 PutDeviceEvents: ${readings.size} reading(s), txId='$transmitterSerialNumber'" }

                val body = """{"deviceType":"SMSIMeter","deviceName":"Smart Transmitter (Android)","deviceID":"$transmitterSerialNumber","offsetBytes":"$offsetBytes","sgBytes":"$sgBytes","mgBytes":"$mgBytes","patientBytes":"$patientBytes","alertBytes":"$alertBytes","algorithmVersion":"10"}"""

                val reply = DmsHttpClient.post("${careBaseUrl}api/care/PutDeviceEvents", body, token, readBody = false)
                val code = reply.status
                if (code >= 400) {
                    val err = reply.body
                    EversenseLogger.error(TAG, "E3 PutDeviceEvents failed � status: $code")
                    false
                } else {
                    EversenseLogger.info(TAG, "E3 PutDeviceEvents ok � status: $code, readings: ${readings.size}")
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "E3 PutDeviceEvents exception: $e")
                false
            }
        }

        private fun getState(preferences: EversenseStore): EversenseSecureState {
            val json = preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
            return JSON.decodeFromString<EversenseSecureState>(json)
        }
    }

    @Serializable
    @SuppressLint("UnsafeOptInUsageError")
    data class LoginResponseModel(
        val access_token: String,
        val expires_in: Int,
        val token_type: String,
        val expires: String,
        val lastLogin: String
    )
}
