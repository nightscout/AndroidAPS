package app.aaps.core.nssdk.networking

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO

/**
 * Native engine: **CIO**, Ktor's own pure-Kotlin engine.
 *
 * This target is a compile-time proof that commonMain carries no JVM API - it is not a shipping
 * platform. An Apple target would use Darwin here instead, which is the engine an iOS build wants.
 *
 * Request logging is engine specific and is not wired up for this target, because nothing runs it.
 */
internal actual fun nsHttpClient(
    logging: Boolean,
    logger: (String) -> Unit,
    configure: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(CIO) {
    configure()
}
