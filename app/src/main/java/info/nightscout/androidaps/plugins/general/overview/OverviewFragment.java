package info.nightscout.androidaps.plugins.general.overview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjoe64.graphview.GraphView;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import dagger.android.support.DaggerFragment;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.dialogs.CalibrationDialog;
import info.nightscout.androidaps.dialogs.CarbsDialog;
import info.nightscout.androidaps.dialogs.InsulinDialog;
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog;
import info.nightscout.androidaps.dialogs.ProfileViewerDialog;
import info.nightscout.androidaps.dialogs.TempTargetDialog;
import info.nightscout.androidaps.dialogs.TreatmentDialog;
import info.nightscout.androidaps.dialogs.WizardDialog;
import info.nightscout.androidaps.events.EventAcceptOpenLoopChange;
import info.nightscout.androidaps.events.EventCareportalEventChange;
import info.nightscout.androidaps.events.EventExtendedBolusChange;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventProfileNeedsUpdate;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTempTargetChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.APSResult;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.aps.loop.events.EventNewOpenLoopNotification;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus;
import info.nightscout.androidaps.plugins.general.overview.activities.QuickWizardListActivity;
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData;
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore;
import info.nightscout.androidaps.plugins.general.wear.ActionStringHandler;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.source.DexcomPlugin;
import info.nightscout.androidaps.plugins.source.XdripPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.CommandQueue;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.Profiler;
import info.nightscout.androidaps.utils.SingleClickButton;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.ToastUtils;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.protection.ProtectionCheck;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import info.nightscout.androidaps.utils.wizard.BolusWizard;
import info.nightscout.androidaps.utils.wizard.QuickWizard;
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static info.nightscout.androidaps.utils.DateUtil.now;

public class OverviewFragment extends DaggerFragment implements View.OnClickListener, View.OnLongClickListener {
    @Inject HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject SP sp;
    @Inject RxBusWrapper rxBus;
    @Inject MainApp mainApp;
    @Inject ResourceHelper resourceHelper;
    @Inject DefaultValueHelper defaultValueHelper;
    @Inject ProfileFunction profileFunction;
    @Inject ConstraintChecker constraintChecker;
    @Inject StatusLightHandler statusLightHandler;
    @Inject NSDeviceStatus nsDeviceStatus;
    @Inject LoopPlugin loopPlugin;
    @Inject ConfigBuilderPlugin configBuilderPlugin;
    @Inject ActivePluginProvider activePlugin;
    @Inject TreatmentsPlugin treatmentsPlugin;
    @Inject IobCobCalculatorPlugin iobCobCalculatorPlugin;
    @Inject DexcomPlugin dexcomPlugin;
    @Inject XdripPlugin xdripPlugin;
    @Inject NotificationStore notificationStore;
    @Inject ActionStringHandler actionStringHandler;
    @Inject QuickWizard quickWizard;
    @Inject BuildHelper buildHelper;
    @Inject CommandQueue commandQueue;
    @Inject ProtectionCheck protectionCheck;

    private CompositeDisposable disposable = new CompositeDisposable();

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
    ConstraintLayout loopStatusLayout;
    LinearLayout pumpStatusLayout;
    GraphView bgGraph;
    GraphView iobGraph;
    ImageButton chartButton;

    TextView iage;
    TextView cage;
    TextView sage;
    TextView pbage;

    TextView iageView;
    TextView cageView;
    TextView reservoirView;
    TextView sageView;
    TextView batteryView;
    LinearLayout statuslightsLayout;

    RecyclerView notificationsView;
    LinearLayoutManager llm;

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

    public enum CHARTTYPE {PRE, BAS, IOB, COB, DEV, SEN, ACTPRIM, ACTSEC, DEVSLOPE}

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledUpdate = null;

    public OverviewFragment() {
        super();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
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

        if (resourceHelper.gb(R.bool.isTablet) && (Config.NSCLIENT)) {
            view = inflater.inflate(R.layout.overview_fragment_nsclient_tablet, container, false);
        } else if (Config.NSCLIENT) {
            view = inflater.inflate(R.layout.overview_fragment_nsclient, container, false);
            shorttextmode = true;
        } else if (smallHeight || landscape) {
            view = inflater.inflate(R.layout.overview_fragment_landscape, container, false);
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
        loopStatusLayout = view.findViewById(R.id.overview_looplayout);
        pumpStatusLayout = (LinearLayout) view.findViewById(R.id.overview_pumpstatuslayout);

        pumpStatusView.setBackgroundColor(resourceHelper.gc(R.color.colorInitializingBorder));

        iobView = (TextView) view.findViewById(R.id.overview_iob);
        cobView = (TextView) view.findViewById(R.id.overview_cob);
        apsModeView = (TextView) view.findViewById(R.id.overview_apsmode);
        tempTargetView = (TextView) view.findViewById(R.id.overview_temptarget);

        iage = view.findViewById(R.id.careportal_insulinage);
        cage = view.findViewById(R.id.careportal_canulaage);
        sage = view.findViewById(R.id.careportal_sensorage);
        pbage = view.findViewById(R.id.careportal_pbage);

        iageView = view.findViewById(R.id.overview_insulinage);
        cageView = view.findViewById(R.id.overview_canulaage);
        reservoirView = view.findViewById(R.id.overview_reservoirlevel);
        sageView = view.findViewById(R.id.overview_sensorage);
        batteryView = view.findViewById(R.id.overview_batterylevel);
        statuslightsLayout = view.findViewById(R.id.overview_statuslights);

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

        bgGraph.getGridLabelRenderer().setGridColor(resourceHelper.gc(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(resourceHelper.gc(R.color.graphgrid));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(axisWidth);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(3);

        rangeToDisplay = sp.getInt(R.string.key_rangetodisplay, 6);

        bgGraph.setOnLongClickListener(v -> {
            rangeToDisplay += 6;
            rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
            sp.putInt(R.string.key_rangetodisplay, rangeToDisplay);
            updateGUI("rangeChange");
            sp.putBoolean(R.string.key_objectiveusescale, true);
            return false;
        });

        setupChartMenu(view);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        disposable.clear();
        sLoopHandler.removeCallbacksAndMessages(null);
        unregisterForContextMenu(apsModeView);
        unregisterForContextMenu(activeProfileView);
        unregisterForContextMenu(tempTargetView);
    }

    @Override
    public void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventRefreshOverview.class)
                .observeOn(Schedulers.io())
                .subscribe(eventOpenAPSUpdateGui -> scheduleUpdateGUI(eventOpenAPSUpdateGui.getFrom()),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventExtendedBolusChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventExtendedBolusChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventTempBasalChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventTempBasalChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventTreatmentChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventTreatmentChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventTempTargetChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventTempTargetChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventAcceptOpenLoopChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventAcceptOpenLoopChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventCareportalEventChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventCareportalEventChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventInitializationChanged.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventInitializationChanged"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventAutosensCalculationFinished"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventProfileNeedsUpdate.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventProfileNeedsUpdate"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventPreferenceChange"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventNewOpenLoopNotification.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> scheduleUpdateGUI("EventNewOpenLoopNotification"),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventPumpStatusChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> updatePumpStatus(event),
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
        disposable.add(rxBus
                .toObservable(EventIobCalculationProgress.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                            if (iobCalculationProgressView != null)
                                iobCalculationProgressView.setText(event.getProgress());
                        },
                        exception -> FabricPrivacy.getInstance().logException(exception)
                ));
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

    private void setupChartMenu(View view) {
        chartButton = (ImageButton) view.findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(v -> {
            boolean predictionsAvailable;
            if (Config.APS)
                predictionsAvailable = loopPlugin.lastRun != null && loopPlugin.lastRun.request.hasPredictions;
            else if (Config.NSCLIENT)
                predictionsAvailable = true;
            else
                predictionsAvailable = false;

            MenuItem item, dividerItem;
            CharSequence title;
            int titleMaxChars = 0;
            SpannableString s;
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            if (predictionsAvailable) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.PRE.ordinal(), Menu.NONE, "Predictions");
                title = item.getTitle();
                if (titleMaxChars < title.length()) titleMaxChars = title.length();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.prediction, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(sp.getBoolean("showprediction", true));
            }

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.BAS.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_basals));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.basal, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showbasals", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.ACTPRIM.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showactivityprimary", true));

            dividerItem = popup.getMenu().add("");
            dividerItem.setEnabled(false);

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.IOB.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_iob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.iob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showiob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.COB.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_cob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.cob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showcob", true));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEV.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_deviations));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.deviations, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showdeviations", false));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.SEN.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_sensitivity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.ratio, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showratios", false));

            item = popup.getMenu().add(Menu.NONE, CHARTTYPE.ACTSEC.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(sp.getBoolean("showactivitysecondary", true));

            if (buildHelper.isDev()) {
                item = popup.getMenu().add(Menu.NONE, CHARTTYPE.DEVSLOPE.ordinal(), Menu.NONE, "Deviation slope");
                title = item.getTitle();
                if (titleMaxChars < title.length()) titleMaxChars = title.length();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.devslopepos, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(sp.getBoolean("showdevslope", false));
            }

            // Fairly good guestimate for required divider text size...
            title = new String(new char[titleMaxChars + 10]).replace("\0", "_");
            dividerItem.setTitle(title);

            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if (item.getItemId() == CHARTTYPE.PRE.ordinal()) {
                        sp.putBoolean("showprediction", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.BAS.ordinal()) {
                        sp.putBoolean("showbasals", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.IOB.ordinal()) {
                        sp.putBoolean("showiob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.COB.ordinal()) {
                        sp.putBoolean("showcob", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEV.ordinal()) {
                        sp.putBoolean("showdeviations", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.SEN.ordinal()) {
                        sp.putBoolean("showratios", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.ACTPRIM.ordinal()) {
                        sp.putBoolean("showactivityprimary", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.ACTSEC.ordinal()) {
                        sp.putBoolean("showactivitysecondary", !item.isChecked());
                    } else if (item.getItemId() == CHARTTYPE.DEVSLOPE.ordinal()) {
                        sp.putBoolean("showdevslope", !item.isChecked());
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
            final PumpDescription pumpDescription =
                    activePlugin.getActivePump().getPumpDescription();
            if (!profileFunction.isProfileValid("ContexMenuCreation"))
                return;
            menu.setHeaderTitle(resourceHelper.gs(R.string.loop));
            if (loopPlugin.isEnabled(PluginType.LOOP)) {
                menu.add(resourceHelper.gs(R.string.disableloop));
                if (!loopPlugin.isSuspended()) {
                    menu.add(resourceHelper.gs(R.string.suspendloopfor1h));
                    menu.add(resourceHelper.gs(R.string.suspendloopfor2h));
                    menu.add(resourceHelper.gs(R.string.suspendloopfor3h));
                    menu.add(resourceHelper.gs(R.string.suspendloopfor10h));
                } else {
                    if (!loopPlugin.isDisconnected()) {
                        menu.add(resourceHelper.gs(R.string.resume));
                    }
                }
            }

            if (!loopPlugin.isEnabled(PluginType.LOOP)) {
                menu.add(resourceHelper.gs(R.string.enableloop));
            }

            if (!loopPlugin.isDisconnected()) {
                showSuspendtPump(menu, pumpDescription);
            } else {
                menu.add(resourceHelper.gs(R.string.reconnect));
            }

        } else if (v == activeProfileView) {
            menu.setHeaderTitle(resourceHelper.gs(R.string.profile));
            menu.add(resourceHelper.gs(R.string.danar_viewprofile));
            activePlugin.getActiveProfileInterface();
            if (activePlugin.getActiveProfileInterface().getProfile() != null) {
                menu.add(resourceHelper.gs(R.string.careportal_profileswitch));
            }
        } else if (v == tempTargetView) {
            menu.setHeaderTitle(resourceHelper.gs(R.string.careportal_temporarytarget));
            menu.add(resourceHelper.gs(R.string.custom));
            menu.add(resourceHelper.gs(R.string.eatingsoon));
            menu.add(resourceHelper.gs(R.string.activity));
            menu.add(resourceHelper.gs(R.string.hypo));
            if (treatmentsPlugin.getTempTargetFromHistory() != null) {
                menu.add(resourceHelper.gs(R.string.cancel));
            }
        }
    }

    private void showSuspendtPump(ContextMenu menu, PumpDescription pumpDescription) {
        if (pumpDescription.tempDurationStep15mAllowed)
            menu.add(resourceHelper.gs(R.string.disconnectpumpfor15m));
        if (pumpDescription.tempDurationStep30mAllowed)
            menu.add(resourceHelper.gs(R.string.disconnectpumpfor30m));
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor1h));
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor2h));
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor3h));
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        final Profile profile = profileFunction.getProfile();
        if (profile == null)
            return true;
        if (item.getTitle().equals(resourceHelper.gs(R.string.disableloop))) {
            aapsLogger.debug("USER ENTRY: LOOP DISABLED");
            loopPlugin.setPluginEnabled(PluginType.LOOP, false);
            loopPlugin.setFragmentVisible(PluginType.LOOP, false);
            configBuilderPlugin.storeSettings("DisablingLoop");
            updateGUI("suspendmenu");
            commandQueue.cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            loopPlugin.createOfflineEvent(24 * 60); // upload 24h, we don't know real duration
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.enableloop))) {
            aapsLogger.debug("USER ENTRY: LOOP ENABLED");
            loopPlugin.setPluginEnabled(PluginType.LOOP, true);
            loopPlugin.setFragmentVisible(PluginType.LOOP, true);
            configBuilderPlugin.storeSettings("EnablingLoop");
            updateGUI("suspendmenu");
            loopPlugin.createOfflineEvent(0);
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.resume)) ||
                item.getTitle().equals(resourceHelper.gs(R.string.reconnect))) {
            aapsLogger.debug("USER ENTRY: RESUME");
            loopPlugin.suspendTo(0L);
            updateGUI("suspendmenu");
            commandQueue.cancelTempBasal(true, new Callback() {
                @Override
                public void run() {
                    if (!result.success) {
                        ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.tempbasaldeliveryerror));
                    }
                }
            });
            sp.putBoolean(R.string.key_objectiveusereconnect, true);
            loopPlugin.createOfflineEvent(0);
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.suspendloopfor1h))) {
            aapsLogger.debug("USER ENTRY: SUSPEND 1h");
            loopPlugin.suspendLoop(60);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.suspendloopfor2h))) {
            aapsLogger.debug("USER ENTRY: SUSPEND 2h");
            loopPlugin.suspendLoop(120);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.suspendloopfor3h))) {
            aapsLogger.debug("USER ENTRY: SUSPEND 3h");
            loopPlugin.suspendLoop(180);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.suspendloopfor10h))) {
            aapsLogger.debug("USER ENTRY: SUSPEND 10h");
            loopPlugin.suspendLoop(600);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.disconnectpumpfor15m))) {
            aapsLogger.debug("USER ENTRY: DISCONNECT 15m");
            loopPlugin.disconnectPump(15, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.disconnectpumpfor30m))) {
            aapsLogger.debug("USER ENTRY: DISCONNECT 30m");
            loopPlugin.disconnectPump(30, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.disconnectpumpfor1h))) {
            aapsLogger.debug("USER ENTRY: DISCONNECT 1h");
            loopPlugin.disconnectPump(60, profile);
            sp.putBoolean(R.string.key_objectiveusedisconnect, true);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.disconnectpumpfor2h))) {
            aapsLogger.debug("USER ENTRY: DISCONNECT 2h");
            loopPlugin.disconnectPump(120, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.disconnectpumpfor3h))) {
            aapsLogger.debug("USER ENTRY: DISCONNECT 3h");
            loopPlugin.disconnectPump(180, profile);
            updateGUI("suspendmenu");
            return true;
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.careportal_profileswitch))) {
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                new ProfileSwitchDialog().show(manager, "Overview");
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.danar_viewprofile))) {
            Bundle args = new Bundle();
            args.putLong("time", DateUtil.now());
            args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal());
            ProfileViewerDialog pvd = new ProfileViewerDialog();
            pvd.setArguments(args);
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                pvd.show(manager, "ProfileViewDialog");
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.eatingsoon))) {
            aapsLogger.debug("USER ENTRY: TEMP TARGET EATING SOON");
            double target = Profile.toMgdl(defaultValueHelper.determineEatingSoonTT(), profileFunction.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(System.currentTimeMillis())
                    .duration(defaultValueHelper.determineEatingSoonTTDuration())
                    .reason(resourceHelper.gs(R.string.eatingsoon))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            treatmentsPlugin.addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.activity))) {
            aapsLogger.debug("USER ENTRY: TEMP TARGET ACTIVITY");
            double target = Profile.toMgdl(defaultValueHelper.determineActivityTT(), profileFunction.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(defaultValueHelper.determineActivityTTDuration())
                    .reason(resourceHelper.gs(R.string.activity))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            treatmentsPlugin.addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.hypo))) {
            aapsLogger.debug("USER ENTRY: TEMP TARGET HYPO");
            double target = Profile.toMgdl(defaultValueHelper.determineHypoTT(), profileFunction.getUnits());
            TempTarget tempTarget = new TempTarget()
                    .date(now())
                    .duration(defaultValueHelper.determineHypoTTDuration())
                    .reason(resourceHelper.gs(R.string.hypo))
                    .source(Source.USER)
                    .low(target)
                    .high(target);
            treatmentsPlugin.addToHistoryTempTarget(tempTarget);
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.custom))) {
            FragmentManager manager = getFragmentManager();
            if (manager != null)
                new TempTargetDialog().show(manager, "Overview");
        } else if (item.getTitle().equals(resourceHelper.gs(R.string.cancel))) {
            aapsLogger.debug("USER ENTRY: TEMP TARGET CANCEL");
            TempTarget tempTarget = new TempTarget()
                    .source(Source.USER)
                    .date(now())
                    .duration(0)
                    .low(0)
                    .high(0);
            treatmentsPlugin.addToHistoryTempTarget(tempTarget);
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        boolean xdrip = xdripPlugin.isEnabled(PluginType.BGSOURCE);
        boolean dexcom = dexcomPlugin.isEnabled(PluginType.BGSOURCE);

        FragmentManager manager = getFragmentManager();
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (manager == null || manager.isStateSaved())
            return;
        switch (v.getId()) {
            case R.id.overview_accepttempbutton:
                onClickAcceptTemp();
                break;
            case R.id.overview_quickwizardbutton:
                protectionCheck.queryProtection(getActivity(), ProtectionCheck.Protection.BOLUS, this::onClickQuickwizard);
                break;
            case R.id.overview_wizardbutton:
                protectionCheck.queryProtection(getActivity(), ProtectionCheck.Protection.BOLUS, () -> new WizardDialog().show(manager, "WizardDialog"));
                break;
            case R.id.overview_calibrationbutton:
                if (xdrip) {
                    CalibrationDialog calibrationDialog = new CalibrationDialog();
                    calibrationDialog.show(manager, "CalibrationDialog");
                } else if (dexcom) {
                    try {
                        String packageName = dexcomPlugin.findDexcomPackageName();
                        if (packageName != null) {
                            Intent i = new Intent("com.dexcom.cgm.activities.MeterEntryActivity");
                            i.setPackage(packageName);
                            startActivity(i);
                        } else {
                            ToastUtils.showToastInUiThread(getActivity(), resourceHelper.gs(R.string.dexcom_app_not_installed));
                        }
                    } catch (ActivityNotFoundException e) {
                        ToastUtils.showToastInUiThread(getActivity(), resourceHelper.gs(R.string.g5appnotdetected));
                    }
                }
                break;
            case R.id.overview_cgmbutton:
                if (xdrip)
                    openCgmApp("com.eveningoutpost.dexdrip");
                else if (dexcom) {
                    String packageName = dexcomPlugin.findDexcomPackageName();
                    if (packageName != null) {
                        openCgmApp(packageName);
                    } else {
                        ToastUtils.showToastInUiThread(getActivity(), resourceHelper.gs(R.string.dexcom_app_not_installed));
                    }
                }
                break;
            case R.id.overview_treatmentbutton:
                protectionCheck.queryProtection(getActivity(), ProtectionCheck.Protection.BOLUS, () -> new TreatmentDialog().show(manager, "Overview"));
                break;
            case R.id.overview_insulinbutton:
                protectionCheck.queryProtection(getActivity(), ProtectionCheck.Protection.BOLUS, () -> new InsulinDialog().show(manager, "Overview"));
                break;
            case R.id.overview_carbsbutton:
                protectionCheck.queryProtection(getActivity(), ProtectionCheck.Protection.BOLUS, () -> new CarbsDialog().show(manager, "Overview"));
                break;
            case R.id.overview_pumpstatus:
                if (activePlugin.getActivePump().isSuspended() || !activePlugin.getActivePump().isInitialized())
                    commandQueue.readStatus("RefreshClicked", null);
                break;
        }

    }

    private void openCgmApp(String packageName) {
        PackageManager packageManager = getContext().getPackageManager();
        try {
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            OKDialog.show(getContext(), "", resourceHelper.gs(R.string.error_starting_cgm));
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
        Profile profile = profileFunction.getProfile();
        Context context = getContext();

        if (context == null) return;

        if (loopPlugin.isEnabled(PluginType.LOOP) && profile != null) {
            loopPlugin.invoke("Accept temp button", false);
            if (loopPlugin.lastRun != null && loopPlugin.lastRun.lastAPSRun != null && loopPlugin.lastRun.constraintsProcessed.isChangeRequested()) {
                OKDialog.showConfirmation(context, resourceHelper.gs(R.string.pump_tempbasal_label), loopPlugin.lastRun.constraintsProcessed.toSpanned(), () -> {
                    aapsLogger.debug("USER ENTRY: ACCEPT TEMP BASAL");
                    hideTempRecommendation();
                    clearNotification();
                    loopPlugin.acceptChangeRequest();
                });
            }
        }
    }

    private void onClickQuickwizard() {
        final BgReading actualBg = iobCobCalculatorPlugin.actualBg();
        final Profile profile = profileFunction.getProfile();
        final String profileName = profileFunction.getProfileName();
        final PumpInterface pump = activePlugin.getActivePump();

        final QuickWizardEntry quickWizardEntry = quickWizard.getActive();
        if (quickWizardEntry != null && actualBg != null && profile != null) {
            quickWizardButton.setVisibility(View.VISIBLE);
            final BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, actualBg, true);

            if (wizard.getCalculatedTotalInsulin() > 0d && quickWizardEntry.carbs() > 0d) {
                Integer carbsAfterConstraints = constraintChecker.applyCarbsConstraints(new Constraint<>(quickWizardEntry.carbs())).value();

                if (Math.abs(wizard.getInsulinAfterConstraints() - wizard.getCalculatedTotalInsulin()) >= pump.getPumpDescription().pumpType.determineCorrectBolusStepSize(wizard.getInsulinAfterConstraints()) || !carbsAfterConstraints.equals(quickWizardEntry.carbs())) {
                    OKDialog.show(getContext(), resourceHelper.gs(R.string.treatmentdeliveryerror), resourceHelper.gs(R.string.constraints_violation) + "\n" + resourceHelper.gs(R.string.changeyourinput));
                    return;
                }

                wizard.confirmAndExecute(getContext());
            }
        }
    }

    private void hideTempRecommendation() {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(() -> {
                if (acceptTempButton != null)
                    acceptTempButton.setVisibility(View.GONE);
            });
    }

    private void clearNotification() {
        NotificationManager notificationManager =
                (NotificationManager) mainApp.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(Constants.notificationID);

        actionStringHandler.handleInitiate("cancelChangeRequest");
    }

    private void updatePumpStatus(EventPumpStatusChanged event) {
        String status = event.getStatus(resourceHelper);
        if (!status.equals("")) {
            pumpStatusView.setText(status);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            loopStatusLayout.setVisibility(View.GONE);
        } else {
            pumpStatusLayout.setVisibility(View.GONE);
            loopStatusLayout.setVisibility(View.VISIBLE);
        }
    }

    private void scheduleUpdateGUI(final String from) {
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
        aapsLogger.debug(LTag.OVERVIEW, "updateGUI entered from: " + from);
        final long updateGUIStart = System.currentTimeMillis();

        if (getActivity() == null)
            return;

        if (timeView != null) { //must not exists
            timeView.setText(DateUtil.timeString(new Date()));
        }

        notificationStore.updateNotifications(notificationsView);

        pumpStatusLayout.setVisibility(View.GONE);
        loopStatusLayout.setVisibility(View.GONE);

        if (!profileFunction.isProfileValid("Overview")) {
            pumpStatusView.setText(R.string.noprofileset);
            pumpStatusLayout.setVisibility(View.VISIBLE);
            return;
        }
        loopStatusLayout.setVisibility(View.VISIBLE);

        statusLightHandler.updateAge(sage, iage, cage, pbage);
        BgReading actualBG = iobCobCalculatorPlugin.actualBg();
        BgReading lastBG = iobCobCalculatorPlugin.lastBg();

        final PumpInterface pump = activePlugin.getActivePump();

        final Profile profile = profileFunction.getProfile();
        if (profile == null) return;
        final String profileName = profileFunction.getProfileName();

        final String units = profileFunction.getUnits();
        final double lowLine = defaultValueHelper.determineLowLine();
        final double highLine = defaultValueHelper.determineHighLine();

        //Start with updating the BG as it is unaffected by loop.
        // **** BG value ****
        if (lastBG != null) {
            int color = resourceHelper.gc(R.color.inrange);
            if (lastBG.valueToUnits(units) < lowLine)
                color = resourceHelper.gc(R.color.low);
            else if (lastBG.valueToUnits(units) > highLine)
                color = resourceHelper.gc(R.color.high);
            bgView.setText(lastBG.valueToUnitsToString(units));
            arrowView.setText(lastBG.directionToSymbol());
            bgView.setTextColor(color);
            arrowView.setTextColor(color);
            GlucoseStatus glucoseStatus = new GlucoseStatus(injector).getGlucoseStatusData();
            if (glucoseStatus != null) {
                if (deltaView != null)
                    deltaView.setText("Δ " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
                if (deltaShortView != null)
                    deltaShortView.setText(Profile.toSignedUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units));
                if (avgdeltaView != null)
                    avgdeltaView.setText("øΔ15m: " + Profile.toUnitsString(glucoseStatus.short_avgdelta, glucoseStatus.short_avgdelta * Constants.MGDL_TO_MMOLL, units) + "\n" +
                            "øΔ40m: " + Profile.toUnitsString(glucoseStatus.long_avgdelta, glucoseStatus.long_avgdelta * Constants.MGDL_TO_MMOLL, units));
            } else {
                if (deltaView != null)
                    deltaView.setText("Δ " + resourceHelper.gs(R.string.notavailable));
                if (deltaShortView != null)
                    deltaShortView.setText("---");
                if (avgdeltaView != null)
                    avgdeltaView.setText("");
            }
        }

        Constraint<Boolean> closedLoopEnabled = constraintChecker.isClosedLoopAllowed();

        // open loop mode
        if (Config.APS && pump.getPumpDescription().isTempBasalCapable) {
            apsModeView.setVisibility(View.VISIBLE);
            apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault));
            apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault));
            if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuperBolus()) {
                apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning));
                apsModeView.setText(String.format(resourceHelper.gs(R.string.loopsuperbolusfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning));
            } else if (loopPlugin.isDisconnected()) {
                apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonCritical));
                apsModeView.setText(String.format(resourceHelper.gs(R.string.loopdisconnectedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextCritical));
            } else if (loopPlugin.isEnabled(PluginType.LOOP) && loopPlugin.isSuspended()) {
                apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning));
                apsModeView.setText(String.format(resourceHelper.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend()));
                apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning));
            } else if (pump.isSuspended()) {
                apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning));
                apsModeView.setText(resourceHelper.gs(R.string.pumpsuspended));
                apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning));
            } else if (loopPlugin.isEnabled(PluginType.LOOP)) {
                if (closedLoopEnabled.value()) {
                    apsModeView.setText(resourceHelper.gs(R.string.closedloop));
                } else {
                    apsModeView.setText(resourceHelper.gs(R.string.openloop));
                }
            } else {
                apsModeView.setBackgroundColor(resourceHelper.gc(R.color.ribbonCritical));
                apsModeView.setText(resourceHelper.gs(R.string.disabledloop));
                apsModeView.setTextColor(resourceHelper.gc(R.color.ribbonTextCritical));
            }
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        // temp target
        TempTarget tempTarget = treatmentsPlugin.getTempTargetFromHistory();
        if (tempTarget != null) {
            tempTargetView.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning));
            tempTargetView.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning));
            tempTargetView.setText(Profile.toTargetRangeString(tempTarget.low, tempTarget.high, Constants.MGDL, units) + " " + DateUtil.untilString(tempTarget.end(), resourceHelper));
        } else {
            tempTargetView.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault));
            tempTargetView.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault));
            tempTargetView.setText(Profile.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), Constants.MGDL, units));
        }

        // **** Temp button ****
        if (acceptTempButton != null) {
            boolean showAcceptButton = !closedLoopEnabled.value(); // Open mode needed
            showAcceptButton = showAcceptButton && loopPlugin.lastRun != null && loopPlugin.lastRun.lastAPSRun != null; // aps result must exist
            showAcceptButton = showAcceptButton && (loopPlugin.lastRun.lastOpenModeAccept == 0 || loopPlugin.lastRun.lastOpenModeAccept < loopPlugin.lastRun.lastAPSRun.getTime()); // never accepted or before last result
            showAcceptButton = showAcceptButton && loopPlugin.lastRun.constraintsProcessed.isChangeRequested(); // change is requested

            if (showAcceptButton && pump.isInitialized() && !pump.isSuspended() && loopPlugin.isEnabled(PluginType.LOOP)) {
                acceptTempButton.setVisibility(View.VISIBLE);
                acceptTempButton.setText(resourceHelper.gs(R.string.setbasalquestion) + "\n" + loopPlugin.lastRun.constraintsProcessed);
            } else {
                acceptTempButton.setVisibility(View.GONE);
            }
        }

        // **** Calibration & CGM buttons ****
        boolean xDripIsBgSource = xdripPlugin.isEnabled(PluginType.BGSOURCE);
        boolean dexcomIsSource = dexcomPlugin.isEnabled(PluginType.BGSOURCE);
        if (calibrationButton != null) {
            if ((xDripIsBgSource || dexcomIsSource) && actualBG != null && sp.getBoolean(R.string.key_show_calibration_button, true)) {
                calibrationButton.setVisibility(View.VISIBLE);
            } else {
                calibrationButton.setVisibility(View.GONE);
            }
        }
        if (cgmButton != null) {
            if (xDripIsBgSource && sp.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else if (dexcomIsSource && sp.getBoolean(R.string.key_show_cgm_button, false)) {
                cgmButton.setVisibility(View.VISIBLE);
            } else {
                cgmButton.setVisibility(View.GONE);
            }
        }

        final TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis());
        String basalText = "";
        if (shorttextmode) {
            if (activeTemp != null) {
                basalText = "T: " + activeTemp.toStringVeryShort();
            } else {
                basalText = resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal());
            }
        } else {
            if (activeTemp != null) {
                basalText = activeTemp.toStringFull();
            } else {
                basalText = resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal());
            }
        }
        baseBasalView.setText(basalText);
        baseBasalView.setOnClickListener(v -> {
            String fullText = resourceHelper.gs(R.string.pump_basebasalrate_label) + ": " + resourceHelper.gs(R.string.pump_basebasalrate, profile.getBasal()) + "\n";
            if (activeTemp != null) {
                fullText += resourceHelper.gs(R.string.pump_tempbasal_label) + ": " + activeTemp.toStringFull();
            }
            OKDialog.show(getActivity(), resourceHelper.gs(R.string.basal), fullText);
        });

        if (activeTemp != null) {
            baseBasalView.setTextColor(resourceHelper.gc(R.color.basal));
        } else {
            baseBasalView.setTextColor(resourceHelper.gc(R.color.defaulttextcolor));
        }


        final ExtendedBolus extendedBolus = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis());
        String extendedBolusText = "";
        if (extendedBolusView != null) { // must not exists in all layouts
            if (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses())
                extendedBolusText = shorttextmode ? DecimalFormatter.to2Decimal(extendedBolus.absoluteRate()) + "U/h" : extendedBolus.toStringMedium();
            extendedBolusView.setText(extendedBolusText);
            extendedBolusView.setOnClickListener(v -> {
                if (extendedBolus != null)
                    OKDialog.show(getActivity(), resourceHelper.gs(R.string.extended_bolus), extendedBolus.toString());
            });
        }

        activeProfileView.setText(profileFunction.getProfileNameWithDuration());
        if (profile.getPercentage() != 100 || profile.getTimeshift() != 0) {
            activeProfileView.setBackgroundColor(resourceHelper.gc(R.color.ribbonWarning));
            activeProfileView.setTextColor(resourceHelper.gc(R.color.ribbonTextWarning));
        } else {
            activeProfileView.setBackgroundColor(resourceHelper.gc(R.color.ribbonDefault));
            activeProfileView.setTextColor(resourceHelper.gc(R.color.ribbonTextDefault));
        }

        // QuickWizard button
        QuickWizardEntry quickWizardEntry = quickWizard.getActive();
        if (quickWizardEntry != null && lastBG != null && pump.isInitialized() && !pump.isSuspended()) {
            quickWizardButton.setVisibility(View.VISIBLE);
            String text = quickWizardEntry.buttonText() + "\n" + DecimalFormatter.to0Decimal(quickWizardEntry.carbs()) + "g";
            BolusWizard wizard = quickWizardEntry.doCalc(profile, profileName, lastBG, false);
            text += " " + DecimalFormatter.toPumpSupportedBolus(wizard.getCalculatedTotalInsulin(), pump) + "U";
            quickWizardButton.setText(text);
            if (wizard.getCalculatedTotalInsulin() <= 0)
                quickWizardButton.setVisibility(View.GONE);
        } else
            quickWizardButton.setVisibility(View.GONE);

        // **** Various treatment buttons ****
        if (carbsButton != null) {
            if (sp.getBoolean(R.string.key_show_carbs_button, true)
                    && (!activePlugin.getActivePump().getPumpDescription().storesCarbInfo ||
                    (pump.isInitialized() && !pump.isSuspended()))) {
                carbsButton.setVisibility(View.VISIBLE);
            } else {
                carbsButton.setVisibility(View.GONE);
            }
        }

        if (pump.isInitialized() && !pump.isSuspended()) {
            if (treatmentButton != null) {
                if (sp.getBoolean(R.string.key_show_treatment_button, false)) {
                    treatmentButton.setVisibility(View.VISIBLE);
                } else {
                    treatmentButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && wizardButton != null) {
                if (sp.getBoolean(R.string.key_show_wizard_button, true)) {
                    wizardButton.setVisibility(View.VISIBLE);
                } else {
                    wizardButton.setVisibility(View.GONE);
                }
            }
            if (pump.isInitialized() && !pump.isSuspended() && insulinButton != null) {
                if (sp.getBoolean(R.string.key_show_insulin_button, true)) {
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
        int flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        if (timeAgoView != null)
            timeAgoView.setText(DateUtil.minAgo(resourceHelper, lastBG.date));
        if (timeAgoShortView != null)
            timeAgoShortView.setText("(" + DateUtil.minAgoShort(lastBG.date) + ")");

        // iob
        treatmentsPlugin.updateTotalIOBTreatments();
        treatmentsPlugin.updateTotalIOBTempBasals();
        final IobTotal bolusIob = treatmentsPlugin.getLastCalculationTreatments().round();
        final IobTotal basalIob = treatmentsPlugin.getLastCalculationTempBasals().round();

        if (shorttextmode) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U";
            iobView.setText(iobtext);
            iobView.setOnClickListener(v -> {
                String iobtext1 = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U\n"
                        + resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U\n"
                        + resourceHelper.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U\n";
                OKDialog.show(getActivity(), resourceHelper.gs(R.string.iob), iobtext1);
            });
        } else if (resourceHelper.gb(R.bool.isTablet)) {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                    + resourceHelper.gs(R.string.basal) + ": " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U)";
            iobView.setText(iobtext);
        } else {
            String iobtext = DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                    + DecimalFormatter.to2Decimal(bolusIob.iob) + "/"
                    + DecimalFormatter.to2Decimal(basalIob.basaliob) + ")";
            iobView.setText(iobtext);
        }

        // cob
        if (cobView != null) { // view must not exists
            String cobText = resourceHelper.gs(R.string.value_unavailable_short);
            CobInfo cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Overview COB");
            if (cobInfo.displayCob != null) {
                cobText = DecimalFormatter.to0Decimal(cobInfo.displayCob);
                if (cobInfo.futureCarbs > 0)
                    cobText += "(" + DecimalFormatter.to0Decimal(cobInfo.futureCarbs) + ")";
            }
            cobView.setText(cobText);
        }

        if (statuslightsLayout != null)
            if (sp.getBoolean(R.string.key_show_statuslights, false)) {
                if (sp.getBoolean(R.string.key_show_statuslights_extended, false)) {
                    statusLightHandler.extendedStatusLight(cageView, iageView, reservoirView, sageView, batteryView);
                    statuslightsLayout.setVisibility(View.VISIBLE);
                } else {
                    statusLightHandler.statusLight(cageView, iageView, reservoirView, sageView, batteryView);
                    statuslightsLayout.setVisibility(View.VISIBLE);
                }
            } else {
                statuslightsLayout.setVisibility(View.GONE);
            }

        boolean predictionsAvailable;
        if (Config.APS)
            predictionsAvailable = loopPlugin.lastRun != null && loopPlugin.lastRun.request.hasPredictions;
        else if (Config.NSCLIENT)
            predictionsAvailable = true;
        else
            predictionsAvailable = false;
        final boolean finalPredictionsAvailable = predictionsAvailable;

        // pump status from ns
        if (pumpDeviceStatusView != null) {
            pumpDeviceStatusView.setText(nsDeviceStatus.getPumpStatus());
            pumpDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), resourceHelper.gs(R.string.pump), nsDeviceStatus.getExtendedPumpStatus()));
        }

        // OpenAPS status from ns
        if (openapsDeviceStatusView != null) {
            openapsDeviceStatusView.setText(nsDeviceStatus.getOpenApsStatus());
            openapsDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), resourceHelper.gs(R.string.openaps), nsDeviceStatus.getExtendedOpenApsStatus()));
        }

        // Uploader status from ns
        if (uploaderDeviceStatusView != null) {
            uploaderDeviceStatusView.setText(nsDeviceStatus.getUploaderStatusSpanned());
            uploaderDeviceStatusView.setOnClickListener(v -> OKDialog.show(getActivity(), resourceHelper.gs(R.string.uploader), nsDeviceStatus.getExtendedUploaderStatus()));
        }

        // Sensitivity
        if (sensitivityView != null) {
            AutosensData autosensData = iobCobCalculatorPlugin.getLastAutosensData("Overview");
            if (autosensData != null)
                sensitivityView.setText(String.format(Locale.ENGLISH, "%.0f%%", autosensData.autosensResult.ratio * 100));
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

            if (finalPredictionsAvailable && sp.getBoolean("showprediction", false)) {
                if (Config.APS)
                    apsResult = loopPlugin.lastRun.constraintsProcessed;
                else
                    apsResult = NSDeviceStatus.getAPSResult(injector);
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
            Profiler.log(aapsLogger, LTag.OVERVIEW, from + " - 1st graph - START", updateGUIStart);

            final GraphData graphData = new GraphData(injector, bgGraph, iobCobCalculatorPlugin);

            // **** In range Area ****
            graphData.addInRangeArea(fromTime, endTime, lowLine, highLine);

            // **** BG ****
            if (finalPredictionsAvailable && sp.getBoolean("showprediction", false))
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine,
                        apsResult.getPredictions());
            else
                graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null);

            // set manual x bounds to have nice steps
            graphData.formatAxis(fromTime, endTime);

            // Treatments
            graphData.addTreatments(fromTime, endTime);

            if (sp.getBoolean("showactivityprimary", true)) {
                graphData.addActivity(fromTime, endTime, false, 0.8d);
            }

            // add basal data
            if (pump.getPumpDescription().isTempBasalCapable && sp.getBoolean("showbasals", true)) {
                graphData.addBasals(fromTime, now, lowLine / graphData.maxY / 1.2d);
            }

            // add target line
            graphData.addTargetLine(fromTime, toTime, profile, loopPlugin.lastRun);

            // **** NOW line ****
            graphData.addNowLine(now);

            // ------------------ 2nd graph
            Profiler.log(aapsLogger, LTag.OVERVIEW, from + " - 2nd graph - START", updateGUIStart);

            final GraphData secondGraphData = new GraphData(injector, iobGraph, iobCobCalculatorPlugin);

            boolean useIobForScale = false;
            boolean useCobForScale = false;
            boolean useDevForScale = false;
            boolean useRatioForScale = false;
            boolean useDSForScale = false;
            boolean useIAForScale = false;

            if (sp.getBoolean("showiob", true)) {
                useIobForScale = true;
            } else if (sp.getBoolean("showcob", true)) {
                useCobForScale = true;
            } else if (sp.getBoolean("showdeviations", false)) {
                useDevForScale = true;
            } else if (sp.getBoolean("showratios", false)) {
                useRatioForScale = true;
            } else if (sp.getBoolean("showactivitysecondary", false)) {
                useIAForScale = true;
            } else if (sp.getBoolean("showdevslope", false)) {
                useDSForScale = true;
            }

            if (sp.getBoolean("showiob", true))
                secondGraphData.addIob(fromTime, now, useIobForScale, 1d, sp.getBoolean("showprediction", false));
            if (sp.getBoolean("showcob", true))
                secondGraphData.addCob(fromTime, now, useCobForScale, useCobForScale ? 1d : 0.5d);
            if (sp.getBoolean("showdeviations", false))
                secondGraphData.addDeviations(fromTime, now, useDevForScale, 1d);
            if (sp.getBoolean("showratios", false))
                secondGraphData.addRatio(fromTime, now, useRatioForScale, 1d);
            if (sp.getBoolean("showactivitysecondary", true))
                secondGraphData.addActivity(fromTime, endTime, useIAForScale, 0.8d);
            if (sp.getBoolean("showdevslope", false) && buildHelper.isDev())
                secondGraphData.addDeviationSlope(fromTime, now, useDSForScale, 1d);

            // **** NOW line ****
            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(fromTime, endTime);
            secondGraphData.addNowLine(now);

            // do GUI update
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (sp.getBoolean("showiob", true)
                            || sp.getBoolean("showcob", true)
                            || sp.getBoolean("showdeviations", false)
                            || sp.getBoolean("showratios", false)
                            || sp.getBoolean("showactivitysecondary", false)
                            || sp.getBoolean("showdevslope", false)) {
                        iobGraph.setVisibility(View.VISIBLE);
                    } else {
                        iobGraph.setVisibility(View.GONE);
                    }
                    // finally enforce drawing of graphs
                    graphData.performUpdate();
                    secondGraphData.performUpdate();
                    Profiler.log(aapsLogger, LTag.OVERVIEW, from + " - onDataChanged", updateGUIStart);
                });
            }
        }).start();

        Profiler.log(aapsLogger, LTag.OVERVIEW, from, updateGUIStart);
    }


}