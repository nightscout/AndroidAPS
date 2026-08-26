package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerWifiSsid(
    deps: TriggerDeps,
    private val receiverStatusStore: ReceiverStatusStore
) : Trigger(deps) {


    var ssid = InputString()
    var comparator = Comparator(rh)

    @Suppress("unused")
    constructor(deps: TriggerDeps, receiverStatusStore: ReceiverStatusStore, ssid: String, compare: Comparator.Compare) : this(deps, receiverStatusStore) {
        this.ssid = InputString(ssid)
        comparator = Comparator(rh, compare)
    }

    constructor(deps: TriggerDeps, receiverStatusStore: ReceiverStatusStore, triggerWifiSsid: TriggerWifiSsid) : this(deps, receiverStatusStore) {
        this.ssid = InputString(triggerWifiSsid.ssid.value)
        comparator = Comparator(rh, triggerWifiSsid.comparator.value)
    }

    fun setValue(ssid: String): TriggerWifiSsid {
        this.ssid.value = ssid
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerWifiSsid {
        this.comparator.value = comparator
        return this
    }

    override suspend fun shouldRun(): Boolean {
        val eventNetworkChange = receiverStatusStore.networkStatusFlow.value ?: return false
        if (!eventNetworkChange.wifiConnected && comparator.value == Comparator.Compare.IS_NOT_AVAILABLE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        if (eventNetworkChange.wifiConnected && comparator.value.check(eventNetworkChange.ssid, ssid.value)) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("ssid", ssid.value)
            put("comparator", comparator.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        ssid.value = d.lenientStringOrNull("ssid")!!
        comparator.value = Comparator.Compare.valueOf(d.lenientStringOrNull("comparator")!!)
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.ns_wifi_ssids

    override fun friendlyDescription(): String =
        rh.gs(R.string.wifissidcompared, rh.gs(comparator.value.stringRes), ssid.value)

    override fun composeIcon() = Icons.Filled.Wifi
    override fun elementType() = ElementType.AAPS

    override fun duplicate(): Trigger = TriggerWifiSsid(deps, receiverStatusStore, this)

}