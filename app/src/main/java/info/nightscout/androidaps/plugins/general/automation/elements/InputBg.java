package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.utils.NumberPicker;

public class InputBg extends Element {
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

    private String units;
    private double value;
    private final double minValue, maxValue, step;
    private final DecimalFormat decimalFormat;

    public InputBg(String units) {
        this.units = units;

        // set default initial value
        if (units.equals(Constants.MMOL)) {
            // mmol
            value = 5.5;
            minValue = 2;
            maxValue = 30;
            step = 0.1;
            decimalFormat = new DecimalFormat("0.0");
        } else {
            // mg/dL
            value = 100;
            minValue = 36;
            maxValue = 540;
            step = 1;
            decimalFormat = new DecimalFormat("0");
        }
    }

    @Override
    public void generateDialog(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(0d, minValue, maxValue, step, decimalFormat, false, textWatcher);
        numberPicker.setValue(value);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        if (!this.units.equals(units)) {
            String previousUnits = this.units;
            this.units = units;
            value = Profile.toUnits(Profile.toMgdl(value, previousUnits), Profile.toMmol(value, previousUnits), units);
        }
    }

    public double getValue() {
        return value;
    }

    public int getMgdl() {
        return (int)Profile.toMgdl(value, units);
    }

    public void setMgdl(int value) {
        this.value = Profile.fromMgdlToUnits(value, units);
    }
}
