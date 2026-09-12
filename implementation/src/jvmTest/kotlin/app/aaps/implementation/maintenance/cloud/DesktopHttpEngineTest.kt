package app.aaps.implementation.maintenance.cloud

import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * That the desktop target actually has an HTTP engine.
 *
 * `ktor-client-core` is the API only - it can describe a request and has no way to send one. An
 * engine has to be on the classpath, and on the JVM Ktor finds it with a `ServiceLoader` at the
 * moment the client is built, so a missing one is not a compile error. Everything here takes an
 * `HttpClient` as a parameter rather than building one, which hides the gap completely: the code
 * compiles, the tests pass against `MockEngine`, and the first real call throws
 * `Failed to find HTTP client engine implementation`.
 *
 * Android has OkHttp and iOS has Darwin. This is the same statement for the desktop, and it is a
 * test rather than a comment because a dependency is one line and easy to lose in a merge.
 */
class DesktopHttpEngineTest {

    @Test
    fun `a client can be built with no engine named`() {
        HttpClient().use { client ->
            assertTrue(
                client.engine::class.simpleName.orEmpty().contains("OkHttp"),
                "the desktop should run on OkHttp, the engine :core:nssdk already brings here, but got ${client.engine::class.simpleName}"
            )
        }
    }
}
