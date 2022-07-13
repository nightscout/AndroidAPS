package info.nightscout.androidaps.comm

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import info.nightscout.androidaps.R
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectOutputStream

class ExceptionService : IntentService(ExceptionService::class.simpleName) {

    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private val exceptionPath get() = getString(R.string.path_log_exception)
    private var transcriptionNodeId: String? = null

    override fun onCreate() {
        super.onCreate()

        handler.post { updateTranscriptionCapability() }
    }

    private fun updateTranscriptionCapability() {
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(DataLayerListenerServiceWear.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        )
        Log.d("WEAR", "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
        pickBestNodeId(capabilityInfo.nodes)?.let { transcriptionNodeId = it }
        Log.d("WEAR", "Selected node: $transcriptionNodeId")
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): String? =
        nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id

    private fun sendMessage(path: String, data: ByteArray) {
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        Log.d("WEAR", "sendMessage:  $path failure")
                    }
                }
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        Log.d("WEAR", "onHandleIntent: ErrorService $intent")

        val bos = ByteArrayOutputStream()
        var oos: ObjectOutputStream? = null
        try {
            oos = ObjectOutputStream(bos)
            oos.writeObject(intent?.getSerializableExtra("exception"))
            val exceptionData = bos.toByteArray()
            val dataMap = DataMap()

            dataMap.putString("board", Build.BOARD)
            dataMap.putString("sdk", Build.VERSION.SDK_INT.toString())
            dataMap.putString("fingerprint", Build.FINGERPRINT)
            dataMap.putString("model", Build.MODEL)
            dataMap.putString("manufacturer", Build.MANUFACTURER)
            dataMap.putString("product", Build.PRODUCT)
            dataMap.putByteArray("exception", exceptionData)

            handler.post {
                sendMessage(exceptionPath, dataMap.toByteArray())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                oos?.close()
            } catch (exx: IOException) {
                // ignore close exception
            }
            try {
                bos.close()
            } catch (exx: IOException) {
                // ignore close exception
            }
        }
    }

    init {
        setIntentRedelivery(true)
    }

    companion object {
        fun reportException(context: Context, ex: Throwable?) {
            val errorIntent = Intent(context, ExceptionService::class.java)
            errorIntent.putExtra("exception", ex)
            context.startService(errorIntent)
        }
    }

}
