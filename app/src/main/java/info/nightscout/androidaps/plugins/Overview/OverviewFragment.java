package info.nightscout.androidaps.plugins.Overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LabelFormatter;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.Series;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.ProfileSwitch;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
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
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConstraintsObjectives.ObjectivesPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.BasalData;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.NSClientInternal.broadcasts.BroadcastAckAlarm;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.OpenAPSAMA.DetermineBasalResultAMA;
import info.nightscout.androidaps.plugins.OpenAPSAMA.OpenAPSAMAPlugin;
import info.nightscout.androidaps.plugins.Overview.Dialogs.CalibrationDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTreatmentDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.WizardDialog;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventSetWakeLock;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.AreaGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DataPointWithLabelInterface;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.DoubleDataPoint;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.FixedLineGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.PointsWithLabelGraphSeries;
import info.nightscout.androidaps.plugins.Overview.graphExtensions.TimeAsXAxisLabelFormatter;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripPlugin;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class OverviewFragment extends Fragment implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
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

    CheckBox showPredictionView;
    CheckBox showBasalsView;
    CheckBox showIobView;
    CheckBox showCobView;
    CheckBox showDeviationsView;
    CheckBox showRatiosView;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

    LinearLayout acceptTempLayout;
    Button treatmentButton;
    Button wizardButton;
    Button calibrationButton;
    Button acceptTempButton;
    Button quickWizardButton;

    CheckBox lockScreen;

    boolean smallWidth;
    boolean smallHeight;

    public static boolean shorttextmode = false;

    private int rangeToDisplay = 6; // for graph

    Handler sLoopHandler = new Handler();
    Runnable sRefreshLoop = null;

    private static Handler sHandler;
    private static HandlerThread sHandlerThread;

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledUpdate = null;

    public OverviewFragment() {
        super();
        if (sHandlerThread == null) {
            sHandlerThread = new HandlerThread(OverviewFragment.class.getSimpleName());
            sHandlerThread.start();
            sHandler = new Handler(sHandlerThread.getLooper());
        }
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
            smallWidth = screen_width < Constants.SMALL_WIDTH;
            smallHeight = screen_height < Constants.SMALL_HEIGHT;
            boolean landscape = screen_height < screen_width;

            View view;

            if (MainApp.sResources.getBoolean(R.bool.isTablet) && BuildConfig.NSCLIENTOLNY) {
                view = inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false);
            } else if (BuildConfig.NSCLIENTOLNY) {
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

            treatmentButton = (Button) view.findViewById(R.id.overview_treatmentbutton);
            treatmentButton.setOnClickListener(this);
            wizardButton = (Button) view.findViewById(R.id.overview_wizardbutton);
            wizardButton.setOnClickListener(this);
            acceptTempButton = (Button) view.findViewById(R.id.overview_accepttempbutton);
            if (acceptTempButton != null)
                acceptTempButton.setOnClickListener(this);
            quickWizardButton = (Button) view.findViewById(R.id.overview_quickwizardbutton);
            quickWizardButton.setOnClickListener(this);
            calibrationButton = (Button) view.findViewById(R.id.overview_calibrationbutton);
            if (calibrationButton != null)
                calibrationButton.setOnClickListener(this);

            acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

            showPredictionView = (CheckBox) view.findViewById(R.id.overview_showprediction);
            showBasalsView = (CheckBox) view.findViewById(R.id.overview_showbasals);
            showIobView = (CheckBox) view.findViewById(R.id.overview_showiob);
            showCobView = (CheckBox) view.findViewById(R.id.overview_showcob);
            showDeviationsView = (CheckBox) view.findViewById(R.id.overview_showdeviations);
            showRatiosView = (CheckBox) view.findViewById(R.id.overview_showratios);
            showPredictionView.setChecked(SP.getBoolean("showprediction", false));
            showBasalsView.setChecked(SP.getBoolean("showbasals", true));
            showIobView.setChecked(SP.getBoolean("showiob", false));
            showCobView.setChecked(SP.getBoolean("showcob", false));
            showDeviationsView.setChecked(SP.getBoolean("showdeviations", false));
            showRatiosView.setChecked(SP.getBoolean("showratios", false));
            showPredictionView.setOnCheckedChangeListener(this);
            showBasalsView.setOnCheckedChangeListener(this);
            showIobView.setOnCheckedChangeListener(this);
            showCobView.setOnCheckedChangeListener(this);
            showDeviationsView.setOnCheckedChangeListener(this);
            showRatiosView.setOnCheckedChangeListener(this);

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
        }

        return null;
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final LoopPlugin activeloop = ConfigBuilderPlugin.getActiveLoop();
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
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.overview_showprediction:
            case R.id.overview_showbasals:
            case R.id.overview_showiob:
                break;
            case R.id.overview_showcob:
                showDeviationsView.setOnCheckedChangeListener(null);
                showDeviationsView.setChecked(false);
                showDeviationsView.setOnCheckedChangeListener(this);
                break;
            case R.id.overview_showdeviations:
                showCobView.setOnCheckedChangeListener(null);
                showCobView.setChecked(false);
                showCobView.setOnCheckedChangeListener(this);
                break;
            case R.id.overview_showratios:
                break;
        }
        SP.putBoolean("showiob", showIobView.isChecked());
        SP.putBoolean("showprediction", showPredictionView.isChecked());
        SP.putBoolean("showbasals", showBasalsView.isChecked());
        SP.putBoolean("showcob", showCobView.isChecked());
        SP.putBoolean("showdeviations", showDeviationsView.isChecked());
        SP.putBoolean("showratios", showRatiosView.isChecked());
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
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
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().cancelTempBasal(true);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(600);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor30m))) {
            activeloop.suspendTo(System.currentTimeMillis() + 30L * 60 * 1000);
            updateGUI("suspendmenu");
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 30, true);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(30);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor1h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 1 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 60, true);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(60);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor2h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 2 * 60, true);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(120);
            return true;
        } else if (item.getTitle().equals(MainApp.sResources.getString(R.string.disconnectpumpfor3h))) {
            activeloop.suspendTo(System.currentTimeMillis() + 3 * 60L * 60 * 1000);
            updateGUI("suspendmenu");
            sHandler.post(new Runnable() {
                @Override
                public void run() {
                    PumpEnactResult result = MainApp.getConfigBuilder().setTempBasalAbsolute(0d, 3 * 60, true);
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.sResources.getString(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(180);
            return true;
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
                if (MainApp.getConfigBuilder().isSuspended() || !MainApp.getConfigBuilder().isInitialized())
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            MainApp.getConfigBuilder().refreshDataFromPump("RefreshClicked");
                        }
                    });
                break;
        }

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
                        sHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                hideTempRecommendation();
                                clearNotification();
                                PumpEnactResult applyResult = MainApp.getConfigBuilder().applyAPSRequest(finalLastRun.constraintsProcessed);
                                if (applyResult.enacted) {
                                    finalLastRun.setByPump = applyResult;
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

        QuickWizard.QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && actualBg != null) {
            quickWizardButton.setVisibility(View.VISIBLE);
            BolusWizard wizard = new BolusWizard();
            wizard.doCalc(profile, tempTarget, quickWizardEntry.carbs(), 0d, actualBg.valueToUnits(profile.getUnits()), 0d, true, true, false, false);

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
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(MainApp.sResources.getString(R.string.confirmation));
                builder.setMessage(confirmMessage);
                builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                            final ConfigBuilderPlugin pump = MainApp.getConfigBuilder();
                            sHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                                    detailedBolusInfo.eventType = CareportalEvent.BOLUSWIZARD;
                                    detailedBolusInfo.insulin = finalInsulinAfterConstraints;
                                    detailedBolusInfo.carbs = finalCarbsAfterConstraints;
                                    detailedBolusInfo.context = context;
                                    detailedBolusInfo.boluscalc = boluscalcJSON;
                                    detailedBolusInfo.source = Source.USER;
                                    PumpEnactResult result = pump.deliverTreatment(detailedBolusInfo);
                                    if (!result.success) {
                                        try {
                                            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                            builder.setTitle(MainApp.sResources.getString(R.string.treatmentdeliveryerror));
                                            builder.setMessage(result.comment);
                                            builder.setPositiveButton(MainApp.sResources.getString(R.string.ok), null);
                                            builder.show();
                                        } catch (WindowManager.BadTokenException | NullPointerException e) {
                                            // window has been destroyed
                                            Notification notification = new Notification(Notification.BOLUS_DELIVERY_ERROR, MainApp.sResources.getString(R.string.treatmentdeliveryerror), Notification.URGENT);
                                            MainApp.bus().post(new EventNewNotification(notification));
                                        }
                                    }
                                }
                            });
                            Answers.getInstance().logCustom(new CustomEvent("QuickWizard"));
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
    public void updateGUI(String from) {
        log.debug("updateGUI entered from: " + from);
        Date updateGUIStart = new Date();

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

        PumpInterface pump = MainApp.getConfigBuilder();

        Profile profile = MainApp.getConfigBuilder().getProfile();
        String units = profile.getUnits();

        if (units == null) {
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
            return;
        }

        Double lowLine = SP.getDouble("low_mark", 0d);
        Double highLine = SP.getDouble("high_mark", 0d);

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
        if (Config.APS && MainApp.getConfigBuilder().getPumpDescription().isTempBasalCapable) {
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
            tempTargetView.setText(Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units));
        } else {
            tempTargetView.setTextColor(Color.WHITE);
            tempTargetView.setBackgroundColor(MainApp.sResources.getColor(R.color.tempTargetDisabledBackground));
            tempTargetView.setText(Profile.toTargetRangeString(profile.getTargetLow(), profile.getTargetHigh(), units, units));
            tempTargetView.setVisibility(View.VISIBLE);
        }
        if (Config.NSCLIENT && tempTarget == null) {
            tempTargetView.setVisibility(View.GONE);
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
                    String fullText = MainApp.sResources.getString(R.string.virtualpump_basebasalrate_label) + ": " + DecimalFormatter.to2Decimal(MainApp.getConfigBuilder().getProfile().getBasal()) + "U/h\n";
                    if (activeTemp != null) {
                        fullText += MainApp.sResources.getString(R.string.virtualpump_tempbasal_label) + ": " + activeTemp.toStringFull();
                    }
                    OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.basal), fullText, null);
                }
            });

        } else {
            if (activeTemp != null) {
                basalText = activeTemp.toStringFull() + " ";
            }
            if (Config.NSCLIENT)
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
        if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
            extendedBolusText = extendedBolus.toString();
        }
        if (extendedBolusView != null) { // must not exists in all layouts
            if (shorttextmode) {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + "U/h";
                } else {
                    extendedBolusText = "";
                }
                extendedBolusView.setText(extendedBolusText);
                extendedBolusView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        OKDialog.show(getActivity(), MainApp.sResources.getString(R.string.extendedbolus), extendedBolus.toString(), null);
                    }
                });

            } else {
                extendedBolusView.setText(extendedBolusText);
            }
        }

        activeProfileView.setText(MainApp.getConfigBuilder().getProfileName());
        activeProfileView.setBackgroundColor(Color.GRAY);

        activeProfileView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
                final OptionsToShow profileswitch = CareportalFragment.profileswitch;
                profileswitch.executeProfileSwitch = true;
                newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
                newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                return true;
            }
        });
        activeProfileView.setLongClickable(true);


        tempTargetView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                NewNSTreatmentDialog newTTDialog = new NewNSTreatmentDialog();
                final OptionsToShow temptarget = CareportalFragment.temptarget;
                temptarget.executeTempTarget = true;
                newTTDialog.setOptions(temptarget, R.string.careportal_temporarytarget);
                newTTDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
                return true;
            }
        });
        tempTargetView.setLongClickable(true);

        // QuickWizard button
        QuickWizard.QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = quickWizardEntry.buttonText() + "\n" + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = new BolusWizard();
            wizard.doCalc(profile, tempTarget, quickWizardEntry.carbs(), 0d, lastBG.valueToUnits(units), 0d, true, true, false, false);
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


        if (lowLine < 1) {
            lowLine = Profile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units);
        }
        if (highLine < 1) {
            highLine = Profile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units);
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
            AutosensData autosensData = IobCobCalculatorPlugin.getAutosensData(System.currentTimeMillis());
            if (autosensData != null)
                cobText = (int) autosensData.cob + " g";
            cobView.setText(cobText);
        }

        boolean showPrediction = showPredictionView.isChecked() && finalLastRun != null && finalLastRun.constraintsProcessed.getClass().equals(DetermineBasalResultAMA.class);
        if (MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class) != null && MainApp.getSpecificPlugin(OpenAPSAMAPlugin.class).isEnabled(PluginBase.APS)) {
            showPredictionView.setVisibility(View.VISIBLE);
            getActivity().findViewById(R.id.overview_showprediction_label).setVisibility(View.VISIBLE);
        } else {
            showPredictionView.setVisibility(View.GONE);
            getActivity().findViewById(R.id.overview_showprediction_label).setVisibility(View.GONE);
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
        //log.debug("updateGUI checkpoint 1");

        // allign to hours
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.HOUR, 1);

        int hoursToFetch;
        long toTime;
        long fromTime;
        long endTime;
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

        LineGraphSeries<DataPoint> basalsLineSeries = null;
        LineGraphSeries<DataPoint> absoluteBasalsLineSeries = null;
        LineGraphSeries<DataPoint> baseBasalsSeries = null;
        LineGraphSeries<DataPoint> tempBasalsSeries = null;
        AreaGraphSeries<DoubleDataPoint> areaSeries;
        LineGraphSeries<DataPoint> seriesNow, seriesNow2;

        // **** TEMP BASALS graph ****
        Double maxBasalValueFound = 0d;

        long now = System.currentTimeMillis();
        if (pump.getPumpDescription().isTempBasalCapable && showBasalsView.isChecked()) {
            List<DataPoint> baseBasalArray = new ArrayList<>();
            List<DataPoint> tempBasalArray = new ArrayList<>();
            List<DataPoint> basalLineArray = new ArrayList<>();
            List<DataPoint> absoluteBasalLineArray = new ArrayList<>();
            double lastLineBasal = 0;
            double lastAbsoluteLineBasal = 0;
            double lastBaseBasal = 0;
            double lastTempBasal = 0;
            for (long time = fromTime; time < now; time += 60 * 1000L) {
                BasalData basalData = IobCobCalculatorPlugin.getBasalData(time);
                double baseBasalValue = basalData.basal;
                double absoluteLineValue = baseBasalValue;
                double tempBasalValue = 0;
                double basal = 0d;
                if (basalData.isTempBasalRunning) {
                    absoluteLineValue = tempBasalValue = basalData.tempBasalAbsolute;
                    if (tempBasalValue != lastTempBasal) {
                        tempBasalArray.add(new DataPoint(time, lastTempBasal));
                        tempBasalArray.add(new DataPoint(time, basal = tempBasalValue));
                    }
                    if (lastBaseBasal != 0d) {
                        baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                        baseBasalArray.add(new DataPoint(time, 0d));
                        lastBaseBasal = 0d;
                    }
                } else {
                    if (baseBasalValue != lastBaseBasal) {
                        baseBasalArray.add(new DataPoint(time, lastBaseBasal));
                        baseBasalArray.add(new DataPoint(time, basal = baseBasalValue));
                        lastBaseBasal = baseBasalValue;
                    }
                    if (lastTempBasal != 0) {
                        tempBasalArray.add(new DataPoint(time, lastTempBasal));
                        tempBasalArray.add(new DataPoint(time, 0d));
                    }
                }

                if (baseBasalValue != lastLineBasal) {
                    basalLineArray.add(new DataPoint(time, lastLineBasal));
                    basalLineArray.add(new DataPoint(time, baseBasalValue));
                }
                if (absoluteLineValue != lastAbsoluteLineBasal) {
                    absoluteBasalLineArray.add(new DataPoint(time, lastAbsoluteLineBasal));
                    absoluteBasalLineArray.add(new DataPoint(time, basal));
                }

                lastAbsoluteLineBasal = absoluteLineValue;
                lastLineBasal = baseBasalValue;
                lastTempBasal = tempBasalValue;
                maxBasalValueFound = Math.max(maxBasalValueFound, basal);
            }
            basalLineArray.add(new DataPoint(now, lastLineBasal));
            baseBasalArray.add(new DataPoint(now, lastBaseBasal));
            tempBasalArray.add(new DataPoint(now, lastTempBasal));
            absoluteBasalLineArray.add(new DataPoint(now, lastAbsoluteLineBasal));

            DataPoint[] baseBasal = new DataPoint[baseBasalArray.size()];
            baseBasal = baseBasalArray.toArray(baseBasal);
            baseBasalsSeries = new LineGraphSeries<>(baseBasal);
            baseBasalsSeries.setDrawBackground(true);
            baseBasalsSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.basebasal));
            baseBasalsSeries.setThickness(0);

            DataPoint[] tempBasal = new DataPoint[tempBasalArray.size()];
            tempBasal = tempBasalArray.toArray(tempBasal);
            tempBasalsSeries = new LineGraphSeries<>(tempBasal);
            tempBasalsSeries.setDrawBackground(true);
            tempBasalsSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.tempbasal));
            tempBasalsSeries.setThickness(0);

            DataPoint[] basalLine = new DataPoint[basalLineArray.size()];
            basalLine = basalLineArray.toArray(basalLine);
            basalsLineSeries = new LineGraphSeries<>(basalLine);
            Paint paint = new Paint();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setPathEffect(new DashPathEffect(new float[]{2, 4}, 0));
            paint.setColor(MainApp.sResources.getColor(R.color.basal));
            basalsLineSeries.setCustomPaint(paint);

            DataPoint[] absoluteBasalLine = new DataPoint[absoluteBasalLineArray.size()];
            absoluteBasalLine = absoluteBasalLineArray.toArray(absoluteBasalLine);
            absoluteBasalsLineSeries = new LineGraphSeries<>(absoluteBasalLine);
            Paint absolutePaint = new Paint();
            absolutePaint.setStyle(Paint.Style.STROKE);
            absolutePaint.setStrokeWidth(4);
            absolutePaint.setColor(MainApp.sResources.getColor(R.color.basal));
            absoluteBasalsLineSeries.setCustomPaint(absolutePaint);
        }

        //log.debug("updateGUI checkpoint 2");

        // **** IOB COB DEV graph ****
        class DeviationDataPoint extends DataPoint {
            public int color;

            public DeviationDataPoint(double x, double y, int color) {
                super(x, y);
                this.color = color;
            }
        }
        FixedLineGraphSeries<DataPoint> iobSeries;
        FixedLineGraphSeries<DataPoint> cobSeries;
        BarGraphSeries<DeviationDataPoint> devSeries;
        LineGraphSeries<DataPoint> ratioSeries;
        Double maxIobValueFound = 0d;
        Double maxCobValueFound = 0d;
        Double maxDevValueFound = 0d;
        Double maxRatioValueFound = 0d;

        if (showIobView.isChecked() || showCobView.isChecked() || showDeviationsView.isChecked() || showRatiosView.isChecked()) {
            //Date start = new Date();
            List<DataPoint> iobArray = new ArrayList<>();
            List<DataPoint> cobArray = new ArrayList<>();
            List<DeviationDataPoint> devArray = new ArrayList<>();
            List<DataPoint> ratioArray = new ArrayList<>();
            double lastIob = 0;
            int lastCob = 0;
            for (long time = fromTime; time <= now; time += 5 * 60 * 1000L) {
                if (showIobView.isChecked()) {
                    double iob = IobCobCalculatorPlugin.calculateFromTreatmentsAndTempsSynchronized(time).iob;
                    if (Math.abs(lastIob - iob) > 0.02) {
                        if (Math.abs(lastIob - iob) > 0.2)
                            iobArray.add(new DataPoint(time, lastIob));
                        iobArray.add(new DataPoint(time, iob));
                        maxIobValueFound = Math.max(maxIobValueFound, Math.abs(iob));
                        lastIob = iob;
                    }
                }
                if (showCobView.isChecked() || showDeviationsView.isChecked() || showRatiosView.isChecked()) {
                    AutosensData autosensData = IobCobCalculatorPlugin.getAutosensData(time);
                    if (autosensData != null && showCobView.isChecked()) {
                        int cob = (int) autosensData.cob;
                        if (cob != lastCob) {
                            if (autosensData.carbsFromBolus > 0)
                                cobArray.add(new DataPoint(time, lastCob));
                            cobArray.add(new DataPoint(time, cob));
                            maxCobValueFound = Math.max(maxCobValueFound, cob);
                            lastCob = cob;
                        }
                    }
                    if (autosensData != null && showDeviationsView.isChecked()) {
                        int color = Color.BLACK; // "="
                        if (autosensData.pastSensitivity.equals("C")) color = Color.GRAY;
                        if (autosensData.pastSensitivity.equals("+")) color = Color.GREEN;
                        if (autosensData.pastSensitivity.equals("-")) color = Color.RED;
                        devArray.add(new DeviationDataPoint(time, autosensData.deviation, color));
                        maxDevValueFound = Math.max(maxDevValueFound, Math.abs(autosensData.deviation));
                    }
                    if (autosensData != null && showRatiosView.isChecked()) {
                        ratioArray.add(new DataPoint(time, autosensData.autosensRatio));
                        maxRatioValueFound = Math.max(maxRatioValueFound, Math.abs(autosensData.autosensRatio));
                    }
                }
            }
            //Profiler.log(log, "IOB processed", start);
            DataPoint[] iobData = new DataPoint[iobArray.size()];
            iobData = iobArray.toArray(iobData);
            iobSeries = new FixedLineGraphSeries<>(iobData);
            iobSeries.setDrawBackground(true);
            iobSeries.setBackgroundColor(0x80FFFFFF & MainApp.sResources.getColor(R.color.iob)); //50%
            iobSeries.setColor(MainApp.sResources.getColor(R.color.iob));
            iobSeries.setThickness(3);


            Double maxByScale = null;
            int graphsToShow = 0;
            if (showIobView.isChecked()) {
                if (maxByScale == null) maxByScale = maxIobValueFound;
                graphsToShow++;
            }
            if (showCobView.isChecked()) {
                if (maxByScale == null) maxByScale = maxCobValueFound;
                graphsToShow++;
            }
            if (showDeviationsView.isChecked()) {
                if (maxByScale == null) maxByScale = maxDevValueFound;
                graphsToShow++;
            }
            if (showRatiosView.isChecked()) {
                if (maxByScale == null) maxByScale = maxRatioValueFound;
                graphsToShow++;
            }

            if (graphsToShow > 1) {
                if (!maxByScale.equals(maxCobValueFound)) {
                    List<DataPoint> cobArrayRescaled = new ArrayList<>();
                    for (int ci = 0; ci < cobArray.size(); ci++) {
                        cobArrayRescaled.add(new DataPoint(cobArray.get(ci).getX(), cobArray.get(ci).getY() * maxByScale / maxCobValueFound / 2));
                    }
                    cobArray = cobArrayRescaled;
                }
                if (!maxByScale.equals(maxDevValueFound)) {
                    List<DeviationDataPoint> devArrayRescaled = new ArrayList<>();
                    for (int ci = 0; ci < devArray.size(); ci++) {
                        devArrayRescaled.add(new DeviationDataPoint(devArray.get(ci).getX(), devArray.get(ci).getY() * maxByScale / maxDevValueFound, devArray.get(ci).color));
                    }
                    devArray = devArrayRescaled;
                }
                if (!maxByScale.equals(maxRatioValueFound)) {
                    List<DataPoint> ratioArrayRescaled = new ArrayList<>();
                    for (int ci = 0; ci < ratioArray.size(); ci++) {
                        ratioArrayRescaled.add(new DataPoint(ratioArray.get(ci).getX(), (ratioArray.get(ci).getY() - 1) * maxByScale / maxRatioValueFound));
                    }
                    ratioArray = ratioArrayRescaled;
                }
            }

            // COB
            DataPoint[] cobData = new DataPoint[cobArray.size()];
            cobData = cobArray.toArray(cobData);
            cobSeries = new FixedLineGraphSeries<>(cobData);
            cobSeries.setDrawBackground(true);
            cobSeries.setBackgroundColor(0xB0FFFFFF & MainApp.sResources.getColor(R.color.cob)); //50%
            cobSeries.setColor(MainApp.sResources.getColor(R.color.cob));
            cobSeries.setThickness(3);

            // DEVIATIONS
            DeviationDataPoint[] devData = new DeviationDataPoint[devArray.size()];
            devData = devArray.toArray(devData);
            devSeries = new BarGraphSeries<>(devData);
            devSeries.setValueDependentColor(new ValueDependentColor<DeviationDataPoint>() {
                @Override
                public int get(DeviationDataPoint data) {
                    return data.color;
                }
            });

            // RATIOS
            DataPoint[] ratioData = new DataPoint[ratioArray.size()];
            ratioData = ratioArray.toArray(ratioData);
            ratioSeries = new LineGraphSeries<>(ratioData);
            ratioSeries.setColor(MainApp.sResources.getColor(R.color.ratio));
            ratioSeries.setThickness(3);

            iobGraph.getSeries().clear();

            if (showIobView.isChecked() && iobData.length > 0) {
                addSeriesWithoutInvalidate(iobSeries, iobGraph);
            }
            if (showCobView.isChecked() && cobData.length > 0) {
                addSeriesWithoutInvalidate(cobSeries, iobGraph);
            }
            if (showDeviationsView.isChecked() && devData.length > 0) {
                addSeriesWithoutInvalidate(devSeries, iobGraph);
            }
            if (showRatiosView.isChecked() && ratioData.length > 0) {
                addSeriesWithoutInvalidate(ratioSeries, iobGraph);
            }
            iobGraph.setVisibility(View.VISIBLE);
        } else {
            iobGraph.setVisibility(View.GONE);
        }
        //log.debug("updateGUI checkpoint 3");

        // remove old data from graph
        bgGraph.getSecondScale().getSeries().clear();
        bgGraph.getSeries().clear();
        //log.debug("updateGUI checkpoint 4");

        // **** Area ****
        DoubleDataPoint[] areaDataPoints = new DoubleDataPoint[]{
                new DoubleDataPoint(fromTime, lowLine, highLine),
                new DoubleDataPoint(endTime, lowLine, highLine)
        };
        areaSeries = new AreaGraphSeries<>(areaDataPoints);
        addSeriesWithoutInvalidate(areaSeries, bgGraph);
        areaSeries.setColor(0);
        areaSeries.setDrawBackground(true);
        areaSeries.setBackgroundColor(MainApp.sResources.getColor(R.color.inrangebackground));

        // set manual x bounds to have nice steps
        bgGraph.getViewport().setMaxX(endTime);
        bgGraph.getViewport().setMinX(fromTime);
        bgGraph.getViewport().setXAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        bgGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space
        iobGraph.getViewport().setMaxX(endTime);
        iobGraph.getViewport().setMinX(fromTime);
        iobGraph.getViewport().setXAxisBoundsManual(true);
        iobGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        iobGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space

        //log.debug("updateGUI checkpoint 5");
        // **** BG graph ****
        List<BgReading> bgReadingsArray = MainApp.getDbHelper().getBgreadingsDataFromTime(fromTime, true);
        List<DataPointWithLabelInterface> bgListArray = new ArrayList<>();

        if (bgReadingsArray.size() == 0) {
            return;
        }

        Iterator<BgReading> it = bgReadingsArray.iterator();
        Double maxBgValue = 0d;
        while (it.hasNext()) {
            BgReading bg = it.next();
            if (bg.value > maxBgValue) maxBgValue = bg.value;
            bgListArray.add(bg);
        }
        if (showPrediction) {
            DetermineBasalResultAMA amaResult = (DetermineBasalResultAMA) finalLastRun.constraintsProcessed;
            List<BgReading> predArray = amaResult.getPredictions();
            bgListArray.addAll(predArray);
        }

        maxBgValue = Profile.fromMgdlToUnits(maxBgValue, units);
        maxBgValue = units.equals(Constants.MGDL) ? Round.roundTo(maxBgValue, 40d) + 80 : Round.roundTo(maxBgValue, 2d) + 4;
        if (highLine > maxBgValue) maxBgValue = highLine;
        Integer numOfVertLines = units.equals(Constants.MGDL) ? (int) (maxBgValue / 40 + 1) : (int) (maxBgValue / 2 + 1);

        DataPointWithLabelInterface[] bg = new DataPointWithLabelInterface[bgListArray.size()];
        bg = bgListArray.toArray(bg);

        if (bg.length > 0) {
            addSeriesWithoutInvalidate(new PointsWithLabelGraphSeries<>(bg), bgGraph);
        }

        //log.debug("updateGUI checkpoint 6");
        // Treatments
        List<DataPointWithLabelInterface> filteredTreatments = new ArrayList<>();

        List<Treatment> treatments = MainApp.getConfigBuilder().getTreatmentsFromHistory();

        for (int tx = 0; tx < treatments.size(); tx++) {
            Treatment t = treatments.get(tx);
            if (t.getX() < fromTime || t.getX() > endTime) continue;
            t.setY(getNearestBg((long) t.getX(), bgReadingsArray));
            filteredTreatments.add(t);
        }

        //log.debug("updateGUI checkpoint 7");
        // ProfileSwitch
        List<ProfileSwitch> profileSwitches = MainApp.getConfigBuilder().getProfileSwitchesFromHistory().getList();

        for (int tx = 0; tx < profileSwitches.size(); tx++) {
            DataPointWithLabelInterface t = profileSwitches.get(tx);
            if (t.getX() < fromTime || t.getX() > endTime) continue;
            filteredTreatments.add(t);
        }

        //log.debug("updateGUI checkpoint 8");
        // Extended bolus
        if (!pump.isFakingTempsByExtendedBoluses()) {
            List<ExtendedBolus> extendedBoluses = MainApp.getConfigBuilder().getExtendedBolusesFromHistory().getList();

            for (int tx = 0; tx < extendedBoluses.size(); tx++) {
                DataPointWithLabelInterface t = extendedBoluses.get(tx);
                if (t.getX() + t.getDuration() < fromTime || t.getX() > endTime) continue;
                if (t.getDuration() == 0) continue;
                t.setY(getNearestBg((long) t.getX(), bgReadingsArray));
                filteredTreatments.add(t);
            }
        }

        //log.debug("updateGUI checkpoint 9");
        // Careportal
        List<CareportalEvent> careportalEvents = MainApp.getDbHelper().getCareportalEventsFromTime(fromTime, true);

        for (int tx = 0; tx < careportalEvents.size(); tx++) {
            DataPointWithLabelInterface t = careportalEvents.get(tx);
            if (t.getX() + t.getDuration() < fromTime || t.getX() > endTime) continue;
            t.setY(getNearestBg((long) t.getX(), bgReadingsArray));
            filteredTreatments.add(t);
        }

        DataPointWithLabelInterface[] treatmentsArray = new DataPointWithLabelInterface[filteredTreatments.size()];
        treatmentsArray = filteredTreatments.toArray(treatmentsArray);
        if (treatmentsArray.length > 0) {
            addSeriesWithoutInvalidate(new PointsWithLabelGraphSeries<>(treatmentsArray), bgGraph);
        }
        //log.debug("updateGUI checkpoint 10");

        // set manual y bounds to have nice steps
        bgGraph.getViewport().setMaxY(maxBgValue);
        bgGraph.getViewport().setMinY(0);
        bgGraph.getViewport().setYAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setNumVerticalLabels(numOfVertLines);

        // set second scale
        if (pump.getPumpDescription().isTempBasalCapable && showBasalsView.isChecked()) {
            bgGraph.getSecondScale().setMinY(0);
            bgGraph.getSecondScale().setMaxY(maxBgValue / lowLine * maxBasalValueFound * 1.2d);
            bgGraph.getSecondScale().addSeries(baseBasalsSeries);
            bgGraph.getSecondScale().addSeries(tempBasalsSeries);
            bgGraph.getSecondScale().addSeries(basalsLineSeries);
            bgGraph.getSecondScale().addSeries(absoluteBasalsLineSeries);
        }
        bgGraph.getSecondScale().setLabelFormatter(new LabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                return "";
            }

            @Override
            public void setViewport(Viewport viewport) {

            }
        });

        //log.debug("updateGUI checkpoint 11");
        // **** NOW line ****
        DataPoint[] nowPoints = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxBgValue)
        };
        addSeriesWithoutInvalidate(seriesNow = new LineGraphSeries<>(nowPoints), bgGraph);
        seriesNow.setDrawDataPoints(false);
        DataPoint[] nowPoints2 = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxIobValueFound)
        };
        addSeriesWithoutInvalidate(seriesNow2 = new LineGraphSeries<>(nowPoints2), iobGraph);
        seriesNow2.setDrawDataPoints(false);
        //seriesNow.setThickness(1);
        // custom paint to make a dotted line
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
        paint.setColor(Color.WHITE);
        seriesNow.setCustomPaint(paint);
        seriesNow2.setCustomPaint(paint);
        bgGraph.onDataChanged(false, false);
        iobGraph.onDataChanged(false, false);

        Profiler.log(log, from, updateGUIStart);
    }

    public double getNearestBg(long date, List<BgReading> bgReadingsArray) {
        double bg = 0;
        String units = MainApp.getConfigBuilder().getProfileUnits();
        for (int r = bgReadingsArray.size() - 1; r >= 0; r--) {
            BgReading reading = bgReadingsArray.get(r);
            if (reading.date > date) continue;
            bg = Profile.fromMgdlToUnits(reading.value, units);
            break;
        }
        return bg;
    }

    void addSeriesWithoutInvalidate(Series s, GraphView graph) {
        s.onGraphViewAttached(graph);
        graph.getSeries().add(s);
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
