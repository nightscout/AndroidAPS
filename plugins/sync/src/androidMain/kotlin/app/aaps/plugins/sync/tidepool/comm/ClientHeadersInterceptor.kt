package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.configuration.Config
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Says which app and version send each Tidepool request, with the same values as the dataset's
 * `client.name` and `client.version`. Tidepool does not need these headers to work; they help to find
 * AAPS traffic in its logs. Before, fixed values ("aaps", "0.2.0") were sent, and only with a delete.
 */
class ClientHeadersInterceptor(private val config: Config) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val version = config.VERSION_NAME.asHeaderValue()
        return chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "AAPS/$version")
                .header("X-Tidepool-Client-Name", config.APPLICATION_ID.asHeaderValue())
                .header("X-Tidepool-Client-Version", version)
                .build()
        )
    }

    // OkHttp refuses a header value with characters outside printable ASCII, and a dev build's version
    // name could hold one. A refused header would fail every request, so such characters are dropped.
    private fun String.asHeaderValue(): String = filter { it in ' '..'~' }
}
