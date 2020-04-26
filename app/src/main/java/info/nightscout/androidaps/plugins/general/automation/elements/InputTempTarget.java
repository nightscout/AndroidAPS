package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.NumberPicker;

public class InputTempTarget extends Element {

    private String units = Constants.MGDL;
    private double value;
    double minValue;
    private double maxValue;
    private double step;
    private DecimalFormat decimalFormat;

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            value = Math.max(minValue, value);
            value = Math.min(maxValue, value);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    public InputTempTarget() {
        super();
        setUnits(ProfileFunctions.getSystemUnits());
        if (getUnits().equals(Constants.MMOL))
            value = 6;
        else
            value = 110;
    }

    public InputTempTarget(InputTempTarget another) {
        super();
        value = another.getValue();
        setUnits(another.getUnits());
    }


    @Override
    public void addToLayout(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, null, textWatcher);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public String getUnits() {
        return units;
    }

    public InputTempTarget setUnits(String units) {
        // set default initial value
        if (units.equals(Constants.MMOL)) {
            // mmol
            minValue = Constants.MIN_TT_MMOL;
            maxValue = Constants.MAX_TT_MMOL;
            step = 0.1;
            decimalFormat = new DecimalFormat("0.0");
        } else {
            // mg/dL
            minValue = Constants.MIN_TT_MGDL;
            maxValue = Constants.MAX_TT_MGDL;
            step = 1;
            decimalFormat = new DecimalFormat("0");
        }

        this.units = units;
        return this;
    }

    public InputTempTarget setValue(double value) {
        this.value = value;
        return this;
    }

    public double getValue() {
        return value;
    }

}
