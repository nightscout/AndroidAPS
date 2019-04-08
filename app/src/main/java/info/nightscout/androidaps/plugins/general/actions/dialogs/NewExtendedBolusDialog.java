package info.nightscout.androidaps.plugins.general.actions.dialogs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.overview.dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.NumberPicker;
import info.nightscout.androidaps.utils.SafeParse;

public class NewExtendedBolusDialog extends DialogFragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(NewExtendedBolusDialog.class);

    NumberPicker editInsulin;
    NumberPicker editDuration;

    public NewExtendedBolusDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().setTitle(MainApp.gs(R.string.overview_extendedbolus_button));

        View view = inflater.inflate(R.layout.overview_newextendedbolus_dialog, container, false);

        Double maxInsulin = MainApp.getConstraintChecker().getMaxExtendedBolusAllowed().value();
        editInsulin = (NumberPicker) view.findViewById(R.id.overview_newextendedbolus_insulin);
        editInsulin.setParams(0d, 0d, maxInsulin, 0.1d, new DecimalFormat("0.00"), false);

        double extendedDurationStep = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().extendedBolusDurationStep;
        double extendedMaxDuration = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().extendedBolusMaxDuration;
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

                    String confirmMessage = MainApp.gs(R.string.setextendedbolusquestion);

                    Double insulinAfterConstraint = MainApp.getConstraintChecker().applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
                    confirmMessage += " " + insulinAfterConstraint + " U  ";
                    confirmMessage += MainApp.gs(R.string.duration) + " " + durationInMinutes + "min ?";
                    if (Math.abs(insulinAfterConstraint - insulin) > 0.01d)
                        confirmMessage += "\n" + MainApp.gs(R.string.constraintapllied);
                    insulin = insulinAfterConstraint;

                    final Double finalInsulin = insulin;
                    final int finalDurationInMinutes = durationInMinutes;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(MainApp.gs(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(MainApp.gs(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ConfigBuilderPlugin.getPlugin().getCommandQueue().extendedBolus(finalInsulin, finalDurationInMinutes, new Callback() {
                                @Override
                                public void run() {
                                    if (!result.success) {
                                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                        i.putExtra("soundid", R.raw.boluserror);
                                        i.putExtra("status", result.comment);
                                        i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror));
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        MainApp.instance().startActivity(i);
                                    }
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
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
