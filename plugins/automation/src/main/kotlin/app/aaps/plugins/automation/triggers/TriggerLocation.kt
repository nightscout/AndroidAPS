package app.aaps.plugins.automation.triggers

import android.location.Location
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.compose.IconTint
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.InputLocationMode
import app.aaps.plugins.automation.elements.InputString
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerLocation(injector: HasAndroidInjector) : Trigger(injector) {

    var latitude = InputDouble(0.0, -90.0, +90.0, 0.000001, DecimalFormat("0.000000"))
    var longitude = InputDouble(0.0, -180.0, +180.0, 0.000001, DecimalFormat("0.000000"))
    var distance = InputDouble(200.0, 0.0, 100000.0, 10.0, DecimalFormat("0"))
    var modeSelected = InputLocationMode(rh)
    var name: InputString = InputString()

    var lastMode = InputLocationMode.Mode.INSIDE

    private constructor(injector: HasAndroidInjector, triggerLocation: TriggerLocation) : this(injector) {
        latitude = InputDouble(triggerLocation.latitude)
        longitude = InputDouble(triggerLocation.longitude)
        distance = InputDouble(triggerLocation.distance)
        modeSelected = InputLocationMode(rh, triggerLocation.modeSelected.value)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT)
            lastMode = InputLocationMode.Mode.OUTSIDE
        name = triggerLocation.name
    }

    override suspend fun shouldRun(): Boolean {
        val location: Location = locationDataContainer.lastLocation ?: return false
        val a = Location("Trigger")
        a.latitude = latitude.value
        a.longitude = longitude.value
        val calculatedDistance = location.distanceTo(a).toDouble()
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

    override fun dataJSON(): JSONObject =
        JSONObject()
            .put("latitude", latitude.value)
            .put("longitude", longitude.value)
            .put("distance", distance.value)
            .put("name", name.value)
            .put("mode", modeSelected.value)

    override fun fromJSON(data: String): Trigger {
        val d = JSONObject(data)
        latitude.value = JsonHelper.safeGetDouble(d, "latitude")
        longitude.value = JsonHelper.safeGetDouble(d, "longitude")
        distance.value = JsonHelper.safeGetDouble(d, "distance")
        name.value = JsonHelper.safeGetString(d, "name")!!
        modeSelected.value = InputLocationMode.Mode.valueOf(JsonHelper.safeGetString(d, "mode")!!)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT) lastMode = InputLocationMode.Mode.OUTSIDE
        return this
    }

    override fun friendlyName(): Int = R.string.location

    override fun friendlyDescription(): String =
        rh.gs(R.string.locationis, rh.gs(modeSelected.value.stringRes), " " + name.value)

    override fun composeIcon() = Icons.Filled.LocationOn
    override fun composeIconTint() = IconTint.Location

    override fun duplicate(): Trigger = TriggerLocation(injector, this)

    // Method to return the actual mode based on the current distance
    fun currentMode(currentDistance: Double): InputLocationMode.Mode {
        return if (currentDistance <= distance.value) InputLocationMode.Mode.INSIDE else InputLocationMode.Mode.OUTSIDE
    }
}
