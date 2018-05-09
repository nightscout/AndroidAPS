package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.CheckBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.utils.SP;

/**
 * Created by Rumen Georgiev on 5/9/2018.
 */

public class SWCheckbox extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWCheckbox.class);

    int labelsArray;
    int valuesArray;
    String label = "";
    int preferenceID;
    private CheckBox checkBox;

    public SWCheckbox() {
        super(Type.CHECKBOX);
    }

    public SWCheckbox option(String label, int preferenceID) {
        this.label = label;
        this.preferenceID = preferenceID;
        return this;
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();
        // Get if there is already value in SP
        Boolean previousValue;
        previousValue = SP.getBoolean(preferenceId, false);
        checkBox = new CheckBox(context);
        checkBox.setText(label);
        checkBox.setChecked(previousValue);
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                save(checkBox.isChecked());
            }
        });
        layout.addView(checkBox);
        super.generateDialog(view, layout);
    }
    public void save(boolean value){
        SP.putBoolean(preferenceID, value);
    }
}
