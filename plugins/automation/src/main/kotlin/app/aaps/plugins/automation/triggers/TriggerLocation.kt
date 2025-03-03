package app.aaps.plugins.automation.triggers

import android.content.Context
import android.location.Location
import android.widget.LinearLayout
import app.aaps.core.data.firebase.RemoteConfigKeys
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputButton
import app.aaps.plugins.automation.elements.InputDouble
import app.aaps.plugins.automation.elements.InputLocationMode
import app.aaps.plugins.automation.elements.InputString
import app.aaps.plugins.automation.elements.LabelWithElement
import app.aaps.plugins.automation.elements.LayoutBuilder
import app.aaps.plugins.automation.elements.StaticLabel
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.rtchagas.pingplacepicker.PingPlacePicker
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Optional

class TriggerLocation(injector: HasAndroidInjector) : Trigger(injector), PingPlacePicker.OnPlaceSelectedListener {

   var context: Context? = null

    var latitude = InputDouble(0.0, -90.0, +90.0, 0.000001, DecimalFormat("0.000000"))
    var longitude = InputDouble(0.0, -180.0, +180.0, 0.000001, DecimalFormat("0.000000"))
    var distance = InputDouble(200.0, 0.0, 100000.0, 10.0, DecimalFormat("0"))
    var modeSelected = InputLocationMode(rh)
    var name: InputString = InputString()

    var lastMode = InputLocationMode.Mode.INSIDE
    private val buttonAction = Runnable {
        locationDataContainer.lastLocation?.let {
            latitude.setValue(it.latitude)
            longitude.setValue(it.longitude)
        }
    }
    private val mapAction = Runnable {
        val activity = scanForActivity(context) ?: return@Runnable

        val builder = PingPlacePicker.Builder()
        builder
            .setAndroidApiKey(rh.gs(R.string.key_google_apis_android))
            .setMapsApiKey(Firebase.remoteConfig.getString(RemoteConfigKeys.KEY_MAPS_API))
            .setOnPlaceSelectedListener(this)

        // Set a initial location.
        if (latitude.value != 0.0 && longitude.value != 0.0)
            builder.setLatLng(LatLng(latitude.value, longitude.value))

        try {
            val placeIntent = builder.build(activity)
            activity.startActivity(placeIntent)
        } catch (_: Exception) {
            ToastUtils.errorToast(activity, "Google Play Services is not Available")
        }
    }

    private constructor(injector: HasAndroidInjector, triggerLocation: TriggerLocation) : this(injector) {
        latitude = InputDouble(triggerLocation.latitude)
        longitude = InputDouble(triggerLocation.longitude)
        distance = InputDouble(triggerLocation.distance)
        modeSelected = InputLocationMode(rh, triggerLocation.modeSelected.value)
        if (modeSelected.value == InputLocationMode.Mode.GOING_OUT)
            lastMode = InputLocationMode.Mode.OUTSIDE
        name = triggerLocation.name
    }

    // PingPlacePicker
    override fun onPlaceSelected(place: Place, latLng: LatLng) {
        latitude.setValue(latLng.latitude)
        longitude.setValue(latLng.longitude)
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

    override fun icon(): Optional<Int> = Optional.of(R.drawable.ic_location_on)

    override fun duplicate(): Trigger = TriggerLocation(injector, this)

    override fun generateDialog(root: LinearLayout) {
        context = root.context
        LayoutBuilder()
            .add(StaticLabel(rh, R.string.location, this))
            .add(LabelWithElement(rh, rh.gs(app.aaps.core.ui.R.string.name_short), "", name))
            .maybeAdd(InputButton(rh.gs(R.string.currentlocation), buttonAction), locationDataContainer.lastLocation != null)
            .add(InputButton(rh.gs(R.string.choose_from_map), mapAction))
            .add(LabelWithElement(rh, rh.gs(R.string.distance_short), "", distance))
            .add(LabelWithElement(rh, rh.gs(R.string.location_mode), "", modeSelected))
            .build(root)
    }

    // Method to return the actual mode based on the current distance
    fun currentMode(currentDistance: Double): InputLocationMode.Mode {
        return if (currentDistance <= distance.value) InputLocationMode.Mode.INSIDE else InputLocationMode.Mode.OUTSIDE
    }
}