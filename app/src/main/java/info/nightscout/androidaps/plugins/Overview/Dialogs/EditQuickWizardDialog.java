package info.nightscout.androidaps.plugins.Overview.Dialogs;


import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.QuickWizard;
import info.nightscout.androidaps.plugins.Overview.events.EventQuickWizardChange;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SafeParse;

public class EditQuickWizardDialog extends DialogFragment implements View.OnClickListener {

    QuickWizard.QuickWizardEntry entry = new QuickWizard().newEmptyItem();
    QuickWizard quickWizard = MainApp.getSpecificPlugin(OverviewPlugin.class).quickWizard;

    EditText buttonEdit;
    EditText carbsEdit;
    Spinner fromSpinner;
    Spinner toSpinner;
    Button okButton;

    public EditQuickWizardDialog() {
    }

    public void setData(QuickWizard.QuickWizardEntry data) {
        entry = data;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        View view = inflater.inflate(R.layout.overview_editquickwizard_dialog, container, false);
        buttonEdit = (EditText) view.findViewById(R.id.overview_editquickwizard_button_edit);
        carbsEdit = (EditText) view.findViewById(R.id.overview_editquickwizard_carbs_edit);
        fromSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_from_spinner);
        toSpinner = (Spinner) view.findViewById(R.id.overview_editquickwizard_to_spinner);
        okButton = (Button) view.findViewById(R.id.overview_editquickwizard_ok_button);
        okButton.setOnClickListener(this);

        int posFrom = 0;
        int posTo = 95;
        ArrayList<CharSequence> timeList = new ArrayList<>();
        int pos = 0;
        for (int t = 0; t < 24 * 60 * 60; t += 15 * 60) {
            timeList.add(DateUtil.timeString(DateUtil.toDate(t)));
            if (entry.validFrom() == t) posFrom = pos;
            if (entry.validTo() == t) posTo = pos;
            pos++;
        }
        timeList.add(DateUtil.timeString(DateUtil.toDate(24 * 60 * 60 - 60)));

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(),
                R.layout.spinner_centered, timeList);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);

        buttonEdit.setText(entry.buttonText());
        carbsEdit.setText(entry.carbs().toString());
        fromSpinner.setSelection(posFrom);
        toSpinner.setSelection(posTo);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getDialog() != null)
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.overview_editquickwizard_ok_button:
                if (fromSpinner.getSelectedItem() == null) return;
                if (toSpinner.getSelectedItem() == null) return;
                try {
                    entry.storage.put("buttonText", buttonEdit.getText().toString());
                    entry.storage.put("carbs", SafeParse.stringToInt(carbsEdit.getText().toString()));
                    int validFromInt = DateUtil.toSeconds(fromSpinner.getSelectedItem().toString());
                    entry.storage.put("validFrom", validFromInt);
                    int validToInt = DateUtil.toSeconds(toSpinner.getSelectedItem().toString());
                    entry.storage.put("validTo", validToInt);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                quickWizard.addOrUpdate(entry);
                dismiss();
                MainApp.bus().post(new EventQuickWizardChange());
            break;
        }
    }
}
