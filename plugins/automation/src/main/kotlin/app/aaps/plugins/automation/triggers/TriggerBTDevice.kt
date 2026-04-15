package app.aaps.plugins.automation.triggers

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.AutomationPlugin
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.ComparatorConnect
import app.aaps.plugins.automation.elements.InputDropdownMenu
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject

class TriggerBTDevice(injector: HasAndroidInjector) : Trigger(injector) {

    @Inject lateinit var context: Context
    @Inject lateinit var automationPlugin: AutomationPlugin

    var btDevice = InputDropdownMenu(rh, "")
    var comparator: ComparatorConnect = ComparatorConnect(rh)

    private constructor(injector: HasAndroidInjector, triggerBTDevice: TriggerBTDevice) : this(injector) {
        comparator = ComparatorConnect(rh, triggerBTDevice.comparator.value)
        btDevice.value = triggerBTDevice.btDevice.value
    }

    override suspend fun shouldRun(): Boolean {
        if (eventExists()) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            return true
        }
        return false
    }

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("comparator", comparator.value.toString())
            .put("name", btDevice.value)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        btDevice.value = JsonHelper.safeGetString(d, "name")!!
        comparator.value = ComparatorConnect.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.btdevice

    override fun friendlyDescription(): String =
        rh.gs(R.string.btdevicecompared, btDevice.value, rh.gs(comparator.value.stringRes))

    override fun composeIcon() = Icons.Filled.Bluetooth
    override fun composeIconTint() = IconTint.Network

    override fun duplicate(): Trigger = TriggerBTDevice(injector, this)

    // Get the list of paired BT devices to use in dropdown menu
    private fun devicesPaired(): ArrayList<CharSequence> {
        val s = ArrayList<CharSequence>()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.bondedDevices?.forEach { s.add(it.name) }
        } else {
            ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
        }
        return s
    }

    private fun eventExists(): Boolean {
        ArrayList(automationPlugin.btConnects).forEach {
            if (btDevice.value == it.deviceName) {
                if (comparator.value == ComparatorConnect.Compare.ON_CONNECT && it.state == EventBTChange.Change.CONNECT) return true
                if (comparator.value == ComparatorConnect.Compare.ON_DISCONNECT && it.state == EventBTChange.Change.DISCONNECT) return true
            }
        }
        return false
    }
}