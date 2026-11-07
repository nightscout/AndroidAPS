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
 * Google Drive on Android: the shared provider, given the engine it needs.
 *
 * This replaced `GoogleDriveManager`, 1,445 lines that spoke to Drive over OkHttp by hand. Almost
 * all of it now exists in `commonMain` and is shared with iOS and the desktop, so the three
 * platforms cannot drift - which they had already begun to do, since only Android raised a
 * notification when a backup stopped working.
 *
 * The same engine the old code used. `OkHttp` was never the Android-only part: `GoogleDriveManager`
 * called Drive's REST API directly rather than through Play Services, and that is the single fact
 * that made this portable at all.
 *
 * ## Nothing about the user's stored sign in changes
 *
 * The token names, the selected-folder name and the client id are the ones Android already writes,
 * and the redirect is the same `http://localhost:8080/oauth/callback` Google has registered. So an
 * existing user is not signed out and does not lose the folder they picked - they keep using the
 * same Drive account through different code.
 *
 * The redirect socket needs no Android class at all: `JvmAuthRedirectListener` is in `jvmSharedMain`,
 * so it already contributes on this target - writing an `AndroidAuthRedirectListener` produced a
 * duplicate binding rather than a missing one. The browser is unchanged too: `ComposeMainActivity`
 * opens the URL in a Custom Tab, which is what `AuthBrowser` describes and what Android already did.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CloudStorageProvider>())
@Inject
class AndroidGoogleDriveProvider(
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
