package info.nightscout.androidaps.plugins.general.automation.triggers;


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
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDouble;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public class TriggerAutosensValue extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    private final int minValue = (int) (SP.getDouble("openapsama_autosens_min", 0.7d) * 100);
    private final int maxValue = (int) (SP.getDouble("openapsama_autosens_max", 1.2d) * 100);
    private final double step = 1;
    private DecimalFormat decimalFormat = new DecimalFormat("1");
    private InputDouble value = new InputDouble(100, (double) minValue, (double) maxValue, step, decimalFormat);
    private Comparator comparator = new Comparator();

    public TriggerAutosensValue() {
        super();
    }

    private TriggerAutosensValue(TriggerAutosensValue triggerAutosensValue) {
        super();
        value = new InputDouble(triggerAutosensValue.value);
        lastRun = triggerAutosensValue.lastRun;
        comparator = new Comparator(triggerAutosensValue.comparator);
    }

    public double getValue() {
        return value.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {
        AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensData("Automation trigger");
        if (autosensData == null)
            if (comparator.getValue() == Comparator.Compare.IS_NOT_AVAILABLE)
                return true;
            else
                return false;

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        boolean doRun = comparator.getValue().check((autosensData.autosensResult.ratio), getValue() / 100d);
        if (doRun) {
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
            o.put("type", TriggerAutosensValue.class.getName());
            JSONObject data = new JSONObject();
            data.put("value", getValue());
            data.put("lastRun", lastRun);
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
            value.setValue(JsonHelper.safeGetDouble(d, "value"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.autosenslabel;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.autosenscompared, MainApp.gs(comparator.getValue().getStringRes()), getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.as);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerAutosensValue(this);
    }

    TriggerAutosensValue setValue(int requestedValue) {
        this.value.setValue(requestedValue);
        return this;
    }

    TriggerAutosensValue lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerAutosensValue comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.autosenslabel))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.autosenslabel) + ": ", "", value))
                .build(root);
    }
}
