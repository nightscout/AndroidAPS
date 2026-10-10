package app.aaps.plugins.sync.tidepool.comm

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader.Companion.RETRY_LOGIN_INTERVAL_SECONDS
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.OpenDatasetRequestMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import app.aaps.plugins.sync.tidepool.utils.RateLimit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@SingleIn(AppScope::class)
@Inject
class TidepoolUploader(
    private val aapsLogger: AAPSLogger,
    private val tidepoolRepository: TidepoolRepository,
    private val ctx: Context,
    private val preferences: Preferences,
    private val uploadChunk: UploadChunk,
    private val dateUtil: DateUtil,
    private val receiverDelegate: TidepoolReceiverDelegate,
    private val config: Config,
    private val l: L,
    private val authFlowOut: AuthFlowOut,
    private val rateLimit: RateLimit
) {

    private val isAllowed get() = receiverDelegate.allowed
    private var wl: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {

        private const val INTEGRATION_BASE_URL = "https://int-api.tidepool.org"
        private const val PRODUCTION_BASE_URL = "https://api.tidepool.org"

        /**
         * How AAPS names itself to Tidepool: dataset `deviceManufacturers`, `deviceModel` and `deviceId`,
         * and the prefix of the pump settings `deviceId`. Tidepool shows the device and picks the pump
         * settings layout by this exact string, so NEVER change it.
         * Before 4.0 this was "Tandem". Those old datasets have no `deviceId`, so [startSession] does not
         * find them any more and opens a new dataset. The old data stays in Tidepool under the old name.
         */
        const val DEVICE_NAME = "AAPS"

        // Datasets per request when purge lists them. Old versions opened a new dataset on every
        // session, so a user can have many more than this; purge asks again until all are gone.
        internal const val PURGE_PAGE_SIZE = 100

        // Purge writes a progress line to the Tidepool log after this many deleted datasets
        internal const val PURGE_PROGRESS_STEP = 100

        // Shortest time between two automatic openings of the login page
        internal val RETRY_LOGIN_INTERVAL_SECONDS = T.mins(10).secs().toInt()
    }

    private var retrofit: Retrofit? = null

    private var session: Session? = null

    // Single-flight guard: getLastEnd()..setLastEnd(session.end) is a non-atomic read-modify-write split
    // across the async upload callback, and doUpload() is fanned in concurrently from several triggers.
    // Only one chunk may be in flight so concurrent triggers don't read the same LastEnd and upload
    // overlapping windows. Held from getNext() until the upload's success/failure callback.
    private val uploadMutex = Mutex()

    // Set when a purge was requested but no dataset was open yet; consumed once the session/dataset is ready.
    @Volatile private var pendingPurge = false

    private fun getRetrofitInstance(): Retrofit? {
        if (retrofit == null) {

            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            // The session token gives full access to the user's Tidepool data, keep it out of the log (#5206)
            httpLoggingInterceptor.redactHeader(SESSION_TOKEN_HEADER)

            val client = OkHttpClient.Builder()
                .also {
                    // First, so the body log below shows the headers as they are sent
                    it.addInterceptor(ClientHeadersInterceptor(config))
                    if (l.findByName(LTag.TIDEPOOL.tag).enabled && (config.isEngineeringMode() || config.isDev()))
                        it.addInterceptor(httpLoggingInterceptor)
                    it.addInterceptor(InfoInterceptor(aapsLogger))
                    it.addInterceptor(DeleteTimeoutInterceptor())
                }.build()

            retrofit = Retrofit.Builder()
                .baseUrl(if (preferences.get(TidepoolBooleanKey.UseTestServers)) INTEGRATION_BASE_URL else PRODUCTION_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }

    fun createSession(): Session {
        //aapsLogger.debug(LTag.TIDEPOOL, "createSession")
        val service = getRetrofitInstance()?.create(TidepoolApiService::class.java)
        return Session(SESSION_TOKEN_HEADER, service)
    }

    fun resetInstance() {
        aapsLogger.debug(LTag.TIDEPOOL, "Instance reset")
        retrofit = null
        session = null
        // Reset connection status so the next doUpload() triggers a fresh login
        // instead of trying to use the now-null session
        authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NONE)
    }

    /**
     * IMPROVED: Simplified login without connectivity checks
     *
     * Connectivity is now checked by TidepoolPlugin.doUpload() BEFORE calling this method.
     * This separation of concerns makes the code clearer and prevents state machine deadlock.
     *
     * Old behavior:
     *   - Checked connectivity here and set BLOCKED state
     *   - Auth state could get stuck in BLOCKED
     *
     * New behavior:
     *   - Assumes caller already checked connectivity
     *   - Only handles authentication state transitions
     *   - No BLOCKED state to get stuck in
     */
    @Synchronized
    fun doLogin(doUpload: Boolean = false, from: String?) {
        aapsLogger.debug(LTag.TIDEPOOL, "doLogin from=$from doUpload=$doUpload currentStatus=${authFlowOut.connectionStatus}")

        // IMPROVEMENT: Removed connectivity check - caller's responsibility
        // This prevents mixing connectivity constraints with auth state
        //
        // REMOVED CODE:
        // if (!isAllowed) {
        //     authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED)
        //     aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
        //     return
        // }

        // Check if already in a connected or connecting state
        if (authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED ||
            authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.FETCHING_TOKEN
        ) {
            aapsLogger.debug(LTag.TIDEPOOL, "Already connected or connecting")
            return
        }

        // Proceed with authentication
        handleTokenLoginAndStartSession(doUpload, from)
    }

    fun handleTokenLoginAndStartSession(doUpload: Boolean, from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "handleTokenLoginAndStartSession")
        authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FETCHING_TOKEN, "Connecting")
        authFlowOut.authState.performActionWithFreshTokens(authFlowOut.authService) { accessToken, _, tokenException ->
            val lastTokenResponse = authFlowOut.authState.lastTokenResponse
            when {
                // Only the network or the server failed. Keep the saved credentials and try the silent
                // refresh again on the next upload instead of opening the login page in the browser.
                accessToken == null && AuthFlowOut.isTransientTokenError(tokenException) -> {
                    aapsLogger.debug(LTag.TIDEPOOL, "Token refresh failed for now, keeping login: $tokenException")
                    // NO_SESSION makes the next doUpload() call doLogin() again.
                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NO_SESSION, "Network problem, will try again later")
                    cancelPendingPurge()
                }

                accessToken == null                                                      -> {
                    aapsLogger.error(LTag.TIDEPOOL, "Failing to use access token - trying initial login again: $tokenException")
                    tidepoolRepository.addLog("Got exception token: $tokenException")
                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, "Failed to use token")
                    cancelPendingPurge()
                    retryInitialLogin("handleTokenLoginAndStartSession accessToken == null")
                }

                lastTokenResponse == null                                                -> {
                    aapsLogger.error(LTag.TIDEPOOL, "Failing to get response / token type - trying initial login again")
                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, "Failed to get token")
                    cancelPendingPurge()
                    retryInitialLogin("handleTokenLoginAndStartSession lastTokenResponse == null")
                }

                else                                                                     -> {
                    val session = createSession().also {
                        it.authReply = AuthReplyMessage().apply { userid = preferences.get(TidepoolStringNonKey.SubscriptionId) }
                        it.token = accessToken
                    }
                    authFlowOut.saveAuthState()
                    startSession(session, doUpload, from)
                }
            }
        }
    }

    /**
     * Open the login page in the browser, but not more often than once per [RETRY_LOGIN_INTERVAL_SECONDS].
     * Uploads are triggered from many places, so without this limit a broken login could open the browser
     * again and again. The Login button in the plugin screen calls [AuthFlowOut.doTidePoolInitialLogin]
     * directly and is never limited.
     */
    private fun retryInitialLogin(from: String) {
        if (rateLimit.rateLimit("tidepool-retry-login", RETRY_LOGIN_INTERVAL_SECONDS))
            authFlowOut.doTidePoolInitialLogin(from)
        else
            aapsLogger.debug(LTag.TIDEPOOL, "Not opening login page again so soon: $from")
    }

    fun startSession(newSession: Session, doUpload: Boolean = false, @Suppress("unused") from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "startSession $from")
        extendWakeLock(30000)
        session = newSession
        session?.let { session ->
            if (session.authReply?.userid != null) {
                // See if we already have an open data set to write to
                // Must match the client.name the dataset is CREATED with (OpenDatasetRequestMessage -> ClientInfo(config.APPLICATION_ID)).
                // A hardcoded "AAPS" here never matched config.APPLICATION_ID ("info.nightscout.androidaps"), so the existing open
                // dataset was never found and a new dataset (new uploadId) was opened on every session. Because the Tidepool
                // dataset.delete.origin deduplicator is scoped to a single uploadId, prior syncs' data could never be replaced,
                // so every full sync duplicated all data. Reusing one dataset lets the per-uploadId origin dedup collapse re-uploads.
                // The deviceId filter skips datasets from before 4.0 (named "Tandem", no deviceId), see DEVICE_NAME.
                val datasetCall = session.service?.getDataSets(
                    session.token!!,
                    session.authReply!!.userid!!, config.APPLICATION_ID, DEVICE_NAME, 1
                )
                datasetCall?.enqueue(
                    TidepoolCallback<List<DatasetReplyMessage>>(
                        aapsLogger, tidepoolRepository, session, "Get Open Datasets",
                        onSuccess = {
                            if (session.datasetReply == null) {
                                tidepoolRepository.addLog("Creating new dataset")
                                val call = session.service.openDataSet(session.token!!, session.authReply!!.userid!!, OpenDatasetRequestMessage(config, dateUtil).getBody())
                                call.enqueue(
                                    TidepoolCallback<DatasetReplyMessage>(
                                        aapsLogger, tidepoolRepository, session, "Open New Dataset",
                                        {
                                            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "New dataset OK")
                                            when {
                                                // The new dataset is empty, but older datasets (e.g. from before 4.0) can still hold data.
                                                pendingPurge -> executePurge()
                                                doUpload     -> scope.launch { doUpload("startSession openDataset") }
                                                else         -> releaseWakeLock()
                                            }
                                        }, {
                                            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "New dataset FAILED")
                                            cancelPendingPurge()
                                            releaseWakeLock()
                                        })
                                )
                            } else {
                                aapsLogger.debug(LTag.TIDEPOOL, "Existing Dataset: " + session.datasetReply!!.getUploadId())
                                // TODO: Wouldn't need to do this if we could block on the above `call.enqueue`.
                                // ie, do the openDataSet conditionally, and then do `doUpload` either way.
                                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "Appending to existing dataset")
                                when {
                                    pendingPurge -> executePurge()
                                    doUpload     -> scope.launch { doUpload("startSession existing dataset") }
                                    else         -> releaseWakeLock()
                                }
                            }
                        }, onFail = {
                            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Open dataset FAILED")
                            cancelPendingPurge()
                            releaseWakeLock()
                        })
                )
            } else {
                aapsLogger.error("Got login response but cannot determine userId - cannot proceed")
                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Error userId")
                cancelPendingPurge()
                releaseWakeLock()
            }
        }
    }

    suspend fun doUpload(from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from")
        if (!isAllowed) {
            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED)
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        val session = this.session
        if (session == null) {
            aapsLogger.warn(LTag.TIDEPOOL, "Session is null, triggering re-login")
            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NONE)
            doLogin(doUpload = true, from = "doUpload session recovery")
            return
        }
        // Skip if a chunk is already in flight; concurrent triggers must not read the same LastEnd (see uploadMutex).
        if (!uploadMutex.tryLock()) {
            aapsLogger.debug(LTag.TIDEPOOL, "Upload already in progress, skipping trigger from $from")
            return
        }
        var locked = true
        try {
            extendWakeLock(60000)
            session.iterations++
            val batches = uploadChunk.getNext(session)
            when {
                batches == null   -> {
                    aapsLogger.error("Upload chunk is null, cannot proceed")
                    releaseWakeLock()
                }

                batches.isEmpty() -> {
                    aapsLogger.debug(LTag.TIDEPOOL, "Empty dataset - marking as succeeded")
                    tidepoolRepository.addLog("No data to upload")
                    releaseWakeLock()
                    locked = false
                    uploadMutex.unlock()
                    uploadNext()
                }

                else              -> {
                    tidepoolRepository.addLog("Uploading")
                    val service = session.service
                    val uploadId = session.datasetReply?.getUploadId()
                    if (service != null && uploadId != null) {
                        // Ownership of the lock passes to uploadBatch, which releases it in every branch.
                        uploadBatch(session, service, uploadId, batches, 0, from)
                        locked = false
                    }
                }
            }
        } finally {
            // Release for every synchronous exit path (null/empty chunk, missing service, or a getNext throw).
            if (locked) uploadMutex.unlock()
        }
    }

    /**
     * Send [batches] one after another, starting with [index]. `LastEnd` moves only after the last one, so
     * when one fails the whole time window is sent again later; the deduplicator then replaces the records
     * that arrived already, by their origin id, instead of adding them a second time.
     *
     * Owns the upload lock: every branch either releases it or passes it on to the next batch.
     */
    private fun uploadBatch(session: Session, service: TidepoolApiService, uploadId: String, batches: List<String>, index: Int, from: String?) {
        if (batches.size > 1) tidepoolRepository.addLog("Uploading part ${index + 1} of ${batches.size}")
        extendWakeLock(60000)
        withFreshToken(
            session,
            // FAILED makes the next upload log in again, which tells a network problem from a lost login
            onFailure = { uploadFailed("Upload FAILED - no valid token") }
        ) { token ->
            try {
                service.doUpload(token, uploadId, batches[index].toRequestBody("application/json".toMediaTypeOrNull())).enqueue(
                    TidepoolCallback<UploadReplyMessage>(
                        aapsLogger, tidepoolRepository, session, "Data Upload $from",
                        {
                            if (index + 1 < batches.size) uploadBatch(session, service, uploadId, batches, index + 1, from)
                            else {
                                uploadChunk.setLastEnd(session.end)
                                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "Upload completed OK")
                                releaseWakeLock()
                                uploadMutex.unlock()
                                uploadNext()
                            }
                        }, { uploadFailed("Upload FAILED") })
                )
            } catch (e: Exception) {
                aapsLogger.error(LTag.TIDEPOOL, "Upload could not start", e)
                uploadFailed("Upload FAILED")
            }
        }
    }

    private fun uploadFailed(message: String) {
        authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, message)
        releaseWakeLock()
        uploadMutex.unlock()
    }

    /**
     * Delete ALL AAPS-uploaded data from Tidepool by removing every dataset with our `client.name`
     * (`config.APPLICATION_ID`): the one in use and older ones, e.g. from before 4.0 ("Tandem") or from
     * versions that opened a new dataset on every session. Then reset so the next upload opens a fresh
     * empty dataset. The old datasets matter: our records have fixed origin ids and the deduplicator works
     * only inside one dataset, so a "Full sync" next to an old dataset would show everything twice. Use it to clear
     * data corrupted by an earlier generator (e.g. the resync basal-overlap bug), which a plain re-sync
     * cannot repair because it never re-emits the old records' start-times. Existing forward progress
     * (the `LastEnd` watermark) is left untouched, so "Full sync" is still the way to re-upload history.
     *
     * Logs in / opens a session first if needed and defers via [pendingPurge].
     *
     * NOTE: [TidepoolApiService.deleteDataSet] (DELETE /v1/datasets/{id}) is verified against the Tidepool
     * API spec (200 + empty body) but not runtime-tested; [executePurge] treats any 2xx as success.
     */
    fun purge() {
        if (!isAllowed) {
            tidepoolRepository.addLog("Purge blocked by connectivity settings")
            return
        }
        pendingPurge = true
        extendWakeLock(30000)
        if (session?.datasetReply?.getUploadId() != null) executePurge()
        else {
            tidepoolRepository.addLog("Purge: connecting…")
            doLogin(doUpload = false, from = "purge")
        }
    }

    /**
     * Drop a purge that waits for an open dataset ([pendingPurge]) because connecting failed. Without this
     * the request would stay armed and a later upload could open a session hours afterwards and delete all
     * data at a moment the user is not looking. The user can start the purge again when the connection works.
     */
    private fun cancelPendingPurge() {
        if (pendingPurge) {
            pendingPurge = false
            aapsLogger.warn(LTag.TIDEPOOL, "Purge dropped, could not connect")
            tidepoolRepository.addLog("Purge failed - not connected")
            releaseWakeLock()
        }
    }

    private fun executePurge() {
        pendingPurge = false
        val session = this.session
        val service = session?.service
        val userId = session?.authReply?.userid
        val currentId = session?.datasetReply?.getUploadId()
        if (session == null || service == null || userId == null || currentId == null) {
            aapsLogger.warn(LTag.TIDEPOOL, "Purge: no open dataset to delete")
            tidepoolRepository.addLog("Purge failed - not connected")
            releaseWakeLock()
            return
        }
        tidepoolRepository.addLog("Purging all Tidepool data…")
        purgeNextPage(session, service, userId, currentId, mutableSetOf())
    }

    /**
     * Run [action] with an access token that is still valid, or [onFailure] when there is none.
     *
     * A session lives much longer than its access token (about 10 minutes), so the token from the session
     * start is not enough: with it, the first upload after the token expired failed with 401, and a long
     * purge stopped half way. AppAuth refreshes only when the token is about to expire, so this costs
     * nothing most of the time. The new token goes into [session] as well.
     *
     * [action] or [onFailure] runs either right away on this thread, or later on the thread of the refresh.
     */
    private fun withFreshToken(session: Session, onFailure: () -> Unit, action: (String) -> Unit) {
        authFlowOut.authState.performActionWithFreshTokens(authFlowOut.authService) { accessToken, _, tokenException ->
            if (accessToken == null) {
                aapsLogger.warn(LTag.TIDEPOOL, "No valid token: $tokenException")
                onFailure()
            } else {
                session.token = accessToken
                authFlowOut.saveAuthState() // a refresh can also bring a new refresh token
                action(accessToken)
            }
        }
    }

    private fun purgeWithoutToken() {
        tidepoolRepository.addLog("Purge failed - not connected")
        releaseWakeLock()
    }

    /**
     * List our datasets, delete all of them except the one in use, then list again. Deleted datasets are
     * not listed any more, so this ends when only the dataset in use is left. That one is deleted last:
     * if a delete fails before, the session still has a working dataset to upload to.
     * [deleted] guards against a loop if the server still lists a dataset right after deleting it.
     */
    private fun purgeNextPage(session: Session, service: TidepoolApiService, userId: String, currentId: String, deleted: MutableSet<String>) {
        withFreshToken(session, ::purgeWithoutToken) { token -> listAndDelete(session, service, token, userId, currentId, deleted) }
    }

    private fun listAndDelete(session: Session, service: TidepoolApiService, token: String, userId: String, currentId: String, deleted: MutableSet<String>) {
        service.getDataSets(token, userId, config.APPLICATION_ID, null, PURGE_PAGE_SIZE).enqueue(object : Callback<List<DatasetReplyMessage>> {
            override fun onResponse(call: Call<List<DatasetReplyMessage>>, response: Response<List<DatasetReplyMessage>>) {
                val dataSets = response.body()
                if (!response.isSuccessful || dataSets == null) {
                    purgeFailed("${response.code()} ${response.message()}")
                    return
                }
                val others = dataSets.mapNotNull { it.getUploadId() }.filter { it != currentId && it !in deleted }
                if (others.isEmpty())
                    deleteDataSets(session, service, listOf(currentId), deleted) {
                        tidepoolRepository.addLog("All Tidepool data purged (deleted datasets: ${deleted.size})")
                        resetInstance() // drop the deleted dataset; the next upload opens a fresh one
                        releaseWakeLock()
                    }
                else
                    deleteDataSets(session, service, others, deleted) { purgeNextPage(session, service, userId, currentId, deleted) }
            }

            override fun onFailure(call: Call<List<DatasetReplyMessage>>, t: Throwable) = purgeFailed(t.toString())
        })
    }

    /** Delete [ids] one after another, then run [onDone]. Stops at the first failure. */
    private fun deleteDataSets(session: Session, service: TidepoolApiService, ids: List<String>, deleted: MutableSet<String>, onDone: () -> Unit) {
        val id = ids.firstOrNull() ?: return onDone()
        // A big dataset can take up to the DELETE read timeout, keep the phone awake that long
        extendWakeLock(T.mins(DeleteTimeoutInterceptor.DELETE_READ_TIMEOUT_MINUTES + 1L).msecs())
        withFreshToken(session, ::purgeWithoutToken) { token ->
            // A dedicated body-agnostic callback: DELETE returns 200 with an empty body, which TidepoolCallback
            // would treat as a failure (it requires a non-null parsed body).
            service.deleteDataSet(token, id).enqueue(object : Callback<DatasetReplyMessage> {
                override fun onResponse(call: Call<DatasetReplyMessage>, response: Response<DatasetReplyMessage>) {
                    if (response.isSuccessful) {
                        aapsLogger.debug(LTag.TIDEPOOL, "Purged Tidepool dataset $id")
                        deleted.add(id)
                        // An account from old versions can hold thousands of datasets, and purge then runs for
                        // many minutes. Show that it moves.
                        if (deleted.size % PURGE_PROGRESS_STEP == 0) tidepoolRepository.addLog("Purge: ${deleted.size} datasets deleted")
                        deleteDataSets(session, service, ids.drop(1), deleted, onDone)
                    } else purgeFailed("${response.code()} ${response.message()}")
                }

                override fun onFailure(call: Call<DatasetReplyMessage>, t: Throwable) = purgeFailed(t.toString())
            })
        }
    }

    private fun purgeFailed(reason: String) {
        aapsLogger.error(LTag.TIDEPOOL, "Purge FAILED: $reason")
        tidepoolRepository.addLog("Purge FAILED: $reason")
        releaseWakeLock()
    }

    private fun uploadNext() {
        //aapsLogger.debug(LTag.TIDEPOOL, "uploadNext")
        if (!isAllowed) {
            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED)
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        if (uploadChunk.getLastEnd() < dateUtil.now() - T.hours(3).msecs() - T.mins(1).msecs()) {
            SystemClock.sleep(3000)
            aapsLogger.debug(LTag.TIDEPOOL, "Restarting doUpload. Last: " + dateUtil.dateAndTimeString(uploadChunk.getLastEnd()))
            scope.launch { doUpload("uploadNext") }
        }
    }

    @Synchronized
    private fun extendWakeLock(ms: Long) {
        if (wl == null) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidAPS:TidepoolUploader")
            wl?.acquire(ms)
        } else {
            releaseWakeLock() // lets not get too messy
            wl?.acquire(ms)
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        wl?.let {
            if (it.isHeld)
                try {
                    it.release()
                } catch (e: Exception) {
                    aapsLogger.error("Error releasing wakelock: $e")
                }
        }
    }

}