package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.setupwizard.SWNumberValidator;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;


public class SWEditNumberWithUnits extends SWItem {

    private SWNumberValidator validator = new SWNumberValidator() {
        @Override
        public boolean isValid(double value) {
            return value >= min && value <= max;
        }
    };
    private int updateDelay = 0;
    private double init, min, max;

    public SWEditNumberWithUnits(double defaultMMOL, double minMMOL, double maxMMOL) {
        super(Type.UNITNUMBER);
        init = defaultMMOL;
        min = minMMOL;
        max = maxMMOL;
    }

    @Override
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (validator != null && validator.isValid(SafeParse.stringToDouble(s.toString())))
                    save(s.toString(), updateDelay);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        TextView l = new TextView(context);
        l.setId(View.generateViewId());
        l.setText(label);
        l.setTypeface(l.getTypeface(), Typeface.BOLD);
        layout.addView(l);

        double initValue = SP.getDouble(preferenceId, init);
        initValue = Profile.toCurrentUnits(initValue);

        NumberPicker numberPicker = new NumberPicker(context);
        if (ProfileFunctions.getSystemUnits().equals(Constants.MMOL))
            numberPicker.setParams(initValue, min, max, 0.1d, new DecimalFormat("0.0"), false, null, watcher);
        else
            numberPicker.setParams(initValue, min * 18, max * 18, 1d, new DecimalFormat("0"), false, null, watcher);

//        LinearLayout.LayoutParams ll = (LinearLayout.LayoutParams) numberPicker.getLayoutParams();
//        ll.gravity = Gravity.CENTER;
//        numberPicker.setLayoutParams(ll);
        layout.addView(numberPicker);

        TextView c = new TextView(context);
        c.setId(View.generateViewId());
        c.setText(comment);
        c.setTypeface(c.getTypeface(), Typeface.ITALIC);
        layout.addView(c);

        super.generateDialog(layout);
    }

    public SWEditNumberWithUnits preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

    public SWEditNumberWithUnits updateDelay(int updateDelay) {
        this.updateDelay = updateDelay;
        return this;
    }
}
