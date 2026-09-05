package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.core.ui.compose.icons.IcGoogleDrive
import app.aaps.implementation.ImplementationStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlin.time.Clock

/**
 * Google Drive on iOS: the shared provider, given the pieces it needs.
 *
 * There is no iOS Drive implementation - this builds the common one. What is here is only what
 * cannot be common: the HTTP engine (`Darwin`, which runs on `NSURLSession`), the socket that
 * catches the redirect, and the clock.
 *
 * ## The client id is Android's, on purpose
 *
 * The same id, the same `http://localhost:8080/oauth/callback`. Google registered that redirect for
 * this client already, and the loopback flow is one an iOS app can complete - which is why this
 * needed no new registration and no custom URL scheme. See [AuthRedirectListener].
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<CloudStorageProvider>())
@Inject
class IosGoogleDriveProvider(
    aapsLogger: AAPSLogger,
    store: KeyValueStore,
    listener: AuthRedirectListener
) : CloudStorageProvider by build(aapsLogger, store, listener) {

    private companion object {

        /**
         * The client AAPS is already registered as. Not a secret - a native app cannot keep one,
         * which is exactly why the flow uses PKCE instead of relying on it.
         */
        private const val CLIENT_ID = "705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com"

        private fun build(aapsLogger: AAPSLogger, store: KeyValueStore, listener: AuthRedirectListener): GoogleDriveProvider {
            val http = HttpClient(Darwin)
            val tokens = GoogleTokenStore(store)
            val redirectUri = "http://localhost:${GoogleDriveProvider.REDIRECT_PORT}/oauth/callback"
            val tokenClient = GoogleTokenClient(
                http = http,
                tokens = tokens,
                clientId = CLIENT_ID,
                redirectUri = redirectUri,
                now = { Clock.System.now().toEpochMilliseconds() }
            )
            return GoogleDriveProvider(
                aapsLogger = aapsLogger,
                store = store,
                listener = listener,
                auth = GoogleAuthRequest(platformCryptoPrimitives()),
                tokens = tokens,
                tokenClient = tokenClient,
                api = GoogleDriveApi(http, accessToken = { tokenClient.validAccessToken() }),
                storageType = StorageTypes.GOOGLE_DRIVE,
                displayName = "Google Drive",
                icon = IcGoogleDrive,
                authorizedText = ImplementationStrings.google_drive_authorized,
                reAuthRequiredText = ImplementationStrings.google_drive_reauth_required,
                clientId = CLIENT_ID
            )
        }
    }
}
