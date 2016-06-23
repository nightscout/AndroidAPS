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

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.utils.SafeParse;

public class NewTempBasalDialog extends DialogFragment implements View.OnClickListener {

    Button okButton;
    EditText basalEdit;
    RadioButton percentRadio;
    RadioButton absoluteRadio;
    RadioButton h05Radio;
    RadioButton h10Radio;
    RadioButton h20Radio;
    RadioButton h30Radio;
    RadioButton h40Radio;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_newtempbasal_fragment, container, false);
        okButton = (Button) view.findViewById(R.id.overview_newtempbasal_okbutton);
        basalEdit = (EditText) view.findViewById(R.id.overview_newtempbasal_basal);
        percentRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_percent);
        absoluteRadio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_absolute);
        h05Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_05h);
        h10Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_1h);
        h20Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_2h);
        h30Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_3h);
        h40Radio = (RadioButton) view.findViewById(R.id.overview_newtempbasal_4h);

        okButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.overview_newtempbasal_okbutton:
                try {
                    int basalPercent = 100;
                    Double basal = SafeParse.stringToDouble(basalEdit.getText().toString());
                    final boolean setAsPercent = percentRadio.isChecked();
                    int durationInMinutes = 30;
                    if (h10Radio.isChecked()) durationInMinutes = 60;
                    if (h20Radio.isChecked()) durationInMinutes = 120;
                    if (h30Radio.isChecked()) durationInMinutes = 180;
                    if (h40Radio.isChecked()) durationInMinutes = 240;

                    String confirmMessage = getString(R.string.setbasalquestion);
                    if (setAsPercent) {
                        basalPercent = MainApp.getConfigBuilder().applyBasalConstraints(basal.intValue());
                        confirmMessage += "\n " + basalPercent + "% ";
                        confirmMessage += getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (basalPercent != basal.intValue())
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                    } else {
                        Double basalAfterConstraint = MainApp.getConfigBuilder().applyBasalConstraints(basal);
                        confirmMessage += "\n " + basalAfterConstraint + " U/h ";
                        confirmMessage += getString(R.string.duration) + " " + durationInMinutes + "min ?";
                        if (basalAfterConstraint != basal)
                            confirmMessage += "\n" + getString(R.string.constraintapllied);
                        basal = basalAfterConstraint;
                    }

                    final int finalBasalPercent = basalPercent;
                    final Double finalBasal = basal;
                    final int finalDurationInMinutes = durationInMinutes;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                            Result result;
                            if (setAsPercent) {
                                result = pump.setTempBasalPercent(finalBasalPercent, finalDurationInMinutes);
                            } else {
                                result = pump.setTempBasalAbsolute(finalBasal, finalDurationInMinutes);
                            }
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
