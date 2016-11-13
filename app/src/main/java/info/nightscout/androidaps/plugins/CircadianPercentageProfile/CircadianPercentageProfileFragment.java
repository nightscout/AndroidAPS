package info.nightscout.androidaps.plugins.CircadianPercentageProfile;


import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.FragmentBase;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SafeParse;
import info.nightscout.utils.ToastUtils;

public class CircadianPercentageProfileFragment extends Fragment implements FragmentBase {
    private static Logger log = LoggerFactory.getLogger(CircadianPercentageProfileFragment.class);

    private static CircadianPercentageProfilePlugin circadianPercentageProfilePlugin = new CircadianPercentageProfilePlugin();

    public static CircadianPercentageProfilePlugin getPlugin() {
        return circadianPercentageProfilePlugin;
    }

    EditText diaView;
    RadioButton mgdlView;
    RadioButton mmolView;
    EditText carView;
    EditText targetlowView;
    EditText targethighView;
    EditText percentageView;
    EditText timeshiftView;
    TextView profileView;
    TextView baseprofileIC;
    TextView baseprofileBasal;
    TextView baseprofileISF;
    Button profileswitchButton;
    ImageView percentageIcon;
    ImageView timeIcon;
    ImageView basaleditIcon;
    ImageView iceditIcon;
    ImageView isfeditIcon;




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.circadianpercentageprofile_fragment, container, false);
        diaView = (EditText) layout.findViewById(R.id.simpleprofile_dia);
        mgdlView = (RadioButton) layout.findViewById(R.id.simpleprofile_mgdl);
        mmolView = (RadioButton) layout.findViewById(R.id.simpleprofile_mmol);
        carView = (EditText) layout.findViewById(R.id.simpleprofile_car);
        targetlowView = (EditText) layout.findViewById(R.id.simpleprofile_targetlow);
        targethighView = (EditText) layout.findViewById(R.id.simpleprofile_targethigh);
        percentageView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_percentage);
        timeshiftView = (EditText) layout.findViewById(R.id.circadianpercentageprofile_timeshift);
        profileView = (TextView) layout.findViewById(R.id.circadianpercentageprofile_profileview);
        baseprofileBasal = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofilebasal);
        baseprofileIC = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofileic);
        baseprofileISF = (TextView) layout.findViewById(R.id.circadianpercentageprofile_baseprofileisf);
        percentageIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_percentageicon);
        timeIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_timeicon);
        profileswitchButton = (Button) layout.findViewById(R.id.circadianpercentageprofile_profileswitch);

        basaleditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_basaledit);
        iceditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_icedit);
        isfeditIcon = (ImageView) layout.findViewById(R.id.circadianpercentageprofile_isfedit);



        mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
        mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
        diaView.setText(circadianPercentageProfilePlugin.dia.toString());
        carView.setText(circadianPercentageProfilePlugin.car.toString());
        targetlowView.setText(circadianPercentageProfilePlugin.targetLow.toString());
        targethighView.setText(circadianPercentageProfilePlugin.targetHigh.toString());
        percentageView.setText("" + circadianPercentageProfilePlugin.percentage);
        timeshiftView.setText("" + circadianPercentageProfilePlugin.timeshift);
        updateProfileInfo();

        mgdlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mgdl = mgdlView.isChecked();
                circadianPercentageProfilePlugin.mmol = !circadianPercentageProfilePlugin.mgdl;
                mmolView.setChecked(circadianPercentageProfilePlugin.mmol);
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        });
        mmolView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                circadianPercentageProfilePlugin.mmol = mmolView.isChecked();
                circadianPercentageProfilePlugin.mgdl = !circadianPercentageProfilePlugin.mmol;
                mgdlView.setChecked(circadianPercentageProfilePlugin.mgdl);
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        });

        profileswitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, false, false, false, true, false);
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
            }
        });

        timeshiftView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if(b)
                    ToastUtils.showToastInUiThread(getContext(), "Time in hours by which the profile will be shifted round robin.");

            }
        });

        percentageView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if(b)
                    ToastUtils.showToastInUiThread(getContext(), "Percentage factor by which the base profile will be multiplied.");
            }
        });

        timeIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                timeshiftView.requestFocusFromTouch();
                timeshiftView.setSelection(timeshiftView.getText().length());
                ((InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(timeshiftView, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        percentageIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                percentageView.requestFocusFromTouch();
                percentageView.setSelection(percentageView.getText().length());
                ((InputMethodManager) getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(percentageView, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        basaleditIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BasalEditDialog basalEditDialog = new BasalEditDialog();
                basalEditDialog.show(getFragmentManager(), "Edit Basal");
            }
        });


        TextWatcher textWatch = new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {

                if(SafeParse.stringToInt(percentageView.getText().toString()) == 0) {
                    circadianPercentageProfilePlugin.percentage = 100;
                } else {
                    circadianPercentageProfilePlugin.percentage = SafeParse.stringToInt(percentageView.getText().toString());
                }
                circadianPercentageProfilePlugin.dia = SafeParse.stringToDouble(diaView.getText().toString());
                circadianPercentageProfilePlugin.car = SafeParse.stringToDouble(carView.getText().toString());
                circadianPercentageProfilePlugin.targetLow = SafeParse.stringToDouble(targetlowView.getText().toString());
                circadianPercentageProfilePlugin.targetHigh = SafeParse.stringToDouble(targethighView.getText().toString());
                circadianPercentageProfilePlugin.timeshift = SafeParse.stringToInt(timeshiftView.getText().toString());
                circadianPercentageProfilePlugin.storeSettings();
                updateProfileInfo();
            }
        };

        diaView.addTextChangedListener(textWatch);
        carView.addTextChangedListener(textWatch);
        targetlowView.addTextChangedListener(textWatch);
        targethighView.addTextChangedListener(textWatch);
        percentageView.addTextChangedListener(textWatch);
        timeshiftView.addTextChangedListener(textWatch);

        return layout;
    }

    private void updateProfileInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h3>");
        sb.append("Active Profile:");
        sb.append("</h3>");
        sb.append("<h4>Basal:</h4> " + circadianPercentageProfilePlugin.basalString());
        sb.append("<h4>IC:</h4> " + circadianPercentageProfilePlugin.icString());
        sb.append("<h4>ISF:</h4> " + circadianPercentageProfilePlugin.isfString());
        profileView.setText(Html.fromHtml(sb.toString()));

        baseprofileBasal.setText(Html.fromHtml("<h3>Base Profile:</h3><h4>Basal: </h4>" + circadianPercentageProfilePlugin.baseBasalString()));
        baseprofileIC.setText(Html.fromHtml("<h4>IC: </h4>" + circadianPercentageProfilePlugin.baseIcString()));
        baseprofileISF.setText(Html.fromHtml("<h4>ISF: </h4>" + circadianPercentageProfilePlugin.baseIsfString()));
    }

    private class BasalEditDialog extends DialogFragment{

        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            getDialog().setTitle("Edit Base-Basal rates: ");
            View view = inflater.inflate(R.layout.circadianpercentageprofile_editbasal_dialog, container, false);
            LinearLayout list = (LinearLayout) view.findViewById(R.id.circadianpp_editbasal_listlayout);
            for (int i = 0; i < 24; i++) {
                View childview = inflater.inflate(R.layout.circadianpercentageprofile_listelement, container, false);
                ((TextView)childview.findViewById(R.id.basal_time_elem)).setText((i<10?"0":"") + i + ":00: ");

                if(i==0){
                    (childview.findViewById(R.id.basal_copyprev_elem)).setVisibility(View.INVISIBLE);;
                } else {
                    //TODO: Add listener
                }

                //TODO: safe EditTexts in array for prev buttonaction!
                ((TextView)childview.findViewById(R.id.basal_edittext_elem)).setText(DecimalFormatter.to2Decimal(getPlugin().basebasal[i]));

                list.addView(childview);
            }
            getDialog().setCancelable(true);
            return view;
        }





}

}
