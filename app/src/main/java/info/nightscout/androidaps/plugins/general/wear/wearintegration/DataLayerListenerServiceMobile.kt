package info.nightscout.androidaps.plugins.general.wear.wearintegration

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.events.EventMobileToWear
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.wear.WearPlugin
import info.nightscout.androidaps.plugins.general.wear.events.EventWearUpdateGui
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.EventData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DataLayerListenerServiceMobile : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var loop: Loop
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var config: Config
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
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

    private val rxPath get() = getString(R.string.path_rx_bridge)

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        handler.post { updateTranscriptionCapability() }
        disposable += rxBus
            .toObservable(EventMobileToWear::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { sendMessage(rxPath, it.payload.serialize()) }
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        scope.cancel()
    }

    @Suppress("ControlFlowWithEmptyBody", "UNUSED_EXPRESSION")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        //aapsLogger.debug(LTag.WEAR, "onDataChanged")

        if (wearPlugin.isEnabled()) {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val path = event.dataItem.uri.path

                    aapsLogger.debug(LTag.WEAR, "onDataChanged: Path: $path, EventDataItem=${event.dataItem}")
                    try {
                        when (path) {
                        }
                    } catch (exception: Exception) {
                        aapsLogger.error(LTag.WEAR, "Message failed", exception)
                    }
                }
            }
        }
        super.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (wearPlugin.isEnabled()) {
            when (messageEvent.path) {
                rxPath -> {
                    aapsLogger.debug(LTag.WEAR, "onMessageReceived rxPath: ${String(messageEvent.data)}")
                    val command = EventData.deserialize(String(messageEvent.data))
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
        } catch (e: Exception) {
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

    @Suppress("unused")
    private fun sendMessage(path: String, data: ByteArray) {
        aapsLogger.debug(LTag.WEAR, "sendMessage: $path")
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure")
                    }
                }
        }
    }

    companion object {

        const val WEAR_CAPABILITY = "androidaps_wear"
    }
}
