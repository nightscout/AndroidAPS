package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.utils.SP;

public class SWRadioButton extends SWItem {

    private static Logger log = LoggerFactory.getLogger(SWRadioButton.class);
    int labelsArray;
    int valuesArray;
    private RadioGroup radioGroup;
    public boolean somethingChecked = false;

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
    public void generateDialog(View view, LinearLayout layout){
        Context context = view.getContext();
//        LinearLayout layout = (LinearLayout) view.findViewById(view.getId());
//        layout.removeAllViews();
        String[] labels = context.getResources().getStringArray(labelsArray);
        String[] values = context.getResources().getStringArray(valuesArray);
        // Get if there is already value in SP
        String previousValue = SP.getString(preferenceId, "unset");
//        log.debug("Value for "+view.getContext().getString(preferenceId)+" is "+previousValue);
        radioGroup = new RadioGroup(context);
        radioGroup.clearCheck();

        for (int row = 0; row < 1; row++) {

            radioGroup.setOrientation(LinearLayout.VERTICAL);
            radioGroup.setVisibility(View.VISIBLE);

            for (int i = 0; i < labels.length; i++) {
                RadioButton rdbtn = new RadioButton(context);
                rdbtn.setId((row * 2) + i);
                rdbtn.setText(labels[i]);
                if(previousValue.equals(values[i]))
                    rdbtn.setChecked(true);
                radioGroup.addView(rdbtn);
            }
        }

        radioGroup.setOnCheckedChangeListener(new  RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                save();
            }

        });
        layout.addView(radioGroup);

    }

    public RadioGroup getRadioGroup(){
        return this.radioGroup;
    }

    public String getCheckedValue(){
        if(radioGroup != null && radioGroup.getCheckedRadioButtonId() > -1){
            Context context = radioGroup.getRootView().getContext();
            String[] values = context.getResources().getStringArray(valuesArray);
            return values[radioGroup.getCheckedRadioButtonId()];
        } else {
            return "none";
        }
    }

    public boolean isSomethingChecked(){
        return this.somethingChecked;
    }

    public void save(){
        if(!getCheckedValue().equals("none")) {
            SP.putString(preferenceId, getCheckedValue());
            MainApp.bus().post(new EventPreferenceChange(preferenceId));
        }
    }

    public String preferenceSet(){
        return SP.getString(preferenceId, "none");
    }


}
