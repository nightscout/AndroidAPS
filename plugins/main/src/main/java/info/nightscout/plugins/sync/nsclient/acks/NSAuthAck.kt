package info.nightscout.plugins.sync.nsclient.acks

import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.Event
import io.socket.client.Ack
import org.json.JSONObject

class NSAuthAck(private val rxBus: RxBus) : Event(), Ack {

    var read = false
    var write = false
    var writeTreatment = false
    override fun call(vararg args: Any) {
        val response = args[0] as JSONObject
        read = response.optBoolean("read")
        write = response.optBoolean("write")
        writeTreatment = response.optBoolean("write_treatment")
        rxBus.send(this)
    }
}