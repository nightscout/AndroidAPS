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

    /**
     * Outcome of an upload attempt, detailed enough to diagnose from an exported AAPS log.
     *
     * Declared on the class rather than inside [Companion] so callers can name it as
     * `EversenseHttp365Util.UploadOutcome`.
     *
     * [EversenseLogger] output never reaches the exported log file - it uses arbitrary string
     * tags while the file appender only captures AAPS's LTag loggers - so everything needed to
     * diagnose a missing reading has to travel back to the caller, which logs through
     * aapsLogger.
     */
    data class UploadOutcome(
        val success: Boolean,
        /** Readings actually serialized and POSTed. Zero is not a success. */
        val sentCount: Int = 0,
        /** Readings dropped before sending because they carried no raw BLE data. */
        val skippedNoRawData: Int = 0,
        val httpStatus: Int? = null,
        /**
         * Readings POSTed with an empty SensorId. The portal indexes records by that field, so a
         * reading sent without one can be accepted and still never appear. Backfill readings are
         * the known source - GlucoseHistoryItem has no sensorId to carry.
         */
        val sentWithEmptySensorId: Int = 0,
        /** Server reply, truncated - this is what identifies a silent server-side reject. */
        val responseBody: String = "",
        val error: String? = null
    ) {

        /** One line, safe for the log: no token, no credentials, body clipped. */
        fun describe(): String =
            "sent=$sentCount skippedNoRawData=$skippedNoRawData emptySensorId=$sentWithEmptySensorId " +
                "status=${httpStatus ?: "-"}" +
                (error?.let { " error=$it" } ?: "") +
                (if (responseBody.isNotBlank()) " body=${responseBody.take(RESPONSE_BODY_LOG_LIMIT)}" else "")

        private companion object {

            const val RESPONSE_BODY_LOG_LIMIT = 300
        }
    }

    companion object {
        private val TAG = "EversenseHttp365Util"
        private val JSON = Json { ignoreUnknownKeys = true }

        private val CLIENT_ID = "eversenseMMAAndroid"
        private val CLIENT_SECRET = "6ksPx#]~wQ3U"
        private val CLIENT_NO = 2
        private val CLIENT_TYPE = 128

        // US endpoints. Overridable for unit tests. Used whenever
        // EversenseSecureState.isEuropeanRegion is false (the default) — untouched by the
        // EU routing below.
        internal var tokenBaseUrl = "https://usiamapi.eversensedms.com/"
        internal var uploadBaseUrl = "https://usmobileappmsprod.eversensedms.com/"
        internal var careBaseUrl = "https://usapialpha.eversensedms.com/"
        internal var vaultBaseUrl = "https://deviceauthorization.eversensedms.com/"

        // EU/OUS endpoints, selected per-call via effectiveTokenBaseUrl/effectiveUploadBaseUrl/
        // effectiveCareBaseUrl when EversenseSecureState.isEuropeanRegion is true.
        // euTokenBaseUrl is the exact host EversenseHttpE3Util.kt already uses for E3 (see its
        // header comment: confirmed from a decompiled real Eversense EU app, proven working in
        // production for real EU E3 transmitters). A first attempt at this guessed
        // "ousiamapi.eversensedms.com" (naive "us"->"ous" swap, no "alpha") purely from a CNAME
        // match to a distinctly-named AWS load balancer; that host is real infrastructure but
        // NOT this API - confirmed live via a real EU 365 user's Eversense.log, which showed
        // every login() call returning a bare IIS 404 (Server: Microsoft-HTTPAPI/2.0), not the
        // Cloudflare-fronted IdentityServer4 400 the working US host (and this corrected host)
        // both return to the same bare GET. euUploadBaseUrl and euCareBaseUrl were independently
        // verified live to give the same healthy app-level responses as their US counterparts, so
        // they're left as originally set.
        private const val euTokenBaseUrl = "https://ousiamapialpha.eversensedms.com/"
        private const val euUploadBaseUrl = "https://ousmobileappmsprod.eversensedms.com/"
        private const val euCareBaseUrl = "https://ousalphaapiservices.eversensedms.com/"

        // euVaultBaseUrl was initially left unrouted (vaultBaseUrl used for both regions) on the
        // theory that fleet/vault cert issuance is shared, account-independent infrastructure -
        // wrong: a real EU user's Eversense.log showed getFleetSecretV2() 404ing against
        // vaultBaseUrl even after the token-host fix made login() succeed. This "ous"-prefixed
        // host is confirmed live: identical response codes to vaultBaseUrl for every request
        // tried (401 with no Authorization header, 405 on HEAD), AND both return the exact same
        // distinctive error body - "Eversense.Link_expired" - to the same unauthenticated GET,
        // which isn't infrastructure coincidence.
        private const val euVaultBaseUrl = "https://ousdeviceauthorization.eversensedms.com/"

        private fun effectiveTokenBaseUrl(state: EversenseSecureState) = if (state.isEuropeanRegion) euTokenBaseUrl else tokenBaseUrl
        private fun effectiveUploadBaseUrl(state: EversenseSecureState) = if (state.isEuropeanRegion) euUploadBaseUrl else uploadBaseUrl
        private fun effectiveCareBaseUrl(state: EversenseSecureState) = if (state.isEuropeanRegion) euCareBaseUrl else careBaseUrl
        private fun effectiveVaultBaseUrl(state: EversenseSecureState) = if (state.isEuropeanRegion) euVaultBaseUrl else vaultBaseUrl

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

                val url = URL("${effectiveTokenBaseUrl(state)}connect/token")
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

        fun getFleetSecretV2(preferences: SharedPreferences, accessToken: String, serialNumber: ByteArray, nonce: ByteArray, flags: Boolean, publicKey: ByteArray): FleetSecretV2ResponseModel? {
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
                    URL("${effectiveVaultBaseUrl(getState(preferences))}api/vault/GetTxCertificate?$query")
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
         *
         * Returns an [UploadOutcome] rather than a Boolean, because a Boolean hid the two things
         * that matter when readings go missing from the DMS portal: how many readings were really
         * sent (as opposed to how many were handed in), and what the server said. Both response
         * bodies used to be read and then discarded, so a server that accepted the request and
         * silently dropped the reading looked identical to a genuine success.
         */
        fun uploadGlucoseReadings(
            preferences: SharedPreferences,
            readings: List<EversenseCGMResult>,
            transmitterSerialNumber: String,
            firmwareVersion: String
        ): UploadOutcome {
            if (readings.isEmpty()) return UploadOutcome(success = true)
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "Cannot upload glucose — no valid access token")
                return UploadOutcome(success = false, error = "no valid access token")
            }
            val state = getState(preferences)

            return try {
                // Only upload readings that have raw BLE data — readings without rawResponseHex are skipped.
                val uploadable = readings.filter { it.rawResponseHex.isNotEmpty() }
                if (uploadable.isEmpty()) {
                    EversenseLogger.info(TAG, "No readings with raw BLE data to upload — skipping")
                    // Deliberately not success: nothing reached the server, and reporting this as a
                    // send is what made dropped readings look like healthy uploads in the log.
                    return UploadOutcome(
                        success = false,
                        skippedNoRawData = readings.size,
                        error = "no readings carried raw BLE data"
                    )
                }

                EversenseLogger.info(TAG, "Uploading ${uploadable.size} reading(s) — TransmitterId='$transmitterSerialNumber'")

                // Counted, not filtered: a reading with no sensorId is still sent, because dropping
                // it would trade one silent loss for another. Reported so a portal gap can be tied
                // to the empty index key rather than guessed at.
                val emptySensorIds = uploadable.count { it.sensorId.isEmpty() }

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

                val url = URL("${effectiveUploadBaseUrl(state)}api/v1.0/DiagnosticLog/PostEssentialLogs")
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
                    EversenseLogger.error(TAG, "Glucose upload failed — status: $responseCode, body: $error")
                    UploadOutcome(
                        success = false,
                        sentCount = 0,
                        skippedNoRawData = readings.size - uploadable.size,
                        httpStatus = responseCode,
                        sentWithEmptySensorId = emptySensorIds,
                        responseBody = error
                    )
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "Glucose upload success — status: $responseCode, readings: ${uploadable.size}, body: $responseBody")
                    // Carried out, but the body is what tells us whether the server actually kept
                    // the reading - a 2xx alone has proven not to mean it landed in the portal.
                    UploadOutcome(
                        success = true,
                        sentCount = uploadable.size,
                        skippedNoRawData = readings.size - uploadable.size,
                        httpStatus = responseCode,
                        sentWithEmptySensorId = emptySensorIds,
                        responseBody = responseBody
                    )
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Glucose upload exception: $e")
                UploadOutcome(success = false, error = e.toString())
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
        ): UploadOutcome {
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "Cannot post current values — no valid access token")
                return UploadOutcome(success = false, error = "no valid access token")
            }
            return try {
                val ts = dateFormatter.get().format(Date(timestamp))
                val jsonBody = """{"CurrentGlucose":$glucose,"CGTime":"$ts","GlucoseTrend":${EversenseDmsBinaryCodec.trendOrdinal(trend)},"SignalStrength":${EversenseDmsBinaryCodec.signalStrengthOrdinal(signalStrength)},"BatteryStrength":${batteryPercentage.coerceAtLeast(0)},"IsTransmitterConnected":1}"""

                val url = URL("${effectiveCareBaseUrl(getState(preferences))}api/care/PutCurrentValues")
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
                    EversenseLogger.error(TAG, "PutCurrentValues failed — status: $responseCode, body: $error")
                    UploadOutcome(success = false, httpStatus = responseCode, responseBody = error)
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "PutCurrentValues success — status: $responseCode, glucose=$glucose, body: $responseBody")
                    UploadOutcome(success = true, sentCount = 1, httpStatus = responseCode, responseBody = responseBody)
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "PutCurrentValues exception: $e")
                UploadOutcome(success = false, error = e.toString())
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
        ): UploadOutcome {
            if (readings.isEmpty()) return UploadOutcome(success = true)
            val token = getOrRefreshToken(preferences) ?: run {
                EversenseLogger.error(TAG, "Cannot post device events — no valid access token")
                return UploadOutcome(success = false, error = "no valid access token")
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

                val url = URL("${effectiveCareBaseUrl(getState(preferences))}api/care/PutDeviceEvents")
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
                    EversenseLogger.error(TAG, "PutDeviceEvents failed — status: $responseCode, body: $error")
                    UploadOutcome(success = false, httpStatus = responseCode, responseBody = error)
                } else {
                    val responseBody = try { conn.inputStream.readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.info(TAG, "PutDeviceEvents success — status: $responseCode, readings: ${readings.size}, body: $responseBody")
                    UploadOutcome(
                        success = true,
                        sentCount = readings.size,
                        sentWithEmptySensorId = readings.count { it.sensorId.isEmpty() },
                        httpStatus = responseCode,
                        responseBody = responseBody
                    )
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "PutDeviceEvents exception: $e")
                UploadOutcome(success = false, error = e.toString())
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