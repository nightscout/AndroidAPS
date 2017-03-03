package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;

public class NewExtendedBolusDialog extends DialogFragment implements View.OnClickListener {

    Button okButton;
    EditText insulinEdit;
    RadioButton h05Radio;
    RadioButton h10Radio;
    RadioButton h20Radio;
    RadioButton h30Radio;
    RadioButton h40Radio;

    PlusMinusEditText editInsulin;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public NewExtendedBolusDialog() {
        mHandlerThread = new HandlerThread(NewExtendedBolusDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.overview_extendedbolus_button));

        View view = inflater.inflate(R.layout.overview_newextendedbolus_dialog, container, false);
        okButton = (Button) view.findViewById(R.id.overview_newextendedbolus_okbutton);
        insulinEdit = (EditText) view.findViewById(R.id.overview_newextendedbolus_insulin);
        h05Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_05h);
        h10Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_1h);
        h20Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_2h);
        h30Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_3h);
        h40Radio = (RadioButton) view.findViewById(R.id.overview_newextendedbolus_4h);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        editInsulin = new PlusMinusEditText(view, R.id.overview_newextendedbolus_insulin, R.id.overview_newextendedbolus_insulin_plus, R.id.overview_newextendedbolus_insulin_minus, 0d, 0d, maxInsulin, 0.1d, new DecimalFormat("0.00"), false);

        okButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
                    if (insulinAfterConstraint - insulin != 0d)
                        confirmMessage += "\n" + getString(R.string.constraintapllied);
                    insulin = insulinAfterConstraint;

                    final Double finalInsulin = insulin;
                    final int finalDurationInMinutes = durationInMinutes;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(context.getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            final PumpInterface pump = MainApp.getConfigBuilder();
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult result = pump.setExtendedBolus(finalInsulin, finalDurationInMinutes);
                                    if (!result.success) {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setTitle(context.getString(R.string.treatmentdeliveryerror));
                                        builder.setMessage(result.comment);
                                        builder.setPositiveButton(context.getString(R.string.ok), null);
                                        builder.show();
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("ExtendedBolus"));
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
