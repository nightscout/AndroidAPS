package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;

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
    public void generateDialog(View view){
        Context context = view.getContext();
        LinearLayout layout = (LinearLayout) view.findViewById(view.getId());
        layout.removeAllViews();
        String[] labels = context.getResources().getStringArray(labelsArray);
        String[] values = context.getResources().getStringArray(valuesArray);


        radioGroup = new RadioGroup(context);
        radioGroup.clearCheck();

        for (int row = 0; row < 1; row++) {

            radioGroup.setOrientation(LinearLayout.VERTICAL);
            radioGroup.setVisibility(View.VISIBLE);

            for (int i = 0; i < labels.length; i++) {
                RadioButton rdbtn = new RadioButton(context);
                rdbtn.setId((row * 2) + i);
                rdbtn.setText(labels[i]);
//                log.debug("Button ["+labels[i]+"]="+rdbtn.getId()+" value is "+values[rdbtn.getId()]);
                radioGroup.addView(rdbtn);
            }
        }


        layout.addView(radioGroup);

    }

    public RadioGroup getRadioGroup(){
        return this.radioGroup;
    }

    // returns the id of the group
    public int getGroupId(){
        return radioGroup.getId();
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

    // return true if we have something checked
    public boolean isValid(){
        if(getCheckedValue().equals("none"))
            return false;
        else
            return true;
    }

}
