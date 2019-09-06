package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.location.Location;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.InputButton;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDouble;
import info.nightscout.androidaps.plugins.general.automation.elements.InputString;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.services.LocationService;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerLocation extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    InputDouble latitude = new InputDouble(0d, -90d, +90d, 0.000001d, new DecimalFormat("0.000000"));
    InputDouble longitude = new InputDouble(0d, -180d, +180d, 0.000001d, new DecimalFormat("0.000000"));
    InputDouble distance = new InputDouble(200d, 0, 100000, 10d, new DecimalFormat("0"));
    InputString name = new InputString();

    private Runnable buttonAction = () -> {
        Location location = LocationService.getLastLocation();
        if (location != null) {
            latitude.setValue(location.getLatitude());
            longitude.setValue(location.getLongitude());
            log.debug(String.format("Grabbed location: %f %f", latitude.getValue(), longitude.getValue()));
        }
    };

    public TriggerLocation() {
        super();
    }

    private TriggerLocation(TriggerLocation triggerLocation) {
        super();
        latitude = new InputDouble(triggerLocation.latitude);
        longitude = new InputDouble(triggerLocation.longitude);
        distance = new InputDouble(triggerLocation.distance);
        lastRun = triggerLocation.lastRun;
        name = triggerLocation.name;
    }

    @Override
    public synchronized boolean shouldRun() {
        Location location = LocationService.getLastLocation();
        if (location == null)
            return false;

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        Location a = new Location("Trigger");
        a.setLatitude(latitude.getValue());
        a.setLongitude(longitude.getValue());
        double calculatedDistance = location.distanceTo(a);

        if (calculatedDistance < distance.getValue()) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription());
            return true;
        }
        return false;
    }

    @Override
    public synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerLocation.class.getName());
            JSONObject data = new JSONObject();
            data.put("latitude", latitude.getValue());
            data.put("longitude", longitude.getValue());
            data.put("distance", distance.getValue());
            data.put("name", name.getValue());
            data.put("lastRun", lastRun);
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            latitude.setValue(JsonHelper.safeGetDouble(d, "latitude"));
            longitude.setValue(JsonHelper.safeGetDouble(d, "longitude"));
            distance.setValue(JsonHelper.safeGetDouble(d, "distance"));
            name.setValue(JsonHelper.safeGetString(d, "name"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.location;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.locationis, name.getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.ic_location_on);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerLocation(this);
    }


    TriggerLocation setLatitude(double value) {
        latitude.setValue(value);
        return this;
    }

    TriggerLocation setLongitude(double value) {
        longitude.setValue(value);
        return this;
    }

    TriggerLocation setdistance(double value) {
        distance.setValue(value);
        return this;
    }

    TriggerLocation lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.location))
                .add(new LabelWithElement(MainApp.gs(R.string.name_short), "", name))
                .add(new LabelWithElement(MainApp.gs(R.string.latitude_short), "", latitude))
                .add(new LabelWithElement(MainApp.gs(R.string.longitude_short), "", longitude))
                .add(new LabelWithElement(MainApp.gs(R.string.distance_short), "", distance))
                .add(new InputButton(MainApp.gs(R.string.currentlocation), buttonAction), LocationService.getLastLocation() != null)
                .build(root);
    }
}
