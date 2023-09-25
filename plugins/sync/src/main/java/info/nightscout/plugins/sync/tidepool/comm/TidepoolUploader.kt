package info.nightscout.plugins.sync.tidepool.comm

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.ui.dialogs.OKDialog
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.ReceiverDelegate
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolStatus
import info.nightscout.plugins.sync.tidepool.messages.AuthReplyMessage
import info.nightscout.plugins.sync.tidepool.messages.AuthRequestMessage
import info.nightscout.plugins.sync.tidepool.messages.DatasetReplyMessage
import info.nightscout.plugins.sync.tidepool.messages.OpenDatasetRequestMessage
import info.nightscout.plugins.sync.tidepool.messages.UploadReplyMessage
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
    private val rh: ResourceHelper,
    private val sp: SP,
    private val uploadChunk: UploadChunk,
    private val dateUtil: DateUtil,
    private val receiverDelegate: ReceiverDelegate,
    private val config: Config
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

    enum class ConnectionStatus {
        BLOCKED, DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }

    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    private fun getRetrofitInstance(): Retrofit? {
        if (retrofit == null) {

            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

            val client = OkHttpClient.Builder()
                .addInterceptor(httpLoggingInterceptor)
                .addInterceptor(InfoInterceptor(aapsLogger))
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(if (sp.getBoolean(R.string.key_tidepool_dev_servers, false)) INTEGRATION_BASE_URL else PRODUCTION_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit
    }

    private fun createSession(): Session {
        val service = getRetrofitInstance()?.create(TidepoolApiService::class.java)
        return Session(AuthRequestMessage.getAuthRequestHeader(sp), SESSION_TOKEN_HEADER, service)
    }

    fun resetInstance() {
        retrofit = null
        aapsLogger.debug(LTag.TIDEPOOL, "Instance reset")
        connectionStatus = ConnectionStatus.DISCONNECTED
    }

    @Synchronized
    fun doLogin(doUpload: Boolean = false) {
        if (!isAllowed) {
            connectionStatus = ConnectionStatus.BLOCKED
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING) {
            aapsLogger.debug(LTag.TIDEPOOL, "Already connected")
            return
        }
        // TODO failure backoff
        extendWakeLock(30000)
        session = createSession()
        val authHeader = session?.authHeader
        if (authHeader != null) {
            connectionStatus = ConnectionStatus.CONNECTING
            rxBus.send(EventTidepoolStatus(("Connecting")))
            val call = session?.service?.getLogin(authHeader)

            call?.enqueue(TidepoolCallback<AuthReplyMessage>(
                aapsLogger, rxBus, session!!, "Login",
                {
                    startSession(session!!, doUpload)
                }, {
                    connectionStatus = ConnectionStatus.FAILED
                    releaseWakeLock()
                })
            )
            return
        } else {
            aapsLogger.debug(LTag.TIDEPOOL, "Cannot do login as user credentials have not been set correctly")
            connectionStatus = ConnectionStatus.FAILED
            rxBus.send(EventTidepoolStatus(("Invalid credentials")))
            releaseWakeLock()
            return
        }
    }

    fun testLogin(rootContext: Context) {
        val session = createSession()
        session.authHeader?.let {
            val call = session.service?.getLogin(it)

            call?.enqueue(TidepoolCallback<AuthReplyMessage>(
                aapsLogger, rxBus, session, "Login",
                {
                    OKDialog.show(rootContext, rh.gs(R.string.tidepool), "Successfully logged into Tidepool.")
                }, {
                    OKDialog.show(rootContext, rh.gs(R.string.tidepool), "Failed to log into Tidepool.\nCheck that your user name and password are correct.")
                })
            )
        }
            ?: OKDialog.show(rootContext, rh.gs(R.string.tidepool), "Cannot do login as user credentials have not been set correctly")

    }

    private fun startSession(session: Session, doUpload: Boolean = false) {
        extendWakeLock(30000)
        if (session.authReply?.userid != null) {
            // See if we already have an open data set to write to
            val datasetCall = session.service!!.getOpenDataSets(
                session.token!!,
                session.authReply!!.userid!!, "AAPS", 1
            )

            datasetCall.enqueue(
                TidepoolCallback<List<DatasetReplyMessage>>(
                    aapsLogger, rxBus, session, "Get Open Datasets",
                    {
                        if (session.datasetReply == null) {
                            rxBus.send(EventTidepoolStatus(("Creating new dataset")))
                            val call = session.service.openDataSet(session.token!!, session.authReply!!.userid!!, OpenDatasetRequestMessage(config, dateUtil).getBody())
                            call.enqueue(TidepoolCallback<DatasetReplyMessage>(
                                aapsLogger, rxBus, session, "Open New Dataset",
                                {
                                    connectionStatus = ConnectionStatus.CONNECTED
                                    rxBus.send(EventTidepoolStatus(("New dataset OK")))
                                    if (doUpload) doUpload()
                                    else releaseWakeLock()
                                }, {
                                    rxBus.send(EventTidepoolStatus(("New dataset FAILED")))
                                    connectionStatus = ConnectionStatus.FAILED
                                    releaseWakeLock()
                                })
                            )
                        } else {
                            aapsLogger.debug(LTag.TIDEPOOL, "Existing Dataset: " + session.datasetReply!!.getUploadId())
                            // TODO: Wouldn't need to do this if we could block on the above `call.enqueue`.
                            // ie, do the openDataSet conditionally, and then do `doUpload` either way.
                            connectionStatus = ConnectionStatus.CONNECTED
                            rxBus.send(EventTidepoolStatus(("Appending to existing dataset")))
                            if (doUpload) doUpload()
                            else releaseWakeLock()
                        }
                    }, {
                        connectionStatus = ConnectionStatus.FAILED
                        rxBus.send(EventTidepoolStatus(("Open dataset FAILED")))
                        releaseWakeLock()
                    })
            )
        } else {
            aapsLogger.error("Got login response but cannot determine userId - cannot proceed")
            connectionStatus = ConnectionStatus.FAILED
            rxBus.send(EventTidepoolStatus(("Error userId")))
            releaseWakeLock()
        }
    }

    @Synchronized
    fun doUpload() {
        if (!isAllowed) {
            connectionStatus = ConnectionStatus.BLOCKED
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
                        call.enqueue(TidepoolCallback<UploadReplyMessage>(
                            aapsLogger, rxBus, session, "Data Upload",
                            {
                                uploadChunk.setLastEnd(session.end)
                                connectionStatus = ConnectionStatus.CONNECTED
                                rxBus.send(EventTidepoolStatus(("Upload completed OK")))
                                releaseWakeLock()
                                uploadNext()
                            }, {
                                connectionStatus = ConnectionStatus.DISCONNECTED
                                rxBus.send(EventTidepoolStatus(("Upload FAILED")))
                                releaseWakeLock()
                            })
                        )
                    }
                }
            }
        }
    }

    private fun uploadNext() {
        if (!isAllowed) {
            connectionStatus = ConnectionStatus.BLOCKED
            aapsLogger.debug(LTag.TIDEPOOL, "Blocked by connectivity settings")
            return
        }
        if (uploadChunk.getLastEnd() < dateUtil.now() - T.hours(3).msecs() - T.mins(1).msecs()) {
            SystemClock.sleep(3000)
            aapsLogger.debug(LTag.TIDEPOOL, "Restarting doUpload. Last: " + dateUtil.dateAndTimeString(uploadChunk.getLastEnd()))
            doUpload()
        }
    }

    fun deleteDataSet() {
        if (session?.datasetReply?.id != null) {
            extendWakeLock(60000)
            val call = session!!.service?.deleteDataSet(session!!.token!!, session!!.datasetReply!!.id!!)
            call?.enqueue(TidepoolCallback(
                aapsLogger, rxBus, session!!, "Delete Dataset",
                {
                    connectionStatus = ConnectionStatus.DISCONNECTED
                    rxBus.send(EventTidepoolStatus(("Dataset removed OK")))
                    releaseWakeLock()
                }, {
                    connectionStatus = ConnectionStatus.DISCONNECTED
                    rxBus.send(EventTidepoolStatus(("Dataset remove FAILED")))
                    releaseWakeLock()
                })
            )
        } else {
            aapsLogger.error("Got login response but cannot determine datasetId - cannot proceed")
        }
    }

    @Suppress("unused")
    fun deleteAllData() {
        val session = this.session
        val token = session?.token
        val userId = session?.authReply?.userid
        try {
            requireNotNull(session)
            requireNotNull(token)
            requireNotNull(userId)
            extendWakeLock(60000)
            val call = session.service?.deleteAllData(token, userId)
            call?.enqueue(
                TidepoolCallback(
                    aapsLogger, rxBus, session, "Delete all data",
                    {
                        connectionStatus = ConnectionStatus.DISCONNECTED
                        rxBus.send(EventTidepoolStatus(("All data removed OK")))
                        releaseWakeLock()
                    }, {
                        connectionStatus = ConnectionStatus.DISCONNECTED
                        rxBus.send(EventTidepoolStatus(("All data remove FAILED")))
                        releaseWakeLock()
                    })
            )
        } catch (e: IllegalArgumentException) {
            aapsLogger.error("Got login response but cannot determine userId - cannot proceed")
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