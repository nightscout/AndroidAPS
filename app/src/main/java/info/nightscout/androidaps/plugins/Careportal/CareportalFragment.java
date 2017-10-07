package info.nightscout.androidaps.plugins.Careportal;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Common.SubscriberFragment;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;

public class CareportalFragment extends SubscriberFragment implements View.OnClickListener {

    static CareportalPlugin careportalPlugin;

    TextView iage;
    TextView cage;
    TextView sage;
    TextView pbage;

    View statsLayout;
    LinearLayout butonsLayout;
    View noProfileView;

    static public CareportalPlugin getPlugin() {
        if (careportalPlugin == null) {
            careportalPlugin = new CareportalPlugin();
        }
        return careportalPlugin;
    }

    //                                                    bg,insulin,carbs,prebolus,duration,percent,absolute,profile,split,temptarget
    public static final OptionsToShow bgcheck = new OptionsToShow(R.id.careportal_bgcheck, R.string.careportal_bgcheck, true, true, true, false, false, false, false, false, false, false);
    public static final OptionsToShow snackbolus = new OptionsToShow(R.id.careportal_snackbolus, R.string.careportal_snackbolus, true, true, true, true, false, false, false, false, false, false);
    public static final OptionsToShow mealbolus = new OptionsToShow(R.id.careportal_mealbolus, R.string.careportal_mealbolus, true, true, true, true, false, false, false, false, false, false);
    public static final OptionsToShow correctionbolus = new OptionsToShow(R.id.careportal_correctionbolus, R.string.careportal_correctionbolus, true, true, true, true, false, false, false, false, false, false);
    public static final OptionsToShow carbcorrection = new OptionsToShow(R.id.careportal_carbscorrection, R.string.careportal_carbscorrection, true, false, true, false, false, false, false, false, false, false);
    public static final OptionsToShow combobolus = new OptionsToShow(R.id.careportal_combobolus, R.string.careportal_combobolus, true, true, true, true, true, false, false, false, true, false);
    public static final OptionsToShow announcement = new OptionsToShow(R.id.careportal_announcement, R.string.careportal_announcement, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow note = new OptionsToShow(R.id.careportal_note, R.string.careportal_note, true, false, false, false, true, false, false, false, false, false);
    public static final OptionsToShow question = new OptionsToShow(R.id.careportal_question, R.string.careportal_question, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow exercise = new OptionsToShow(R.id.careportal_exercise, R.string.careportal_exercise, false, false, false, false, true, false, false, false, false, false);
    public static final OptionsToShow sitechange = new OptionsToShow(R.id.careportal_pumpsitechange, R.string.careportal_pumpsitechange, true, true, false, false, false, false, false, false, false, false);
    public static final OptionsToShow sensorstart = new OptionsToShow(R.id.careportal_cgmsensorstart, R.string.careportal_cgmsensorstart, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow sensorchange = new OptionsToShow(R.id.careportal_cgmsensorinsert, R.string.careportal_cgmsensorinsert, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow insulinchange = new OptionsToShow(R.id.careportal_insulincartridgechange, R.string.careportal_insulincartridgechange, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow pumpbatterychange = new OptionsToShow(R.id.careportal_pumpbatterychange, R.string.careportal_pumpbatterychange, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow tempbasalstart = new OptionsToShow(R.id.careportal_tempbasalstart, R.string.careportal_tempbasalstart, true, false, false, false, true, true, true, false, false, false);
    public static final OptionsToShow tempbasalend = new OptionsToShow(R.id.careportal_tempbasalend, R.string.careportal_tempbasalend, true, false, false, false, false, false, false, false, false, false);
    public static final OptionsToShow profileswitch = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch, true, false, false, false, true, false, false, true, false, false);
    public static final OptionsToShow openapsoffline = new OptionsToShow(R.id.careportal_openapsoffline, R.string.careportal_openapsoffline, false, false, false, false, true, false, false, false, false, false);
    public static final OptionsToShow temptarget = new OptionsToShow(R.id.careportal_temporarytarget, R.string.careportal_temporarytarget, false, false, false, false, true, false, false, false, false, true);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        try {
            View view = inflater.inflate(R.layout.careportal_fragment, container, false);

            view.findViewById(R.id.careportal_bgcheck).setOnClickListener(this);
            view.findViewById(R.id.careportal_announcement).setOnClickListener(this);
            view.findViewById(R.id.careportal_cgmsensorinsert).setOnClickListener(this);
            view.findViewById(R.id.careportal_cgmsensorstart).setOnClickListener(this);
            view.findViewById(R.id.careportal_combobolus).setOnClickListener(this);
            view.findViewById(R.id.careportal_correctionbolus).setOnClickListener(this);
            view.findViewById(R.id.careportal_carbscorrection).setOnClickListener(this);
            view.findViewById(R.id.careportal_exercise).setOnClickListener(this);
            view.findViewById(R.id.careportal_insulincartridgechange).setOnClickListener(this);
            view.findViewById(R.id.careportal_pumpbatterychange).setOnClickListener(this);
            view.findViewById(R.id.careportal_mealbolus).setOnClickListener(this);
            view.findViewById(R.id.careportal_note).setOnClickListener(this);
            view.findViewById(R.id.careportal_profileswitch).setOnClickListener(this);
            view.findViewById(R.id.careportal_pumpsitechange).setOnClickListener(this);
            view.findViewById(R.id.careportal_question).setOnClickListener(this);
            view.findViewById(R.id.careportal_snackbolus).setOnClickListener(this);
            view.findViewById(R.id.careportal_tempbasalend).setOnClickListener(this);
            view.findViewById(R.id.careportal_tempbasalstart).setOnClickListener(this);
            view.findViewById(R.id.careportal_openapsoffline).setOnClickListener(this);
            view.findViewById(R.id.careportal_temporarytarget).setOnClickListener(this);

            iage = (TextView) view.findViewById(R.id.careportal_insulinage);
            cage = (TextView) view.findViewById(R.id.careportal_canulaage);
            sage = (TextView) view.findViewById(R.id.careportal_sensorage);
            pbage = (TextView) view.findViewById(R.id.careportal_pbage);

            statsLayout = view.findViewById(R.id.careportal_stats);

            noProfileView = view.findViewById(R.id.profileview_noprofile);
            butonsLayout = (LinearLayout) view.findViewById(R.id.careportal_buttons);

            ProfileStore profileStore = ConfigBuilderPlugin.getActiveProfileInterface().getProfile();
            if (profileStore == null) {
                noProfileView.setVisibility(View.VISIBLE);
                butonsLayout.setVisibility(View.GONE);
            } else {
                noProfileView.setVisibility(View.GONE);
                butonsLayout.setVisibility(View.VISIBLE);
            }

            if (BuildConfig.NSCLIENTOLNY)
                statsLayout.setVisibility(View.GONE); // visible on overview

            updateGUI();
            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
        }

        return null;
    }

    @Override
    public void onClick(View view) {
        action(view.getId(), getFragmentManager());
    }

    public static void action(int id, FragmentManager manager) {
        NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
        switch (id) {
            case R.id.careportal_bgcheck:
                newDialog.setOptions(bgcheck, R.string.careportal_bgcheck);
                break;
            case R.id.careportal_announcement:
                newDialog.setOptions(announcement, R.string.careportal_announcement);
                break;
            case R.id.careportal_cgmsensorinsert:
                newDialog.setOptions(sensorchange, R.string.careportal_cgmsensorinsert);
                break;
            case R.id.careportal_cgmsensorstart:
                newDialog.setOptions(sensorstart, R.string.careportal_cgmsensorstart);
                break;
            case R.id.careportal_combobolus:
                newDialog.setOptions(combobolus, R.string.careportal_combobolus);
                break;
            case R.id.careportal_correctionbolus:
                newDialog.setOptions(correctionbolus, R.string.careportal_correctionbolus);
                break;
            case R.id.careportal_carbscorrection:
                newDialog.setOptions(carbcorrection, R.string.careportal_carbscorrection);
                break;
            case R.id.careportal_exercise:
                newDialog.setOptions(exercise, R.string.careportal_exercise);
                break;
            case R.id.careportal_insulincartridgechange:
                newDialog.setOptions(insulinchange, R.string.careportal_insulincartridgechange);
                break;
            case R.id.careportal_pumpbatterychange:
                newDialog.setOptions(pumpbatterychange, R.string.careportal_pumpbatterychange);
                break;
            case R.id.careportal_mealbolus:
                newDialog.setOptions(mealbolus, R.string.careportal_mealbolus);
                break;
            case R.id.careportal_note:
                newDialog.setOptions(note, R.string.careportal_note);
                break;
            case R.id.careportal_profileswitch:
                profileswitch.executeProfileSwitch = false;
                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
                break;
            case R.id.careportal_pumpsitechange:
                newDialog.setOptions(sitechange, R.string.careportal_pumpsitechange);
                break;
            case R.id.careportal_question:
                newDialog.setOptions(question, R.string.careportal_question);
                break;
            case R.id.careportal_snackbolus:
                newDialog.setOptions(snackbolus, R.string.careportal_snackbolus);
                break;
            case R.id.careportal_tempbasalstart:
                newDialog.setOptions(tempbasalstart, R.string.careportal_tempbasalstart);
                break;
            case R.id.careportal_tempbasalend:
                newDialog.setOptions(tempbasalend, R.string.careportal_tempbasalend);
                break;
            case R.id.careportal_openapsoffline:
                newDialog.setOptions(openapsoffline, R.string.careportal_openapsoffline);
                break;
            case R.id.careportal_temporarytarget:
                temptarget.executeTempTarget = false;
                newDialog.setOptions(temptarget, R.string.careportal_temporarytarget);
                break;
            default:
                newDialog = null;
        }
        if (newDialog != null)
            newDialog.show(manager, "NewNSTreatmentDialog");
    }

    @Subscribe
    public void onStatusEvent(final EventCareportalEventChange c) {
        updateGUI();
    }

    @Override
    protected void updateGUI() {
        Activity activity = getActivity();
        updateAge(activity, sage, iage, cage, pbage);
    }

    public static void updateAge(Activity activity, final TextView sage, final TextView iage, final TextView cage, final TextView pbage) {
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            CareportalEvent careportalEvent;
                            String notavailable = OverviewFragment.shorttextmode ? "-" : MainApp.sResources.getString(R.string.notavailable);
                            if (sage != null) {
                                careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.SENSORCHANGE);
                                sage.setText(careportalEvent != null ? careportalEvent.age() : notavailable);
                            }
                            if (iage != null) {
                                careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.INSULINCHANGE);
                                iage.setText(careportalEvent != null ? careportalEvent.age() : notavailable);
                            }
                            if (cage != null) {
                                careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.SITECHANGE);
                                cage.setText(careportalEvent != null ? careportalEvent.age() : notavailable);
                            }
                            if (pbage != null) {
                                careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(CareportalEvent.PUMPBATTERYCHANGE);
                                pbage.setText(careportalEvent != null ? careportalEvent.age() : notavailable);
                            }
                        }
                    }
            );
        }
    }

}
