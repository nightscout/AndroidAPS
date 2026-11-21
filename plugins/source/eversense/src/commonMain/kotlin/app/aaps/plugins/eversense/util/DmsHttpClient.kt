package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** The HTTP engine of the platform: OkHttp on Android, NSURLSession on iOS. */
internal expect val dmsHttpEngine: HttpClientEngineFactory<*>

/** URL-safe Base64 with no padding, as the Eversense vault takes its query values. */
internal val URL_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/** One transport implementation for E3 and 365, with explicit response-reading policies. */
internal object DmsHttpClient {

    data class Reply(val status: Int, val body: String)

    private const val CLIENT_ID = "eversenseMMAAndroid"
    private const val CLIENT_SECRET = "6ksPx#]~wQ3U"
    private const val POST_TIMEOUT_MS = 30_000L

    private val client by lazy { HttpClient(dmsHttpEngine) { install(HttpTimeout) } }

    fun passwordGrant(username: String, password: String): String = listOf(
        "grant_type=password", "client_id=$CLIENT_ID", "client_secret=$CLIENT_SECRET",
        "username=${formEncode(username)}", "password=${formEncode(password)}"
    ).joinToString("&")

    suspend fun post(
        url: String, body: String, token: String? = null, form: Boolean = false,
        tolerantBody: Boolean = false, readBody: Boolean = true
    ): Reply = exchange(url, HttpMethod.Post, body, token, if (form) "application/x-www-form-urlencoded" else "application/json", POST_TIMEOUT_MS, tolerantBody, readBody)

    /** No time limit of its own: the caller limits the whole registration. */
    suspend fun get(url: String, token: String): Reply = exchange(url, HttpMethod.Get, null, token, null, null, false, true)

    private suspend fun exchange(
        url: String, method: HttpMethod, body: String?, token: String?, contentType: String?,
        timeoutMs: Long?, tolerantBody: Boolean, readBody: Boolean
    ): Reply {
        val response = client.request(url) {
            this.method = method
            if (timeoutMs != null) timeout {
                connectTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) setBody(TextContent(body, ContentType.parse(contentType ?: "application/json")))
        }
        val status = response.status.value
        val text = if (!readBody) "" else try {
            response.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!tolerantBody) throw error
            ""
        }
        return Reply(status, text)
    }

    /**
     * `application/x-www-form-urlencoded` as `java.net.URLEncoder` wrote it: letters, digits and `.-*_` stay, a space
     * is `+`, every other byte of the UTF-8 form is `%XX` in upper case.
     */
    fun formEncode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val octet = byte.toInt() and 0xff
            val char = octet.toChar()
            when {
                char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ".-*_" -> append(char)
                char == ' '                                                           -> append('+')
                else                                                                  -> append('%').append(octet.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    /** `yyyy-MM-dd'T'HH:mm:ss` in UTC, the time format of the DMS records. */
    fun timestamp(millis: Long): String {
        val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC)
        fun two(value: Int) = value.toString().padStart(2, '0')
        return "${time.year.toString().padStart(4, '0')}-${two(time.month.number)}-${two(time.day)}T${two(time.hour)}:${two(time.minute)}:${two(time.second)}"
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
        reply.status in 200..299                                                      -> Outcome.OK
        reply.status == 401 || (reply.status == 400 && "invalid_grant" in reply.body) -> Outcome.REFUSED
        else                                                                          -> Outcome.SERVER_ERROR
    }

    /** Signs in once at [tokenBaseUrl] and stores nothing: the wizard's check of a login it has not stored yet. */
    suspend fun check(tokenBaseUrl: String, username: String, password: String): Outcome = try {
        outcome(DmsHttpClient.post("${tokenBaseUrl}connect/token", DmsHttpClient.passwordGrant(username, password), form = true))
    } catch (error: CancellationException) {
        throw error
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

    suspend fun resolve(preferences: EversenseStore, login: suspend () -> Login?): String? {
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
