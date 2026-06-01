package com.nightscout.eversense.util

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nightscout.eversense.enums.EversenseTrendArrow
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseSecureState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
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
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_JSON = "application/json"

        // OAuth2 client credentials embedded in the official Eversense Android app (publicly
        // extractable from the APK). Not a personal secret — same value ships to all users.
        private const val CLIENT_ID     = "eversenseMMAAndroid" // NOSONAR
        private const val CLIENT_SECRET = "6ksPx#]~wQ3U" // NOSONAR

        // EU/OUS endpoints � confirmed from decompiled Eversense EU app v7.1.1
        internal var tokenBaseUrl = "https://ousiamapialpha.eversensedms.com/"
        internal var careBaseUrl  = "https://ousalphaapiservices.eversensedms.com/"

        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
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
                conn.setRequestProperty(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded")
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(formBody); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    EversenseLogger.error(TAG, "E3 login failed � status: $code, body: $err")
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
                val ts = dateFormatter.format(Date(timestamp))
                val body = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${trendOrdinal(trend)},"SignalStrength":${signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

                val conn = URL("${careBaseUrl}api/care/PutCurrentValues").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout    = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    EversenseLogger.error(TAG, "E3 PutCurrentValues failed � status: $code, body: $err")
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
            transmitterSerialNumber: String
        ): Boolean {
            if (readings.isEmpty()) return true
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "E3 putDeviceEvents � no valid token")
                return false
            }
            return try {
                val sensorId    = readings.firstOrNull { it.sensorId.isNotEmpty() }?.sensorId ?: ""
                val tzOffsetSec = TimeZone.getDefault().getOffset(Date().time) / 1000
                val offsetBytes = Base64.getEncoder().encodeToString(int32LE(tzOffsetSec))
                val sgBytes     = buildSgBytes(readings)
                val mgBytes     = buildEmptyMgBytes()
                val patientBytes = buildEmptyPatientBytes()
                val alertBytes  = buildAlertBytes(sensorId)

                EversenseLogger.info(TAG, "E3 PutDeviceEvents: ${readings.size} reading(s), txId='$transmitterSerialNumber'")

                val body = """{"deviceType":"SMSIMeter","deviceName":"Smart Transmitter (Android)","deviceID":"$transmitterSerialNumber","offsetBytes":"$offsetBytes","sgBytes":"$sgBytes","mgBytes":"$mgBytes","patientBytes":"$patientBytes","alertBytes":"$alertBytes","algorithmVersion":"10"}"""

                val conn = URL("${careBaseUrl}api/care/PutDeviceEvents").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout    = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

                val code = conn.responseCode
                if (code >= 400) {
                    val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    EversenseLogger.error(TAG, "E3 PutDeviceEvents failed � status: $code, body: $err")
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

        // -- Binary helpers (shared with E365 format) --------------------------

        private fun int16LE(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        private fun int24LE(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte())
        private fun int32LE(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())

        private fun calcDateBytes(tsMs: Long): ByteArray {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT")).also { it.timeInMillis = tsMs }
            val year = cal.get(Calendar.YEAR); val month = cal.get(Calendar.MONTH) + 1; val day = cal.get(Calendar.DAY_OF_MONTH)
            var b1 = (year - 2000) shl 1; if (month > 7) b1 += 1
            val b0 = ((month and 7) shl 5) or day
            return byteArrayOf(b0.toByte(), b1.toByte())
        }

        private fun calcTimeBytes(tsMs: Long): ByteArray {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT")).also { it.timeInMillis = tsMs }
            val hour = cal.get(Calendar.HOUR_OF_DAY); val minute = cal.get(Calendar.MINUTE); val second = cal.get(Calendar.SECOND)
            val b0 = ((minute and 7) shl 5) or (second / 2)
            val b1 = (hour shl 3) or ((minute and 56) shr 3)
            return byteArrayOf(b0.toByte(), b1.toByte())
        }

        private fun buildSgBytes(readings: List<EversenseCGMResult>): String {
            val baos = ByteArrayOutputStream()
            baos.write(byteArrayOf(0x8C.toByte(), 0x00, 0x01, 0x00, 0x00))
            baos.write(int24LE(readings.size))
            readings.forEachIndexed { idx, r ->
                val sensorIdBytes = if (r.sensorId.isNotEmpty())
                    r.sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                else ByteArray(10)
                baos.write(int24LE(idx + 1))
                baos.write(calcDateBytes(r.datetime))
                baos.write(calcTimeBytes(r.datetime))
                baos.write(int16LE(r.glucoseInMgDl))
                baos.write(0x00)
                baos.write(sensorIdBytes)
                repeat(5) { baos.write(int16LE(0)) }
                baos.write(int16LE(0))
                baos.write(0x00)
                repeat(3) { baos.write(int16LE(0)) }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }

        private fun buildEmptyMgBytes()      = Base64.getEncoder().encodeToString(byteArrayOf(0x98.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00))
        private fun buildEmptyPatientBytes() = Base64.getEncoder().encodeToString(byteArrayOf(0x9E.toByte(), 0x01, 0x00, 0x00, 0x00))

        private fun buildAlertBytes(sensorId: String): String {
            val baos = ByteArrayOutputStream()
            baos.write(byteArrayOf(0x93.toByte(), 0x01, 0x00, 0x00, 0x00))
            if (sensorId.isNotEmpty())
                baos.write(sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            baos.write(0x00)
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }

        private fun signalStrengthOrdinal(percent: Int) = when {
            percent >= 75 -> 5; percent >= 48 -> 4; percent >= 30 -> 3
            percent >= 28 -> 2; percent >= 25 -> 1; else -> 0
        }

        private fun trendOrdinal(trend: EversenseTrendArrow) = when (trend) {
            EversenseTrendArrow.NONE            -> 0
            EversenseTrendArrow.SINGLE_DOWN     -> 1
            EversenseTrendArrow.FORTY_FIVE_DOWN -> 2
            EversenseTrendArrow.FLAT            -> 3
            EversenseTrendArrow.FORTY_FIVE_UP   -> 4
            EversenseTrendArrow.SINGLE_UP       -> 5
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
