package info.nightscout.androidaps.setupwizard.elements;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.utils.SP;

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
    public void generateDialog(LinearLayout layout) {
        Context context = layout.getContext();

        TextView pdesc = new TextView(context);
        pdesc.setText(getComment());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 40);
        pdesc.setLayoutParams(params);
        layout.addView(pdesc);

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
            save(values()[i], 0);
        });
        layout.addView(radioGroup);

        super.generateDialog(layout);
    }

    public SWRadioButton preferenceId(int preferenceId) {
        this.preferenceId = preferenceId;
        return this;
    }

}
