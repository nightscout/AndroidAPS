package info.nightscout.androidaps.plugins.general.automation.elements;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.utils.NumberPicker;

public class InputBg extends Element {
    static final int MMOL_MIN = 3;
    static final int MMOL_MAX = 20;
    static final int MGDL_MIN = 54;
    static final int MGDL_MAX = 360;

    private String units = Constants.MGDL;
    private double value;
    double minValue;
    private double maxValue;
    private double step;
    private DecimalFormat decimalFormat;

    public InputBg() {
        super();
        setUnits(ProfileFunctions.getInstance().getProfileUnits());
        if (getUnits().equals(Constants.MMOL))
            value = MMOL_MIN;
        else
            value = MGDL_MIN;
    }

    public InputBg(InputBg another) {
        super();
        value = another.getValue();
        setUnits(another.getUnits());
    }


    @Override
    public void addToLayout(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        numberPicker.setParams(value, minValue, maxValue, step, decimalFormat, true, null, null);
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
            minValue = MMOL_MIN;
            maxValue = MMOL_MAX;
            step = 0.1;
            decimalFormat = new DecimalFormat("0.0");
        } else {
            // mg/dL
            minValue = MGDL_MIN;
            maxValue = MGDL_MAX;
            step = 1;
            decimalFormat = new DecimalFormat("0");
        }

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
