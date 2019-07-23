package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.NumberPicker;

public class InputBg extends Element {

    final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {
            if (units.equals(Constants.MMOL)) {
                value = Math.max(value, 4d);
                value = Math.min(value, 15d);
            } else {
                value = Math.max(value, 72d);
                value = Math.min(value, 270d);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private String units = Constants.MGDL;
    private double value;
    double minValue;
    private double maxValue;
    private double step;
    private DecimalFormat decimalFormat;

    public InputBg() {
        super();
        setUnits(ProfileFunctions.getInstance().getProfileUnits());
    }

    public InputBg(InputBg another) {
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

    public InputBg setUnits(String units) {
        // set default initial value
        if (units.equals(Constants.MMOL)) {
            // mmol
            minValue = 2;
            maxValue = 30;
            step = 0.1;
            decimalFormat = new DecimalFormat("0.0");
        } else {
            // mg/dL
            minValue = 40;
            maxValue = 540;
            step = 1;
            decimalFormat = new DecimalFormat("0");
        }

        // make sure that value is in range
        textWatcher.afterTextChanged(null);

        this.units = units;
        return this;
    }

    public InputBg setValue(double value) {
        this.value = value;
        return this;
    }

    public double getValue() {
        return value;
    }

}
