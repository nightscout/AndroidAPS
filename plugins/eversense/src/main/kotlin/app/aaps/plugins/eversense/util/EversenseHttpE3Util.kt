package app.aaps.plugins.eversense.util

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.models.EversenseSecureState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

        // OAuth2 client credentials embedded in the official Eversense Android app (publicly
        // extractable from the APK). Not a personal secret — same value ships to all users.
        @Suppress("kotlin:S6418") // Public OAuth2 client ID from official Eversense APK — not a personal secret
        private const val CLIENT_ID     = "eversenseMMAAndroid"
        @Suppress("kotlin:S6418") // Public OAuth2 client secret from official Eversense APK — not a personal secret
        private const val CLIENT_SECRET = "6ksPx#]~wQ3U"

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

        fun login(preferences: SharedPreferences): LoginResponseModel? {
            val state = getState(preferences)
            return try {
                val formBody = listOf(
                    "grant_type=password",
                    "client_id=$CLIENT_ID",
                    "client_secret=$CLIENT_SECRET",
                    "username=${URLEncoder.encode(state.username, "UTF-8")}",
                    "password=${URLEncoder.encode(state.password, "UTF-8")}"
                ).joinToString("&")

                val conn = URL("${tokenBaseUrl}connect/token").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout    = 30_000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(formBody); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    EversenseLogger.error(TAG, "E3 login failed � status: $code")
                    return null
                }
                val body = conn.inputStream.readBytes().toString(Charsets.UTF_8)
                EversenseLogger.info(TAG, "E3 login success � status: $code")
                Json.decodeFromString(LoginResponseModel.serializer(), body)
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "E3 login exception: $e")
                null
            }
        }

        fun getOrRefreshToken(preferences: SharedPreferences): String? {
            val expiry  = preferences.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)
            val cached  = preferences.getString(StorageKeys.ACCESS_TOKEN, null)
            if (cached != null && System.currentTimeMillis() < expiry - 300_000L) return cached
            val fresh   = login(preferences) ?: return null
            val newExpiry = System.currentTimeMillis() + (fresh.expires_in * 1000L)
            preferences.edit(commit = true) {
                putString(StorageKeys.ACCESS_TOKEN, fresh.access_token)
                putLong(StorageKeys.ACCESS_TOKEN_EXPIRY, newExpiry)
            }
            return fresh.access_token
        }

        // -- Portal sync -------------------------------------------------------

        /**
         * POST api/care/PutCurrentValues � updates Last Sync Date on portal and feeds AGP reports.
         */
        fun putCurrentValues(
            preferences: SharedPreferences,
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

                val conn = URL("${careBaseUrl}api/care/PutCurrentValues").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout    = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    EversenseLogger.error(TAG, "E3 PutCurrentValues failed � status: $code")
                    false
                } else {
                    EversenseLogger.info(TAG, "E3 PutCurrentValues ok � status: $code, glucose=$glucose")
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
            preferences: SharedPreferences,
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

                EversenseLogger.info(TAG, "E3 PutDeviceEvents: ${readings.size} reading(s), txId='$transmitterSerialNumber'")

                val body = """{"deviceType":"SMSIMeter","deviceName":"Smart Transmitter (Android)","deviceID":"$transmitterSerialNumber","offsetBytes":"$offsetBytes","sgBytes":"$sgBytes","mgBytes":"$mgBytes","patientBytes":"$patientBytes","alertBytes":"$alertBytes","algorithmVersion":"10"}"""

                val conn = URL("${careBaseUrl}api/care/PutDeviceEvents").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout    = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
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

        private fun getState(preferences: SharedPreferences): EversenseSecureState {
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
