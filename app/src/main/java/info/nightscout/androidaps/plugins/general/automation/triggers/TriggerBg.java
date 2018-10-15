package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.utils.JsonHelper;
import info.nightscout.utils.NumberPicker;

public class TriggerBg extends Trigger {

    protected double threshold;
    protected Comparator comparator = Comparator.IS_EQUAL;
    protected String units = ProfileFunctions.getInstance().getProfileUnits();

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            // TODO: validate inputs
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    @Override
    public synchronized boolean shouldRun() {
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();

        if (glucoseStatus == null && comparator.equals(Comparator.IS_NOT_AVAILABLE))
            return true;
        if (glucoseStatus == null)
            return false;

        return comparator.check(glucoseStatus.glucose, Profile.toMgdl(threshold, units));
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerBg.class.getName());
            JSONObject data = new JSONObject();
            data.put("threshold", threshold);
            data.put("comparator", comparator.toString());
            data.put("units", units);
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
            threshold = JsonHelper.safeGetDouble(d, "threshold");
            comparator = Comparator.valueOf(JsonHelper.safeGetString(d, "comparator"));
            units = JsonHelper.safeGetString(d, "units");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return R.string.glucose;
    }

    @Override
    public String friendlyDescription() {
        if (comparator.equals(Comparator.IS_NOT_AVAILABLE))
            return MainApp.gs(R.string.glucoseisnotavailable);
        else
            return MainApp.gs(R.string.glucosecompared, comparator.getStringRes(), threshold, units);
    }

    TriggerBg threshold(double threshold) {
        this.threshold = threshold;
        return this;
    }

    TriggerBg comparator(Comparator comparator) {
        this.comparator = comparator;
        return this;
    }

    TriggerBg units(String units) {
        this.units = units;
        return this;
    }

    @Override
    public View createView(Context context) {
        LinearLayout root = (LinearLayout) super.createView(context);

        // spinner for comparator
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, Comparator.labels());
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        spinnerParams.setMargins(0, MainApp.dpToPx(4), 0, MainApp.dpToPx(4));
        spinner.setLayoutParams(spinnerParams);
        root.addView(spinner);

        // horizontal layout
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout);

        // input filed for threshold
        NumberPicker numberPicker = new NumberPicker(context, null);
        numberPicker.setParams(0d, 0d, (double) 500, 1d, new DecimalFormat("0"), false, textWatcher);
        numberPicker.setValue(100.0);
        layout.addView(numberPicker);

        // text view for unit
        TextView tvUnits = new TextView(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        params.setMargins(MainApp.dpToPx(6), 0, 0, 0);
        tvUnits.setLayoutParams(params);
        tvUnits.setText(units);
        tvUnits.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(tvUnits);

        return root;
    }
}
