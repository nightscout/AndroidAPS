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
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.utils.PlusMinusEditText;
import info.nightscout.utils.SafeParse;

public class FillDialog extends DialogFragment implements OnClickListener {

    Button deliverButton;
    TextView insulin;

    PlusMinusEditText editInsulin;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public FillDialog() {
        mHandlerThread = new HandlerThread(FillDialog.class.getSimpleName());
        mHandlerThread.start();
        this.mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.actions_fill_dialog, null, false);

        deliverButton = (Button) view.findViewById(R.id.treatments_newtreatment_deliverbutton);

        deliverButton.setOnClickListener(this);
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        insulin = (TextView) view.findViewById(R.id.treatments_newtreatment_insulinamount);
        Double maxInsulin = MainApp.getConfigBuilder().applyBolusConstraints(Constants.bolusOnlyForCheckLimit);

        editInsulin = new PlusMinusEditText(view, R.id.treatments_newtreatment_insulinamount, R.id.treatments_newtreatment_insulinamount_plus, R.id.treatments_newtreatment_insulinamount_minus, 0d, 0d, maxInsulin, 0.05d, new DecimalFormat("0.00"), false);

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
            case R.id.treatments_newtreatment_deliverbutton:

                try {
                    Double insulin = SafeParse.stringToDouble(this.insulin.getText().toString());

                    String confirmMessage = getString(R.string.fillwarning) + "\n";

                    Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(insulin);
                    confirmMessage += getString(R.string.bolus) + ": " + insulinAfterConstraints + "U";
                    if (insulinAfterConstraints - insulin != 0)
                        confirmMessage += "\n" + getString(R.string.constraintapllied);

                    final Double finalInsulinAfterConstraints = insulinAfterConstraints;

                    final Context context = getContext();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);

                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.primefill), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0) {
                                final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        PumpEnactResult result = pump.deliverTreatment(finalInsulinAfterConstraints, 0, context, false);
                                        if (!result.success) {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                            builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                            builder.setMessage(result.comment);
                                            builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                            builder.show();
                                        }
                                    }
                                });
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