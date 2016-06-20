package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Result;

public class NewTreatmentDialog extends DialogFragment implements OnClickListener {

    Button deliverButton;
    TextView insulin;
    TextView carbs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newtreatment_fragment, null, false);

        deliverButton = (Button) view.findViewById(R.id.treatments_newtreatment_deliverbutton);

        deliverButton.setOnClickListener(this);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        insulin = (TextView) view.findViewById(R.id.treatments_newtreatment_insulinamount);
        carbs = (TextView) view.findViewById(R.id.treatments_newtreatment_carbsamount);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_newtreatment_deliverbutton:
                SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
                Double maxbolus = Double.parseDouble(SP.getString("treatmentssafety_maxbolus", "3"));
                Double maxcarbs = Double.parseDouble(SP.getString("treatmentssafety_maxcarbs", "48"));

                try {
                    String insulinText = this.insulin.getText().toString().replace(",", ".");
                    String carbsText = this.carbs.getText().toString().replace(",", ".");
                    Double insulin = Double.parseDouble(!insulinText.equals("") ? insulinText : "0");
                    Double carbs = Double.parseDouble(!carbsText.equals("") ? carbsText : "0");
                    if (insulin > maxbolus) {
                        this.insulin.setText("");
                    } else if (carbs > maxcarbs) {
                        this.carbs.setText("");
                    } else if (insulin > 0d || carbs > 0d) {
                        dismiss();
                        Result result = MainActivity.getConfigBuilder().getActivePump().deliverTreatment(insulin, carbs);
                        if (!result.success) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                            builder.setTitle(this.getContext().getString(R.string.treatmentdeliveryerror));
                            builder.setMessage(result.comment);
                            builder.setPositiveButton(this.getContext().getString(R.string.ok), null);
                            builder.show();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }

    }

}