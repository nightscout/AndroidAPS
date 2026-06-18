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
import java.util.Calendar
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

        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
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
                    val ts = dateFormatter.format(Date(r.datetime))
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

        // Map signal strength percentage to SIGNAL_STRENGTH ordinal (from decompiled app)
        // NO_SIGNAL=0, POOR=1, VERY_LOW=2, LOW=3, GOOD=4, EXCELLENT=5
        private fun signalStrengthOrdinal(percent: Int): Int = when {
            percent >= 75 -> 5
            percent >= 48 -> 4
            percent >= 30 -> 3
            percent >= 28 -> 2
            percent >= 25 -> 1
            else          -> 0
        }

        // Map EversenseTrendArrow to Eversense ARROW_TYPE ordinal (from decompiled app)
        // STALE=0, FALLING_FAST=1, FALLING=2, FLAT=3, RISING=4, RISING_FAST=5
        private fun trendOrdinal(trend: EversenseTrendArrow): Int = when (trend) {
            EversenseTrendArrow.NONE          -> 0
            EversenseTrendArrow.SINGLE_DOWN   -> 1
            EversenseTrendArrow.FORTY_FIVE_DOWN -> 2
            EversenseTrendArrow.FLAT          -> 3
            EversenseTrendArrow.FORTY_FIVE_UP -> 4
            EversenseTrendArrow.SINGLE_UP     -> 5
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
                val ts = dateFormatter.format(Date(timestamp))
                val jsonBody = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${trendOrdinal(trend)},"SignalStrength":${signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

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
            calibrations: List<CalibrationHistoryItem> = emptyList()
        ): Boolean {
            if (readings.isEmpty()) return true
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "Cannot post device events — no valid access token")
                return false
            }
            return try {
                val sensorId = readings.firstOrNull { it.sensorId.isNotEmpty() }?.sensorId ?: ""
                val tzOffsetSec = TimeZone.getDefault().getOffset(Date().time) / 1000
                val offsetBytes = Base64.getEncoder().encodeToString(int32LE(tzOffsetSec))
                val sgBytes = buildSgBytes(readings)
                val mgBytes = if (calibrations.isNotEmpty()) buildMgBytes(calibrations) else buildEmptyMgBytes()
                val patientBytes = buildEmptyPatientBytes()
                val alertBytes = buildAlertBytes(sensorId)

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

        // ─── Binary encoding helpers (from com.senseonics.bluetoothle.BinaryOperations) ──────

        private fun int16LE(v: Int): ByteArray =
            byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

        private fun int24LE(v: Int): ByteArray =
            byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte())

        private fun int32LE(v: Int): ByteArray =
            byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())

        /** Encode a UTC timestamp as the 2-byte date format used by the Eversense transmitter binary protocol. */
        private fun calcDateBytes(tsMs: Long): ByteArray {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            cal.timeInMillis = tsMs
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1 // 1-12
            val day = cal.get(Calendar.DAY_OF_MONTH)
            var b1 = (year - 2000) shl 1
            if (month > 7) b1 += 1
            val b0 = ((month and 7) shl 5) or day
            return byteArrayOf(b0.toByte(), b1.toByte())
        }

        /** Encode a UTC timestamp as the 2-byte time format used by the Eversense transmitter binary protocol. */
        private fun calcTimeBytes(tsMs: Long): ByteArray {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            cal.timeInMillis = tsMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val second = cal.get(Calendar.SECOND)
            val b0 = ((minute and 7) shl 5) or (second / 2)
            val b1 = (hour shl 3) or ((minute and 56) shr 3)
            return byteArrayOf(b0.toByte(), b1.toByte())
        }

        /**
         * Build the sgBytes base64 blob from a list of glucose readings.
         * Format: header (8C 00 01 00 00 + 3-byte LE count) followed by one record per reading.
         * Raw sensor ADC values are zeroed — only glucose, timestamp, and sensor ID are populated.
         */
        private fun buildSgBytes(readings: List<EversenseCGMResult>): String {
            val baos = ByteArrayOutputStream()
            // Header: 8C 00 01 00 00 + count (3 bytes LE)
            baos.write(byteArrayOf(0x8C.toByte(), 0x00, 0x01, 0x00, 0x00))
            baos.write(int24LE(readings.size))
            readings.forEachIndexed { idx, r ->
                val sensorIdBytes = if (r.sensorId.isNotEmpty())
                    r.sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                else ByteArray(10)
                baos.write(int24LE(idx + 1))              // record number (1-based)
                baos.write(calcDateBytes(r.datetime))      // date (2 bytes)
                baos.write(calcTimeBytes(r.datetime))      // time (2 bytes)
                baos.write(int16LE(r.glucoseInMgDl))       // glucose (2 bytes LE)
                baos.write(0x00)                           // padding
                baos.write(sensorIdBytes)                  // sensor ID bytes
                // RAW_DATA_INDEX 1, 2, 3, 7, 8 — zeroed (no raw ADC data available)
                repeat(5) { baos.write(int16LE(0)) }
                // Accel values (2 bytes) — zeroed
                baos.write(int16LE(0))
                // AccelTemp (1 byte) — zeroed
                baos.write(0x00)
                // RAW_DATA_INDEX 4, 5, 6 — zeroed
                repeat(3) { baos.write(int16LE(0)) }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }

        /** Build mgBytes with actual calibration records for DMS upload. */
        private fun buildMgBytes(calibrations: List<CalibrationHistoryItem>): String {
            val baos = ByteArrayOutputStream()
            baos.write(byteArrayOf(0x98.toByte(), 0x01, 0x00))
            baos.write(int16LE(calibrations.size))
            baos.write(0x00)
            calibrations.forEachIndexed { idx, c ->
                baos.write(int24LE(idx + 1))
                baos.write(calcDateBytes(c.datetime))
                baos.write(calcTimeBytes(c.datetime))
                baos.write(int16LE(c.glucoseInMgDl))
                baos.write(1) // ACTUALLY_USED_FOR_CALIBRATION
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }

        /** Build the mgBytes base64 blob with zero BGM/calibration records. */
        private fun buildEmptyMgBytes(): String {
            // Header: 98 01 00 + count(2 bytes LE) + 00  → 0 records
            val bytes = byteArrayOf(0x98.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00)
            return Base64.getEncoder().encodeToString(bytes)
        }

        /** Build the patientBytes base64 blob with zero patient event records. */
        private fun buildEmptyPatientBytes(): String {
            // Header: 9E 01 00 + count(2 bytes LE)  → 0 events
            val bytes = byteArrayOf(0x9E.toByte(), 0x01, 0x00, 0x00, 0x00)
            return Base64.getEncoder().encodeToString(bytes)
        }

        /**
         * Build the alertBytes base64 blob with zero alert records.
         * The header includes the raw sensor ID bytes followed by a zero-count field.
         */
        private fun buildAlertBytes(sensorId: String): String {
            // Header: 93 01 00 + count(2 bytes LE) + sensorIdBytes + 00  → 0 alerts
            val baos = ByteArrayOutputStream()
            baos.write(byteArrayOf(0x93.toByte(), 0x01, 0x00, 0x00, 0x00))
            if (sensorId.isNotEmpty()) {
                val sensorIdBytes = sensorId.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                baos.write(sensorIdBytes)
            }
            baos.write(0x00)
            return Base64.getEncoder().encodeToString(baos.toByteArray())
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