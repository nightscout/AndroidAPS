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
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.BtConnectionSource
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorConnect
import app.aaps.plugins.automation.elements.InputDropdownMenu
import org.json.JSONObject
import javax.inject.Inject

class TriggerBTDevice(
    deps: TriggerDeps,
    private val context: Context,
    private val btConnectionSource: BtConnectionSource
) : Trigger(deps) {


    var btDevice = InputDropdownMenu(rh, "")
    var comparator: ComparatorConnect = ComparatorConnect(rh)

    private constructor(deps: TriggerDeps, context: Context, btConnectionSource: BtConnectionSource, triggerBTDevice: TriggerBTDevice) : this(deps, context, btConnectionSource) {
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
    override fun elementType() = ElementType.AAPS

    override fun duplicate(): Trigger = TriggerBTDevice(deps, context, btConnectionSource, this)

    // Get the list of paired BT devices to use in dropdown menu
    private fun devicesPaired(): ArrayList<CharSequence> {
        val s = ArrayList<CharSequence>()
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.bondedDevices?.forEach { s.add(it.name) }
        } else {
            rxBus.send(EventShowSnackbar(rh.gs(app.aaps.core.ui.R.string.need_connect_permission), EventShowSnackbar.Type.Error))
        }
        return s
    }

    private fun eventExists(): Boolean {
        btConnectionSource.recentBtConnects().forEach {
            if (btDevice.value == it.deviceName) {
                if (comparator.value == ComparatorConnect.Compare.ON_CONNECT && it.state == EventBTChange.Change.CONNECT) return true
                if (comparator.value == ComparatorConnect.Compare.ON_DISCONNECT && it.state == EventBTChange.Change.DISCONNECT) return true
            }
        }
        return false
    }
}