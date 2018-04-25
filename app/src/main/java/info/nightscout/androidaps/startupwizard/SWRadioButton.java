package info.nightscout.androidaps.startupwizard;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class SWRadioButton extends SWItem {

    int labelsArray;
    int valuesArray;


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

/*        textlabel.setGravity(Gravity.START);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(10, 0, 0, 0); // llp.setMargins(left, top, right, bottom);
        textlabel.setLayoutParams(llp);
        textlabel.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.linearBlockBackground));
        TextViewCompat.setTextAppearance(textlabel, android.R.style.TextAppearance_Medium);*/

        RadioGroup rg = new RadioGroup(context);
        for (int row = 0; row < 1; row++) {

            rg.setOrientation(LinearLayout.VERTICAL);
            rg.setVisibility(View.VISIBLE);

            for (int i = 0; i < labels.length; i++) {
                RadioButton rdbtn = new RadioButton(context);
                rdbtn.setId((row * 2) + i);
                rdbtn.setText(labels[i]);
                rg.addView(rdbtn);
            }
        }
        layout.addView(rg);

    }


}
