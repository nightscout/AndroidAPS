package com.nightscout.eversense.util

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nightscout.eversense.models.EversenseCGMResult
import com.nightscout.eversense.models.EversenseSecureState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

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

        fun login(preference: SharedPreferences): LoginResponseModel? {
            val state = getState(preference)
            try {
                val formBody = listOf(
                    "grant_type=password",
                    "client_id=$CLIENT_ID",
                    "client_secret=$CLIENT_SECRET",
                    "username=${state.username}",
                    "password=${state.password}"
                ).joinToString("&")

                val url = URL("${tokenBaseUrl}connect/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val stream = conn.outputStream
                val outputStreamWriter = OutputStreamWriter(stream, "UTF-8")
                outputStreamWriter.write(formBody)
                outputStreamWriter.flush()
                outputStreamWriter.close()
                stream.close()
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
                    EversenseLogger.error(TAG, "Failed to do login - status: ${conn.responseCode}, data: $dataJson")
                    return null
                }

                EversenseLogger.info(TAG, "Login success - status: ${conn.responseCode}")
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
                    EversenseLogger.error(TAG, "Failed to do login - status: ${conn.responseCode}, data: $dataJson")
                    return null
                }

                val response = Json.decodeFromString(FleetSecretV2ResponseModel.serializer(), dataJson)
                if (response.Status != "Success" || response.Result.Certificate == null) {
                    EversenseLogger.error(TAG, "Received invalid response - message: $dataJson")
                    return null
                }

                return response
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Failed to get fleetSecretV2 - exception: $e")
                return null
            }
        }

        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

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
                // EssentialLog must be base64-encoded bytes (System.Byte[] in .NET JSON serialization)
                // Body must be a bare JSON array — server deserializes directly to List<GlucoseEssentialLogsVM>
                val jsonBody = readings.joinToString(prefix = "[", postfix = "]") { r ->
                    val rawBytes = r.rawResponseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val essentialLogBase64 = Base64.getEncoder().encodeToString(rawBytes)
                    """{"SensorId":"${r.sensorId}","TransmitterId":"$transmitterSerialNumber","Timestamp":"${dateFormatter.format(Date(r.datetime))}","CurrentGlucoseValue":${r.glucoseInMgDl},"CurrentGlucoseDateTime":"${dateFormatter.format(Date(r.datetime))}","FWVersion":"$firmwareVersion","EssentialLog":"$essentialLogBase64"}"""
                }

                val url = URL("${uploadBaseUrl}api/v1.0/DiagnosticLog/PostEssentialLogs")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
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
                    val error = try { BufferedInputStream(conn.errorStream).readBytes().toString(Charsets.UTF_8) } catch (e: Exception) { "" }
                    EversenseLogger.error(TAG, "Glucose upload failed — status: $responseCode, body: $error")
                    false
                } else {
                    EversenseLogger.info(TAG, "Glucose upload success — status: $responseCode, readings: ${readings.size}")
                    true
                }
            } catch (e: Exception) {
                EversenseLogger.error(TAG, "Glucose upload exception: $e")
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