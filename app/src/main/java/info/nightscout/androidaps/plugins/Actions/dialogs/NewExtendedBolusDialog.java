package info.nightscout.androidaps.plugins.Actions.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.NumberPicker;
import info.nightscout.utils.SafeParse;

public class NewExtendedBolusDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewExtendedBolusDialog.class);

    NumberPicker editInsulin;
    NumberPicker editDuration;

    public NewExtendedBolusDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(getString(R.string.overview_extendedbolus_button));

        View view = inflater.inflate(R.layout.overview_newextendedbolus_dialog, container, false);

        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);
        editInsulin = (NumberPicker) view.findViewById(R.id.overview_newextendedbolus_insulin);
        editInsulin.setParams(0d, 0d, maxInsulin, 0.1d, new DecimalFormat("0.00"), false);

        double extendedDurationStep = ConfigBuilderPlugin.getActivePump().getPumpDescription().extendedBolusDurationStep;
        double extendedMaxDuration = ConfigBuilderPlugin.getActivePump().getPumpDescription().extendedBolusMaxDuration;
        editDuration = (NumberPicker) view.findViewById(R.id.overview_newextendedbolus_duration);
        editDuration.setParams(extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, new DecimalFormat("0"), false);

        view.findViewById(R.id.ok).setOnClickListener(this);
        view.findViewById(R.id.cancel).setOnClickListener(this);

        setCancelable(true);
        getDialog().setCanceledOnTouchOutside(false);
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                try {
                    Double insulin = SafeParse.stringToDouble(editInsulin.getText());
                    int durationInMinutes = SafeParse.stringToInt(editDuration.getText());

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
                            ConfigBuilderPlugin.getCommandQueue().extendedBolus(finalInsulin, finalDurationInMinutes, new Callback() {
                                @Override
                                public void run() {
                                    if (!result.success) {
                                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                        i.putExtra("soundid", R.raw.boluserror);
                                        i.putExtra("status", result.comment);
                                        i.putExtra("title", MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        MainApp.instance().startActivity(i);
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
                    log.error("Unhandled exception", e);
                }
                break;
            case R.id.cancel:
                dismiss();
                break;
        }
    }

}
