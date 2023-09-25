package info.nightscout.plugins.sync.nsclient.acks

import androidx.work.OneTimeWorkRequest
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.receivers.DataWorkerStorage
import info.nightscout.plugins.sync.nsclient.services.NSClientService
import info.nightscout.plugins.sync.nsclient.workers.NSClientUpdateRemoveAckWorker
import io.socket.client.Ack
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by mike on 21.02.2016.
 */
class NSUpdateAck(
    val action: String,
    var _id: String,
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val nsClientService: NSClientService,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    val originalObject: Any? = null
) : Event(), Ack {

    var result = false
    override fun call(vararg args: Any) {
        val response = args[0] as JSONObject
        if (response.has("result")) try {
            if (response.getString("result") == "success") {
                result = true
            } else if (response.getString("result") == "Missing _id") {
                result = true
                aapsLogger.debug(LTag.NSCLIENT, "Internal error: Missing _id returned on dbUpdate ack")
            }
            processUpdateAck()
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    private fun processUpdateAck() {
        nsClientService.lastAckTime = dateUtil.now()
        dataWorkerStorage.enqueue(
            OneTimeWorkRequest.Builder(NSClientUpdateRemoveAckWorker::class.java)
                .setInputData(dataWorkerStorage.storeInputData(this))
                .build()
        )
    }
}