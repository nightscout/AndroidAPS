package app.aaps.plugins.sync.wear.wearintegration

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventMobileToWearWatchface
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.wear.WearPlugin
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

class DataLayerListenerServiceMobile : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var loop: Loop
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    inner class LocalBinder : Binder() {

        fun getService(): DataLayerListenerServiceMobile = this@DataLayerListenerServiceMobile
    }

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    //private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val disposable = CompositeDisposable()

    private val rxPath get() = getString(app.aaps.core.interfaces.R.string.path_rx_bridge)
    private val rxWatchfacePath get() = getString(app.aaps.core.interfaces.R.string.path_rx_data_bridge)

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        handler.post { updateTranscriptionCapability() }
        disposable += rxBus
            .toObservable(EventMobileToWear::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { sendMessage(rxPath, it.payload.serialize()) }
        disposable += rxBus
            .toObservable(EventMobileToWearWatchface::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { sendMessage(rxWatchfacePath, it.payload) }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
        scope.cancel()
    }

    @ExperimentalSerializationApi
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (wearPlugin.isEnabled()) {
            when (messageEvent.path) {
                rxPath          -> {
                    aapsLogger.debug(LTag.WEAR, "onMessageReceived rxPath: ${String(messageEvent.data)}")
                    val command = EventData.deserialize(String(messageEvent.data))
                    rxBus.send(command.also { it.sourceNodeId = messageEvent.sourceNodeId })
                }

                rxWatchfacePath -> {
                    aapsLogger.debug(LTag.WEAR, "onMessageReceived rxDataPath: ${messageEvent.data.size}")
                    val command = EventData.deserializeByte(messageEvent.data)
                    rxBus.send(command.also { it.sourceNodeId = messageEvent.sourceNodeId })
                }
            }
        }
    }

    private var transcriptionNodeId: String? = null

    private fun updateTranscriptionCapability() {
        try {
            val capabilityInfo: CapabilityInfo = Tasks.await(
                capabilityClient.getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            )
            aapsLogger.debug(LTag.WEAR, "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
            val bestNode = pickBestNodeId(capabilityInfo.nodes)
            transcriptionNodeId = bestNode?.id
            wearPlugin.connectedDevice = bestNode?.displayName ?: rh.gs(R.string.no_watch_connected)
            rxBus.send(EventWearUpdateGui())
            aapsLogger.debug(LTag.WEAR, "Selected node: ${bestNode?.displayName} $transcriptionNodeId")
            rxBus.send(EventMobileToWear(EventData.ActionPing(System.currentTimeMillis())))
            rxBus.send(EventData.ActionResendData("WatchUpdaterService"))
        } catch (_: Exception) {
            fabricPrivacy.logCustom("WearOS_unsupported")
        }
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): Node? =
        nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()

    @Suppress("unused")
    private fun sendData(path: String, vararg params: DataMap) {
        if (wearPlugin.isEnabled()) {
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
    }

    private fun sendMessage(path: String, data: String?) {
        aapsLogger.debug(LTag.WEAR, "sendMessage: $path $data")
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data?.toByteArray() ?: byteArrayOf()).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure")
                    }
                }
        }
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

        const val WEAR_CAPABILITY = "androidaps_wear"
    }
}
