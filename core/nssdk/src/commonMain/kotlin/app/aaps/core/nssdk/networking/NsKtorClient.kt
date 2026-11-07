package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.nsSdkJson
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Creates the client with a platform engine, and whatever logging that engine supports.
 *
 * The engine is the one part of the HTTP stack that cannot be shared: JVM and Android use OkHttp
 * (which is what the app already ships, so nothing new is added), and Apple targets use Darwin.
 * Request logging is engine specific too, which is why it lives here rather than in the common
 * configuration below.
 */
internal expect fun nsHttpClient(
    logging: Boolean,
    logger: (String) -> Unit,
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient

/**
 * Builds the Ktor client used to talk to Nightscout.
 *
 * Two settings are load bearing and must not be "improved":
 *
 * - **`expectSuccess = false`.** Ktor's default throws on any non-2xx. The whole client is written
 *   the other way round: a 4xx is a value, not an exception. `getSettings` returns a 404 result,
 *   `updateSvg` and `deleteSettings` treat 404 as success, and every write returns
 *   `CreateUpdateResponse(response = <status>)`. Turning this on would make all of that dead code -
 *   and because `ClientRequestException` is not in the retry exclusion list, each of those calls
 *   would also be retried four times before failing. See `NsSdkStatusContractTest`.
 * - **No `HttpRequestRetry` plugin.** Retries are already done one level up by
 *   `NSAndroidClientImpl.callWrapper`. Adding Ktor's plugin would multiply with it, and the
 *   `utcOffset` fallback re-enters a public method, so a single reading could produce well over ten
 *   POSTs inside one sync.
 *
 * There is deliberately **no cache**. The OkHttp disk cache existed only so a revalidated GET could
 * surface as a 304, which the paging workers used as their stop condition. That signal is replaced
 * by "stop when the cursor cannot advance", which says what it means and needs no `Context`.
 */
internal object NsKtorClient {

    /**
     * Everything the client needs, built together so the two [HttpClient]s can be closed as a pair.
     *
     * There are two on purpose: the refresh call must not carry an `Authorization` header, or the
     * interceptor would recurse into itself.
     */
    class Stack(val api: NightscoutApi, private val main: HttpClient, private val refresh: HttpClient) {

        fun close() {
            main.close()
            refresh.close()
        }
    }

    fun stack(baseUrl: String, refreshToken: String, logging: Boolean, logger: (String) -> Unit): Stack {
        val main = build(logging, logger)
        val refresh = build(logging, logger)
        NsAuth.install(main, refresh, baseUrl, refreshToken)
        return Stack(NightscoutApi(main, baseUrl), main, refresh)
    }

    fun build(logging: Boolean, logger: (String) -> Unit): HttpClient = nsHttpClient(logging, logger) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(nsSdkJson)
        }

        install(HttpTimeout) {
            socketTimeoutMillis = SOCKET_TIMEOUT
            connectTimeoutMillis = CONNECT_TIMEOUT
        }
    }

    /** Matches the old OkHttp read timeout. */
    private const val SOCKET_TIMEOUT = 60L * 1000

    /**
     * New, and deliberately so: OkHttp applied its own 10 s connect default, which Ktor does not.
     * Leaving it unset would let a dead host hang until the socket timeout instead.
     */
    private const val CONNECT_TIMEOUT = 10L * 1000
}
