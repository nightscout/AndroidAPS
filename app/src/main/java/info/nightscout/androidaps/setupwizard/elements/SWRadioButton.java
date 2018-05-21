package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.utils.SP;

public class SWRadioButton extends SWItem {
    private static Logger log = LoggerFactory.getLogger(SWRadioButton.class);

    int labelsArray;
    int valuesArray;
    private RadioGroup radioGroup;

    public SWRadioButton() {
        super(Type.RADIOBUTTON);
    }

    public SWRadioButton option(int labels, int values) {
        this.labelsArray = labels;
        this.valuesArray = values;
        return this;
    }

    public String[] labels() {
        return MainApp.sResources.getStringArray(labelsArray);
    }

    public String[] values() {
        return MainApp.sResources.getStringArray(valuesArray);
    }

    @Override
    public void generateDialog(View view, LinearLayout layout) {
        Context context = view.getContext();
        // Get if there is already value in SP
        String previousValue = SP.getString(preferenceId, "none");
        radioGroup = new RadioGroup(context);
        radioGroup.clearCheck();
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setVisibility(View.VISIBLE);

        for (int i = 0; i < labels().length; i++) {
            RadioButton rdbtn = new RadioButton(context);
            rdbtn.setId(View.generateViewId());
            rdbtn.setText(labels()[i]);
            if (previousValue.equals(values()[i]))
                rdbtn.setChecked(true);
            rdbtn.setTag(i);
            radioGroup.addView(rdbtn);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int i = (int) group.findViewById(checkedId).getTag();
            save(values()[i]);
        });
        layout.addView(radioGroup);
        super.generateDialog(view, layout);
    }

    public SWRadioButton preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

}
