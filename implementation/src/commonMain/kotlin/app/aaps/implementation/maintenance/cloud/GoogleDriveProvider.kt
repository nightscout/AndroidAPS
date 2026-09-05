package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.CloudFile
import app.aaps.core.interfaces.maintenance.CloudFileListResult
import app.aaps.core.interfaces.maintenance.CloudFolder
import app.aaps.core.interfaces.maintenance.CloudStorageProvider
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.keys.interfaces.TextRef
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.objects.crypto.platformCryptoPrimitives
import app.aaps.core.ui.compose.icons.IcGoogleDrive
import app.aaps.implementation.ImplementationStrings
import io.ktor.client.HttpClient

/**
 * Google Drive as a place to keep settings exports, written to work on every platform.
 *
 * iOS and the desktop both run this - `IosGoogleDriveProvider` and `DesktopGoogleDriveProvider` are
 * the two lines of engine choice around [googleDriveProvider] below. Android is the last one still
 * on its own `GoogleDriveManager`, so a change here reaches two of the three platforms and does not
 * touch a phone.
 *
 * Composed rather than written: [GoogleAuthRequest] builds the sign in, [AuthRedirectListener]
 * catches the answer, [GoogleTokenClient] turns it into tokens and keeps them fresh, and
 * [GoogleDriveApi] does the work. Each of those is tested on its own; what is here is the order they
 * go in.
 *
 * ## The sign in, step by step
 *
 * 1. [startAuth] makes a verifier, a challenge and a state, starts the listener, and returns a URL.
 * 2. The shell shows that URL **in a browser presented over the app**. Handing it to the system
 *    browser instead would switch away from AAPS, and a backgrounded app is suspended within seconds
 *    - taking the listener with it, so the redirect would arrive at a closed port.
 * 3. [waitForAuthCode] waits for the redirect and checks the state.
 * 4. [completeAuth] swaps the code for tokens.
 *
 * Step 2 is the one that is easy to get wrong, because handing a URL to the browser is what every
 * other link in the app does.
 */
class GoogleDriveProvider(
    private val aapsLogger: AAPSLogger,
    private val store: KeyValueStore,
    private val listener: AuthRedirectListener,
    private val auth: GoogleAuthRequest,
    private val tokens: GoogleTokenStore,
    private val tokenClient: GoogleTokenClient,
    private val api: GoogleDriveApi,
    override val storageType: String,
    override val displayName: String,
    override val icon: ImageVector,
    override val authorizedText: TextRef,
    override val reAuthRequiredText: TextRef,
    private val clientId: String,
    private val redirectPort: Int = REDIRECT_PORT
) : CloudStorageProvider {

    // ----- Signing in -----

    /** Returns the URL to show, or null when the port could not be opened for the redirect. */
    override suspend fun startAuth(): String? {
        val verifier = auth.newVerifier()
        val state = auth.newState()
        tokens.codeVerifier = verifier
        tokens.state = state

        if (!listener.start(redirectPort)) {
            aapsLogger.error(LTag.CORE, "$TAG could not open port $redirectPort for the sign in")
            return null
        }

        return auth.authorizationUrl(
            authEndpoint = AUTH_ENDPOINT,
            clientId = clientId,
            redirectUri = redirectUri(),
            scope = SCOPE,
            challenge = auth.challengeFor(verifier),
            state = state
        )
    }

    /**
     * Waits for the redirect and gives back the code.
     *
     * The listener is stopped whatever happens, so a sign in the user abandoned does not leave a port
     * open behind them.
     */
    override suspend fun waitForAuthCode(timeoutMs: Long): String? {
        val expected = tokens.state
        if (expected == null) {
            aapsLogger.error(LTag.CORE, "$TAG asked to wait for a sign in that was not started")
            listener.stop()
            return null
        }
        return try {
            when (val result = listener.awaitCallback(expected, timeoutMs)) {
                is OAuthCallback.Result.Code   -> result.code
                is OAuthCallback.Result.Denied -> {
                    aapsLogger.info(LTag.CORE, "$TAG the user did not allow the sign in (${result.error})")
                    null
                }

                else                           -> null
            }
        } finally {
            listener.stop()
        }
    }

    override suspend fun completeAuth(authCode: String): Boolean {
        val failure = tokenClient.exchangeCode(authCode)
        if (failure != null) {
            aapsLogger.error(LTag.CORE, "$TAG the sign in could not be completed: $failure")
            storeConnectionError(true)
            return false
        }
        storeConnectionError(false)
        return true
    }

    override fun hasValidCredentials(): Boolean = tokens.refreshToken != null

    override fun clearCredentials() {
        tokens.clear()
        clearConnectionError()
    }

    override suspend fun revokeAccess(): Boolean {
        // Nothing to revoke, and clearing is still the right local answer.
        clearCredentials()
        return true
    }

    override suspend fun getValidAccessToken(): String? = tokenClient.validAccessToken().getOrNull()

    // ----- Using it -----

    override suspend fun testConnection(): Boolean = api.testConnection().fold(
        onSuccess = { clearConnectionError(); true },
        onFailure = { noteFailure(it); false }
    )

    override suspend fun getOrCreateFolderPath(path: String): String? =
        api.folderIdForPath(path).onFailure { noteFailure(it) }.getOrNull()

    override suspend fun createFolder(name: String, parentId: String): String? =
        api.createFolder(name, parentId).onFailure { noteFailure(it) }.getOrNull()

    override suspend fun listFolders(parentId: String): List<CloudFolder> =
        api.listFolders(parentId)
            .onFailure { noteFailure(it) }
            .getOrDefault(emptyList())
            .map { CloudFolder(id = it.id, name = it.name) }

    override suspend fun uploadFileToPath(fileName: String, content: ByteArray, mimeType: String, path: String): String? {
        val folderId = getOrCreateFolderPath(path) ?: return null
        return api.uploadFile(fileName, content, folderId, mimeType).onFailure { noteFailure(it) }.getOrNull()
    }

    override suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): String? =
        api.uploadFile(fileName, content, getSelectedFolderId(), mimeType).onFailure { noteFailure(it) }.getOrNull()

    override suspend fun downloadFile(fileId: String): ByteArray? =
        api.downloadFile(fileId).onFailure { noteFailure(it) }.getOrNull()

    override suspend fun listSettingsFiles(pageSize: Int, pageToken: String?): CloudFileListResult {
        val page = api.listSettingsFiles(getSelectedFolderId(), pageToken, pageSize)
            .onFailure { noteFailure(it) }
            .getOrNull() ?: return CloudFileListResult(files = emptyList())
        return CloudFileListResult(
            files = page.files.map { CloudFile(id = it.id, name = it.name, mimeType = SETTINGS_MIME) },
            nextPageToken = page.nextPageToken
        )
    }

    override suspend fun countSettingsFiles(): Int {
        var total = 0
        var token: String? = null
        do {
            val page = api.listSettingsFiles(getSelectedFolderId(), token, COUNT_PAGE_SIZE).getOrNull() ?: return total
            total += page.files.size
            token = page.nextPageToken
        } while (token != null)
        return total
    }

    override fun getSelectedFolderId(): String = store.getString(SELECTED_FOLDER, "root")

    override fun setSelectedFolderId(folderId: String) = store.putString(SELECTED_FOLDER, folderId)

    // ----- Whether the last attempt worked -----

    /**
     * Whether the last attempt to reach Drive failed.
     *
     * Held in memory, not stored, which is what the Android manager does and is the right shape: a
     * connection error describes now, not forever. Persisting it means a user whose train went into a
     * tunnel yesterday still sees a warning today, on a working connection, until something happens
     * to clear it - and if they stop trying, nothing ever does.
     *
     * Whether it is worth showing at all is the caller's question, since only it knows whether Drive
     * is the storage the user actually chose.
     */
    private var connectionError = false

    override fun hasConnectionError(): Boolean = connectionError

    override fun clearConnectionError() = storeConnectionError(false)

    private fun storeConnectionError(failed: Boolean) {
        connectionError = failed
    }

    /**
     * A sign in that is finished is cleared; anything else is remembered as a connection error.
     *
     * The difference is what the user is shown: one of them has to send them back through a sign in,
     * the other should say that Drive could not be reached and leave the sign in alone.
     */
    private fun noteFailure(error: Throwable) {
        val expired = (error as? DriveException)?.failure is DriveFailure.SignInExpired ||
            (error as? TokenException)?.failure is TokenFailure.SignInExpired
        if (expired) {
            aapsLogger.warn(LTag.CORE, "$TAG the Google sign in is no longer valid, it has been cleared")
            tokens.clear()
        } else {
            aapsLogger.error(LTag.CORE, "$TAG Drive could not be reached: ${error.message}")
        }
        storeConnectionError(true)
    }

    private fun redirectUri(): String = "http://localhost:$redirectPort/oauth/callback"

    companion object {

        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

        /** Only files AAPS created. It cannot read the rest of someone's Drive, and should not. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"

        /** The port Google already has registered against the redirect for this client. */
        const val REDIRECT_PORT = 8080

        /**
         * The client AAPS is registered as, on every platform.
         *
         * Not a secret - a native app cannot keep one, which is exactly why the flow uses PKCE
         * instead of relying on it. One definition because it is one registration: Android, iOS and
         * the desktop all present this id against the same `http://localhost:8080/oauth/callback`,
         * and a second copy of it is a second thing to get wrong.
         */
        const val CLIENT_ID = "705061051276-3ied5cqa3kqhb0hpr7p0rggoffhq46ef.apps.googleusercontent.com"

        /** Where the redirect lands, built from [REDIRECT_PORT] so the two cannot disagree. */
        const val REDIRECT_URI = "http://localhost:$REDIRECT_PORT/oauth/callback"

        private const val TAG = "GoogleDriveProvider:"

        // The name Android already writes, like the token names in GoogleTokenStore. A different name
        // here would keep the user signed in but lose the folder they picked, so the next export would
        // go silently to the root of their Drive instead of to their backups folder.
        private const val SELECTED_FOLDER = "google_drive_folder_id"
        private const val COUNT_PAGE_SIZE = 100

        /** Every export is json; Drive is asked not to return anything else. */
        private const val SETTINGS_MIME = "application/json"
    }
}

/**
 * Builds the Drive provider for a platform, given that platform's HTTP engine.
 *
 * Every platform needs the same seven pieces put together in the same order, and the only thing that
 * genuinely differs is the engine - `Darwin` on iOS, `OkHttp` on Android and the desktop. This
 * existed as a copy per platform and the copies were not equal: one of them passed
 * `{ tokenClient.validAccessToken() }` for [GoogleDriveApi]'s access token, which compiles, silently
 * drops the `forceRefresh` flag, and turns a 401 caused by a fast clock into a cleared sign in. The
 * wiring is written once here so that cannot happen a second time when Android joins.
 *
 * The caller supplies [http] and keeps ownership of it; nothing here closes it, because the provider
 * lives as long as the app does.
 */
fun googleDriveProvider(
    aapsLogger: AAPSLogger,
    store: KeyValueStore,
    listener: AuthRedirectListener,
    http: HttpClient,
    now: () -> Long
): GoogleDriveProvider {
    val tokens = GoogleTokenStore(store)
    val tokenClient = GoogleTokenClient(
        http = http,
        tokens = tokens,
        clientId = GoogleDriveProvider.CLIENT_ID,
        redirectUri = GoogleDriveProvider.REDIRECT_URI,
        now = now
    )
    return GoogleDriveProvider(
        aapsLogger = aapsLogger,
        store = store,
        listener = listener,
        auth = GoogleAuthRequest(platformCryptoPrimitives()),
        tokens = tokens,
        tokenClient = tokenClient,
        // `force` is passed on, not dropped. See the KDoc above.
        api = GoogleDriveApi(http, accessToken = { force -> tokenClient.validAccessToken(force) }),
        storageType = StorageTypes.GOOGLE_DRIVE,
        displayName = "Google Drive",
        icon = IcGoogleDrive,
        authorizedText = ImplementationStrings.google_drive_authorized,
        reAuthRequiredText = ImplementationStrings.google_drive_reauth_required,
        clientId = GoogleDriveProvider.CLIENT_ID
    )
}
