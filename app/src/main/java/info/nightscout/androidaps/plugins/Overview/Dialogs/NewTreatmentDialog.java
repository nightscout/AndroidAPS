package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.interfaces.PumpInterface;

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

                try {
                    String insulinText = this.insulin.getText().toString().replace(",", ".");
                    String carbsText = this.carbs.getText().toString().replace(",", ".");
                    Double insulin = Double.parseDouble(!insulinText.equals("") ? insulinText : "0");
                    Integer carbs = Integer.parseInt(!carbsText.equals("") ? carbsText : "0");

                    String confirmMessage = getString(R.string.entertreatmentquestion);

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
                    Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(carbs);

                    confirmMessage += getString(R.string.bolus) + ": " + insulinAfterConstraints + "U";
                    confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";
                    if (insulinAfterConstraints != insulin || carbsAfterConstraints != carbs)
                        confirmMessage += "\n" + getString(R.string.constraintapllied);

                    final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final Integer finalCarbsAfterConstraints = carbsAfterConstraints;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                                Result result = pump.deliverTreatment(finalInsulinAfterConstraints, finalCarbsAfterConstraints);
                                if (!result.success) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                    builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                                    builder.setMessage(result.comment);
                                    builder.setPositiveButton(getContext().getString(R.string.ok), null);
                                    builder.show();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }

    }

}