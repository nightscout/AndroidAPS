package info.nightscout.plugins.sync.nsclient.acks

import androidx.work.OneTimeWorkRequest
import info.nightscout.core.utils.receivers.DataWorkerStorage
import info.nightscout.plugins.sync.nsclient.services.NSClientService
import info.nightscout.plugins.sync.nsclient.workers.NSClientAddAckWorker
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.Event
import info.nightscout.rx.events.EventNSClientRestart
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import io.socket.client.Ack
import org.json.JSONArray
import org.json.JSONObject

class NSAddAck(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val nsClientService: NSClientService,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    val originalObject: Any? = null
) : Event(), Ack {

    var id: String? = null
    var json: JSONObject? = null
    override fun call(vararg args: Any) {
        // Regular response
        try {
            val responseArray = args[0] as JSONArray
            val response: JSONObject
            if (responseArray.length() > 0) {
                response = responseArray.getJSONObject(0)
                id = response.getString("_id")
                json = response
            }
            processAddAck()
            return
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
        // Check for not authorized
        try {
            val response = args[0] as JSONObject
            if (response.has("result")) {
                id = null
                if (response.getString("result").contains("Not")) {
                    rxBus.send(EventNSClientRestart())
                    return
                }
                aapsLogger.debug(LTag.NSCLIENT, "DBACCESS " + response.getString("result"))
            }
        } catch (e: Exception) {
            aapsLogger.error("Unhandled exception", e)
        }
    }

    private fun processAddAck() {
        nsClientService.lastAckTime = dateUtil.now()
        dataWorkerStorage.enqueue(
            OneTimeWorkRequest.Builder(NSClientAddAckWorker::class.java)
                .setInputData(dataWorkerStorage.storeInputData(this))
                .build()
        )
    }
}