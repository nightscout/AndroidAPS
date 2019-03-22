package info.nightscout.androidaps.plugins.treatments.dialogs;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;

import org.json.JSONObject;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.JsonHelper;

public class WizardInfoDialog extends DialogFragment implements OnClickListener {
    JSONObject json;

    public WizardInfoDialog() {
        super();
    }

    public void setData(JSONObject json) {
        this.json = json;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.treatments_wizardinfo_dialog, container, false);

        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        view.findViewById(R.id.ok).setOnClickListener(this);

        // BG
        ((TextView) view.findViewById(R.id.treatments_wizard_bg)).setText(DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "bg")) + " ISF: " + DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "isf")));
        ((TextView) view.findViewById(R.id.treatments_wizard_bginsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulinbg")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_bgcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "insulinbgused"));
        ((CheckBox) view.findViewById(R.id.treatments_wizard_ttcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "ttused"));
        // Trend
        ((TextView) view.findViewById(R.id.treatments_wizard_bgtrend)).setText(JsonHelper.safeGetString(json, "trend"));
        ((TextView) view.findViewById(R.id.treatments_wizard_bgtrendinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulintrend")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_bgtrendcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "trendused"));
        // COB
        ((TextView) view.findViewById(R.id.treatments_wizard_cob)).setText(DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "cob")) + "g IC: " + DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "ic")));
        ((TextView) view.findViewById(R.id.treatments_wizard_cobinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulincob")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_cobcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "cobused"));
        // Bolus IOB
        ((TextView) view.findViewById(R.id.treatments_wizard_bolusiobinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "bolusiob")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_bolusiobcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "bolusiobused"));
        // Basal IOB
        ((TextView) view.findViewById(R.id.treatments_wizard_basaliobinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "basaliob")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_basaliobcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "basaliobused"));
        // Superbolus
        ((TextView) view.findViewById(R.id.treatments_wizard_sbinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulinsuperbolus")) + "U");
        ((CheckBox) view.findViewById(R.id.treatments_wizard_sbcheckbox)).setChecked(JsonHelper.safeGetBoolean(json, "superbolusused"));
        // Carbs
        ((TextView) view.findViewById(R.id.treatments_wizard_carbs)).setText(DecimalFormatter.to0Decimal(JsonHelper.safeGetDouble(json, "carbs")) + "g IC: " + DecimalFormatter.to1Decimal(JsonHelper.safeGetDouble(json, "ic")));
        ((TextView) view.findViewById(R.id.treatments_wizard_carbsinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulincarbs")) + "U");
        // Correction
        ((TextView) view.findViewById(R.id.treatments_wizard_correctioninsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "othercorrection")) + "U");
        // Profile
        ((TextView) view.findViewById(R.id.treatments_wizard_profile)).setText(JsonHelper.safeGetString(json, "profile"));
        // Notes
        ((TextView) view.findViewById(R.id.treatments_wizard_notes)).setText(JsonHelper.safeGetString(json, "notes"));
        // Total
        ((TextView) view.findViewById(R.id.treatments_wizard_totalinsulin)).setText(DecimalFormatter.to2Decimal(JsonHelper.safeGetDouble(json, "insulin")) + "U");

        setCancelable(true);
        return view;
    }

    @Override
    public synchronized void onClick(View view) {
        switch (view.getId()) {
            case R.id.ok:
                dismiss();
                break;
        }
    }

}
