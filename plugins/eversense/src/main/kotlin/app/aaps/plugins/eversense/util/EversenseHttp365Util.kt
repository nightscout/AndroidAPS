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
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EversenseHttp365Util {
    companion object {
        private val TAG = "EversenseHttp365Util"
        private val JSON = Json { ignoreUnknownKeys = true }

        private val CLIENT_ID = "eversenseMMAAndroid"
        private val CLIENT_SECRET = "6ksPx#]~wQ3U"
        private val CLIENT_NO = 2
        private val CLIENT_TYPE = 128

        // Overridable for unit tests
        internal var tokenBaseUrl = "https://usiamapi.eversensedms.com/"
        internal var uploadBaseUrl = "https://usmobileappmsprod.eversensedms.com/"
        internal var careBaseUrl = "https://usapialpha.eversensedms.com/"

        fun login(preference: SharedPreferences): LoginResponseModel? {
            val state = getState(preference)
            try {
                val formBody = listOf(
                    "grant_type=password",
                    "client_id=$CLIENT_ID",
                    "client_secret=$CLIENT_SECRET",
                    "username=${URLEncoder.encode(state.username, "UTF-8")}",
                    "password=${URLEncoder.encode(state.password, "UTF-8")}"
                ).joinToString("&")

                val url = URL("${tokenBaseUrl}connect/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(formBody)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode >= 400) {
                    val errorBody = try {
                        conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                    } catch (e: Exception) { "" }
                    EversenseLogger.error(TAG, "Login failed — status: $responseCode")
                    return null
                }

                val dataJson = BufferedInputStream(conn.inputStream).use { stream ->
                    val buffer = ByteArrayOutputStream()
                    var data = stream.read()
                    while (data != -1) {
                        buffer.write(data)
                        data = stream.read()
                    }
                    buffer.toString()
                }

                EversenseLogger.info(TAG, "Login success — status: $responseCode")
                return Json.decodeFromString(LoginResponseModel.serializer(), dataJson)
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Got exception during login - exception: $e")
                return null
            }
        }

        fun getFleetSecretV2(accessToken: String, serialNumber: ByteArray, nonce: ByteArray, flags: Boolean, publicKey: ByteArray): FleetSecretV2ResponseModel? {
            try {
                val publicKeyStr = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(publicKey.copyOfRange(27, publicKey.count()))
                val serialNumberStr =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(serialNumber)
                val nonceStr = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
                val query = listOf(
                    "tx_flags=$flags",
                    "txSerialNumber=$serialNumberStr",
                    "nonce=$nonceStr",
                    "clientNo=$CLIENT_NO",
                    "clientType=$CLIENT_TYPE",
                    "kp_client_unique_id=$publicKeyStr"
                ).joinToString("&")

                val url =
                    URL("https://deviceauthorization.eversensedms.com/api/vault/GetTxCertificate?$query")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connect()

                val bufferStream = BufferedInputStream(conn.inputStream)
                val buffer = ByteArrayOutputStream()
                var data = bufferStream.read()
                while (data != -1) {
                    buffer.write(data)
                    data = bufferStream.read()
                }

                val dataJson = buffer.toString()

                if (conn.responseCode >= 400) {
                    EversenseLogger.error(TAG, "Failed to fetch tx certificate - status: ${conn.responseCode}")
                    return null
                }

                val response = Json.decodeFromString(FleetSecretV2ResponseModel.serializer(), dataJson)
                if (response.Status != "Success" || response.Result.Certificate == null) {
                    EversenseLogger.error(TAG, "Received invalid tx certificate response - status=${response.Status}")
                    return null
                }

                return response
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Failed to get fleetSecretV2 - exception: $e")
                return null
            }
        }

        // SimpleDateFormat is not thread-safe. A ThreadLocal gives each thread its own
        // instance, so a scheduled sync and a manual sync running at the same time cannot
        // corrupt the formatted timestamp. Output is byte-for-byte identical to before.
        private val dateFormatter: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
        }

        fun getOrRefreshToken(preferences: SharedPreferences): String? {
            val expiry = preferences.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)
            val cached = preferences.getString(StorageKeys.ACCESS_TOKEN, null)
            // Use cached token if it has more than 5 minutes remaining
            if (cached != null && System.currentTimeMillis() < expiry - 300_000L) {
                return cached
            }
            // Re-login to get a fresh token
            val fresh = login(preferences) ?: return null
            val newExpiry = System.currentTimeMillis() + (fresh.expires_in * 1000L)
            preferences.edit(commit = true) {
                putString(StorageKeys.ACCESS_TOKEN, fresh.access_token)
                putLong(StorageKeys.ACCESS_TOKEN_EXPIRY, newExpiry)
            }
            return fresh.access_token
        }

        /**
         * Upload glucose readings to the Eversense DMS cloud.
         * Returns true if the server accepted the upload (HTTP 2xx), false on any error.
         */
        fun uploadGlucoseReadings(
            preferences: SharedPreferences,
            readings: List<EversenseCGMResult>,
            transmitterSerialNumber: String,
            firmwareVersion: String
        ): Boolean {
            if (readings.isEmpty()) return true
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "Cannot upload glucose — no valid access token")
                return false
            }

            return try {
                // Only upload readings that have raw BLE data — readings without rawResponseHex are skipped.
                val uploadable = readings.filter { it.rawResponseHex.isNotEmpty() }
                if (uploadable.isEmpty()) {
                    EversenseLogger.info(TAG, "No readings with raw BLE data to upload — skipping")
                    return true
                }

                EversenseLogger.info(TAG, "Uploading ${uploadable.size} reading(s) — TransmitterId='$transmitterSerialNumber'")

                // SensorId: the official app stores the first 8 bytes of the raw sensor ID in reversed
                // byte order, uppercase — matching what the DMS portal indexes readings by.
                // EssentialLog: base64-encoded bytes — the .NET server model uses System.Byte[] which
                // JSON-serializes as base64 (despite the Android app sending "0x"+hex, the server rejects it).
                // Body must be a bare JSON array — server deserializes directly to List<GlucoseEssentialLogsVM>
                val jsonBody = uploadable.joinToString(prefix = "[", postfix = "]") { r ->
                    val portalSensorId = r.sensorId.chunked(2).take(8).reversed().joinToString("").uppercase()
                    val essentialLog = Base64.getEncoder().encodeToString(
                        r.rawResponseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    )
                    val ts = dateFormatter.get().format(Date(r.datetime))
                    EversenseLogger.info(TAG, "  Reading: glucose=${r.glucoseInMgDl} ts=$ts rawHex=${r.rawResponseHex.length / 2}B")
                    """{"SensorId":"$portalSensorId","TransmitterId":"$transmitterSerialNumber","Timestamp":"$ts","CurrentGlucoseValue":${r.glucoseInMgDl},"CurrentGlucoseDateTime":"$ts","FWVersion":"$firmwareVersion","EssentialLog":"$essentialLog"}"""
                }

                val url = URL("${uploadBaseUrl}api/v1.0/DiagnosticLog/PostEssentialLogs")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(jsonBody)
                writer.flush()
                writer.close()
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode >= 400) {
                    val error = try { conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "" } catch (e: Exception) { "" }
                    EversenseLogger.error(TAG, "Glucose upload failed — status: $responseCode")
                    false
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "Glucose upload success — status: $responseCode, readings: ${uploadable.size}")
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Glucose upload exception: $e")
                false
            }
        }

        /**
         * Post current glucose state to the Eversense DMS portal (api/care/PutCurrentValues).
         * This updates "Last Sync Date" on the portal and feeds AGP reports.
         * Returns true on HTTP 2xx, false on any error.
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
                EversenseLogger.error(TAG, "Cannot post current values — no valid access token")
                return false
            }
            return try {
                val ts = dateFormatter.get().format(Date(timestamp))
                val jsonBody = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${EversenseDmsBinaryCodec.trendOrdinal(trend)},"SignalStrength":${EversenseDmsBinaryCodec.signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

                val url = URL("${careBaseUrl}api/care/PutCurrentValues")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody); it.flush() }

                val responseCode = conn.responseCode
                if (responseCode >= 400) {
                    val error = try { conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "" } catch (e: Exception) { "" }
                    EversenseLogger.error(TAG, "PutCurrentValues failed — status: $responseCode")
                    false
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "PutCurrentValues success — status: $responseCode, glucose=$glucose")
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "PutCurrentValues exception: $e")
                false
            }
        }

        /**
         * Post device events (sensor glucose readings) to the Eversense DMS portal.
         * This is the endpoint that populates the Sensor Glucose history table in the portal.
         * Returns true on HTTP 2xx, false on any error.
         *
         * Binary format reverse-engineered from com.senseonics.util.AccountConstants in the
         * decompiled official Eversense app.
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
                EversenseLogger.error(TAG, "Cannot post device events — no valid access token")
                return false
            }
            return try {
                val sensorId = readings.firstOrNull { it.sensorId.isNotEmpty() }?.sensorId ?: ""
                val tzOffsetSec = TimeZone.getDefault().getOffset(Date().time) / 1000
                val offsetBytes = Base64.getEncoder().encodeToString(EversenseDmsBinaryCodec.int32LE(tzOffsetSec))
                val sgBytes = EversenseDmsBinaryCodec.buildSgBytes(readings)
                val mgBytes = if (calibrations.isNotEmpty()) EversenseDmsBinaryCodec.buildMgBytes(calibrations) else EversenseDmsBinaryCodec.buildEmptyMgBytes()
                val patientBytes = EversenseDmsBinaryCodec.buildEmptyPatientBytes()
                val alertBytes = EversenseDmsBinaryCodec.buildAlertBytes(sensorId, alerts, readings.lastOrNull()?.datetime ?: System.currentTimeMillis(), readings.lastOrNull()?.glucoseInMgDl ?: 0)

                EversenseLogger.info(TAG, "PutDeviceEvents: ${readings.size} reading(s), txId='$transmitterSerialNumber', sensorId='$sensorId'")

                val jsonBody = """{"deviceType":"SMSIMeter","deviceName":"Smart Transmitter (Android)","deviceID":"$transmitterSerialNumber","offsetBytes":"$offsetBytes","sgBytes":"$sgBytes","mgBytes":"$mgBytes","patientBytes":"$patientBytes","alertBytes":"$alertBytes","algorithmVersion":"10"}"""

                val url = URL("${careBaseUrl}api/care/PutDeviceEvents")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody); it.flush() }

                val responseCode = conn.responseCode
                if (responseCode >= 400) {
                    val error = try { conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "" } catch (e: Exception) { "" }
                    EversenseLogger.error(TAG, "PutDeviceEvents failed — status: $responseCode")
                    false
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "PutDeviceEvents success — status: $responseCode, readings: ${readings.size}")
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "PutDeviceEvents exception: $e")
                false
            }
        }

        private fun getState(preference: SharedPreferences): EversenseSecureState {
            val stateJson = preference.getString(StorageKeys.SECURE_STATE, null) ?: "{}"
            return JSON.decodeFromString<EversenseSecureState>(stateJson)
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

    @Serializable
    @SuppressLint("UnsafeOptInUsageError")
    data class FleetSecretV2ResponseModel(
        val Status: String,
        val StatusCode: Int,
        val Result: FleetSecretV2Result
    )

    @Serializable
    @SuppressLint("UnsafeOptInUsageError")
    data class FleetSecretV2Result(
        val Certificate: String? = null,
        val Digital_Signature: String? = null,
        val IsKeyAvailable: Boolean,
        val KpAuthKey: String? = null,
        val KpTxId: String? = null,
        val KpTxUniqueId: String? = null,
        val tx_flag: Boolean? = null,
        val TxFleetKey: String? = null,
        val TxKeyVersion: String? = null
    )
}