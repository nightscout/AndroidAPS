package com.nightscout.eversense.util

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.nightscout.eversense.models.EversenseSecureState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class EversenseHttp365Util {
    companion object {
        private val TAG = "EversenseHttp365Util"
        private val JSON = Json { ignoreUnknownKeys = true }

        private val CLIENT_ID = "eversenseMMAAndroid"
        private val CLIENT_SECRET = "6ksPx#]~wQ3U"
        private val CLIENT_NO = 2
        private val CLIENT_TYPE = 128

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

                val url = URL("https://usiamapi.eversensedms.com/connect/token")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
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