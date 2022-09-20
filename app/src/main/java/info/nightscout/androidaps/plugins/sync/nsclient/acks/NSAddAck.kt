package info.nightscout.androidaps.plugins.sync.nsclient.acks

import info.nightscout.androidaps.events.Event
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sync.nsclient.events.EventNSClientRestart
import io.socket.client.Ack
import org.json.JSONArray
import org.json.JSONObject

class NSAddAck(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    val originalObject: Any? = null
) : Event(), Ack {

    var id: String? = null
    var nsClientID: String? = null
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
                if (response.has("NSCLIENT_ID")) {
                    nsClientID = response.getString("NSCLIENT_ID")
                }
            }
            rxBus.send(this)
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
}