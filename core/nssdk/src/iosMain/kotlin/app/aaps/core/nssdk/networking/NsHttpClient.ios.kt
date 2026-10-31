package app.aaps.core.nssdk.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/**
 * Apple engine: **Darwin**, which runs on `NSURLSession`. That is what an iOS build wants - it gets
 * the system's own connection handling, proxy settings and certificate validation rather than a
 * second HTTP stack, exactly as the JVM side reuses the OkHttp the app already ships.
 *
 * Request logging is still not wired up here. On JVM it is an OkHttp interceptor, which has no
 * Darwin counterpart; the portable answer is Ktor's own `Logging` plugin, and that is worth adding
 * when there is an iOS client to read the log.
 */
internal actual fun nsHttpClient(
    logging: Boolean,
    logger: (String) -> Unit,
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin) {
    configure()
}
