package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.widget.LinearLayout;

import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.elements.Comparator;
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration;
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement;
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder;
import info.nightscout.androidaps.plugins.general.automation.elements.StaticLabel;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.JsonHelper;
import info.nightscout.androidaps.utils.T;

public class TriggerBolusAgo extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);
    private InputDuration minutesAgo = new InputDuration(0, InputDuration.TimeUnit.MINUTES);
    private Comparator comparator = new Comparator();

    public TriggerBolusAgo() {
        super();
    }

    private TriggerBolusAgo(TriggerBolusAgo triggerBolusAgo) {
        super();
        minutesAgo = new InputDuration(triggerBolusAgo.minutesAgo);
        lastRun = triggerBolusAgo.lastRun;
        comparator = new Comparator(triggerBolusAgo.comparator);
    }

    public double getValue() {
        return minutesAgo.getValue();
    }

    public Comparator getComparator() {
        return comparator;
    }

    @Override
    public synchronized boolean shouldRun() {

        if (lastRun > DateUtil.now() - T.mins(5).msecs())
            return false;

        long lastBolusTime = TreatmentsPlugin.getPlugin().getLastBolusTime(false);

        if (lastBolusTime == 0)
            if (comparator.getValue() == Comparator.Compare.IS_NOT_AVAILABLE)
                return true;
            else
                return false;

        double minutesAgo = (double) (DateUtil.now() - lastBolusTime) / (60 * 1000);
        if (L.isEnabled(L.AUTOMATION))
            log.debug("LastBolus min ago: " + minutesAgo);

        boolean doRun = comparator.getValue().check((minutesAgo), getValue());
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
            o.put("type", TriggerBolusAgo.class.getName());
            JSONObject data = new JSONObject();
            data.put("minutesAgo", getValue());
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
            minutesAgo.setMinutes(JsonHelper.safeGetInt(d, "minutesAgo"));
            lastRun = JsonHelper.safeGetLong(d, "lastRun");
            comparator.setValue(Comparator.Compare.valueOf(JsonHelper.safeGetString(d, "comparator")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.lastboluslabel;
    }

    @Override
    public String friendlyDescription() {
        return MainApp.gs(R.string.lastboluscompared, MainApp.gs(comparator.getValue().getStringRes()), (int) getValue());
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.of(R.drawable.icon_bolus);
    }

    @Override
    public Trigger duplicate() {
        return new TriggerBolusAgo(this);
    }

    TriggerBolusAgo setValue(int requestedValue) {
        this.minutesAgo.setMinutes(requestedValue);
        return this;
    }

    TriggerBolusAgo lastRun(long lastRun) {
        this.lastRun = lastRun;
        return this;
    }

    TriggerBolusAgo comparator(Comparator.Compare compare) {
        this.comparator = new Comparator().setValue(compare);
        return this;
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        new LayoutBuilder()
                .add(new StaticLabel(R.string.lastboluslabel))
                .add(comparator)
                .add(new LabelWithElement(MainApp.gs(R.string.lastboluslabel) + ": ", "", minutesAgo))
                .build(root);
    }
}
