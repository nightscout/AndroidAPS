package info.nightscout.androidaps.plugins.general.automation.triggers

import android.location.Location
import android.widget.LinearLayout
import com.google.common.base.Optional
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.automation.elements.*
import info.nightscout.androidaps.utils.JsonHelper
import org.json.JSONObject
import java.text.DecimalFormat

class TriggerLocation(injector: HasAndroidInjector) : Trigger(injector) {
    var latitude = InputDouble(injector, 0.0, -90.0, +90.0, 0.000001, DecimalFormat("0.000000"))
    var longitude = InputDouble(injector, 0.0, -180.0, +180.0, 0.000001, DecimalFormat("0.000000"))
    var distance = InputDouble(injector, 200.0, 0.0, 100000.0, 10.0, DecimalFormat("0"))
    var modeSelected = InputLocationMode(injector)
    var name: InputString = InputString(injector)

    var lastMode = InputLocationMode.Mode.INSIDE
    private val buttonAction = Runnable {
        locationDataContainer.lastLocation?.let {
            latitude.setValue(it.latitude)
            longitude.setValue(it.longitude)
            aapsLogger.debug(LTag.AUTOMATION, String.format("Grabbed location: %f %f", latitude.value, longitude.value))
        }
    }

    private constructor(injector: HasAndroidInjector, triggerLocation: TriggerLocation) : this(injector) {
        latitude = InputDouble(injector, triggerLocation.latitude)
        longitude = InputDouble(injector, triggerLocation.longitude)
        distance = InputDouble(injector, triggerLocation.distance)
        modeSelected = InputLocationMode(injector, triggerLocation.modeSelected.value)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT)
            lastMode = InputLocationMode.Mode.OUTSIDE
        name = triggerLocation.name
    }

    @Synchronized override fun shouldRun(): Boolean {
        val location: Location = locationDataContainer.lastLocation ?: return false
        val a = Location("Trigger")
        a.latitude = latitude.value
        a.longitude = longitude.value
        val calculatedDistance = location.distanceTo(a).toDouble()
        if (modeSelected.value == InputLocationMode.Mode.INSIDE && calculatedDistance <= distance.value ||
            modeSelected.value == InputLocationMode.Mode.OUTSIDE && calculatedDistance > distance.value ||
            modeSelected.value == InputLocationMode.Mode.GOING_IN && calculatedDistance <= distance.value && lastMode == InputLocationMode.Mode.OUTSIDE ||
            modeSelected.value == InputLocationMode.Mode.GOING_OUT && calculatedDistance > distance.value && lastMode == InputLocationMode.Mode.INSIDE) {
            aapsLogger.debug(LTag.AUTOMATION, "Ready for execution: " + friendlyDescription())
            lastMode = currentMode(calculatedDistance)
            return true
        }
        lastMode = currentMode(calculatedDistance) // current mode will be last mode for the next check
        aapsLogger.debug(LTag.AUTOMATION, "NOT ready for execution: " + friendlyDescription())
        return false
    }

    override fun toJSON(): String {
        val data = JSONObject()
            .put("latitude", latitude.value)
            .put("longitude", longitude.value)
            .put("distance", distance.value)
            .put("name", name.value)
            .put("mode", modeSelected.value)
        return JSONObject()
            .put("type", this::class.java.name)
            .put("data", data)
            .toString()
    }

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
        resourceHelper.gs(R.string.locationis, resourceHelper.gs(modeSelected.value.stringRes), " " + name.value)

    override fun icon(): Optional<Int?> = Optional.of(R.drawable.ic_location_on)

    override fun duplicate(): Trigger = TriggerLocation(injector, this)

    override fun generateDialog(root: LinearLayout) {
        LayoutBuilder()
            .add(StaticLabel(injector, R.string.location, this))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.name_short), "", name))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.latitude_short), "", latitude))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.longitude_short), "", longitude))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.distance_short), "", distance))
            .add(LabelWithElement(injector, resourceHelper.gs(R.string.location_mode), "", modeSelected))
            .add(InputButton(injector, resourceHelper.gs(R.string.currentlocation), buttonAction), locationDataContainer.lastLocation != null)
            .build(root)
    }

    // Method to return the actual mode based on the current distance
    fun currentMode(currentDistance: Double): InputLocationMode.Mode {
        return if (currentDistance <= distance.value) InputLocationMode.Mode.INSIDE else InputLocationMode.Mode.OUTSIDE
    }
}