package info.nightscout.androidaps.plugins.general.automation.elements;

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

    public InputTempTarget() {
        super();
        setUnits(ProfileFunctions.getInstance().getProfileUnits());
        if (getUnits().equals(Constants.MMOL))
            value = Constants.MIN_TT_MMOL;
        else
            value = Constants.MIN_TT_MGDL;
    }

    public InputTempTarget(InputTempTarget another) {
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
