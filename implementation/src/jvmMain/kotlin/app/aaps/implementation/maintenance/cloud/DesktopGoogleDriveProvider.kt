package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Clock

/**
 * Google Drive on the desktop: the shared provider, given the engine it needs.
 *
 * The counterpart of `IosGoogleDriveProvider`, and the same two lines around [googleDriveProvider].
 * `OkHttp` rather than `CIO` because `:core:nssdk` already brings OkHttp to this target for the
 * Nightscout client, so the desktop gains an engine binding instead of a second HTTP stack. The
 * redirect socket arrives injected as `JvmAuthRedirectListener`, and `DesktopAuthBrowser` opens the
 * sign in page.
 *
 * ## Per client, like everything else the app stores
 *
 * The tokens go through [KeyValueStore], which on desktop is `DesktopSp`, reading the preference
 * file inside this client's own data directory. So AAPSClient and AAPSClient2 sign in to Drive
 * separately and may point at different Google accounts, exactly as two Android clients do - while
 * the folder they export into is the shared `AAPS` one.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CloudStorageProvider>())
@Inject
class DesktopGoogleDriveProvider(
    aapsLogger: AAPSLogger,
    store: KeyValueStore,
    listener: AuthRedirectListener,
    notificationManager: NotificationManager
) : CloudStorageProvider by googleDriveProvider(
    aapsLogger = aapsLogger,
    store = store,
    listener = listener,
    notificationManager = notificationManager,
    http = HttpClient(OkHttp),
    now = { Clock.System.now().toEpochMilliseconds() }
)
