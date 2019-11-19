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
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;

public class TriggerCOB extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    private final int minValue = 0;
    private final int maxValue = SP.getInt(R.string.key_treatmentssafety_maxcarbs, 48);
    private InputDouble value = new InputDouble(0, (double) minValue, (double) maxValue, 1, new DecimalFormat("1"));
    private Comparator comparator = new Comparator();

    public TriggerCOB() {
        super();
    }

    private TriggerCOB(TriggerCOB triggerCOB) {
        super();
        value = new InputDouble(triggerCOB.value);
        lastRun = triggerCOB.lastRun;
        comparator = new Comparator(triggerCOB.comparator);
    }

    public double getValue() {
        return value.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {
        CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "AutomationTriggerCOB");
        if (cobInfo == null)
            if (comparator.getValue() == Comparator.Compare.IS_NOT_AVAILABLE)
                return true;
            else
                return false;

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        boolean doRun = comparator.getValue().check((cobInfo.displayCob), getValue());
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
            o.put("type", TriggerCOB.class.getName());
            JSONObject data = new JSONObject();
            data.put("carbs", getValue());
            data.put("lastRun", lastRun);
            data.put("comparator", comparator.getValue().toString());
            o.put("data", data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            value.setValue(JsonHelper.safeGetDouble(d, "carbs"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            log.error("Unhandled exception", e);
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.triggercoblabel;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.cobcompared, MainApp.gs(comparator.getValue().getStringRes()), getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_cp_bolus_carbs);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerCOB(this);
    }

    TriggerCOB setValue(int requestedValue) {
        this.value.setValue(requestedValue);
        return this;
    }

    TriggerCOB lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerCOB comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.triggercoblabel))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.triggercoblabel) + ": ", "", value))
                .build(root);
    }
}
