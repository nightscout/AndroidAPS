package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.utils.SafeParse;

public class NewExtendedBolusDialog extends DialogFragment implements View.OnClickListener {

    Button okButton;
    EditText insulinEdit;
    RadioButton h05Radio;
    RadioButton h10Radio;
    RadioButton h20Radio;
    RadioButton h30Radio;
    RadioButton h40Radio;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newextendedbolus_fragment, container, false);
        okButton = (Button) view.findViewById(R.id.overview_newextendedbolus_okbutton);
        insulinEdit = (EditText) view.findViewById(R.id.overview_newextendedbolus_insulin);
        h05Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_05h);
        h10Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_1h);
        h20Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_2h);
        h30Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_3h);
        h40Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_4h);

        okButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_newextendedbolus_okbutton:
                try {
                    Double insulin = SafeParse.stringToDouble(insulinEdit.getText().toString());
                    int durationInMinutes = 30;
                    if (h10Radio.isChecked()) durationInMinutes = 60;
                    if (h20Radio.isChecked()) durationInMinutes = 120;
                    if (h30Radio.isChecked()) durationInMinutes = 180;
                    if (h40Radio.isChecked()) durationInMinutes = 240;

                    String confirmMessage = getString(R.string.setextendedbolusquestion);

                    Double insulinAfterConstraint = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
                    confirmMessage += " " + insulinAfterConstraint + " U  ";
                    confirmMessage += getString(R.string.duration) + " " + durationInMinutes + "min ?";
                    if (insulinAfterConstraint != insulin)
                        confirmMessage += "\n" + getString(R.string.constraintapllied);
                    insulin = insulinAfterConstraint;

                    final Double finalInsulin = insulin;
                    final int finalDurationInMinutes = durationInMinutes;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                            PumpEnactResult result = pump.setExtendedBolus(finalInsulin, finalDurationInMinutes);
                            if (!result.success) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                                builder.setMessage(result.comment);
                                builder.setPositiveButton(getContext().getString(R.string.ok), null);
                                builder.show();
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();

                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
    }
}
