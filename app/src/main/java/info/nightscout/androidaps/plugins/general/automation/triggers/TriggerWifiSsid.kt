package info.nightscout.androidaps.plugins.general.automation.triggers

import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import javax.inject.Inject

class TriggerWifiSsid(injector: HasAndroidInjector) : Trigger(injector) {
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore

    var ssid = InputString(injector)
    var comparator = Comparator(injector)

    constructor(injector: HasAndroidInjector, ssid: String, compare: Comparator.Compare) : this(injector) {
        this.ssid = InputString(injector, ssid)
        comparator = Comparator(injector, compare)
    }

    constructor(injector: HasAndroidInjector, triggerWifiSsid: TriggerWifiSsid) : this(injector) {
        this.ssid = InputString(injector, triggerWifiSsid.ssid.value)
        comparator = Comparator(injector, triggerWifiSsid.comparator.value)
    }

    fun setValue(ssid: String): TriggerWifiSsid {
        this.ssid.value = ssid
        return this
    }

    fun comparator(comparator: Comparator.Compare): TriggerWifiSsid {
        this.comparator.value = comparator
        return this
    }

    override fun shouldRun(): Boolean {
        val eventNetworkChange = receiverStatusStore.lastNetworkEvent ?: return false
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

    override fun toJSON(): String {
        val data = JSONObject()
            .put("ssid", ssid.value)
            .put("comparator", comparator.value.toString())
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        ssid.value = JsonHelper.safeGetString(d, "ssid")!!
        comparator.value = Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.ns_wifi_ssids

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.wifissidcompared, resourceHelper.gs(comparator.value.stringRes), ssid.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_network_wifi)

    override fun duplicate(): Trigger = TriggerWifiSsid(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.ns_wifi_ssids, this))
            .add(comparator)
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.ns_wifi_ssids) + ": ", "", ssid))
            .build(root)
    }
}