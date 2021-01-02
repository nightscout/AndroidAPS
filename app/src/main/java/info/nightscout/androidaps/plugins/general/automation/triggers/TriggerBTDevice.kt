package info.nightscout.androidaps.plugins.general.automation.triggers

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventBTChange
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorConnect
import info.nightscout.androidaps.plugins.general.automation.elements.InputDropdownMenu
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import java.util.*
import javax.inject.Inject

class TriggerBTDevice(injector: HasAndroidInjector) : Trigger(injector) {
    @Inject lateinit var context: Context
    @Inject lateinit var automationPlugin: AutomationPlugin

    var btDevice = InputDropdownMenu(injector, "")
    var comparator: ComparatorConnect = ComparatorConnect(injector)

    private constructor(injector: HasAndroidInjector, triggerBTDevice: TriggerBTDevice) : this(injector) {
        comparator = ComparatorConnect(injector, triggerBTDevice.comparator.value)
        btDevice.value = triggerBTDevice.btDevice.value
    }

    @Synchronized
    override fun shouldRun(): Boolean {
        if (eventExists()) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        return false
    }

    @Synchronized override fun toJSON(): String {
        val data = JSONObject()
            .put("comparator", comparator.value.toString())
            .put("name", btDevice.value)
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        btDevice.value = JsonHelper.safeGetString(d, "name")!!
        comparator.value = ComparatorConnect.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.btdevice

    override fun friendlyDescription(): String =
        resourceHelper.gs(R.string.btdevicecompared, btDevice.value, resourceHelper.gs(comparator.value.stringRes))

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_bluetooth_white_48dp)

    override fun duplicate(): Trigger = TriggerBTDevice(injector, this)

    override fun generateDialog(root: LinearLayout) {
        val pairedDevices = devicesPaired()
        btDevice.setList(pairedDevices)
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.btdevice, this))
            .add(btDevice)
            .add(comparator)
            .build(root)
    }

    // Get the list of paired BT devices to use in dropdown menu
    private fun devicesPaired(): ArrayList<CharSequence> {
        val s = ArrayList<CharSequence>()
        BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.forEach { s.add(it.name) }
        return s
    }

    private fun eventExists(): Boolean {
        automationPlugin.btConnects.forEach {
            if (btDevice.value == it.deviceName) {
                if (comparator.value == ComparatorConnect.Compare.ON_CONNECT && it.state == EventBTChange.Change.CONNECT) return true
                if (comparator.value == ComparatorConnect.Compare.ON_DISCONNECT && it.state == EventBTChange.Change.DISCONNECT) return true
            }
        }
        return false
    }
}