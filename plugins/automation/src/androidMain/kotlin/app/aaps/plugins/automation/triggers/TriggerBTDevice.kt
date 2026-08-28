package app.aaps.plugins.automation.triggers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.BtConnectionSource
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.ComparatorConnect
import app.aaps.plugins.automation.elements.InputDropdownMenu
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerBTDevice(
    deps: TriggerDeps,
    private val btConnectionSource: BtConnectionSource
) : Trigger(deps) {


    var btDevice = InputDropdownMenu("")
    var comparator: ComparatorConnect = ComparatorConnect(rh)

    private constructor(deps: TriggerDeps, btConnectionSource: BtConnectionSource, triggerBTDevice: TriggerBTDevice) : this(deps, btConnectionSource) {
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

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("comparator", comparator.value.toString())
            put("name", btDevice.value)
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        btDevice.value = d.lenientStringOrNull("name")!!
        comparator.value = ComparatorConnect.Compare.valueOf(d.lenientStringOrNull("comparator")!!)
        return this
    }

    override fun friendlyName(): Int = R.string.btdevice

    override fun friendlyDescription(): String =
        rh.gs(R.string.btdevicecompared, btDevice.value, rh.gs(comparator.value.stringRes))

    override fun composeIcon() = Icons.Filled.Bluetooth
    override fun elementType() = ElementType.AAPS

    override fun duplicate(): Trigger = TriggerBTDevice(deps, btConnectionSource, this)

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