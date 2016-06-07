package info.nightscout.androidaps.plugins.Treatments.Dialogs;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

public class NewTreatmentDialogFragment extends DialogFragment implements OnClickListener {

    Button deliverButton;
    Communicator communicator;
    TextView insulin;
    TextView carbs;

    @Override
    public void onAttach(Activity activity) {

        super.onAttach(activity);

        if (activity instanceof Communicator) {
            communicator = (Communicator) getActivity();

        } else {
            throw new ClassCastException(activity.toString()
                    + " must implemenet NewTreatmentDialogFragment.Communicator");
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_newtreatment_fragment, null, false);

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
                Double maxbolus = Double.parseDouble(SP.getString("safety_maxbolus", "3"));
                Double maxcarbs = Double.parseDouble(SP.getString("safety_maxcarbs", "48"));


                String insulinText = this.insulin.getText().toString();
                String carbsText = this.carbs.getText().toString();
                Double insulin = Double.parseDouble(!insulinText.equals("") ? this.insulin.getText().toString() : "0");
                Double carbs = Double.parseDouble(!carbsText.equals("") ? this.carbs.getText().toString() : "0");
                if (insulin > maxbolus) {
                    this.insulin.setText("");
                } else if (carbs > maxcarbs) {
                    this.carbs.setText("");
                } else if (insulin > 0d || carbs > 0d) {
                    dismiss();
                    communicator.treatmentDeliverRequest(insulin, carbs);
                }
                break;
        }

    }

    public interface Communicator {
        void treatmentDeliverRequest(Double insulin, Double carbs);
    }

}