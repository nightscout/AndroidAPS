package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.sync.nsclientV3.ws.NsSocket
import app.aaps.plugins.sync.nsclientV3.ws.NsSocketFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder, and the only one here whose real implementation already exists.
 *
 * `SwiftNsSocketFactory` in `ios/app/Shared/NsSocketBridge.swift` is the real one, on the official
 * `socket.io-client-swift` - the same project's client as the `socket.io-client-java` Android uses,
 * which is what keeps both platforms speaking to Nightscout identically. It cannot be bound inside
 * the Kotlin graph, because it is Swift: the app has to hand it in at start up, and wiring that is
 * the remaining step.
 *
 * Returning null is the interface's own answer for "no socket could be made", so callers already
 * handle it - the Nightscout client simply does not connect.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosNsSocketFactory @Inject constructor(
    private val aapsLogger: AAPSLogger
) : NsSocketFactory {

    override fun create(url: String): NsSocket? {
        aapsLogger.notOnIosYet("NsSocketFactory.create - the Swift factory is not wired in yet")
        return null
    }
}
