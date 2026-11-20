package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import java.io.IOException
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

/**
 * What a password login says about the login itself. The wizard checks a login with it before storing it, and every
 * later login records a refusal, so that a password changed on the Eversense side reaches the user as "sign in again"
 * instead of uploads and registrations failing in silence.
 */
internal object DmsSignIn {

    enum class Outcome { OK, REFUSED, NO_CONNECTION, SERVER_ERROR }

    /** 401, or 400 with `invalid_grant`, is the server refusing the username or password. Any other error is not about the login. */
    fun outcome(reply: DmsHttpClient.Reply): Outcome = when {
        reply.status in 200..299                                                -> Outcome.OK
        reply.status == 401 || (reply.status == 400 && "invalid_grant" in reply.body) -> Outcome.REFUSED
        else                                                                    -> Outcome.SERVER_ERROR
    }

    /** Signs in once at [tokenBaseUrl] and stores nothing: the wizard's check of a login it has not stored yet. */
    fun check(tokenBaseUrl: String, username: String, password: String): Outcome = try {
        outcome(DmsHttpClient.post("${tokenBaseUrl}connect/token", DmsHttpClient.passwordGrant(username, password), form = true))
    } catch (_: IOException) {
        Outcome.NO_CONNECTION
    } catch (_: Exception) {
        Outcome.SERVER_ERROR
    }

    /** Records whether the server accepted or refused the stored login. A failure that is not about the login changes nothing. */
    fun remember(store: EversenseStore, outcome: Outcome) {
        when (outcome) {
            Outcome.OK      -> if (store.contains(StorageKeys.LOGIN_REFUSED)) store.edit(commit = true) { remove(StorageKeys.LOGIN_REFUSED) }
            Outcome.REFUSED -> store.edit(commit = true) { putBoolean(StorageKeys.LOGIN_REFUSED, true) }
            else            -> Unit
        }
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
