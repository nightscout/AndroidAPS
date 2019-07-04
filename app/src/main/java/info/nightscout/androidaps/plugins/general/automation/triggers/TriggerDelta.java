package info.nightscout.androidaps.plugins.general.automation.triggers;


import android.widget.LinearLayout;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDelta.DeltaType;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerDelta extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    private double minValue = 0d;
    private double maxValue = 1d;
    private double step = 1;
    private DecimalFormat decimalFormat = new DecimalFormat("1");
    private String units;
    private DeltaType deltaType;

    private InputDelta value = new InputDelta( (double) minValue,(double) minValue, (double) maxValue, step, decimalFormat, deltaType);
    private Comparator comparator = new Comparator();

    public TriggerDelta() {
        super();
        this.units = ProfileFunctions.getInstance().getProfileUnits();
        initializer();
    }

    private TriggerDelta(TriggerDelta triggerDelta) {
        super();
        this.units = ProfileFunctions.getInstance().getProfileUnits();
        initializer();
        value = triggerDelta.value;
        lastRun = triggerDelta.lastRun;
    }

    public double getValue() {
        deltaType = value.getDeltaType();
        return value.getValue();
    }

    public DeltaType getType() {
        return deltaType;
    }

    public String getUnits() {
        return this.units;
    }

    public Comparator getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        if (glucoseStatus == null)
            if (comparator.getValue() == Comparator.Compare.IS_NOT_AVAILABLE)
                return true;
            else
                return false;

        // Setting type of delta
        double delta;

        if (deltaType == DeltaType.SHORT_AVERAGE)
            delta = glucoseStatus.short_avgdelta;
        else if (deltaType == DeltaType.LONG_AVERAGE)
            delta = glucoseStatus.long_avgdelta;
        else
            delta = glucoseStatus.delta;

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        boolean doRun = comparator.getValue().check(delta, Profile.toMgdl(value.getValue(), this.units));
        if (doRun) {
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: delta is " + delta + " " + friendlyDescription());
            return true;
        }
        return false;
    }

    @Override
    public synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerDelta.class.getName());
            JSONObject data = new JSONObject();
            data.put("value", getValue());
            data.put("units", units);
            data.put("lastRun", lastRun);
            data.put("deltaType", getType());
            data.put("comparator", comparator.getValue().toString());
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
            units = JsonHelper.safeGetString(d, "units");
            deltaType = DeltaType.valueOf(JsonHelper.safeGetString(d, "deltaType", ""));
            value.setValue(JsonHelper.safeGetDouble(d, "value"), deltaType);
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.deltalabel;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.deltacompared, MainApp.gs(comparator.getValue().getStringRes()), getValue(), MainApp.gs(deltaType.getStringRes()));
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_auto_delta); 
    }

    @Override
    public Trigger duplicate() {
        return new TriggerDelta(this);
    }

    TriggerDelta setValue(double requestedValue, DeltaType requestedType) {
        this.value.setValue(requestedValue, requestedType);
        this.deltaType = requestedType;
        return this;
    }

    TriggerDelta setUnits(String units) {
        this.units = units;
        return this;
    }

    void initializer(){
        if (this.units.equals(Constants.MMOL)) {
            this.maxValue = 4d;
            this.minValue = -4d;
            this.step = 0.1d;
            this.decimalFormat = new DecimalFormat("0.1");
            this.deltaType = DeltaType.DELTA;
        } else {
            this.maxValue = 72d;
            this.minValue = -72d;
            this.step = 1d;
            this.deltaType = DeltaType.DELTA;
        }
        value = new InputDelta( (double) minValue,(double) minValue, (double) maxValue, step, decimalFormat, deltaType);
    }


    TriggerDelta lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerDelta comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.deltalabel))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.deltalabel) + ": ", "", value))
                .build(root);
    }

}