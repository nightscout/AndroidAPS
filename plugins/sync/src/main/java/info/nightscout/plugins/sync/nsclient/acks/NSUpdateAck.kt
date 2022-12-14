package info.nightscout.plugins.sync.nsclient.acks

import androidx.work.OneTimeWorkRequest
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.plugins.sync.nsclient.services.NSClientService
import info.nightscout.plugins.sync.nsclient.workers.NSClientUpdateRemoveAckWorker
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.Event
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import io.socket.client.Ack
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by mike on 21.02.2016.
 */
class NSUpdateAck(
    val action : String,
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