package info.nightscout.androidaps.plugins.general.tidepool.comm

import android.content.Context
import android.os.PowerManager
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.general.tidepool.TidepoolPlugin
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolStatus
import info.nightscout.androidaps.plugins.general.tidepool.messages.*
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TidepoolUploader {

    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    private var wl: PowerManager.WakeLock? = null


    private const val INTEGRATION_BASE_URL = "https://int-api.tidepool.org"
    private const val PRODUCTION_BASE_URL = "https://api.tidepool.org"

    private var retrofit: Retrofit? = null

    var session: Session? = null

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, FAILED
    }

    var connectionStatus: ConnectionStatus = TidepoolUploader.ConnectionStatus.DISCONNECTED

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

    // TODO: call on preference change
    fun resetInstance() {
        retrofit = null
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("Instance reset")
    }

    @Synchronized
    fun doLogin() {
        if (!SP.getBoolean(R.string.key_cloud_storage_tidepool_enable, false)) {
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("Cannot login as disabled by preference")
            return
        }
        if (connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTED || connectionStatus == TidepoolUploader.ConnectionStatus.CONNECTING) {
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("Already connected")
            return
        }
        // TODO failure backoff
        extendWakeLock(30000)
        session = Session(AuthRequestMessage.getAuthRequestHeader(), SESSION_TOKEN_HEADER)
        if (session?.authHeader != null) {
            connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTING
            status("Connecting")
            val call = session!!.service?.getLogin(session?.authHeader!!)

            call?.enqueue(TidepoolCallback<AuthReplyMessage>(session!!, "Login", {
                startSession(session!!)
            }, {
                connectionStatus = TidepoolUploader.ConnectionStatus.FAILED;
                loginFailed()
            }))
            return
        } else {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Cannot do login as user credentials have not been set correctly")
            connectionStatus = TidepoolUploader.ConnectionStatus.FAILED;
            status("Invalid credentials")
            releaseWakeLock()
            return
        }
    }

    fun testLogin(rootContext: Context) {

        var message = "Failed to log into Tidepool.\n" + "Check that your user name and password are correct."

        val session = Session(AuthRequestMessage.getAuthRequestHeader(), SESSION_TOKEN_HEADER)
        if (session.authHeader != null) {
            val call = session.service!!.getLogin(session.authHeader!!)

            val response = call.execute()
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Header: " + response.code())
            message = "Successfully logged into Tidepool."

        } else {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Cannot do login as user credentials have not been set correctly")
        }

        OKDialog.show(rootContext, MainApp.gs(R.string.tidepool), message, null);
    }


    private fun loginFailed() {
        releaseWakeLock()
    }

    fun startSession(session: Session) {
        extendWakeLock(30000)
        if (session.authReply?.userid != null) {
            // See if we already have an open data set to write to
            val datasetCall = session.service!!.getOpenDataSets(session.token!!,
                    session.authReply!!.userid!!, BuildConfig.APPLICATION_ID, 1)

            datasetCall.enqueue(TidepoolCallback<List<DatasetReplyMessage>>(session, "Get Open Datasets", {
                if (session.datasetReply == null) {
                    status("Creating new dataset")
                    val call = session.service.openDataSet(session.token!!, session.authReply!!.userid!!, OpenDatasetRequestMessage().getBody())
                    call.enqueue(TidepoolCallback<DatasetReplyMessage>(session, "Open New Dataset", {
                        connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTED;
                        status("New dataset OK")
                        releaseWakeLock()
                    }, {
                        status("New dataset FAILED")
                        connectionStatus = TidepoolUploader.ConnectionStatus.FAILED;
                        releaseWakeLock()
                    }))
                } else {
                    if (L.isEnabled(L.TIDEPOOL))
                        log.debug("Existing Dataset: " + session.datasetReply!!.getUploadId())
                    // TODO: Wouldn't need to do this if we could block on the above `call.enqueue`.
                    // ie, do the openDataSet conditionally, and then do `doUpload` either way.
                    connectionStatus = TidepoolUploader.ConnectionStatus.CONNECTED;
                    status("Appending to existing dataset")
                    releaseWakeLock()
                }
            }, {
                connectionStatus = TidepoolUploader.ConnectionStatus.FAILED;
                status("Open dataset FAILED")
                releaseWakeLock()
            }))
        } else {
            log.error("Got login response but cannot determine userid - cannot proceed")
            connectionStatus = TidepoolUploader.ConnectionStatus.FAILED;
            status("Error userid")
            releaseWakeLock()
        }
    }

    fun doUpload() {
        if (!TidepoolPlugin.enabled()) {
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("Cannot upload - preference disabled")
            return
        }
        if (session == null) {
            log.error("Session is null, cannot proceed")
            return
        }
        extendWakeLock(60000)
        session!!.iterations++
        val chunk = UploadChunk.getNext(session)
        if (chunk == null) {
            log.error("Upload chunk is null, cannot proceed")
            releaseWakeLock()
        } else if (chunk.length == 2) {
            if (L.isEnabled(L.TIDEPOOL)) log.debug("Empty dataset - marking as succeeded")
            status("No data to upload")
            releaseWakeLock()
        } else {
            val body = RequestBody.create(MediaType.parse("application/json"), chunk)

            status("Uploading")
            val call = session!!.service!!.doUpload(session!!.token!!, session!!.datasetReply!!.getUploadId()!!, body)
            call.enqueue(TidepoolCallback<UploadReplyMessage>(session!!, "Data Upload", {
                UploadChunk.setLastEnd(session!!.end)
                status("Upload completed OK")
                releaseWakeLock()
            }, {
                status("Upload FAILED")
                releaseWakeLock()
            }))
        }

    }

    fun status(status: String) {
        if (L.isEnabled(L.TIDEPOOL))
            log.debug("New status: $status")
        MainApp.bus().post(EventTidepoolStatus(status))
    }


    fun deleteDataSet() {
        if (session?.datasetReply?.id != null) {
            extendWakeLock(60000)
            val call = session!!.service?.deleteDataSet(session!!.token!!, session!!.datasetReply!!.id!!)
            call?.enqueue(TidepoolCallback(session!!, "Delete Dataset", {
                connectionStatus = TidepoolUploader.ConnectionStatus.DISCONNECTED
                status("Dataset removed OK")
                releaseWakeLock()
            }, {
                connectionStatus = TidepoolUploader.ConnectionStatus.DISCONNECTED
                status("Dataset remove FAILED")
                releaseWakeLock()
            }))
        } else {
            log.error("Got login response but cannot determine dataseId - cannot proceed")
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
        if (wl == null) return
        if (wl!!.isHeld()) {
            try {
                wl!!.release()
            } catch (e: Exception) {
                log.error("Error releasing wakelock: $e")
            }
        }
    }

}