package info.nightscout.androidaps.plugins.general.tidepool.comm

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import info.nightscout.androidaps.plugins.general.tidepool.messages.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.T
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TidepoolUploader {

    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    private var wl: PowerManager.WakeLock? = null

    private const val INTEGRATION_BASE_URL = "https://int-api.tidepool.org"
    private const val PRODUCTION_BASE_URL = "https://api.tidepool.org"

    internal const val VERSION = "0.0.1"

    private var retrofit: Retrofit? = null

    private var session: Session? = null

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }

    val PUMPTYPE = "Tandem"

    var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    fun getRetrofitInstance(): Retrofit? {
        if (retrofit == null) {

            val httpLoggingInterceptor = HttpLoggingInterceptor()
            httpLoggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

            val client = OkHttpClient.Builder()
                    .addInterceptor(httpLoggingInterceptor)
                    .addInterceptor(InfoInterceptor(TidepoolUploader::class.java.name))
                    .build()

            retrofit = Retrofit.Builder()
                    .baseUrl(if (SP.getBoolean(R.string.key_tidepool_dev_servers, false)) INTEGRATION_BASE_URL else PRODUCTION_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
        }
        return retrofit
    }

    fun createSession(): Session {
        val service = getRetrofitInstance()?.create(TidepoolApiService::class.java)
        return Session(AuthRequestMessage.getAuthRequestHeader(), SESSION_TOKEN_HEADER, service)
    }

    // TODO: call on preference change
    fun resetInstance() {
        retrofit = null
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("Instance reset")
        connectionStatus = ConnectionStatus.DISCONNECTED
    }

    @Synchronized
    fun doLogin(doUpload: Boolean = false) {
        if (connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED || connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTING) {
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("Already connected")
            return
        }
        // TODO failure backoff
        extendWakeLock(30000)
        session = createSession()
        val authHeader = session?.authHeader
        if (authHeader != null) {
            connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTING
            RxBus.send(EventTidepoolStatus(("Connecting")))
            val call = session?.service?.getLogin(authHeader)

            call?.enqueue(TidepoolCallback<AuthReplyMessage>(session!!, "Login", {
                startSession(session!!, doUpload)
            }, {
                connectionStatus = TidepoolUploader.ConnectionStatus.FAILED
                releaseWakeLock()
            }))
            return
        } else {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Cannot do login as user credentials have not been set correctly")
            connectionStatus = TidepoolUploader.ConnectionStatus.FAILED
            RxBus.send(EventTidepoolStatus(("Invalid credentials")))
            releaseWakeLock()
            return
        }
    }

    fun testLogin(rootContext: Context) {
        val session = createSession()
        session.authHeader?.let {
            val call = session.service?.getLogin(it)

            call?.enqueue(TidepoolCallback<AuthReplyMessage>(session, "Login", {
                OKDialog.show(rootContext, MainApp.gs(R.string.tidepool), "Successfully logged into Tidepool.")
            }, {
                OKDialog.show(rootContext, MainApp.gs(R.string.tidepool), "Failed to log into Tidepool.\nCheck that your user name and password are correct.")
            }))

        }
                ?: OKDialog.show(rootContext, MainApp.gs(R.string.tidepool), "Cannot do login as user credentials have not been set correctly")

    }

    private fun startSession(session: Session, doUpload: Boolean = false) {
        extendWakeLock(30000)
        if (session.authReply?.userid != null) {
            // See if we already have an open data set to write to
            val datasetCall = session.service!!.getOpenDataSets(session.token!!,
                    session.authReply!!.userid!!, BuildConfig.APPLICATION_ID, 1)

            datasetCall.enqueue(TidepoolCallback<List<DatasetReplyMessage>>(session, "Get Open Datasets", {
                if (session.datasetReply == null) {
                    RxBus.send(EventTidepoolStatus(("Creating new dataset")))
                    val call = session.service.openDataSet(session.token!!, session.authReply!!.userid!!, OpenDatasetRequestMessage().getBody())
                    call.enqueue(TidepoolCallback<DatasetReplyMessage>(session, "Open New Dataset", {
                        connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTED
                        RxBus.send(EventTidepoolStatus(("New dataset OK")))
                        if (doUpload) doUpload()
                        else
                            releaseWakeLock()
                    }, {
                        RxBus.send(EventTidepoolStatus(("New dataset FAILED")))
                        connectionStatus = TidepoolUploader.ConnectionStatus.FAILED
                        releaseWakeLock()
                    }))
                } else {
                    if (L.isEnabled(L.TIDEPOOL))
                        log.debug("Existing Dataset: " + session.datasetReply!!.getUploadId())
                    // TODO: Wouldn't need to do this if we could block on the above `call.enqueue`.
                    // ie, do the openDataSet conditionally, and then do `doUpload` either way.
                    connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTED
                    RxBus.send(EventTidepoolStatus(("Appending to existing dataset")))
                    if (doUpload) doUpload()
                    else
                        releaseWakeLock()
                }
            }, {
                connectionStatus = TidepoolUploader.ConnectionStatus.FAILED
                RxBus.send(EventTidepoolStatus(("Open dataset FAILED")))
                releaseWakeLock()
            }))
        } else {
            log.error("Got login response but cannot determine userId - cannot proceed")
            connectionStatus = TidepoolUploader.ConnectionStatus.FAILED
            RxBus.send(EventTidepoolStatus(("Error userId")))
            releaseWakeLock()
        }
    }

    @Synchronized
    fun doUpload() {
        session.let { session ->
            if (session == null) {
                log.error("Session is null, cannot proceed")
                releaseWakeLock()
                return
            }
            extendWakeLock(60000)
            session.iterations++
            val chunk = UploadChunk.getNext(session)
            when {
                chunk == null -> {
                    log.error("Upload chunk is null, cannot proceed")
                    releaseWakeLock()
                }

                chunk.length == 2 -> {
                    if (L.isEnabled(L.TIDEPOOL)) log.debug("Empty dataset - marking as succeeded")
                    RxBus.send(EventTidepoolStatus(("No data to upload")))
                    releaseWakeLock()
                    unploadNext()
                }

                else -> {
                    val body = chunk.toRequestBody("application/json".toMediaTypeOrNull())

                    RxBus.send(EventTidepoolStatus(("Uploading")))
                    if (session.service != null && session.token != null && session.datasetReply != null) {
                        val call = session.service.doUpload(session.token!!, session.datasetReply!!.getUploadId()!!, body)
                        call.enqueue(TidepoolCallback<UploadReplyMessage>(session, "Data Upload", {
                            setLastEnd(session.end)
                            RxBus.send(EventTidepoolStatus(("Upload completed OK")))
                            releaseWakeLock()
                            unploadNext()
                        }, {
                            RxBus.send(EventTidepoolStatus(("Upload FAILED")))
                            releaseWakeLock()
                        }))
                    }
                }
            }
        }
    }

    private fun unploadNext() {
        if (getLastEnd() < DateUtil.now() - T.mins(1).msecs()) {
            SystemClock.sleep(3000)
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("Restarting doUpload. Last: " + DateUtil.dateAndTimeString(getLastEnd()))
            doUpload()
        }
    }

    fun deleteDataSet() {
        if (session?.datasetReply?.id != null) {
            extendWakeLock(60000)
            val call = session!!.service?.deleteDataSet(session!!.token!!, session!!.datasetReply!!.id!!)
            call?.enqueue(TidepoolCallback(session!!, "Delete Dataset", {
                connectionStatus = ConnectionStatus.DISCONNECTED
                RxBus.send(EventTidepoolStatus(("Dataset removed OK")))
                releaseWakeLock()
            }, {
                connectionStatus = ConnectionStatus.DISCONNECTED
                RxBus.send(EventTidepoolStatus(("Dataset remove FAILED")))
                releaseWakeLock()
            }))
        } else {
            log.error("Got login response but cannot determine datasetId - cannot proceed")
        }
    }

    fun deleteAllData() {
        val session = this.session
        val token = session?.token
        val userid = session?.authReply?.userid
        try {
            requireNotNull(session)
            requireNotNull(token)
            requireNotNull(userid)
            extendWakeLock(60000)
            val call = session.service?.deleteAllData(token, userid)
            call?.enqueue(TidepoolCallback(session, "Delete all data", {
                connectionStatus = ConnectionStatus.DISCONNECTED
                RxBus.send(EventTidepoolStatus(("All data removed OK")))
                releaseWakeLock()
            }, {
                connectionStatus = ConnectionStatus.DISCONNECTED
                RxBus.send(EventTidepoolStatus(("All data remove FAILED")))
                releaseWakeLock()
            }))
        } catch (e: IllegalArgumentException) {
            log.error("Got login response but cannot determine userId - cannot proceed")
        }
    }

    fun getLastEnd(): Long {
        val result = SP.getLong(R.string.key_tidepool_last_end, 0)
        return Math.max(result, DateUtil.now() - T.months(2).msecs())
    }

    fun setLastEnd(time: Long) {
        if (time > getLastEnd()) {
            SP.putLong(R.string.key_tidepool_last_end, time)
            val friendlyEnd = DateUtil.dateAndTimeString(time)
            RxBus.send(EventTidepoolStatus(("Marking uploaded data up to $friendlyEnd")))
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Updating last end to: " + DateUtil.dateAndTimeString(time))
        } else {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Cannot set last end to: " + DateUtil.dateAndTimeString(time) + " vs " + DateUtil.dateAndTimeString(getLastEnd()))
        }
    }

    @Synchronized
    private fun extendWakeLock(ms: Long) {
        if (wl == null) {
            val pm = MainApp.instance().getSystemService(Context.POWER_SERVICE) as PowerManager
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
            if (it.isHeld) {
                try {
                    it.release()
                } catch (e: Exception) {
                    log.error("Error releasing wakelock: $e")
                }
            }
        }
    }

}