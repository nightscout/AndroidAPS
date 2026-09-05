package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [AuthRedirectListener] on iOS, over [IosLoopbackAuthServer].
 *
 * A thin binding rather than a second implementation: the socket work is already written and tested
 * against a real simulator, and this only gives it the shape the shared sign in expects.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAuthRedirectListener @Inject constructor(aapsLogger: AAPSLogger) : AuthRedirectListener {

    private val server = IosLoopbackAuthServer(aapsLogger)

    override fun start(port: Int): Boolean = server.start(port)

    override suspend fun awaitCallback(expectedState: String, timeoutMs: Long): OAuthCallback.Result? =
        server.awaitCallback(expectedState, timeoutMs)

    override fun stop() = server.stop()
}
