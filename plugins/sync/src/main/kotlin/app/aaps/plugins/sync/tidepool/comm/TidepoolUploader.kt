package app.aaps.plugins.sync.tidepool.comm

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.OpenDatasetRequestMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TidepoolUploader @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val ctx: Context,
    private val preferences: Preferences,
    private val uploadChunk: UploadChunk,
    private val dateUtil: DateUtil,
    private val receiverDelegate: ReceiverDelegate,
    private val config: Config,
    private val l: L,
    private val authFlowOut: AuthFlowOut
) {

    private val isAllowed get() = receiverDelegate.allowed
    private var wl: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {

        private const val INTEGRATION_BASE_URL = "https://int-api.tidepool.org"
        private const val PRODUCTION_BASE_URL = "https://api.tidepool.org"
        internal const val VERSION = "0.0.1"
        const val PUMP_TYPE = "Tandem"
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

            val client = OkHttpClient.Builder()
                .also {
                    if (l.findByName(LTag.TIDEPOOL.tag).enabled && (config.isEngineeringMode() || config.isDev()))
                        it.addInterceptor(httpLoggingInterceptor)
                    it.addInterceptor(InfoInterceptor(aapsLogger))
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
        authFlowOut.authState.performActionWithFreshTokens(authFlowOut.authService) { accessToken, idToken, tokenException ->
            if (tokenException != null) {
                rxBus.send(EventTidepoolStatus(("Got exception token: $tokenException")))
                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, "Token exception")
                authFlowOut.doTidePoolInitialLogin("handleTokenLoginAndStartSession Token exception")
            } else if (accessToken != null) {
                authFlowOut.authState.lastTokenResponse?.let { lastResponse ->
                    val session = createSession().also {
                        it.authReply = AuthReplyMessage().apply { userid = preferences.get(TidepoolStringNonKey.SubscriptionId) }
                        it.token = accessToken
                    }
                    authFlowOut.saveAuthState()
                    startSession(session, doUpload, from)
                } ?: {
                    aapsLogger.error(LTag.TIDEPOOL, "Failing to get response / token type - trying initial login again")
                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, "Failed to get token")
                    authFlowOut.doTidePoolInitialLogin("handleTokenLoginAndStartSession lastTokenResponse == null")
                }
            } else {
                aapsLogger.error(LTag.TIDEPOOL, "Failing to use access token - trying initial login again")
                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.NOT_LOGGED_IN, "Failed to use token")
                authFlowOut.doTidePoolInitialLogin("handleTokenLoginAndStartSession accessToken == null")
            }
        }
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
                val datasetCall = session.service?.getOpenDataSets(
                    session.token!!,
                    session.authReply!!.userid!!, config.APPLICATION_ID, 1
                )
                datasetCall?.enqueue(
                    TidepoolCallback<List<DatasetReplyMessage>>(
                        aapsLogger, rxBus, session, "Get Open Datasets",
                        onSuccess = {
                            if (session.datasetReply == null) {
                                rxBus.send(EventTidepoolStatus(("Creating new dataset")))
                                val call = session.service.openDataSet(session.token!!, session.authReply!!.userid!!, OpenDatasetRequestMessage(config, dateUtil).getBody())
                                call.enqueue(
                                    TidepoolCallback<DatasetReplyMessage>(
                                        aapsLogger, rxBus, session, "Open New Dataset",
                                        {
                                            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "New dataset OK")
                                            when {
                                                // A freshly opened dataset holds no prior data, so there is nothing to delete.
                                                pendingPurge -> {
                                                    pendingPurge = false
                                                    rxBus.send(EventTidepoolStatus("No existing Tidepool data to purge"))
                                                    releaseWakeLock()
                                                }

                                                doUpload      -> scope.launch { doUpload("startSession openDataset") }
                                                else          -> releaseWakeLock()
                                            }
                                        }, {
                                            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "New dataset FAILED")
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
                            releaseWakeLock()
                        })
                )
            } else {
                aapsLogger.error("Got login response but cannot determine userId - cannot proceed")
                authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Error userId")
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
            val chunk = uploadChunk.getNext(session)
            when {
                chunk == null     -> {
                    aapsLogger.error("Upload chunk is null, cannot proceed")
                    releaseWakeLock()
                }

                chunk.length == 2 -> {
                    aapsLogger.debug(LTag.TIDEPOOL, "Empty dataset - marking as succeeded")
                    rxBus.send(EventTidepoolStatus(("No data to upload")))
                    releaseWakeLock()
                    locked = false
                    uploadMutex.unlock()
                    uploadNext()
                }

                else              -> {
                    val body = chunk.toRequestBody("application/json".toMediaTypeOrNull())

                    rxBus.send(EventTidepoolStatus(("Uploading")))
                    if (session.service != null && session.token != null && session.datasetReply != null) {
                        val call = session.service.doUpload(session.token!!, session.datasetReply!!.getUploadId()!!, body)
                        // Ownership of the lock passes to the async callback (released in both branches).
                        call.enqueue(
                            TidepoolCallback<UploadReplyMessage>(
                                aapsLogger, rxBus, session, "Data Upload $from",
                                {
                                    uploadChunk.setLastEnd(session.end)
                                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "Upload completed OK")
                                    releaseWakeLock()
                                    uploadMutex.unlock()
                                    uploadNext()
                                }, {
                                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Upload FAILED")
                                    releaseWakeLock()
                                    uploadMutex.unlock()
                                })
                        )
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
     * Delete ALL AAPS-uploaded data from Tidepool by removing the reused dataset (the one keyed by
     * `config.APPLICATION_ID`), then reset so the next upload opens a fresh empty dataset. Use it to clear
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
            rxBus.send(EventTidepoolStatus("Purge blocked by connectivity settings"))
            return
        }
        pendingPurge = true
        extendWakeLock(30000)
        if (session?.datasetReply?.getUploadId() != null) executePurge()
        else {
            rxBus.send(EventTidepoolStatus("Purge: connecting…"))
            doLogin(doUpload = false, from = "purge")
        }
    }

    private fun executePurge() {
        pendingPurge = false
        val session = this.session
        val service = session?.service
        val token = session?.token
        val uploadId = session?.datasetReply?.getUploadId()
        if (service == null || token == null || uploadId == null) {
            aapsLogger.warn(LTag.TIDEPOOL, "Purge: no open dataset to delete")
            rxBus.send(EventTidepoolStatus("Purge failed - not connected"))
            releaseWakeLock()
            return
        }
        rxBus.send(EventTidepoolStatus("Purging all Tidepool data…"))
        // A dedicated body-agnostic callback: DELETE returns 200 with an empty body, which TidepoolCallback
        // would treat as a failure (it requires a non-null parsed body).
        service.deleteDataSet(token, uploadId).enqueue(object : Callback<DatasetReplyMessage> {
            override fun onResponse(call: Call<DatasetReplyMessage>, response: Response<DatasetReplyMessage>) {
                if (response.isSuccessful) {
                    aapsLogger.debug(LTag.TIDEPOOL, "Purged Tidepool dataset $uploadId")
                    rxBus.send(EventTidepoolStatus("All Tidepool data purged"))
                    resetInstance() // drop the deleted dataset; the next upload opens a fresh one
                } else {
                    val msg = "Purge FAILED: ${response.code()} ${response.message()}"
                    aapsLogger.error(LTag.TIDEPOOL, msg)
                    rxBus.send(EventTidepoolStatus(msg))
                }
                releaseWakeLock()
            }

            override fun onFailure(call: Call<DatasetReplyMessage>, t: Throwable) {
                aapsLogger.error(LTag.TIDEPOOL, "Purge failed: $t")
                rxBus.send(EventTidepoolStatus("Purge FAILED: $t"))
                releaseWakeLock()
            }
        })
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