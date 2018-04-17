package info.nightscout.androidaps.plugins.Overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.jjoe64.graphview.GraphView;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.QuickWizardEntry;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastAckAlarm;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.CalibrationDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTreatmentDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.WizardDialog;
import info.nightscout.androidaps.plugins.Overview.activities.QuickWizardListActivity;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventSetWakeLock;
import info.nightscout.androidaps.plugins.Overview.graphData.GraphData;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.Overview.notifications.NotificationStore;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.Treatments.fragments.ProfileViewerDialog;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.SP;
import info.nightscout.utils.SingleClickButton;
import info.nightscout.utils.ToastUtils;

public class OverviewFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, View.OnLongClickListener {
    private static Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    TextView timeView;
    TextView bgView;
    TextView arrowView;
    TextView timeAgoView;
    TextView deltaView;
    TextView avgdeltaView;
    TextView baseBasalView;
    TextView extendedBolusView;
    TextView activeProfileView;
    TextView iobView;
    TextView cobView;
    TextView apsModeView;
    TextView tempTargetView;
    TextView pumpStatusView;
    TextView pumpDeviceStatusView;
    TextView openapsDeviceStatusView;
    TextView uploaderDeviceStatusView;
    LinearLayout loopStatusLayout;
    LinearLayout pumpStatusLayout;
    GraphView bgGraph;
    GraphView iobGraph;

    TextView iage;
    TextView cage;
    TextView sage;
    TextView pbage;

    TextView showPredictionLabel;
    CheckBox showPredictionCheckbox;
    TextView showBasalsLabel;
    CheckBox showBasalsCheckbox;
    TextView showIobLabel;
    CheckBox showIobCheckbox;
    TextView showCobLabel;
    CheckBox showCobCheckbox;
    TextView showDeviationsLabel;
    CheckBox showDeviationsCheckbox;
    TextView showRatiosLabel;
    CheckBox showRatiosCheckbox;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

    LinearLayout acceptTempLayout;
    SingleClickButton treatmentButton;
    SingleClickButton wizardButton;
    SingleClickButton calibrationButton;
    SingleClickButton acceptTempButton;
    SingleClickButton quickWizardButton;

    CheckBox lockScreen;

    boolean smallWidth;
    boolean smallHeight;

    public static boolean shorttextmode = false;

    private boolean accepted;

    private int rangeToDisplay = 6; // for graph

    Handler sLoopHandler = new Handler();
    Runnable sRefreshLoop = null;

    final Object updateSync = new Object();

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledUpdate = null;

    public OverviewFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        try {
            //check screen width
            final DisplayMetrics dm = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
            int screen_width = dm.widthPixels;
            int screen_height = dm.heightPixels;
            smallWidth = screen_width <= Constants.SMALL_WIDTH;
            smallHeight = screen_height <= Constants.SMALL_HEIGHT;
            boolean landscape = screen_height < screen_width;

            View view;

            if (MainApp.sResources.getBoolean(R.bool.isTablet) && (Config.NSCLIENT || Config.G5UPLOADER)) {
                view = inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false);
            } else if (Config.NSCLIENT || Config.G5UPLOADER) {
                view = inflater.inflate(R.layout.overview_fragment_nsclient, container, false);
                shorttextmode = true;
            } else if (smallHeight || landscape) {
                view = inflater.inflate(R.layout.overview_fragment_smallheight, container, false);
            } else {
                view = inflater.inflate(R.layout.overview_fragment, container, false);
            }

            timeView = (TextView) view.findViewById(R.id.overview_time);
            bgView = (TextView) view.findViewById(R.id.overview_bg);
            arrowView = (TextView) view.findViewById(R.id.overview_arrow);
            if (smallWidth) {
                arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 35);
            }
            timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
            deltaView = (TextView) view.findViewById(R.id.overview_delta);
            avgdeltaView = (TextView) view.findViewById(R.id.overview_avgdelta);
            baseBasalView = (TextView) view.findViewById(R.id.overview_basebasal);
            extendedBolusView = (TextView) view.findViewById(R.id.overview_extendedbolus);
            activeProfileView = (TextView) view.findViewById(R.id.overview_activeprofile);
            pumpStatusView = (TextView) view.findViewById(R.id.overview_pumpstatus);
            pumpDeviceStatusView = (TextView) view.findViewById(R.id.overview_pump);
            openapsDeviceStatusView = (TextView) view.findViewById(R.id.overview_openaps);
            uploaderDeviceStatusView = (TextView) view.findViewById(R.id.overview_uploader);
            loopStatusLayout = (LinearLayout) view.findViewById(R.id.overview_looplayout);
            pumpStatusLayout = (LinearLayout) view.findViewById(R.id.overview_pumpstatuslayout);

            pumpStatusView.setBackgroundColor(MainApp.sResources.getColor(R.color.colorInitializingBorder));

            iobView = (TextView) view.findViewById(R.id.overview_iob);
            cobView = (TextView) view.findViewById(R.id.overview_cob);
            apsModeView = (TextView) view.findViewById(R.id.overview_apsmode);
            tempTargetView = (TextView) view.findViewById(R.id.overview_temptarget);

            iage = (TextView) view.findViewById(R.id.careportal_insulinage);
            cage = (TextView) view.findViewById(R.id.careportal_canulaage);
            sage = (TextView) view.findViewById(R.id.careportal_sensorage);
            pbage = (TextView) view.findViewById(R.id.careportal_pbage);

            bgGraph = (GraphView) view.findViewById(R.id.overview_bggraph);
            iobGraph = (GraphView) view.findViewById(R.id.overview_iobgraph);

            treatmentButton = (SingleClickButton) view.findViewById(R.id.overview_treatmentbutton);
            treatmentButton.setOnClickListener(this);
            wizardButton = (SingleClickButton) view.findViewById(R.id.overview_wizardbutton);
            wizardButton.setOnClickListener(this);
            acceptTempButton = (SingleClickButton) view.findViewById(R.id.overview_accepttempbutton);
            if (acceptTempButton != null)
                acceptTempButton.setOnClickListener(this);
            quickWizardButton = (SingleClickButton) view.findViewById(R.id.overview_quickwizardbutton);
            quickWizardButton.setOnClickListener(this);
            quickWizardButton.setOnLongClickListener(this);
            calibrationButton = (SingleClickButton) view.findViewById(R.id.overview_calibrationbutton);
            if (calibrationButton != null)
                calibrationButton.setOnClickListener(this);

            acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

            showPredictionCheckbox = (CheckBox) view.findViewById(R.id.overview_showprediction);
            showBasalsCheckbox = (CheckBox) view.findViewById(R.id.overview_showbasals);
            showIobCheckbox = (CheckBox) view.findViewById(R.id.overview_showiob);
            showCobCheckbox = (CheckBox) view.findViewById(R.id.overview_showcob);
            showDeviationsCheckbox = (CheckBox) view.findViewById(R.id.overview_showdeviations);
            showRatiosCheckbox = (CheckBox) view.findViewById(R.id.overview_showratios);
            showPredictionCheckbox.setChecked(SP.getBoolean("showprediction", false));
            showBasalsCheckbox.setChecked(SP.getBoolean("showbasals", true));
            showIobCheckbox.setChecked(SP.getBoolean("showiob", false));
            showCobCheckbox.setChecked(SP.getBoolean("showcob", false));
            showDeviationsCheckbox.setChecked(SP.getBoolean("showdeviations", false));
            showRatiosCheckbox.setChecked(SP.getBoolean("showratios", false));
            showPredictionCheckbox.setOnCheckedChangeListener(this);
            showBasalsCheckbox.setOnCheckedChangeListener(this);
            showIobCheckbox.setOnCheckedChangeListener(this);
            showCobCheckbox.setOnCheckedChangeListener(this);
            showDeviationsCheckbox.setOnCheckedChangeListener(this);
            showRatiosCheckbox.setOnCheckedChangeListener(this);

            showPredictionLabel = (TextView) view.findViewById(R.id.overview_showprediction_label);
            showPredictionLabel.setOnClickListener(this);
            showBasalsLabel = (TextView) view.findViewById(R.id.overview_showbasals_label);
            showBasalsLabel.setOnClickListener(this);
            showIobLabel = (TextView) view.findViewById(R.id.overview_showiob_label);
            showIobLabel.setOnClickListener(this);
            showCobLabel = (TextView) view.findViewById(R.id.overview_showcob_label);
            showCobLabel.setOnClickListener(this);
            showDeviationsLabel = (TextView) view.findViewById(R.id.overview_showdeviations_label);
            showDeviationsLabel.setOnClickListener(this);
            showRatiosLabel = (TextView) view.findViewById(R.id.overview_showratios_label);
            showRatiosLabel.setOnClickListener(this);

            notificationsView = (RecyclerView) view.findViewById(R.id.overview_notifications);
            notificationsView.setHasFixedSize(true);
            llm = new LinearLayoutManager(view.getContext());
            notificationsView.setLayoutManager(llm);

            bgGraph.getGridLabelRenderer().setGridColor(MainApp.sResources.getColor(R.color.graphgrid));
            bgGraph.getGridLabelRenderer().reloadStyles();
            iobGraph.getGridLabelRenderer().setGridColor(MainApp.sResources.getColor(R.color.graphgrid));
            iobGraph.getGridLabelRenderer().reloadStyles();
            iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
            bgGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
            iobGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
            iobGraph.getGridLabelRenderer().setNumVerticalLabels(5);

            rangeToDisplay = SP.getInt(R.string.key_rangetodisplay, 6);

            bgGraph.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    rangeToDisplay += 6;
                    rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
                    SP.putInt(R.string.key_rangetodisplay, rangeToDisplay);
                    updateGUI("rangeChange");
                    return false;
                }
            });

            lockScreen = (CheckBox) view.findViewById(R.id.overview_lockscreen);
            if (lockScreen != null) {
                lockScreen.setChecked(SP.getBoolean("lockscreen", false));
                lockScreen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SP.putBoolean("lockscreen", isChecked);
                        MainApp.bus().post(new EventSetWakeLock(isChecked));
                    }
                });
            }

            return view;
        } catch (Exception e) {
            Crashlytics.logException(e);
            log.debug("Runtime Exception", e);
        }

        return null;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v == apsModeView) {
            final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
            final PumpDescription pumpDescription = ConfigBuilderPlugin.getActivePump().getPumpDescription();
            if (activeloop == null)
                return;
            menu.setHeaderTitle(MainApp.sResources.getString(R.string.loop));
            if (activeloop.isEnabled(PluginBase.LOOP)) {
                menu.add(MainApp.sResources.getString(R.string.disableloop));
                if (!activeloop.isSuspended()) {
                    menu.add(MainApp.sResources.getString(R.string.suspendloopfor1h));
                    menu.add(MainApp.sResources.getString(R.string.suspendloopfor2h));
                    menu.add(MainApp.sResources.getString(R.string.suspendloopfor3h));
                    menu.add(MainApp.sResources.getString(R.string.suspendloopfor10h));
                    if (pumpDescription.tempDurationStep <= 30)
                        menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor30m));
                    menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor1h));
                    menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor2h));
                    menu.add(MainApp.sResources.getString(R.string.disconnectpumpfor3h));
                } else {
                    menu.add(MainApp.sResources.getString(R.string.resume));
                }
            }
            if (!activeloop.isEnabled(PluginBase.LOOP))
                menu.add(MainApp.sResources.getString(R.string.enableloop));
        } else if (v == activeProfileView) {
            menu.setHeaderTitle(MainApp.sResources.getString(R.string.profile));
            menu.add(MainApp.sResources.getString(R.string.danar_viewprofile));
            menu.add(MainApp.sResources.getString(R.string.careportal_profileswitch));
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.overview_showprediction:
            case R.id.overview_showbasals:
            case R.id.overview_showiob:
                break;
            case R.id.overview_showcob:
                showDeviationsCheckbox.setOnCheckedChangeListener(null);
                showDeviationsCheckbox.setChecked(false);
                showDeviationsCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.overview_showdeviations:
                showCobCheckbox.setOnCheckedChangeListener(null);
                showCobCheckbox.setChecked(false);
                showCobCheckbox.setOnCheckedChangeListener(this);
                break;
            case R.id.overview_showratios:
                break;
        }
        SP.putBoolean("showiob", showIobCheckbox.isChecked());
        SP.putBoolean("showprediction", showPredictionCheckbox.isChecked());
        SP.putBoolean("showbasals", showBasalsCheckbox.isChecked());
        SP.putBoolean("showcob", showCobCheckbox.isChecked());
        SP.putBoolean("showdeviations", showDeviationsCheckbox.isChecked());
        SP.putBoolean("showratios", showRatiosCheckbox.isChecked());
        scheduleUpdateGUI("onGraphCheckboxesCheckedChanged");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
        if (item.getTitle().equals(MainApp.sResources.getString(R.string.disableloop))) {
            activeloop.setFragmentEnabled(PluginBase.LOOP, false);
            activeloop.setFragmentVisible(PluginBase.LOOP, false);
            MainApp.getConfigBuilder().storeSettings();
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(60); // upload 60 min, we don;t know real duration
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.enableloop))) {
            activeloop.setFragmentEnabled(PluginBase.LOOP, true);
            activeloop.setFragmentVisible(PluginBase.LOOP, true);
            MainApp.getConfigBuilder().storeSettings();
            updateGUI("suspendmenu");
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.resume))) {
            activeloop.suspendTo(0L);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor1h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(60);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor2h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(120);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor3h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 3 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(180);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.suspendloopfor10h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 10 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(600);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor30m))) {
            activeloop.disconnectTo(System.currentTimeMillis() + 30L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().tempBasalPercent(0, 30, true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            if (MainApp.getConfigBuilder().getActivePump().getPumpDescription().isExtendedBolusCapable && MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                ConfigBuilderPlugin.getCommandQueue().cancelExtended( new Callback() {
                    @Override
                    public void run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.extendedbolusdeliveryerror));
                        }
                    }
                });
            }
            NSUpload.uploadOpenAPSOffline(30);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor1h))) {
            activeloop.disconnectTo(System.currentTimeMillis() + 1 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().tempBasalPercent(0, 60, true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            if (MainApp.getConfigBuilder().getActivePump().getPumpDescription().isExtendedBolusCapable && MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                ConfigBuilderPlugin.getCommandQueue().cancelExtended( new Callback() {
                    @Override
                    public void run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.extendedbolusdeliveryerror));
                        }
                    }
                });
            }
            NSUpload.uploadOpenAPSOffline(60);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor2h))) {
            activeloop.disconnectTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().tempBasalPercent(0, 2 * 60, true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            if (MainApp.getConfigBuilder().getActivePump().getPumpDescription().isExtendedBolusCapable && MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                ConfigBuilderPlugin.getCommandQueue().cancelExtended( new Callback() {
                    @Override
                    public void run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.extendedbolusdeliveryerror));
                        }
                    }
                });
            }
            NSUpload.uploadOpenAPSOffline(120);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor3h))) {
            activeloop.disconnectTo(System.currentTimeMillis() + 3 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getCommandQueue().tempBasalPercent(0, 3 * 60, true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            if (MainApp.getConfigBuilder().getActivePump().getPumpDescription().isExtendedBolusCapable && MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                ConfigBuilderPlugin.getCommandQueue().cancelExtended( new Callback() {
                    @Override
                    public void run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.extendedbolusdeliveryerror));
                        }
                    }
                });
            }
            NSUpload.uploadOpenAPSOffline(180);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.careportal_profileswitch))) {
            NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
            final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
            profileswitch.executeProfileSwitch = true;
            newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
            newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.danar_viewprofile))) {
            ProfileViewerDialog pvd = ProfileViewerDialog.newInstance(System.currentTimeMillis());
            FragmentManager manager = getFragmentManager();
            pvd.show(manager, "ProfileViewDialog");
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        FragmentManager manager = getFragmentManager();
        switch (v.getId()) {
            case R.id.overview_accepttempbutton:
                onClickAcceptTemp();
                break;
            case R.id.overview_quickwizardbutton:
                onClickQuickwizard();
                break;
            case R.id.overview_wizardbutton:
                WizardDialog wizardDialog = new WizardDialog();
                wizardDialog.show(manager, "WizardDialog");
                break;
            case R.id.overview_calibrationbutton:
                CalibrationDialog calibrationDialog = new CalibrationDialog();
                calibrationDialog.show(manager, "CalibrationDialog");
                break;
            case R.id.overview_treatmentbutton:
                NewTreatmentDialog treatmentDialogFragment = new NewTreatmentDialog();
                treatmentDialogFragment.show(manager, "TreatmentDialog");
                break;
            case R.id.overview_pumpstatus:
                if (ConfigBuilderPlugin.getActivePump().isSuspended() || !ConfigBuilderPlugin.getActivePump().isInitialized())
                    ConfigBuilderPlugin.getCommandQueue().readStatus("RefreshClicked", null);
                break;
            case R.id.overview_showprediction_label:
                showPredictionCheckbox.toggle();
                break;
            case R.id.overview_showbasals_label:
                showBasalsCheckbox.toggle();
                break;
            case R.id.overview_showiob_label:
                showIobCheckbox.toggle();
                break;
            case R.id.overview_showcob_label:
                showCobCheckbox.toggle();
                break;
            case R.id.overview_showdeviations_label:
                showDeviationsCheckbox.toggle();
                break;
            case R.id.overview_showratios_label:
                showRatiosCheckbox.toggle();
                break;
        }

    }

    @Override
    public boolean onLongClick(View v) {
        switch (v.getId()) {
            case R.id.overview_quickwizardbutton:
                Intent i = new Intent(v.getContext(), QuickWizardListActivity.class);
                startActivity(i);
                return true;
        }
        return false;
    }

    private void onClickAcceptTemp() {
        if (ConfigBuilderPlugin.getActiveLoop() != null) {
            ConfigBuilderPlugin.getActiveLoop().invoke("Accept temp button", false);
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            if (finalLastRun != null && finalLastRun.lastAPSRun != null && finalLastRun.constraintsProcessed.changeRequested) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle(getContext().getString(R.string.confirmation));
                builder.setMessage(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
                builder.setPositiveButton(getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        hideTempRecommendation();
                        clearNotification();
                        MainApp.getConfigBuilder().applyAPSRequest(finalLastRun.constraintsProcessed, new Callback() {
                            @Override
                            public void run() {
                                if (result.enacted) {
                                    finalLastRun.setByPump = result;
                                    finalLastRun.lastEnact = new Date();
                                    finalLastRun.lastOpenModeAccept = new Date();
                                    NSUpload.uploadDeviceStatus();
                                    ObjectivesPlugin objectivesPlugin = MainApp.getSpecificPlugin(ObjectivesPlugin.class);
                                    if (objectivesPlugin != null) {
                                        ObjectivesPlugin.manualEnacts++;
                                        ObjectivesPlugin.saveProgress();
                                    }
                                }
                                scheduleUpdateGUI("onClickAcceptTemp");
                            }
                        });
                        Answers.getInstance().logCustom(new CustomEvent("AcceptTemp"));
                    }
                });
                builder.setNegativeButton(getContext().getString(R.string.cancel), null);
                builder.show();
            }
        }
    }

    void onClickQuickwizard() {
        final BgReading actualBg = DatabaseHelper.actualBg();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        final TempTarget tempTarget = MainApp.getConfigBuilder().getTempTargetFromHistory();

        final QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && actualBg != null) {
            quickWizardButton.setVisibility(View.VISIBLE);
            final BolusWizard wizard = quickWizardEntry.doCalc(profile, tempTarget, actualBg, true);

            final JSONObject boluscalcJSON = new JSONObject();
            try {
                boluscalcJSON.put("eventTime", DateUtil.toISOString(new Date()));
                boluscalcJSON.put("targetBGLow", wizard.targetBGLow);
                boluscalcJSON.put("targetBGHigh", wizard.targetBGHigh);
                boluscalcJSON.put("isf", wizard.sens);
                boluscalcJSON.put("ic", wizard.ic);
                boluscalcJSON.put("iob", -(wizard.insulingFromBolusIOB + wizard.insulingFromBasalsIOB));
                boluscalcJSON.put("bolusiobused", true);
                boluscalcJSON.put("basaliobused", true);
                boluscalcJSON.put("bg", actualBg.valueToUnits(profile.getUnits()));
                boluscalcJSON.put("insulinbg", wizard.insulinFromBG);
                boluscalcJSON.put("insulinbgused", true);
                boluscalcJSON.put("bgdiff", wizard.bgDiff);
                boluscalcJSON.put("insulincarbs", wizard.insulinFromCarbs);
                boluscalcJSON.put("carbs", quickWizardEntry.carbs());
                boluscalcJSON.put("othercorrection", 0d);
                boluscalcJSON.put("insulintrend", wizard.insulinFromTrend);
                boluscalcJSON.put("insulin", wizard.calculatedTotalInsulin);
            } catch (JSONException e) {
                log.error("Unhandled exception", e);
            }
            if (wizard.calculatedTotalInsulin > 0d && quickWizardEntry.carbs() > 0d) {
                DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
                String confirmMessage = getString(R.string.entertreatmentquestion);

                Double insulinAfterConstraints = MainApp.getConfigBuilder().applyBolusConstraints(wizard.calculatedTotalInsulin);
                Integer carbsAfterConstraints = MainApp.getConfigBuilder().applyCarbsConstraints(quickWizardEntry.carbs());

                confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                if (!insulinAfterConstraints.equals(wizard.calculatedTotalInsulin) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                    builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                    builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                    builder.show();
                    return;
                }

                final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                final Integer finalCarbsAfterConstraints = carbsAfterConstraints;
                final Context context = getContext();
                final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                accepted = false;
                builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                builder.setMessage(confirmMessage);
                builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        synchronized (builder) {
                            if (accepted) {
                                log.debug("guarding: already accepted");
                                return;
                            }
                            accepted = true;
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                if (wizard.superBolus) {
                                    final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
                                    if (activeloop != null) {
                                        activeloop.superBolusTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000);
                                        MainApp.bus().post(new EventRefreshOverview("WizardDialog"));
                                    }
                                    ConfigBuilderPlugin.getCommandQueue().tempBasalPercent(0, 120, true, new Callback() {
                                        @Override
                                        public void run() {
                                            if (!result.success) {
                                                Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                                i.putExtra("soundid", R.raw.boluserror);
                                                i.putExtra("status", result.comment);
                                                i.putExtra("title", MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                MainApp.instance().startActivity(i);
                                            }
                                        }
                                    });
                                }
                                DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                detailedBolusInfo.eventType = CareportalEvent.BOLUSWIZARD;
                                detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                                detailedBolusInfo.context = context;
                                detailedBolusInfo.boluscalc = boluscalcJSON;
                                detailedBolusInfo.source = Source.USER;
                                ConfigBuilderPlugin.getCommandQueue().bolus(detailedBolusInfo, new Callback() {
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
                                Answers.getInstance().logCustom(new CustomEvent("QuickWizard"));
                            }
                        }
                    }
                });
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.show();
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
        sLoopHandler.removeCallbacksAndMessages(null);
        unregisterForContextMenu(apsModeView);
        unregisterForContextMenu(activeProfileView);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        sRefreshLoop = new Runnable() {
            @Override
            public void run() {
                scheduleUpdateGUI("refreshLoop");
                sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
            }
        };
        sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        registerForContextMenu(apsModeView);
        registerForContextMenu(activeProfileView);
        updateGUI("onResume");
    }

    @Subscribe
    public void onStatusEvent(final EventInitializationChanged ev) {
        scheduleUpdateGUI("EventInitializationChanged");
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        scheduleUpdateGUI("EventPreferenceChange");
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshOverview ev) {
        scheduleUpdateGUI(ev.from);
    }

    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished ev) {
        scheduleUpdateGUI("EventAutosensCalculationFinished");
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        scheduleUpdateGUI("EventTreatmentChange");
    }

    @Subscribe
    public void onStatusEvent(final EventCareportalEventChange ev) {
        scheduleUpdateGUI("EventCareportalEventChange");
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        scheduleUpdateGUI("EventTempBasalChange");
    }

    @Subscribe
    public void onStatusEvent(final EventExtendedBolusChange ev) {
        scheduleUpdateGUI("EventExtendedBolusChange");
    }

//  Handled by EventAutosensCalculationFinished
//    @Subscribe
//    public void onStatusEvent(final EventNewBG ev) {
//        scheduleUpdateGUI("EventNewBG");
//    }

    @Subscribe
    public void onStatusEvent(final EventNewOpenLoopNotification ev) {
        scheduleUpdateGUI("EventNewOpenLoopNotification");
    }

//  Handled by EventAutosensCalculationFinished
//    @Subscribe
//    public void onStatusEvent(final EventNewBasalProfile ev) {
//        scheduleUpdateGUI("EventNewBasalProfile");
//    }

    @Subscribe
    public void onStatusEvent(final EventTempTargetChange ev) {
        scheduleUpdateGUI("EventTempTargetChange");
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged s) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePumpStatus(s.textStatus());
                }
            });
    }

    private void hideTempRecommendation() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (acceptTempLayout != null)
                        acceptTempLayout.setVisibility(View.GONE);
                }
            });
    }

    private void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.notificationID);
    }

    private void updatePumpStatus(String status) {
        if (!status.equals("")) {
            pumpStatusView.setText(status);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
        } else {
            pumpStatusLayout.setVisibility(View.GONE);
            loopStatusLayout.setVisibility(View.VISIBLE);
        }
    }

    public void scheduleUpdateGUI(final String from) {
        class UpdateRunnable implements Runnable {
            public void run() {
                Activity activity = getActivity();
                if (activity != null)
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateGUI(from);
                            scheduledUpdate = null;
                        }
                    });
            }
        }
        // prepare task for execution in 400 msec
        // cancel waiting task to prevent multiple updates
        if (scheduledUpdate != null)
            scheduledUpdate.cancel(false);
        Runnable task = new UpdateRunnable();
        final int msec = 500;
        scheduledUpdate = worker.schedule(task, msec, TimeUnit.MILLISECONDS);
    }

    @SuppressLint("SetTextI18n")
    public void updateGUI(final String from) {
        log.debug("updateGUI entered from: " + from);
        final Date updateGUIStart = new Date();

        if (getActivity() == null)
            return;

        if (timeView != null) { //must not exists
            timeView.setText(DateUtil.timeString(new Date()));
        }
        if (MainApp.getConfigBuilder().getProfile() == null) {// app not initialized yet
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
            return;
        }
        pumpStatusLayout.setVisibility(View.GONE);
        loopStatusLayout.setVisibility(View.VISIBLE);

        updateNotifications();
        CareportalFragment.updateAge(getActivity(), sage, iage, cage, pbage);
        BgReading actualBG = DatabaseHelper.actualBg();
        BgReading lastBG = DatabaseHelper.lastBg();

        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();

        Profile profile = MainApp.getConfigBuilder().getProfile();
        String units = profile.getUnits();

        if (units == null) {
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
            return;
        }

        double lowLineSetting = SP.getDouble("low_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units));
        double highLineSetting = SP.getDouble("high_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units));

        if (lowLineSetting < 1)
            lowLineSetting = Profile.fromMgdlToUnits(76d, units);
        if (highLineSetting < 1)
            highLineSetting = Profile.fromMgdlToUnits(180d, units);

        final double lowLine = lowLineSetting;
        final double highLine = highLineSetting;

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            int color = MainApp.sResources.getColor(R.color.inrange);
            if (lastBG.valueToUnits(units) < lowLine)
                color = MainApp.sResources.getColor(R.color.low);
            else if (lastBG.valueToUnits(units) > highLine)
                color = MainApp.sResources.getColor(R.color.high);
            bgView.setText(lastBG.valueToUnitsToString(units));
            arrowView.setText(lastBG.directionToSymbol());
            bgView.setTextColor(color);
            arrowView.setTextColor(color);
            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
            if (glucoseStatus != null) {
                deltaView.setText("Δ " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
                if (avgdeltaView != null)
                    avgdeltaView.setText("øΔ15m: " + Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units) +
                            "  øΔ40m: " + Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units));
            } else {
                deltaView.setText("Δ " + MainApp.sResources.getString(R.string.notavailable));
                if (avgdeltaView != null)
                    avgdeltaView.setText("");
            }
        }

        // open loop mode
        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
        if (Config.APS && pump.getPumpDescription().isTempBasalCapable) {
            apsModeView.setVisibility(View.VISIBLE);
            apsModeView.setBackgroundColor(MainApp.sResources.getColor(R.color.loopenabled));
            apsModeView.setTextColor(Color.BLACK);
            final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
            if (activeloop != null && activeloop.isEnabled(activeloop.getType()) && activeloop.isSuperBolus()) {
                apsModeView.setBackgroundColor(MainApp.sResources.getColor(R.color.looppumpsuspended));
                apsModeView.setText(String.format(MainApp.sResources.getString(R.string.loopsuperbolusfor), activeloop.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (activeloop != null && activeloop.isEnabled(activeloop.getType()) && activeloop.isSuspended()) {
                apsModeView.setBackgroundColor(MainApp.sResources.getColor(R.color.looppumpsuspended));
                apsModeView.setText(String.format(MainApp.sResources.getString(R.string.loopsuspendedfor), activeloop.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (pump.isSuspended()) {
                apsModeView.setBackgroundColor(MainApp.sResources.getColor(R.color.looppumpsuspended));
                apsModeView.setText(MainApp.sResources.getString(R.string.pumpsuspended));
                apsModeView.setTextColor(Color.WHITE);
            } else if (activeloop != null && activeloop.isEnabled(activeloop.getType())) {
                if (MainApp.getConfigBuilder().isClosedModeEnabled()) {
                    apsModeView.setText(MainApp.sResources.getString(R.string.closedloop));
                } else {
                    apsModeView.setText(MainApp.sResources.getString(R.string.openloop));
                }
            } else {
                apsModeView.setBackgroundColor(MainApp.sResources.getColor(R.color.loopdisabled));
                apsModeView.setText(MainApp.sResources.getString(R.string.disabledloop));
                apsModeView.setTextColor(Color.WHITE);
            }
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        // temp target
        TempTarget tempTarget = MainApp.getConfigBuilder().getTempTargetFromHistory();
        if (tempTarget != null) {
            tempTargetView.setTextColor(Color.BLACK);
            tempTargetView.setBackgroundColor(MainApp.sResources.getColor(R.color.tempTargetBackground));
            tempTargetView.setVisibility(View.VISIBLE);
            tempTargetView.setText(Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end()));
        } else {
            tempTargetView.setTextColor(Color.WHITE);
            tempTargetView.setBackgroundColor(MainApp.sResources.getColor(R.color.tempTargetDisabledBackground));
            tempTargetView.setText(Profile.toTargetRangeString(profile.getTargetLow(), profile.getTargetHigh(), units, units));
            tempTargetView.setVisibility(View.VISIBLE);
        }

        // **** Temp button ****
        if (acceptTempLayout != null) {
            boolean showAcceptButton = !MainApp.getConfigBuilder().isClosedModeEnabled(); // Open mode needed
            showAcceptButton = showAcceptButton && finalLastRun != null && finalLastRun.lastAPSRun != null; // aps result must exist
            showAcceptButton = showAcceptButton && (finalLastRun.lastOpenModeAccept == null || finalLastRun.lastOpenModeAccept.getTime() < finalLastRun.lastAPSRun.getTime()); // never accepted or before last result
            showAcceptButton = showAcceptButton && finalLastRun.constraintsProcessed.changeRequested; // change is requested

            if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && ConfigBuilderPlugin.getActiveLoop() != null) {
                acceptTempLayout.setVisibility(View.VISIBLE);
                acceptTempButton.setText(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
            } else {
                acceptTempLayout.setVisibility(View.GONE);
            }
        }

        // **** Calibration button ****
        if (calibrationButton != null) {
            if (MainApp.getSpecificPlugin(SourceXdripPlugin.class) != null && MainApp.getSpecificPlugin(SourceXdripPlugin.class).isEnabled(PluginBase.BGSOURCE) && profile != null && DatabaseHelper.actualBg() != null) {
                calibrationButton.setVisibility(View.VISIBLE);
            } else {
                calibrationButton.setVisibility(View.GONE);
            }
        }

        final TemporaryBasal activeTemp = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        String basalText = "";
        if (shorttextmode) {
            if (activeTemp != null) {
                basalText = "T: " + activeTemp.toStringVeryShort();
            } else {
                basalText = DecimalFormatter.to2Decimal(MainApp.getConfigBuilder().getProfile().getBasal()) + "U/h";
            }
            baseBasalView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String fullText = MainApp.sResources.getString(R.string.pump_basebasalrate_label) + ": " + DecimalFormatter.to2Decimal(MainApp.getConfigBuilder().getProfile().getBasal()) + "U/h\n";
                    if (activeTemp != null) {
                        fullText += MainApp.sResources.getString(R.string.pump_tempbasal_label) + ": " + activeTemp.toStringFull();
                    }
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.basal), fullText, null);
                }
            });

        } else {
            if (activeTemp != null) {
                basalText = activeTemp.toStringFull() + " ";
            }
            if (Config.NSCLIENT || Config.G5UPLOADER)
                basalText += "(" + DecimalFormatter.to2Decimal(MainApp.getConfigBuilder().getProfile().getBasal()) + " U/h)";
            else if (pump.getPumpDescription().isTempBasalCapable) {
                basalText += "(" + DecimalFormatter.to2Decimal(pump.getBaseBasalRate()) + "U/h)";
            }
        }
        if (activeTemp != null) {
            baseBasalView.setTextColor(MainApp.sResources.getColor(R.color.basal));
        } else {
            baseBasalView.setTextColor(Color.WHITE);

        }

        baseBasalView.setText(basalText);

        final ExtendedBolus extendedBolus = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
        String extendedBolusText = "";
        if (extendedBolusView != null) { // must not exists in all layouts
            if (shorttextmode) {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + "U/h";
                }
                extendedBolusView.setText(extendedBolusText);
                extendedBolusView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.extendedbolus), extendedBolus.toString(), null);
                    }
                });

            } else {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = extendedBolus.toString();
                }
                extendedBolusView.setText(extendedBolusText);
            }
            if (extendedBolusText.equals(""))
                extendedBolusView.setVisibility(View.GONE);
            else
                extendedBolusView.setVisibility(View.VISIBLE);
        }

        activeProfileView.setText(MainApp.getConfigBuilder().getProfileName());
        activeProfileView.setBackgroundColor(Color.GRAY);

        tempTargetView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                NewNSTreatmentDialog newTTDialog = new NewNSTreatmentDialog();
                final OptionsToShow temptarget = CareportalFragment.TEMPTARGET;
                temptarget.executeTempTarget = true;
                newTTDialog.setOptions(temptarget, R.string.careportal_temporarytarget);
                newTTDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                return true;
            }
        });
        tempTargetView.setLongClickable(true);

        // QuickWizard button
        QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = quickWizardEntry.buttonText() + "\n" + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = quickWizardEntry.doCalc(profile, tempTarget, lastBG, false);
            text += " " + DecimalFormatter.to2Decimal(wizard.calculatedTotalInsulin) + "U";
            quickWizardButton.setText(text);
            if (wizard.calculatedTotalInsulin <= 0)
                quickWizardButton.setVisibility(View.GONE);
        } else
            quickWizardButton.setVisibility(View.GONE);

        // Bolus and calc button
        if (pump.isInitialized() && !pump.isSuspended()) {
            wizardButton.setVisibility(View.VISIBLE);
            treatmentButton.setVisibility(View.VISIBLE);
        } else {
            wizardButton.setVisibility(View.GONE);
            treatmentButton.setVisibility(View.GONE);
        }


        // **** BG value ****
        if (lastBG == null) { //left this here as it seems you want to exit at this point if it is null...
            return;
        }
        Integer flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        Long agoMsec = System.currentTimeMillis() - lastBG.date;
        int agoMin = (int) (agoMsec / 60d / 1000d);
        timeAgoView.setText(String.format(MainApp.sResources.getString(R.string.minago), agoMin));

        // iob
        MainApp.getConfigBuilder().updateTotalIOBTreatments();
        MainApp.getConfigBuilder().updateTotalIOBTempBasals();
        final IobTotal bolusIob = MainApp.getConfigBuilder().getLastCalculationTreatments().round();
        final IobTotal basalIob = MainApp.getConfigBuilder().getLastCalculationTempBasals().round();

        if (shorttextmode) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U";
            iobView.setText(iobtext);
            iobView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U\n"
                            + getString(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U\n"
                            + getString(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U\n";
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.iob), iobtext, null);
                }
            });
        } else if (MainApp.sResources.getBoolean(R.bool.isTablet)) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + getString(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                    + getString(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
            iobView.setText(iobtext);
        } else {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "/"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
            iobView.setText(iobtext);
        }

        // cob
        if (cobView != null) { // view must not exists
            String cobText = "";
            AutosensData autosensData = IobCobCalculatorPlugin.getLastAutosensData("Overview COB");
            if (autosensData != null)
                cobText = (int) autosensData.cob + " g";
            cobView.setText(cobText);
        }

        final boolean showPrediction = showPredictionCheckbox.isChecked() && finalLastRun != null && finalLastRun.constraintsProcessed.getClass().equals(DetermineBasalResultAMA.class);
        if (MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class) != null && MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class).isEnabled(PluginBase.APS)) {
            showPredictionCheckbox.setVisibility(View.VISIBLE);
            showPredictionLabel.setVisibility(View.VISIBLE);
        } else {
            showPredictionCheckbox.setVisibility(View.GONE);
            showPredictionLabel.setVisibility(View.GONE);
        }

        // pump status from ns
        if (pumpDeviceStatusView != null) {
            pumpDeviceStatusView.setText(NSDeviceStatus.getInstance().getPumpStatus());
            pumpDeviceStatusView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.pump), NSDeviceStatus.getInstance().getExtendedPumpStatus(), null);
                }
            });
        }

        // OpenAPS status from ns
        if (openapsDeviceStatusView != null) {
            openapsDeviceStatusView.setText(NSDeviceStatus.getInstance().getOpenApsStatus());
            openapsDeviceStatusView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.openaps), NSDeviceStatus.getInstance().getExtendedOpenApsStatus(), null);
                }
            });
        }

        // Uploader status from ns
        if (uploaderDeviceStatusView != null) {
            uploaderDeviceStatusView.setText(NSDeviceStatus.getInstance().getUploaderStatus());
            uploaderDeviceStatusView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.uploader), NSDeviceStatus.getInstance().getExtendedUploaderStatus(), null);
                }
            });
        }

        // ****** GRAPH *******

        new Thread(new Runnable() {
            @Override
            public void run() {
                // allign to hours
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(System.currentTimeMillis());
                calendar.set(Calendar.MILLISECOND, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.add(Calendar.HOUR, 1);

                int hoursToFetch;
                final long toTime;
                final long fromTime;
                final long endTime;
                if (showPrediction) {
                    int predHours = (int) (Math.ceil(((DetermineBasalResultAMA) finalLastRun.constraintsProcessed).getLatestPredictionsTime() - System.currentTimeMillis()) / (60 * 60 * 1000));
                    predHours = Math.min(2, predHours);
                    predHours = Math.max(0, predHours);
                    hoursToFetch = rangeToDisplay - predHours;
                    toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                    fromTime = toTime - hoursToFetch * 60 * 60 * 1000L;
                    endTime = toTime + predHours * 60 * 60 * 1000L;
                } else {
                    hoursToFetch = rangeToDisplay;
                    toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                    fromTime = toTime - hoursToFetch * 60 * 60 * 1000L;
                    endTime = toTime;
                }


                final long now = System.currentTimeMillis();

                //  ------------------ 1st graph
                Profiler.log(log, from + " - 1st graph - START", updateGUIStart);

                final GraphData graphData = new GraphData(bgGraph);

                // **** In range Area ****
                graphData.addInRangeArea(fromTime, endTime, lowLine, highLine);

                // **** BG ****
                if (showPrediction)
                    graphData.addBgReadings(fromTime, toTime, lowLine, highLine, (DetermineBasalResultAMA) finalLastRun.constraintsProcessed);
                else
                    graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null);

                // set manual x bounds to have nice steps
                graphData.formatAxis(fromTime, endTime);

                // Treatments
                graphData.addTreatments(fromTime, endTime);

                // add basal data
                if (pump.getPumpDescription().isTempBasalCapable && showBasalsCheckbox.isChecked()) {
                    graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2d);
                }

                // **** NOW line ****
                graphData.addNowLine(now);

                // ------------------ 2nd graph
                Profiler.log(log, from + " - 2nd graph - START", updateGUIStart);

                final GraphData secondGraphData = new GraphData(iobGraph);

                boolean useIobForScale = false;
                boolean useCobForScale = false;
                boolean useDevForScale = false;
                boolean useRatioForScale = false;

                if (showIobCheckbox.isChecked()) {
                    useIobForScale = true;
                } else if (showCobCheckbox.isChecked()) {
                    useCobForScale = true;
                } else if (showDeviationsCheckbox.isChecked()) {
                    useDevForScale = true;
                } else if (showRatiosCheckbox.isChecked()) {
                    useRatioForScale = true;
                }

                if (showIobCheckbox.isChecked())
                    secondGraphData.addIob(fromTime, now, useIobForScale, 1d);
                if (showCobCheckbox.isChecked())
                    secondGraphData.addCob(fromTime, now, useCobForScale, useCobForScale ? 1d : 0.5d);
                if (showDeviationsCheckbox.isChecked())
                    secondGraphData.addDeviations(fromTime, now, useDevForScale, 1d);
                if (showRatiosCheckbox.isChecked())
                    secondGraphData.addRatio(fromTime, now, useRatioForScale, 1d);

                // **** NOW line ****
                // set manual x bounds to have nice steps
                secondGraphData.formatAxis(fromTime, endTime);
                secondGraphData.addNowLine(now);

                // do GUI update
                FragmentActivity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (showIobCheckbox.isChecked() || showCobCheckbox.isChecked() || showDeviationsCheckbox.isChecked() || showRatiosCheckbox.isChecked()) {
                                iobGraph.setVisibility(View.VISIBLE);
                            } else {
                                iobGraph.setVisibility(View.GONE);
                            }
                            // finally enforce drawing of graphs
                            graphData.performUpdate();
                            secondGraphData.performUpdate();
                            Profiler.log(log, from + " - onDataChanged", updateGUIStart);
                        }
                    });
                }
            }
        }).start();

        Profiler.log(log, from, updateGUIStart);
    }

    //Notifications
    static class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.NotificationsViewHolder> {

        List<Notification> notificationsList;

        RecyclerViewAdapter(List<Notification> notificationsList) {
            this.notificationsList = notificationsList;
        }

        @Override
        public NotificationsViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.overview_notification_item, viewGroup, false);
            return new NotificationsViewHolder(v);
        }

        @Override
        public void onBindViewHolder(NotificationsViewHolder holder, int position) {
            Notification notification = notificationsList.get(position);
            holder.dismiss.setTag(notification);
            if (Objects.equals(notification.text, MainApp.sResources.getString(R.string.nsalarm_staledata)))
                holder.dismiss.setText("snooze");
            holder.text.setText(notification.text);
            holder.time.setText(DateUtil.timeString(notification.date));
            if (notification.level == Notification.URGENT)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationUrgent));
            else if (notification.level == Notification.NORMAL)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationNormal));
            else if (notification.level == Notification.LOW)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationLow));
            else if (notification.level == Notification.INFO)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationInfo));
            else if (notification.level == Notification.ANNOUNCEMENT)
                holder.cv.setBackgroundColor(ContextCompat.getColor(MainApp.instance(), R.color.notificationAnnouncement));
        }

        @Override
        public int getItemCount() {
            return notificationsList.size();
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        static class NotificationsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
            CardView cv;
            TextView time;
            TextView text;
            Button dismiss;

            NotificationsViewHolder(View itemView) {
                super(itemView);
                cv = (CardView) itemView.findViewById(R.id.notification_cardview);
                time = (TextView) itemView.findViewById(R.id.notification_time);
                text = (TextView) itemView.findViewById(R.id.notification_text);
                dismiss = (Button) itemView.findViewById(R.id.notification_dismiss);
                dismiss.setOnClickListener(this);
            }

            @Override
            public void onClick(View v) {
                Notification notification = (Notification) v.getTag();
                switch (v.getId()) {
                    case R.id.notification_dismiss:
                        MainApp.bus().post(new EventDismissNotification(notification.id));
                        if (notification.nsAlarm != null) {
                            BroadcastAckAlarm.handleClearAlarm(notification.nsAlarm, MainApp.instance().getApplicationContext(), 60 * 60 * 1000L);
                        }
                        // Adding current time to snooze if we got staleData
                        log.debug("Notification text is: " + notification.text);
                        if (notification.text.equals(MainApp.sResources.getString(R.string.nsalarm_staledata))) {
                            NotificationStore nstore = OverviewPlugin.getPlugin().notificationStore;
                            long msToSnooze = SP.getInt("nsalarm_staledatavalue", 15) * 60 * 1000L;
                            log.debug("snooze nsalarm_staledatavalue in minutes is " + SP.getInt("nsalarm_staledatavalue", 15) + "\n in ms is: " + msToSnooze + " currentTimeMillis is: " + System.currentTimeMillis());
                            nstore.snoozeTo(System.currentTimeMillis() + (SP.getInt("nsalarm_staledatavalue", 15) * 60 * 1000L));
                        }
                        break;
                }
            }
        }

    }

    void updateNotifications() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    NotificationStore nstore = OverviewPlugin.getPlugin().notificationStore;
                    nstore.removeExpired();
                    nstore.unSnooze();
                    if (nstore.store.size() > 0) {
                        RecyclerViewAdapter adapter = new RecyclerViewAdapter(nstore.store);
                        notificationsView.setAdapter(adapter);
                        notificationsView.setVisibility(View.VISIBLE);
                    } else {
                        notificationsView.setVisibility(View.GONE);
                    }
                }
            });
    }


}
