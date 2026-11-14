package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One transport implementation for E3 and 365, with explicit response-reading policies. */
internal object DmsHttpClient {
    data class Reply(val status: Int, val body: String)
    private const val CLIENT_ID = "eversenseMMAAndroid"
    private const val CLIENT_SECRET = "6ksPx#]~wQ3U"

    fun passwordGrant(username: String, password: String): String = listOf(
        "grant_type=password", "client_id=$CLIENT_ID", "client_secret=$CLIENT_SECRET",
        "username=${URLEncoder.encode(username, "UTF-8")}", "password=${URLEncoder.encode(password, "UTF-8")}"
    ).joinToString("&")

    fun post(url: String, body: String, token: String? = null, form: Boolean = false,
             tolerantBody: Boolean = false, readBody: Boolean = true): Reply =
        exchange(url, "POST", body, token, if (form) "application/x-www-form-urlencoded" else "application/json", 30_000, tolerantBody, readBody)

    fun get(url: String, token: String): Reply = exchange(url, "GET", null, token, null, 0, false, true)

    private fun exchange(url: String, method: String, body: String?, token: String?, contentType: String?,
                         timeout: Int, tolerantBody: Boolean, readBody: Boolean): Reply {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)); it.flush() }
            }
            val status = connection.responseCode
            val text = if (!readBody) "" else try {
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (error: Exception) {
                if (!tolerantBody) throw error
                ""
            }
            return Reply(status, text)
        } finally { connection.disconnect() }
    }
}

/** Cache semantics are shared; the model-specific login still selects its own endpoint. */
internal object DmsAccessTokens {
    data class Login(val token: String, val expiresInSeconds: Int)
    fun resolve(preferences: EversenseStore, login: () -> Login?): String? {
        val expiry = preferences.getLong(StorageKeys.ACCESS_TOKEN_EXPIRY, 0)
        val cached = preferences.getString(StorageKeys.ACCESS_TOKEN, null)
        if (cached != null && PacketTime.nowMillis() < expiry - 300_000L) return cached
        val session = login() ?: return null
        preferences.edit(commit = true) {
            putString(StorageKeys.ACCESS_TOKEN, session.token)
            putLong(StorageKeys.ACCESS_TOKEN_EXPIRY, PacketTime.nowMillis() + session.expiresInSeconds * 1000L)
        }
        return session.token
    }
}
