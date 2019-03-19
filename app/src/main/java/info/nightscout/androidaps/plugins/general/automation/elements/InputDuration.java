package info.nightscout.androidaps.plugins.general.automation.elements;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import java.text.DecimalFormat;

import info.nightscout.utils.NumberPicker;

public class InputDuration extends Element {
    public enum TimeUnit {
        MINUTES,
        HOURS
    }

    private TimeUnit unit;
    private double value;

    public InputDuration(double value, TimeUnit unit) {
        this.unit = unit;
        this.value = value;
    }


    @Override
    public void generateDialog(LinearLayout root) {
        NumberPicker numberPicker = new NumberPicker(root.getContext(), null);
        if (unit.equals(TimeUnit.MINUTES)) {
            // Minutes
            numberPicker.setParams(0d, 0d, 24 * 60d, 10d, new DecimalFormat("0"), false);
        } else {
            // Hours
            numberPicker.setParams(0d, 0d, 24d, 1d, new DecimalFormat("0"), false);
        }
        numberPicker.setValue(value);
        numberPicker.setOnValueChangedListener(value -> this.value = value);
        root.addView(numberPicker);
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public double getValue() {
        return value;
    }

    public int getMinutes() {
        if (unit.equals(TimeUnit.MINUTES)) {
            return (int)value;
        } else {
            return (int)(value * 60d);
        }
    }
}
