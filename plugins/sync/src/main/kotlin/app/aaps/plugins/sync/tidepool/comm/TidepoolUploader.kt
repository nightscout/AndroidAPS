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
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import app.aaps.plugins.sync.tidepool.messages.OpenDatasetRequestMessage
import app.aaps.plugins.sync.tidepool.messages.UploadReplyMessage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
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

    companion object {

        private const val INTEGRATION_BASE_URL = "https://int-api.tidepool.org"
        private const val PRODUCTION_BASE_URL = "https://api.tidepool.org"
        internal const val VERSION = "0.0.1"
        const val PUMP_TYPE = "Tandem"
    }

    private var retrofit: Retrofit? = null

    private var session: Session? = null

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
        //aapsLogger.debug(LTag.TIDEPOOL, "resetInstance")
        aapsLogger.debug(LTag.TIDEPOOL, "Instance reset")
        retrofit = null
        session = null
    }

    @Synchronized
    fun doLogin(doUpload: Boolean = false, from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "doLogin $from")
        if (!isAllowed) {
            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED)
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        if (authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED || authFlowOut.connectionStatus == AuthFlowOut.ConnectionStatus.FETCHING_TOKEN) {
            aapsLogger.debug(LTag.TIDEPOOL, "Already connected")
            return
        }
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
                val datasetCall = session.service?.getOpenDataSets(
                    session.token!!,
                    session.authReply!!.userid!!, "AAPS", 1
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
                                            if (doUpload) doUpload("startSession openDataset")
                                            else releaseWakeLock()
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
                                if (doUpload) doUpload("startSession existing dataset")
                                else releaseWakeLock()
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

    @Synchronized
    fun doUpload(from: String?) {
        //aapsLogger.debug(LTag.TIDEPOOL, "doUpload $from")
        if (!isAllowed) {
            authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.BLOCKED)
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        session.let { session ->
            if (session == null) {
                aapsLogger.error("Session is null, cannot proceed")
                releaseWakeLock()
                return
            }
            extendWakeLock(60000)
            session.iterations++
            val chunk = uploadChunk.getNext(session)
            when {
                chunk == null -> {
                    aapsLogger.error("Upload chunk is null, cannot proceed")
                    releaseWakeLock()
                }

                chunk.length == 2 -> {
                    aapsLogger.debug(LTag.TIDEPOOL, "Empty dataset - marking as succeeded")
                    rxBus.send(EventTidepoolStatus(("No data to upload")))
                    releaseWakeLock()
                    uploadNext()
                }

                else -> {
                    val body = chunk.toRequestBody("application/json".toMediaTypeOrNull())

                    rxBus.send(EventTidepoolStatus(("Uploading")))
                    if (session.service != null && session.token != null && session.datasetReply != null) {
                        val call = session.service.doUpload(session.token!!, session.datasetReply!!.getUploadId()!!, body)
                        call.enqueue(
                            TidepoolCallback<UploadReplyMessage>(
                                aapsLogger, rxBus, session, "Data Upload $from",
                                {
                                    uploadChunk.setLastEnd(session.end)
                                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.SESSION_ESTABLISHED, "Upload completed OK")
                                    releaseWakeLock()
                                    uploadNext()
                                }, {
                                    authFlowOut.updateConnectionStatus(AuthFlowOut.ConnectionStatus.FAILED, "Upload FAILED")
                                    releaseWakeLock()
                                })
                        )
                    }
                }
            }
        }
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
            doUpload("uploadNext")
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