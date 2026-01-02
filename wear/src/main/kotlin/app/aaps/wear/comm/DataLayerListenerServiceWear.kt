package app.aaps.wear.comm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearDataToMobile
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.shared.impl.weardata.ZipWatchfaceFormat
import app.aaps.wear.R
import app.aaps.wear.events.EventWearPreferenceChange
import app.aaps.wear.heartrate.HeartRateListener
import app.aaps.wear.interaction.ConfigurationActivity
import app.aaps.wear.wearStepCount.StepCountListener
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.ExperimentalSerializationApi
import javax.inject.Inject

class DataLayerListenerServiceWear : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    //private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val disposable = CompositeDisposable()
    private var heartRateListener: HeartRateListener? = null
    private var stepCountListener: StepCountListener? = null

    private val rxPath get() = getString(app.aaps.core.interfaces.R.string.path_rx_bridge)
    private val rxDataPath get() = getString(app.aaps.core.interfaces.R.string.path_rx_data_bridge)

    @ExperimentalSerializationApi
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        startForegroundService()
        handler.post { updateTranscriptionCapability() }
        disposable += rxBus
            .toObservable(EventWearToMobile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                sendMessage(rxPath, it.payload.serialize())
            }
        disposable += rxBus
            .toObservable(EventWearDataToMobile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                sendMessage(rxDataPath, it.payload.serializeByte())
            }
        disposable += rxBus
            .toObservable(EventWearPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { event: EventWearPreferenceChange ->
                if (event.changedKey == getString(R.string.key_heart_rate_sampling)) updateHeartRateListener()
                if (event.changedKey == getString(R.string.key_steps_sampling)) updateStepsCountListener()
            }

        updateHeartRateListener()
        updateStepsCountListener()
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        scope.cancel()
        disposable.clear()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        when (messageEvent.path) {
            rxPath     -> {
                aapsLogger.debug(LTag.WEAR, "onMessageReceived: ${String(messageEvent.data)}")
                val command = EventData.deserialize(String(messageEvent.data))
                rxBus.send(command.also { it.sourceNodeId = messageEvent.sourceNodeId })
                // Use this sender
                transcriptionNodeId = messageEvent.sourceNodeId
                aapsLogger.debug(LTag.WEAR, "Updated node: $transcriptionNodeId")
            }

            rxDataPath -> {
                aapsLogger.debug(LTag.WEAR, "onMessageReceived: ${messageEvent.data.size}")
                ZipWatchfaceFormat.loadCustomWatchface(messageEvent.data, "", false)?.let {
                    val command = EventData.ActionSetCustomWatchface(it.cwfData)
                    rxBus.send(command.also { it.sourceNodeId = messageEvent.sourceNodeId })
                }
                // Use this sender
                transcriptionNodeId = messageEvent.sourceNodeId
                aapsLogger.debug(LTag.WEAR, "Updated node: $transcriptionNodeId")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            INTENT_CANCEL_BOLUS        -> {
                //dismiss notification
                NotificationManagerCompat.from(this).cancel(BOLUS_PROGRESS_NOTIF_ID)
                //send cancel-request to phone.
                rxBus.send(EventWearToMobile(EventData.CancelBolus(System.currentTimeMillis())))
            }

            INTENT_WEAR_TO_MOBILE      -> sendMessage(rxPath, intent.extras?.getString(KEY_ACTION_DATA))
            INTENT_CANCEL_NOTIFICATION -> (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(CHANGE_NOTIF_ID)
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notificationIntent = Intent(this, ConfigurationActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.datalayer_notification_title))
            .setContentText(getString(R.string.datalayer_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_icon)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(FOREGROUND_NOTIF_ID, notification)
    }

    private fun updateHeartRateListener() {
        if (sp.getBoolean(R.string.key_heart_rate_sampling, false)) {
            if (heartRateListener == null) {
                heartRateListener = HeartRateListener(
                    this, aapsLogger, sp, aapsSchedulers
                ).also { hrl -> disposable += hrl }
            }
        } else {
            heartRateListener?.let { hrl ->
                disposable.remove(hrl)
                heartRateListener = null
            }
        }
    }

    private fun updateStepsCountListener() {
        if (sp.getBoolean(R.string.key_steps_sampling, false)) {
            if (stepCountListener == null) {
                stepCountListener = StepCountListener(
                    this, aapsLogger, aapsSchedulers
                ).also { scl -> disposable += scl }
            }
        } else {
            stepCountListener?.let { scl ->
                disposable.remove(scl)
                stepCountListener = null
            }
        }
    }

    @Suppress("PrivatePropertyName")
    private val CHANNEL_ID: String = "DataLayerForegroundServiceChannel"
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(CHANNEL_ID, "Data Layer Foreground Service Channel", NotificationManager.IMPORTANCE_LOW)
        serviceChannel.setShowBadge(false)
        serviceChannel.enableLights(false)
        serviceChannel.enableVibration(false)
        serviceChannel.setSound(null, null)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private var transcriptionNodeId: String? = null

    private fun updateTranscriptionCapability() {
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        )
        aapsLogger.debug(LTag.WEAR, "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
        pickBestNodeId(capabilityInfo.nodes)?.let { transcriptionNodeId = it }
        aapsLogger.debug(LTag.WEAR, "Selected node: $transcriptionNodeId")
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): String? =
        nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id

    @Suppress("unused")
    private fun sendData(path: String, vararg params: DataMap) {
        scope.launch {
            try {
                for (dm in params) {
                    val request = PutDataMapRequest.create(path).apply {
                        dataMap.putAll(dm)
                    }
                        .asPutDataRequest()
                        .setUrgent()

                    val result = dataClient.putDataItem(request).await()
                    aapsLogger.debug(LTag.WEAR, "sendData: ${result.uri} ${params.joinToString()}")
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                aapsLogger.error(LTag.WEAR, "DataItem failed: $exception")
            }
        }
    }

    private fun sendMessage(path: String, data: String?) {
        transcriptionNodeId?.also { nodeId ->
            aapsLogger.debug(LTag.WEAR, "sendMessage: $path $data")
            messageClient
                .sendMessage(nodeId, path, data?.toByteArray() ?: byteArrayOf()).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure $it")
                    }
                }
        } ?: aapsLogger.debug(LTag.WEAR, "sendMessage: Ignoring message. No node selected.")
    }

    private fun sendMessage(path: String, data: ByteArray) {
        aapsLogger.debug(LTag.WEAR, "sendMessage: $path ${data.size}")
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure ${data.size}")
                    }
                }
        }
    }

    companion object {

        const val PHONE_CAPABILITY = "androidaps_mobile"

        // Accepted intents
        val INTENT_NEW_DATA = DataLayerListenerServiceWear::class.java.name + ".NewData"
        val INTENT_CANCEL_BOLUS = DataLayerListenerServiceWear::class.java.name + ".CancelBolus"
        val INTENT_WEAR_TO_MOBILE = DataLayerListenerServiceWear::class.java.name + ".WearToMobile"
        val INTENT_CANCEL_NOTIFICATION = DataLayerListenerServiceWear::class.java.name + ".CancelNotification"

        //data keys
        const val KEY_ACTION_DATA = "actionData"
        const val KEY_ACTION = "action"
        const val KEY_MESSAGE = "message"
        const val KEY_TITLE = "title"

        const val BOLUS_PROGRESS_NOTIF_ID = 1
        const val CONFIRM_NOTIF_ID = 2
        const val FOREGROUND_NOTIF_ID = 3
        const val CHANGE_NOTIF_ID = 556677

        const val AAPS_NOTIFY_CHANNEL_ID_OPEN_LOOP = "AndroidAPS-OpenLoop"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS = "bolus progress vibration"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUS_PROGRESS_SILENT = "bolus progress silent"
    }
}