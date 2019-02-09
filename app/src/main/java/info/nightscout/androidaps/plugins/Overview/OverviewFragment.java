package info.nightscout.androidaps.plugins.Overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventProfileSwitchChange;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.Careportal.CareportalFragment;
import info.nightscout.androidaps.plugins.Careportal.Dialogs.NewNSTreatmentDialog;
import info.nightscout.androidaps.plugins.Careportal.OptionsToShow;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.IobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.IobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.Loop.APSResult;
import info.nightscout.androidaps.plugins.Loop.LoopPlugin;
import info.nightscout.androidaps.plugins.Loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.NSClientInternal.NSUpload;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.Overview.Dialogs.CalibrationDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewCarbsDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewInsulinDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTreatmentDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.WizardDialog;
import info.nightscout.androidaps.plugins.Overview.activities.QuickWizardListActivity;
import info.nightscout.androidaps.plugins.Overview.graphData.GraphData;
import info.nightscout.androidaps.plugins.Overview.notifications.NotificationRecyclerViewAdapter;
import info.nightscout.androidaps.plugins.Overview.notifications.NotificationStore;
import info.nightscout.androidaps.plugins.Source.SourceDexcomG5Plugin;
import info.nightscout.androidaps.plugins.Source.SourceXdripPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.plugins.Treatments.fragments.ProfileViewerDialog;
import info.nightscout.androidaps.plugins.Wear.ActionStringHandler;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.utils.BolusWizard;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.DefaultValueHelper;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.Profiler;
import info.nightscout.utils.SP;
import info.nightscout.utils.SingleClickButton;
import info.nightscout.utils.T;
import info.nightscout.utils.ToastUtils;

import static info.nightscout.utils.DateUtil.now;

public class OverviewFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
    private static Logger log = LoggerFactory.getLogger(L.OVERVIEW);

    TextView timeView;
    TextView bgView;
    TextView arrowView;
    TextView sensitivityView;
    TextView timeAgoView;
    TextView timeAgoShortView;
    TextView deltaView;
    TextView deltaShortView;
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
    TextView iobCalculationProgressView;
    LinearLayout loopStatusLayout;
    LinearLayout pumpStatusLayout;
    GraphView bgGraph;
    GraphView iobGraph;
    ImageButton chartButton;

    TextView iage;
    TextView cage;
    TextView sage;
    TextView pbage;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

    LinearLayout acceptTempLayout;
    SingleClickButton acceptTempButton;

    SingleClickButton treatmentButton;
    SingleClickButton wizardButton;
    SingleClickButton calibrationButton;
    SingleClickButton insulinButton;
    SingleClickButton carbsButton;
    SingleClickButton cgmButton;
    SingleClickButton quickWizardButton;

    boolean smallWidth;
    boolean smallHeight;

    public static boolean shorttextmode = false;

    private boolean accepted;

    private int rangeToDisplay = 6; // for graph

    Handler sLoopHandler = new Handler();
    Runnable sRefreshLoop = null;

    public enum CHARTTYPE {PRE, BAS, IOB, COB, DEV, SEN, DEVSLOPE}

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledUpdate = null;

    public OverviewFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //check screen width
        final DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screen_width = dm.widthPixels;
        int screen_height = dm.heightPixels;
        smallWidth = screen_width <= Constants.SMALL_WIDTH;
        smallHeight = screen_height <= Constants.SMALL_HEIGHT;
        boolean landscape = screen_height < screen_width;

        View view;

        if (MainApp.sResources.getBoolean(R.bool.isTablet) && (Config.NSCLIENT)) {
            view = inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false);
        } else if (Config.NSCLIENT) {
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
        sensitivityView = (TextView) view.findViewById(R.id.overview_sensitivity);
        timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
        timeAgoShortView = (TextView) view.findViewById(R.id.overview_timeagoshort);
        deltaView = (TextView) view.findViewById(R.id.overview_delta);
        deltaShortView = (TextView) view.findViewById(R.id.overview_deltashort);
        avgdeltaView = (TextView) view.findViewById(R.id.overview_avgdelta);
        baseBasalView = (TextView) view.findViewById(R.id.overview_basebasal);
        extendedBolusView = (TextView) view.findViewById(R.id.overview_extendedbolus);
        activeProfileView = (TextView) view.findViewById(R.id.overview_activeprofile);
        pumpStatusView = (TextView) view.findViewById(R.id.overview_pumpstatus);
        pumpDeviceStatusView = (TextView) view.findViewById(R.id.overview_pump);
        openapsDeviceStatusView = (TextView) view.findViewById(R.id.overview_openaps);
        uploaderDeviceStatusView = (TextView) view.findViewById(R.id.overview_uploader);
        iobCalculationProgressView = (TextView) view.findViewById(R.id.overview_iobcalculationprogess);
        loopStatusLayout = (LinearLayout) view.findViewById(R.id.overview_looplayout);
        pumpStatusLayout = (LinearLayout) view.findViewById(R.id.overview_pumpstatuslayout);

        pumpStatusView.setBackgroundColor(MainApp.gc(R.color.colorInitializingBorder));

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
        insulinButton = (SingleClickButton) view.findViewById(R.id.overview_insulinbutton);
        if (insulinButton != null)
            insulinButton.setOnClickListener(this);
        carbsButton = (SingleClickButton) view.findViewById(R.id.overview_carbsbutton);
        if (carbsButton != null)
            carbsButton.setOnClickListener(this);
        acceptTempButton = (SingleClickButton) view.findViewById(R.id.overview_accepttempbutton);
        if (acceptTempButton != null)
            acceptTempButton.setOnClickListener(this);
        quickWizardButton = (SingleClickButton) view.findViewById(R.id.overview_quickwizardbutton);
        quickWizardButton.setOnClickListener(this);
        quickWizardButton.setOnLongClickListener(this);
        calibrationButton = (SingleClickButton) view.findViewById(R.id.overview_calibrationbutton);
        if (calibrationButton != null)
            calibrationButton.setOnClickListener(this);
        cgmButton = (SingleClickButton) view.findViewById(R.id.overview_cgmbutton);
        if (cgmButton != null)
            cgmButton.setOnClickListener(this);

        acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

        notificationsView = (RecyclerView) view.findViewById(R.id.overview_notifications);
        notificationsView.setHasFixedSize(false);
        llm = new LinearLayoutManager(view.getContext());
        notificationsView.setLayoutManager(llm);

        int axisWidth = 50;

        if (dm.densityDpi <= 120)
            axisWidth = 3;
        else if (dm.densityDpi <= 160)
            axisWidth = 10;
        else if (dm.densityDpi <= 320)
            axisWidth = 35;
        else if (dm.densityDpi <= 420)
            axisWidth = 50;
        else if (dm.densityDpi <= 560)
            axisWidth = 70;
        else
            axisWidth = 80;

        bgGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(3);

        rangeToDisplay = SP.getInt(R.string.key_rangetodisplay, 6);

        bgGraph.setOnLongClickListener(v -> {
            rangeToDisplay += 6;
            rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
            SP.putInt(R.string.key_rangetodisplay, rangeToDisplay);
            updateGUI("rangeChange");
            return false;
        });

        setupChartMenu(view);

        return view;
    }

    private void setupChartMenu(View view) {
        chartButton = (ImageButton) view.findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(v -> {
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            boolean predictionsAvailable;
            if (Config.APS)
                predictionsAvailable = finalLastRun != null && finalLastRun.request.hasPredictions;
            else if (Config.NSCLIENT)
                predictionsAvailable = true;
            else
                predictionsAvailable = false;

            MenuItem item;
            CharSequence title;
            SpannableString s;
            PopupMenu popup = new PopupMenu(v.getContext(), v);

            if (predictionsAvailable) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.PRE.ordinal(), Menu.NONE, "Predictions");
                title = item.getTitle();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.prediction, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(SP.getBoolean("showprediction", true));
            }

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.BAS.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_basals));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.basal, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showbasals", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.IOB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_iob));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.iob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showiob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.COB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_cob));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.cob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showcob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEV.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_deviations));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.deviations, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showdeviations", false));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.SEN.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_sensitivity));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.ratio, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(SP.getBoolean("showratios", false));

            if (MainApp.devBranch) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEVSLOPE.ordinal(), Menu.NONE, "Deviation slope");
                title = item.getTitle();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.devslopepos, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(SP.getBoolean("showdevslope", false));
            }

            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == CHARTTYPE.PRE.ordinal()) {
                        SP.putBoolean("showprediction", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.BAS.ordinal()) {
                        SP.putBoolean("showbasals", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.IOB.ordinal()) {
                        SP.putBoolean("showiob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.COB.ordinal()) {
                        SP.putBoolean("showcob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEV.ordinal()) {
                        SP.putBoolean("showdeviations", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.SEN.ordinal()) {
                        SP.putBoolean("showratios", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEVSLOPE.ordinal()) {
                        SP.putBoolean("showdevslope", !item.isChecked());
                    }
                    scheduleUpdateGUI("onGraphCheckboxesCheckedChanged");
                    return true;
                }
            });
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp);
            popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu menu) {
                    chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp);
                }
            });
            popup.show();
        });
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v == apsModeView) {
            final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
            final PumpDescription pumpDescription = ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription();
            if (!ProfileFunctions.getInstance().isProfileValid("ContexMenuCreation"))
                return;
            menu.setHeaderTitle(MainApp.gs(R.string.loop));
            if (loopPlugin.isEnabled(PluginType.LOOP)) {
                menu.add(MainApp.gs(R.string.disableloop));
                if (!loopPlugin.isSuspended()) {
                    menu.add(MainApp.gs(R.string.suspendloopfor1h));
                    menu.add(MainApp.gs(R.string.suspendloopfor2h));
                    menu.add(MainApp.gs(R.string.suspendloopfor3h));
                    menu.add(MainApp.gs(R.string.suspendloopfor10h));
                    if (pumpDescription.tempDurationStep15mAllowed)
                        menu.add(MainApp.gs(R.string.disconnectpumpfor15m));
                    if (pumpDescription.tempDurationStep30mAllowed)
                        menu.add(MainApp.gs(R.string.disconnectpumpfor30m));
                    menu.add(MainApp.gs(R.string.disconnectpumpfor1h));
                    menu.add(MainApp.gs(R.string.disconnectpumpfor2h));
                    menu.add(MainApp.gs(R.string.disconnectpumpfor3h));
                } else {
                    menu.add(MainApp.gs(R.string.resume));
                }
            }
            if (!loopPlugin.isEnabled(PluginType.LOOP))
                menu.add(MainApp.gs(R.string.enableloop));
        } else if (v == activeProfileView) {
            menu.setHeaderTitle(MainApp.gs(R.string.profile));
            menu.add(MainApp.gs(R.string.danar_viewprofile));
            if (ConfigBuilderPlugin.getPlugin().getActiveProfileInterface() != null && ConfigBuilderPlugin.getPlugin().getActiveProfileInterface().getProfile() != null) {
                menu.add(MainApp.gs(R.string.careportal_profileswitch));
            }
        } else if (v == tempTargetView) {
            menu.setHeaderTitle(MainApp.gs(R.string.careportal_temporarytarget));
            menu.add(MainApp.gs(R.string.custom));
            menu.add(MainApp.gs(R.string.eatingsoon));
            menu.add(MainApp.gs(R.string.activity));
            menu.add(MainApp.gs(R.string.hypo));
            if (TreatmentsPlugin.getPlugin().getTempTargetFromHistory() != null) {
                menu.add(MainApp.gs(R.string.cancel));
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile == null)
            return true;
        final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
        if (item.getTitle().equals(MainApp.gs(R.string.disableloop))) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, false);
            loopPlugin.setFragmentVisible(PluginType.LOOP, false);
            ConfigBuilderPlugin.getPlugin().storeSettings("DisablingLoop");
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(24 * 60); // upload 24h, we don't know real duration
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.enableloop))) {
            loopPlugin.setPluginEnabled(PluginType.LOOP, true);
            loopPlugin.setFragmentVisible(PluginType.LOOP, true);
            ConfigBuilderPlugin.getPlugin().storeSettings("EnablingLoop");
            updateGUI("suspendmenu");
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.resume))) {
            loopPlugin.suspendTo(0L);
            updateGUI("suspendmenu");
            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            NSUpload.uploadOpenAPSOffline(0);
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor1h))) {
            LoopPlugin.getPlugin().suspendLoop(60);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor2h))) {
            LoopPlugin.getPlugin().suspendLoop(120);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor3h))) {
            LoopPlugin.getPlugin().suspendLoop(180);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.suspendloopfor10h))) {
            LoopPlugin.getPlugin().suspendLoop(600);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor15m))) {
            LoopPlugin.getPlugin().disconnectPump(15, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor30m))) {
            LoopPlugin.getPlugin().disconnectPump(30, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor1h))) {
            LoopPlugin.getPlugin().disconnectPump(60, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor2h))) {
            LoopPlugin.getPlugin().disconnectPump(120, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.disconnectpumpfor3h))) {
            LoopPlugin.getPlugin().disconnectPump(180, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(MainApp.gs(R.string.careportal_profileswitch))) {
            NewNSTreatmentDialog newDialog = new NewNSTreatmentDialog();
            final OptionsToShow profileswitch = CareportalFragment.PROFILESWITCHDIRECT;
            profileswitch.executeProfileSwitch = true;
            newDialog.setOptions(profileswitch, R.string.careportal_profileswitch);
            newDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
        } else if (item.getTitle().equals(MainApp.gs(R.string.danar_viewprofile))) {
            ProfileViewerDialog pvd = ProfileViewerDialog.newInstance(System.currentTimeMillis());
            FragmentManager manager = getFragmentManager();
            pvd.show(manager, "ProfileViewDialog");
        } else if (item.getTitle().equals(MainApp.gs(R.string.eatingsoon))) {
            DefaultValueHelper defHelper = new DefaultValueHelper();
            double target = defHelper.determineEatingSoonTT(profile.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(System.currentTimeMillis())
                    .duration(defHelper.determineEatingSoonTTDuration())
                    .reason(MainApp.gs(R.string.eatingsoon))
                    .source(Source.USER)
                    .low(Profile.toMgdl(target, profile.getUnits()))
                    .high(Profile.toMgdl(target, profile.getUnits()));
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.activity))) {
            DefaultValueHelper defHelper = new DefaultValueHelper();
            double target = defHelper.determineActivityTT(profile.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(defHelper.determineActivityTTDuration())
                    .reason(MainApp.gs(R.string.activity))
                    .source(Source.USER)
                    .low(Profile.toMgdl(target, profile.getUnits()))
                    .high(Profile.toMgdl(target, profile.getUnits()));
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.hypo))) {
            DefaultValueHelper defHelper = new DefaultValueHelper();
            double target = defHelper.determineHypoTT(profile.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(defHelper.determineHypoTTDuration())
                    .reason(MainApp.gs(R.string.hypo))
                    .source(Source.USER)
                    .low(Profile.toMgdl(target, profile.getUnits()))
                    .high(Profile.toMgdl(target, profile.getUnits()));
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(MainApp.gs(R.string.custom))) {
            NewNSTreatmentDialog newTTDialog = new NewNSTreatmentDialog();
            final OptionsToShow temptarget = CareportalFragment.TEMPTARGET;
            temptarget.executeTempTarget = true;
            newTTDialog.setOptions(temptarget, R.string.careportal_temporarytarget);
            newTTDialog.show(getFragmentManager(), "NewNSTreatmentDialog");
        } else if (item.getTitle().equals(MainApp.gs(R.string.cancel))) {
            TempTarget tempTarget = new TempTarget()
                    .source(Source.USER)
                    .date(now())
                    .duration(0)
                    .low(0)
                    .high(0);
            TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        boolean xdrip = SourceXdripPlugin.getPlugin().isEnabled(PluginType.BGSOURCE);
        boolean g5 = SourceDexcomG5Plugin.getPlugin().isEnabled(PluginType.BGSOURCE);
        String units = ProfileFunctions.getInstance().getProfileUnits();

        FragmentManager manager = getFragmentManager();
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager.isStateSaved())
            return;
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
                if (xdrip) {
                    CalibrationDialog calibrationDialog = new CalibrationDialog();
                    calibrationDialog.show(manager, "CalibrationDialog");
                } else if (g5) {
                    try {
                        Intent i = new Intent("com.dexcom.cgm.activities.MeterEntryActivity");
                        startActivity(i);
                    } catch (ActivityNotFoundException e) {
                        ToastUtils.showToastInUiThread(getActivity(), MainApp.gs(R.string.g5appnotdetected));
                    }
                }
                break;
            case R.id.overview_cgmbutton:
                if (xdrip)
                    openCgmApp("com.eveningoutpost.dexdrip");
                else if (g5 && units.equals(Constants.MGDL))
                    openCgmApp("com.dexcom.cgm.region5.mgdl");
                else if (g5 && units.equals(Constants.MMOL))
                    openCgmApp("com.dexcom.cgm.region5.mmol");
                break;
            case R.id.overview_treatmentbutton:
                NewTreatmentDialog treatmentDialogFragment = new NewTreatmentDialog();
                treatmentDialogFragment.show(manager, "TreatmentDialog");
                break;
            case R.id.overview_insulinbutton:
                new NewInsulinDialog().show(manager, "InsulinDialog");
                break;
            case R.id.overview_carbsbutton:
                new NewCarbsDialog().show(manager, "CarbsDialog");
                break;
            case R.id.overview_pumpstatus:
                if (ConfigBuilderPlugin.getPlugin().getActivePump().isSuspended() || !ConfigBuilderPlugin.getPlugin().getActivePump().isInitialized())
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("RefreshClicked", null);
                break;
        }

    }

    public boolean openCgmApp(String packageName) {
        PackageManager packageManager = getContext().getPackageManager();
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            getContext().startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.error_starting_cgm)
                    .setPositiveButton("OK", null)
                    .show();
            return false;
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
        Profile profile = ProfileFunctions.getInstance().getProfile();
        Context context = getContext();

        if (context == null) return;

        if (LoopPlugin.getPlugin().isEnabled(PluginType.LOOP) && profile != null) {
            LoopPlugin.getPlugin().invoke("Accept temp button", false);
            final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
            if (finalLastRun != null && finalLastRun.lastAPSRun != null && finalLastRun.constraintsProcessed.isChangeRequested()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(MainApp.gs(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
                builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                    hideTempRecommendation();
                    clearNotification();
                    LoopPlugin.getPlugin().acceptChangeRequest();
                });
                builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
                builder.show();
            }
        }
    }

    void onClickQuickwizard() {
        final BgReading actualBg = DatabaseHelper.actualBg();
        final Profile profile = ProfileFunctions.getInstance().getProfile();
        final TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();

        final QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && actualBg != null && profile != null) {
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
                String confirmMessage = MainApp.gs(R.string.entertreatmentquestion);

                Double insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(wizard.calculatedTotalInsulin)).value();
                Integer carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                confirmMessage += "\n" + MainApp.gs(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                confirmMessage += "\n" + MainApp.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                if (Math.abs(insulinAfterConstraints - wizard.calculatedTotalInsulin) >= 0.01 || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(MainApp.gs(R.string.treatmentdeliveryerror));
                    builder.setMessage(MainApp.gs(R.string.constraints_violation) + "\n" + MainApp.gs(R.string.changeyourinput));
                    builder.setPositiveButton(MainApp.gs(R.string.ok), null);
                    builder.show();
                    return;
                }

                final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                final Integer finalCarbsAfterConstraints = carbsAfterConstraints;
                final Context context = getContext();
                final AlertDialog.Builder builder = new AlertDialog.Builder(context);
                accepted = false;
                builder.setTitle(MainApp.gs(R.string.confirmation));
                builder.setMessage(confirmMessage);
                builder.setPositiveButton(MainApp.gs(R.string.ok), (dialog, id) -> {
                    synchronized (builder) {
                        if (accepted) {
                            if (L.isEnabled(L.OVERVIEW))
                                log.debug("guarding: already accepted");
                            return;
                        }
                        accepted = true;
                        if (Math.abs(insulinAfterConstraints - wizard.calculatedTotalInsulin) >= 0.01 || finalCarbsAfterConstraints > 0) {
                            if (wizard.superBolus) {
                                final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
                                if (loopPlugin.isEnabled(PluginType.LOOP)) {
                                    loopPlugin.superBolusTo(System.currentTimeMillis() + T.hours(2).msecs());
                                    MainApp.bus().post(new EventRefreshOverview("WizardDialog"));
                                }
                                ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalPercent(0, 120, true, profile, new Callback() {
                                    @Override
                                    public void run() {
                                        if (!result.success) {
                                            Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                                            i.putExtra("soundid", R.raw.boluserror);
                                            i.putExtra("status", result.comment);
                                            i.putExtra("title", MainApp.gs(R.string.tempbasaldeliveryerror));
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
                            if (finalInsulinAfterConstraints > 0 || ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().storesCarbInfo) {
                                ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
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
                            } else {
                                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
                            }
                            FabricPrivacy.getInstance().logCustom(new CustomEvent("QuickWizard"));
                        }
                    }
                });
                builder.setNegativeButton(MainApp.gs(R.string.cancel), null);
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
        unregisterForContextMenu(tempTargetView);
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
        sRefreshLoop = () -> {
            scheduleUpdateGUI("refreshLoop");
            sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        };
        sLoopHandler.postDelayed(sRefreshLoop, 60 * 1000L);
        registerForContextMenu(apsModeView);
        registerForContextMenu(activeProfileView);
        registerForContextMenu(tempTargetView);
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

    @Subscribe
    public void onStatusEvent(final EventNewOpenLoopNotification ev) {
        scheduleUpdateGUI("EventNewOpenLoopNotification");
    }

    @Subscribe
    public void onStatusEvent(final EventAcceptOpenLoopChange ev) {
        scheduleUpdateGUI("EventAcceptOpenLoopChange");
    }

    @Subscribe
    public void onStatusEvent(final EventTempTargetChange ev) {
        scheduleUpdateGUI("EventTempTargetChange");
    }

    @Subscribe
    public void onStatusEvent(final EventProfileSwitchChange ev) {
        scheduleUpdateGUI("EventProfileSwitchChange");
    }

    @Subscribe
    public void onStatusEvent(final EventPumpStatusChanged s) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> updatePumpStatus(s.textStatus()));
    }

    @Subscribe
    public void onStatusEvent(final EventIobCalculationProgress e) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                if (iobCalculationProgressView != null)
                    iobCalculationProgressView.setText(e.progress);
            });
    }

    private void hideTempRecommendation() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                if (acceptTempLayout != null)
                    acceptTempLayout.setVisibility(View.GONE);
            });
    }

    private void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) MainApp.instance().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.notificationID);

        ActionStringHandler.handleInitiate("cancelChangeRequest");
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
                    activity.runOnUiThread(() -> {
                        updateGUI(from);
                        scheduledUpdate = null;
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
        if (L.isEnabled(L.OVERVIEW))
            log.debug("updateGUI entered from: " + from);
        final long updateGUIStart = System.currentTimeMillis();

        if (getActivity() == null)
            return;

        if (timeView != null) { //must not exists
            timeView.setText(DateUtil.timeString(new Date()));
        }

        updateNotifications();

        pumpStatusLayout.setVisibility(View.GONE);
        loopStatusLayout.setVisibility(View.GONE);

        if (!ProfileFunctions.getInstance().isProfileValid("Overview")) {
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            return;
        }
        loopStatusLayout.setVisibility(View.VISIBLE);

        CareportalFragment.updateAge(getActivity(), sage, iage, cage, pbage);
        BgReading actualBG = DatabaseHelper.actualBg();
        BgReading lastBG = DatabaseHelper.lastBg();

        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();

        final Profile profile = ProfileFunctions.getInstance().getProfile();

        final String units = profile.getUnits();
        final double lowLine = OverviewPlugin.getPlugin().determineLowLine(units);
        final double highLine = OverviewPlugin.getPlugin().determineHighLine(units);

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            int color = MainApp.gc(R.color.inrange);
            if (lastBG.valueToUnits(units) < lowLine)
                color = MainApp.gc(R.color.low);
            else if (lastBG.valueToUnits(units) > highLine)
                color = MainApp.gc(R.color.high);
            bgView.setText(lastBG.valueToUnitsToString(units));
            arrowView.setText(lastBG.directionToSymbol());
            bgView.setTextColor(color);
            arrowView.setTextColor(color);
            GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
            if (glucoseStatus != null) {
                if (deltaView != null)
                    deltaView.setText("Δ " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
                if (deltaShortView != null)
                    deltaShortView.setText(Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units));
                if (avgdeltaView != null)
                    avgdeltaView.setText("øΔ15m: " + Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units) +
                            "  øΔ40m: " + Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units));
            } else {
                if (deltaView != null)
                    deltaView.setText("Δ " + MainApp.gs(R.string.notavailable));
                if (deltaShortView != null)
                    deltaShortView.setText("---");
                if (avgdeltaView != null)
                    avgdeltaView.setText("");
            }
        }

        Constraint<Boolean> closedLoopEnabled = MainApp.getConstraintChecker().isClosedLoopAllowed();

        // open loop mode
        final LoopPlugin.LastRun finalLastRun = LoopPlugin.lastRun;
        if (Config.APS && pump.getPumpDescription().isTempBasalCapable) {
            apsModeView.setVisibility(View.VISIBLE);
            apsModeView.setBackgroundColor(MainApp.gc(R.color.loopenabled));
            apsModeView.setTextColor(Color.BLACK);
            final LoopPlugin loopPlugin = LoopPlugin.getPlugin();
            if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuperBolus()) {
                apsModeView.setBackgroundColor(MainApp.gc(R.color.looppumpsuspended));
                apsModeView.setText(String.format(MainApp.gs(R.string.loopsuperbolusfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isDisconnected()) {
                apsModeView.setBackgroundColor(MainApp.gc(R.color.looppumpsuspended));
                apsModeView.setText(String.format(MainApp.gs(R.string.loopdisconnectedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuspended()) {
                apsModeView.setBackgroundColor(MainApp.gc(R.color.looppumpsuspended));
                apsModeView.setText(String.format(MainApp.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(Color.WHITE);
            } else if (pump.isSuspended()) {
                apsModeView.setBackgroundColor(MainApp.gc(R.color.looppumpsuspended));
                apsModeView.setText(MainApp.gs(R.string.pumpsuspended));
                apsModeView.setTextColor(Color.WHITE);
            } else if (loopPlugin.isEnabled(PluginType.LOOP)) {
                if (closedLoopEnabled.value()) {
                    apsModeView.setText(MainApp.gs(R.string.closedloop));
                } else {
                    apsModeView.setText(MainApp.gs(R.string.openloop));
                }
            } else {
                apsModeView.setBackgroundColor(MainApp.gc(R.color.loopdisabled));
                apsModeView.setText(MainApp.gs(R.string.disabledloop));
                apsModeView.setTextColor(Color.WHITE);
            }
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        // temp target
        TempTarget tempTarget = TreatmentsPlugin.getPlugin().getTempTargetFromHistory();
        if (tempTarget != null) {
            tempTargetView.setTextColor(Color.BLACK);
            tempTargetView.setBackgroundColor(MainApp.gc(R.color.tempTargetBackground));
            tempTargetView.setVisibility(View.VISIBLE);
            tempTargetView.setText(Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end()));
        } else {
            tempTargetView.setTextColor(Color.WHITE);
            tempTargetView.setBackgroundColor(MainApp.gc(R.color.tempTargetDisabledBackground));
            tempTargetView.setText(Profile.toTargetRangeString(profile.getTargetLow(), profile.getTargetHigh(), units, units));
            tempTargetView.setVisibility(View.VISIBLE);
        }

        // **** Temp button ****
        if (acceptTempLayout != null) {
            boolean showAcceptButton = !closedLoopEnabled.value(); // Open mode needed
            showAcceptButton = showAcceptButton && finalLastRun != null && finalLastRun.lastAPSRun != null; // aps result must exist
            showAcceptButton = showAcceptButton && (finalLastRun.lastOpenModeAccept == null || finalLastRun.lastOpenModeAccept.getTime() < finalLastRun.lastAPSRun.getTime()); // never accepted or before last result
            showAcceptButton = showAcceptButton && finalLastRun.constraintsProcessed.isChangeRequested(); // change is requested

            if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && LoopPlugin.getPlugin().isEnabled(PluginType.LOOP)) {
                acceptTempLayout.setVisibility(View.VISIBLE);
                acceptTempButton.setText(MainApp.gs(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
            } else {
                acceptTempLayout.setVisibility(View.GONE);
            }
        }

        // **** Calibration & CGM buttons ****
        boolean xDripIsBgSource = MainApp.getSpecificPlugin(SourceXdripPlugin.class) != null && MainApp.getSpecificPlugin(SourceXdripPlugin.class).isEnabled(PluginType.BGSOURCE);
        boolean g5IsBgSource = MainApp.getSpecificPlugin(SourceDexcomG5Plugin.class) != null && MainApp.getSpecificPlugin(SourceDexcomG5Plugin.class).isEnabled(PluginType.BGSOURCE);
        boolean bgAvailable = DatabaseHelper.actualBg() != null;
        if (calibrationButton != null) {
            if ((xDripIsBgSource || g5IsBgSource) && bgAvailable && SP.getBoolean(R.string.key_show_calibration_button, true)) {
                calibrationButton.setVisibility(View.VISIBLE);
            } else {
                calibrationButton.setVisibility(View.GONE);
            }
        }
        if (cgmButton != null) {
            if (xDripIsBgSource && SP.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else if (g5IsBgSource && SP.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else {
                cgmButton.setVisibility(View.GONE);
            }
        }

        final TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        String basalText = "";
        if (shorttextmode) {
            if (activeTemp != null) {
                basalText = "T: " + activeTemp.toStringVeryShort();
            } else {
                basalText = DecimalFormatter.to2Decimal(profile.getBasal()) + "U/h";
            }
            baseBasalView.setOnClickListener(v -> {
                String fullText = MainApp.gs(R.string.pump_basebasalrate_label) + ": " + DecimalFormatter.to2Decimal(profile.getBasal()) + "U/h\n";
                if (activeTemp != null) {
                    fullText += MainApp.gs(R.string.pump_tempbasal_label) + ": " + activeTemp.toStringFull();
                }
                OKDialog.show(getActivity(), MainApp.gs(R.string.basal), fullText, null);
            });

        } else {
            if (activeTemp != null) {
                basalText = activeTemp.toStringFull() + " ";
            }
            if (Config.NSCLIENT)
                basalText += "(" + DecimalFormatter.to2Decimal(profile.getBasal()) + " U/h)";
            else if (pump.getPumpDescription().isTempBasalCapable) {
                basalText += "(" + DecimalFormatter.to2Decimal(pump.getBaseBasalRate()) + "U/h)";
            }
        }
        if (activeTemp != null) {
            baseBasalView.setTextColor(MainApp.gc(R.color.basal));
        } else {
            baseBasalView.setTextColor(Color.WHITE);

        }

        baseBasalView.setText(basalText);

        final ExtendedBolus extendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        String extendedBolusText = "";
        if (extendedBolusView != null) { // must not exists in all layouts
            if (shorttextmode) {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + "U/h";
                }
            } else {
                if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses()) {
                    extendedBolusText = extendedBolus.toString();
                }
            }
            extendedBolusView.setText(extendedBolusText);
            if (Config.NSCLIENT) {
                extendedBolusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.extendedbolus), extendedBolus.toString(), null));
            }
            if (extendedBolusText.equals(""))
                extendedBolusView.setVisibility(Config.NSCLIENT ? View.INVISIBLE : View.GONE);
            else
                extendedBolusView.setVisibility(View.VISIBLE);
        }

        activeProfileView.setText(ProfileFunctions.getInstance().getProfileName());
        activeProfileView.setBackgroundColor(Color.GRAY);

        // QuickWizard button
        QuickWizardEntry quickWizardEntry = OverviewPlugin.getPlugin().quickWizard.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = quickWizardEntry.buttonText() + "\n" + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = quickWizardEntry.doCalc(profile, tempTarget, lastBG, false);
            text += " " + DecimalFormatter.toPumpSupportedBolus(wizard.calculatedTotalInsulin) + "U";
            quickWizardButton.setText(text);
            if (wizard.calculatedTotalInsulin <= 0)
                quickWizardButton.setVisibility(View.GONE);
        } else
            quickWizardButton.setVisibility(View.GONE);

        // **** Various treatment buttons ****
        if (carbsButton != null) {
            if (SP.getBoolean(R.string.key_show_carbs_button, true)
                    && (!ConfigBuilderPlugin.getPlugin().getActivePump().getPumpDescription().storesCarbInfo ||
                    (pump.isInitialized() && !pump.isSuspended()))) {
                carbsButton.setVisibility(View.VISIBLE);
            } else {
                carbsButton.setVisibility(View.GONE);
            }
        }

        if (pump.isInitialized() && !pump.isSuspended()) {
            if (treatmentButton != null) {
                if (SP.getBoolean(R.string.key_show_treatment_button, false)) {
                    treatmentButton.setVisibility(View.VISIBLE);
                } else {
                    treatmentButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && wizardButton != null) {
                if (SP.getBoolean(R.string.key_show_wizard_button, true)) {
                    wizardButton.setVisibility(View.VISIBLE);
                } else {
                    wizardButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && insulinButton != null) {
                if (SP.getBoolean(R.string.key_show_insulin_button, true)) {
                    insulinButton.setVisibility(View.VISIBLE);
                } else {
                    insulinButton.setVisibility(View.GONE);
                }
            }
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

        if (timeAgoView != null)
            timeAgoView.setText(DateUtil.minAgo(lastBG.date));
        if (timeAgoShortView != null)
            timeAgoShortView.setText("(" + DateUtil.minAgoShort(lastBG.date) + ")");

        // iob
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        final IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
        final IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();

        if (shorttextmode) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U";
            iobView.setText(iobtext);
            iobView.setOnClickListener(v -> {
                String iobtext1 = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U\n"
                        + MainApp.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U\n"
                        + MainApp.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U\n";
                OKDialog.show(getActivity(), MainApp.gs(R.string.iob), iobtext1, null);
            });
        } else if (MainApp.sResources.getBoolean(R.bool.isTablet)) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + MainApp.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                    + MainApp.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
            iobView.setText(iobtext);
        } else {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "/"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
            iobView.setText(iobtext);
        }

        // cob
        if (cobView != null) { // view must not exists
            String cobText = MainApp.gs(R.string.value_unavailable_short);
            CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "Overview COB");
            if (cobInfo.displayCob != null) {
                cobText = DecimalFormatter.to0Decimal(cobInfo.displayCob);
                if (cobInfo.futureCarbs > 0)
                    cobText += "(" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) + ")";
            }
            cobView.setText(cobText);
        }

        boolean predictionsAvailable;
        if (Config.APS)
            predictionsAvailable = finalLastRun != null && finalLastRun.request.hasPredictions;
        else if (Config.NSCLIENT)
            predictionsAvailable = true;
        else
            predictionsAvailable = false;
        final boolean finalPredictionsAvailable = predictionsAvailable;

        // pump status from ns
        if (pumpDeviceStatusView != null) {
            pumpDeviceStatusView.setText(NSDeviceStatus.getInstance().getPumpStatus());
            pumpDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.pump), NSDeviceStatus.getInstance().getExtendedPumpStatus(), null));
        }

        // OpenAPS status from ns
        if (openapsDeviceStatusView != null) {
            openapsDeviceStatusView.setText(NSDeviceStatus.getInstance().getOpenApsStatus());
            openapsDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.openaps), NSDeviceStatus.getInstance().getExtendedOpenApsStatus(), null));
        }

        // Uploader status from ns
        if (uploaderDeviceStatusView != null) {
            uploaderDeviceStatusView.setText(NSDeviceStatus.getInstance().getUploaderStatusSpanned());
            uploaderDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), MainApp.gs(R.string.uploader), NSDeviceStatus.getInstance().getExtendedUploaderStatus(), null));
        }

        // Sensitivity
        if (sensitivityView != null) {
            AutosensData autosensData = IobCobCalculatorPlugin.getPlugin().getLastAutosensDataSynchronized("Overview");
            if (autosensData != null)
                sensitivityView.setText(String.format("%.0f%%", autosensData.autosensResult.ratio * 100));
            else
                sensitivityView.setText("");
        }

        // ****** GRAPH *******

        new Thread(() -> {
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

            APSResult apsResult = null;

            if (finalPredictionsAvailable && SP.getBoolean("showprediction", false)) {
                if (Config.APS)
                    apsResult = finalLastRun.constraintsProcessed;
                else
                    apsResult = NSDeviceStatus.getAPSResult();
                int predHours = (int) (Math.ceil(apsResult.getLatestPredictionsTime() - System.currentTimeMillis()) / (60 * 60 * 1000));
                predHours = Math.min(2, predHours);
                predHours = Math.max(0, predHours);
                hoursToFetch = rangeToDisplay - predHours;
                toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                fromTime = toTime - T.hours(hoursToFetch).msecs();
                endTime = toTime + T.hours(predHours).msecs();
            } else {
                hoursToFetch = rangeToDisplay;
                toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
                fromTime = toTime - T.hours(hoursToFetch).msecs();
                endTime = toTime;
            }


            final long now = System.currentTimeMillis();

            //  ------------------ 1st graph
            if (L.isEnabled(L.OVERVIEW))
                Profiler.log(log, from + " - 1st graph - START", updateGUIStart);

            final GraphData graphData = new GraphData(bgGraph, IobCobCalculatorPlugin.getPlugin());

            // **** In range Area ****
            graphData.addInRangeArea(fromTime, endTime, lowLine, highLine);

            // **** BG ****
            if (finalPredictionsAvailable && SP.getBoolean("showprediction", false))
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine,
                        apsResult.getPredictions());
            else
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null);

            // set manual x bounds to have nice steps
            graphData.formatAxis(fromTime, endTime);

            // Treatments
            graphData.addTreatments(fromTime, endTime);

            // add basal data
            if (pump.getPumpDescription().isTempBasalCapable && SP.getBoolean("showbasals", true)) {
                graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2d);
            }

            // add target line
            graphData.addTargetLine(fromTime, toTime, profile);

            // **** NOW line ****
            graphData.addNowLine(now);

            // ------------------ 2nd graph
            if (L.isEnabled(L.OVERVIEW))
                Profiler.log(log, from + " - 2nd graph - START", updateGUIStart);

            final GraphData secondGraphData = new GraphData(iobGraph, IobCobCalculatorPlugin.getPlugin());

            boolean useIobForScale = false;
            boolean useCobForScale = false;
            boolean useDevForScale = false;
            boolean useRatioForScale = false;
            boolean useDSForScale = false;

            if (SP.getBoolean("showiob", true)) {
                useIobForScale = true;
            } else if (SP.getBoolean("showcob", true)) {
                useCobForScale = true;
            } else if (SP.getBoolean("showdeviations", false)) {
                useDevForScale = true;
            } else if (SP.getBoolean("showratios", false)) {
                useRatioForScale = true;
            } else if (SP.getBoolean("showdevslope", false)) {
                useDSForScale = true;
            }

            if (SP.getBoolean("showiob", true))
                secondGraphData.addIob(fromTime, now, useIobForScale, 1d);
            if (SP.getBoolean("showcob", true))
                secondGraphData.addCob(fromTime, now, useCobForScale, useCobForScale ? 1d : 0.5d);
            if (SP.getBoolean("showdeviations", false))
                secondGraphData.addDeviations(fromTime, now, useDevForScale, 1d);
            if (SP.getBoolean("showratios", false))
                secondGraphData.addRatio(fromTime, now, useRatioForScale, 1d);
            if (SP.getBoolean("showdevslope", false) && MainApp.devBranch)
                secondGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1d);

            // **** NOW line ****
            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(fromTime, endTime);
            secondGraphData.addNowLine(now);

            // do GUI update
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (SP.getBoolean("showiob", true)
                            || SP.getBoolean("showcob", true)
                            || SP.getBoolean("showdeviations", false)
                            || SP.getBoolean("showratios", false)
                            || SP.getBoolean("showdevslope", false)) {
                        iobGraph.setVisibility(View.VISIBLE);
                    } else {
                        iobGraph.setVisibility(View.GONE);
                    }
                    // finally enforce drawing of graphs
                    graphData.performUpdate();
                    secondGraphData.performUpdate();
                    if (L.isEnabled(L.OVERVIEW))
                        Profiler.log(log, from + " - onDataChanged", updateGUIStart);
                });
            }
        }).start();

        if (L.isEnabled(L.OVERVIEW))
            Profiler.log(log, from, updateGUIStart);
    }

    //Notifications

    void updateNotifications() {
        NotificationStore nstore = OverviewPlugin.getPlugin().notificationStore;
        nstore.removeExpired();
        nstore.unSnooze();
        if (nstore.store.size() > 0) {
            NotificationRecyclerViewAdapter adapter = new NotificationRecyclerViewAdapter(nstore.store);
            notificationsView.setAdapter(adapter);
            notificationsView.setVisibility(View.VISIBLE);
        } else {
            notificationsView.setVisibility(View.GONE);
        }
    }

}
