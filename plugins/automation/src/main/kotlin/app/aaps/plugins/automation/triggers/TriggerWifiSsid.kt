package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.Comparator
import app.aaps.plugins.automation.elements.InputString
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class TriggerWifiSsid(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    var ssid = InputString()
    var comparator = Comparator(rh)

    @Suppress("unused")
    constructor(injector: HasAndroidInjector, ssid: String, compare: Comparator.Compare) : this(injector) {
        this.ssid = InputString(ssid)
        comparator = Comparator(rh, compare)
    }

    constructor(injector: HasAndroidInjector, triggerWifiSsid: TriggerWifiSsid) : this(injector) {
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

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("ssid", ssid.value)
            .put("comparator", comparator.value.toString())

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        ssid.value = JsonHelper.safeGetString(d, "ssid")!!
        comparator.value = Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.ns_wifi_ssids

    override fun friendlyDescription(): String =
        rh.gs(R.string.wifissidcompared, rh.gs(comparator.value.stringRes), ssid.value)

    override fun composeIcon() = Icons.Filled.Wifi
    override fun composeIconTint() = IconTint.Network

    override fun duplicate(): Trigger = TriggerWifiSsid(injector, this)

}