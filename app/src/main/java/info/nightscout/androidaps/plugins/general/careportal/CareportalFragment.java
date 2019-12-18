package info.nightscout.androidaps.plugins.general.careportal;


import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class CareportalFragment extends Fragment implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(CareportalFragment.class);
    private CompositeDisposable disposable = new CompositeDisposable();

    TextView iage;
    TextView cage;
    TextView sage;
    TextView pbage;

    View statsLayout;
    LinearLayout butonsLayout;
    View noProfileView;

    //                                                    date,bg,insulin,carbs,prebolus,duration,percent,absolute,profile,split,temptarget
    public static final OptionsToShow BGCHECK = new OptionsToShow(R.id.careportal_bgcheck, R.string.careportal_bgcheck).date().bg();
    public static final OptionsToShow SNACKBOLUS = new OptionsToShow(R.id.careportal_snackbolus, R.string.careportal_snackbolus).date().bg().insulin().carbs().prebolus();
    public static final OptionsToShow MEALBOLUS = new OptionsToShow(R.id.careportal_mealbolus, R.string.careportal_mealbolus).date().bg().insulin().carbs().prebolus();
    public static final OptionsToShow CORRECTIONBOLUS = new OptionsToShow(R.id.careportal_correctionbolus, R.string.careportal_correctionbolus).date().bg().insulin().carbs().prebolus();
    public static final OptionsToShow CARBCORRECTION = new OptionsToShow(R.id.careportal_carbscorrection, R.string.careportal_carbscorrection).date().bg().carbs();
    public static final OptionsToShow COMBOBOLUS = new OptionsToShow(R.id.careportal_combobolus, R.string.careportal_combobolus).date().bg().insulin().carbs().prebolus().duration().split();
    public static final OptionsToShow ANNOUNCEMENT = new OptionsToShow(R.id.careportal_announcement, R.string.careportal_announcement).date().bg();
    public static final OptionsToShow NOTE = new OptionsToShow(R.id.careportal_note, R.string.careportal_note).date().bg().duration();
    public static final OptionsToShow QUESTION = new OptionsToShow(R.id.careportal_question, R.string.careportal_question).date().bg();
    public static final OptionsToShow EXERCISE = new OptionsToShow(R.id.careportal_exercise, R.string.careportal_exercise).date().duration();
    public static final OptionsToShow SITECHANGE = new OptionsToShow(R.id.careportal_pumpsitechange, R.string.careportal_pumpsitechange).date().bg();
    public static final OptionsToShow SENSORSTART = new OptionsToShow(R.id.careportal_cgmsensorstart, R.string.careportal_cgmsensorstart).date().bg();
    public static final OptionsToShow SENSORCHANGE = new OptionsToShow(R.id.careportal_cgmsensorinsert, R.string.careportal_cgmsensorinsert).date().bg();
    public static final OptionsToShow INSULINCHANGE = new OptionsToShow(R.id.careportal_insulincartridgechange, R.string.careportal_insulincartridgechange).date().bg();
    public static final OptionsToShow PUMPBATTERYCHANGE = new OptionsToShow(R.id.careportal_pumpbatterychange, R.string.careportal_pumpbatterychange).date().bg();
    public static final OptionsToShow TEMPBASALSTART = new OptionsToShow(R.id.careportal_tempbasalstart, R.string.careportal_tempbasalstart).date().bg().duration().percent().absolute();
    public static final OptionsToShow TEMPBASALEND = new OptionsToShow(R.id.careportal_tempbasalend, R.string.careportal_tempbasalend).date().bg();
    public static final OptionsToShow PROFILESWITCH = new OptionsToShow(R.id.careportal_profileswitch, R.string.careportal_profileswitch).date().duration().profile();
    public static final OptionsToShow OPENAPSOFFLINE = new OptionsToShow(R.id.careportal_openapsoffline, R.string.careportal_openapsoffline).date().duration();
    public static final OptionsToShow TEMPTARGET = new OptionsToShow(R.id.careportal_temporarytarget, R.string.careportal_temporarytarget).date().duration().tempTarget();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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

        iage = view.findViewById(R.id.careportal_insulinage);
        cage = view.findViewById(R.id.careportal_canulaage);
        sage = view.findViewById(R.id.careportal_sensorage);
        pbage = view.findViewById(R.id.careportal_pbage);

        statsLayout = view.findViewById(R.id.careportal_stats);

        noProfileView = view.findViewById(R.id.profileview_noprofile);
        butonsLayout = view.findViewById(R.id.careportal_buttons);

        ProfileStore profileStore = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null ? ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile() : null;
        if (profileStore == null) {
            noProfileView.setVisibility(View.VISIBLE);
            butonsLayout.setVisibility(View.GONE);
        } else {
            noProfileView.setVisibility(View.GONE);
            butonsLayout.setVisibility(View.VISIBLE);
        }

        if (Config.NSCLIENT)
            statsLayout.setVisibility(View.GONE); // visible on overview

        return view;
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventCareportalEventChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updateGUI(), FabricPrivacy::logException)
        );
        updateGUI();
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        disposable.clear();
    }

    @Override
    public void onClick(View view) {
        action(view.getId(), getFragmentManager());
    }

    public static void action(int id, FragmentManager manager) {
        NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
        switch (id) {
            case R.id.careportal_bgcheck:
                newDialog.setOptions(BGCHECK, R.string.careportal_bgcheck);
                break;
            case R.id.careportal_announcement:
                newDialog.setOptions(ANNOUNCEMENT, R.string.careportal_announcement);
                break;
            case R.id.careportal_cgmsensorinsert:
                newDialog.setOptions(SENSORCHANGE, R.string.careportal_cgmsensorinsert);
                break;
            case R.id.careportal_cgmsensorstart:
                newDialog.setOptions(SENSORSTART, R.string.careportal_cgmsensorstart);
                break;
            case R.id.careportal_combobolus:
                newDialog.setOptions(COMBOBOLUS, R.string.careportal_combobolus);
                break;
            case R.id.careportal_correctionbolus:
                newDialog.setOptions(CORRECTIONBOLUS, R.string.careportal_correctionbolus);
                break;
            case R.id.careportal_carbscorrection:
                newDialog.setOptions(CARBCORRECTION, R.string.careportal_carbscorrection);
                break;
            case R.id.careportal_exercise:
                newDialog.setOptions(EXERCISE, R.string.careportal_exercise);
                break;
            case R.id.careportal_insulincartridgechange:
                newDialog.setOptions(INSULINCHANGE, R.string.careportal_insulincartridgechange);
                break;
            case R.id.careportal_pumpbatterychange:
                newDialog.setOptions(PUMPBATTERYCHANGE, R.string.careportal_pumpbatterychange);
                break;
            case R.id.careportal_mealbolus:
                newDialog.setOptions(MEALBOLUS, R.string.careportal_mealbolus);
                break;
            case R.id.careportal_note:
                newDialog.setOptions(NOTE, R.string.careportal_note);
                break;
            case R.id.careportal_profileswitch:
                newDialog.setOptions(PROFILESWITCH, R.string.careportal_profileswitch);
                break;
            case R.id.careportal_pumpsitechange:
                newDialog.setOptions(SITECHANGE, R.string.careportal_pumpsitechange);
                break;
            case R.id.careportal_question:
                newDialog.setOptions(QUESTION, R.string.careportal_question);
                break;
            case R.id.careportal_snackbolus:
                newDialog.setOptions(SNACKBOLUS, R.string.careportal_snackbolus);
                break;
            case R.id.careportal_tempbasalstart:
                newDialog.setOptions(TEMPBASALSTART, R.string.careportal_tempbasalstart);
                break;
            case R.id.careportal_tempbasalend:
                newDialog.setOptions(TEMPBASALEND, R.string.careportal_tempbasalend);
                break;
            case R.id.careportal_openapsoffline:
                newDialog.setOptions(OPENAPSOFFLINE, R.string.careportal_openapsoffline);
                break;
            case R.id.careportal_temporarytarget:
                newDialog.setOptions(TEMPTARGET, R.string.careportal_temporarytarget);
                break;
            default:
                newDialog = null;
        }
        if (newDialog != null)
            newDialog.show(manager, "NewNSTreatmentDialog");
    }

    protected void updateGUI() {
        Activity activity = getActivity();
        updateAge(activity, sage, iage, cage, pbage);
    }

    public static void updateAge(Activity activity, final TextView sage, final TextView iage, final TextView cage, final TextView pbage) {
        if (activity != null) {
            activity.runOnUiThread(
                    () -> {
                        CareportalEvent careportalEvent;
                        NSSettingsStatus nsSettings = NSSettingsStatus.getInstance();

                        double iageUrgent = nsSettings.getExtendedWarnValue("iage", "urgent", 96);
                        double iageWarn = nsSettings.getExtendedWarnValue("iage", "warn", 72);
                        handleAge(iage, CareportalEvent.INSULINCHANGE, iageWarn, iageUrgent);

                        double cageUrgent = nsSettings.getExtendedWarnValue("cage", "urgent", 72);
                        double cageWarn = nsSettings.getExtendedWarnValue("cage", "warn", 48);
                        handleAge(cage, CareportalEvent.SITECHANGE, cageWarn, cageUrgent);

                        double sageUrgent = nsSettings.getExtendedWarnValue("sage", "urgent", 166);
                        double sageWarn = nsSettings.getExtendedWarnValue("sage", "warn", 164);
                        handleAge(sage, CareportalEvent.SENSORCHANGE, sageWarn, sageUrgent);

                        double pbageUrgent = nsSettings.getExtendedWarnValue("bage", "urgent", 360);
                        double pbageWarn = nsSettings.getExtendedWarnValue("bage", "warn", 240);
                        handleAge(pbage, CareportalEvent.PUMPBATTERYCHANGE, pbageWarn, pbageUrgent);
                    }
            );
        }
    }

    public static int determineTextColor(CareportalEvent careportalEvent, double warnThreshold, double urgentThreshold) {
        if (careportalEvent.isOlderThan(urgentThreshold)) {
            return MainApp.gc(R.color.low);
        } else if (careportalEvent.isOlderThan(warnThreshold)) {
            return MainApp.gc(R.color.high);
        } else {
            return Color.WHITE;
        }

    }

    private static TextView handleAge(final TextView age, String eventType, double warnThreshold, double urgentThreshold) {
        return handleAge(age, "", eventType, warnThreshold, urgentThreshold, OverviewFragment.shorttextmode);
    }

    public static TextView handleAge(final TextView age, String prefix, String eventType, double warnThreshold, double urgentThreshold, boolean useShortText) {
        String notavailable = useShortText ? "-" : MainApp.gs(R.string.notavailable);

        if (age != null) {
            CareportalEvent careportalEvent = MainApp.getDbHelper().getLastCareportalEvent(eventType);
            if (careportalEvent != null) {
                age.setTextColor(CareportalFragment.determineTextColor(careportalEvent, warnThreshold, urgentThreshold));
                age.setText(prefix + careportalEvent.age(useShortText));
            } else {
                age.setText(notavailable);
            }
        }

        return age;
    }
}

