package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Clock

/**
 * Google Drive on iOS: the shared provider, given the engine it needs.
 *
 * There is no iOS Drive implementation - [googleDriveProvider] builds the common one, and the only
 * thing that cannot be common is here: `Darwin`, which runs on `NSURLSession`. The redirect socket
 * arrives injected as `IosAuthRedirectListener`.
 *
 * The client id and the `http://localhost:8080/oauth/callback` redirect are Google's registration
 * for AAPS and are stated once, in `GoogleDriveProvider`. Google registered that redirect for this
 * client already and a loopback flow is one an iOS app can complete, which is why this needed no new
 * registration and no custom URL scheme. See [AuthRedirectListener].
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CloudStorageProvider>())
@Inject
class IosGoogleDriveProvider(
    aapsLogger: AAPSLogger,
    store: KeyValueStore,
    listener: AuthRedirectListener
) : CloudStorageProvider by googleDriveProvider(
    aapsLogger = aapsLogger,
    store = store,
    listener = listener,
    http = HttpClient(Darwin),
    now = { Clock.System.now().toEpochMilliseconds() }
)
