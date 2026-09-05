package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientStringOrNull
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.InputLocationMode
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TriggerLocation(deps: TriggerDeps) : Trigger(deps) {

    var latitude = InputDouble(0.0, -90.0, +90.0, 0.000001, NumberFormat.DECIMAL_6)
    var longitude = InputDouble(0.0, -180.0, +180.0, 0.000001, NumberFormat.DECIMAL_6)
    var distance = InputDouble(200.0, 0.0, 100000.0, 10.0, NumberFormat.INTEGER)
    var modeSelected = InputLocationMode(rh)
    var name: InputString = InputString()

    var lastMode = InputLocationMode.Mode.INSIDE

    private constructor(deps: TriggerDeps, triggerLocation: TriggerLocation) : this(deps) {
        latitude = InputDouble(triggerLocation.latitude)
        longitude = InputDouble(triggerLocation.longitude)
        distance = InputDouble(triggerLocation.distance)
        modeSelected = InputLocationMode(rh, triggerLocation.modeSelected.value)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT)
            lastMode = InputLocationMode.Mode.OUTSIDE
        name = triggerLocation.name
    }

    override suspend fun shouldRun(): Boolean {
        val calculatedDistance = lastKnownLocation.distanceTo(latitude.value, longitude.value) ?: return false
        if (modeSelected.value == InputLocationMode.Mode.INSIDE && calculatedDistance <= distance.value ||
            modeSelected.value == InputLocationMode.Mode.OUTSIDE && calculatedDistance > distance.value ||
            modeSelected.value == InputLocationMode.Mode.GOING_IN && calculatedDistance <= distance.value && lastMode == InputLocationMode.Mode.OUTSIDE ||
            modeSelected.value == InputLocationMode.Mode.GOING_OUT && calculatedDistance > distance.value && lastMode == InputLocationMode.Mode.INSIDE
        ) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            lastMode = currentMode(calculatedDistance)
            return true
        }
        lastMode = currentMode(calculatedDistance) // current mode will be last mode for the next check
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun dataJSON(): JsonObject =
        buildJsonObject {
            put("latitude", latitude.value)
            put("longitude", longitude.value)
            put("distance", distance.value)
            put("name", name.value)
            put("mode", modeSelected.value.toString())
        }

    override fun fromJSON(data: String): Trigger {
        val d = jsonOf(data)
        latitude.value = d.lenientDouble("latitude")
        longitude.value = d.lenientDouble("longitude")
        distance.value = d.lenientDouble("distance")
        name.value = d.lenientStringOrNull("name")!!
        modeSelected.value = InputLocationMode.Mode.valueOf(d.lenientStringOrNull("mode")!!)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT) lastMode = InputLocationMode.Mode.OUTSIDE
        return this
    }

    override fun friendlyName(): TextRef = AutomationStrings.location

    override fun friendlyDescription(): String =
        rh.gs(AutomationStrings.locationis, rh.gs(modeSelected.value.stringRes), " " + name.value)

    override fun composeIcon() = Icons.Filled.LocationOn
    override fun elementType() = ElementType.AAPS

    override fun duplicate(): Trigger = TriggerLocation(deps, this)

    // Method to return the actual mode based on the current distance
    fun currentMode(currentDistance: Double): InputLocationMode.Mode {
        return if (currentDistance <= distance.value) InputLocationMode.Mode.INSIDE else InputLocationMode.Mode.OUTSIDE
    }
}
