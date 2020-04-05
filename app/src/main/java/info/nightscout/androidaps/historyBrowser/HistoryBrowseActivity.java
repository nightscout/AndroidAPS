package info.nightscout.androidaps.historyBrowser;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.res.ResourcesCompat;

import com.jjoe64.graphview.GraphView;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;

import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventCustomCalculationFinished;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction;
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment;
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DefaultValueHelper;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class HistoryBrowseActivity extends NoSplashAppCompatActivity {
    @Inject HasAndroidInjector injector;
    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject ProfileFunction profileFunction;
    @Inject DefaultValueHelper defaultValueHelper;
    @Inject IobCobStaticCalculatorPlugin iobCobStaticCalculatorPlugin;
    @Inject ActivePluginProvider activePlugin;
    @Inject BuildHelper buildHelper;
    @Inject FabricPrivacy fabricPrivacy;

    private CompositeDisposable disposable = new CompositeDisposable();

    ImageButton chartButton;

    boolean showBasal = true;
    boolean showIob, showCob, showDev, showRat, showActPrim, showActSec, showDevslope;


    Button buttonDate;
    Button buttonZoom;
    GraphView bgGraph;
    GraphView iobGraph;
    SeekBar seekBar;
    TextView noProfile;
    TextView iobCalculationProgressView;

    private int rangeToDisplay = 24; // for graph
    private long start = 0;


    EventCustomCalculationFinished eventCustomCalculationFinished = new EventCustomCalculationFinished();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historybrowse);

        buttonDate = findViewById(R.id.historybrowse_date);
        buttonZoom = findViewById(R.id.historybrowse_zoom);
        bgGraph = findViewById(R.id.historyybrowse_bggraph);
        iobGraph = findViewById(R.id.historybrowse_iobgraph);
        seekBar = findViewById(R.id.historybrowse_seekBar);
        noProfile = findViewById(R.id.historybrowse_noprofile);
        iobCalculationProgressView = findViewById(R.id.overview_iobcalculationprogess);

        findViewById(R.id.historybrowse_left).setOnClickListener(v -> {
            start -= T.hours(rangeToDisplay).msecs();
            updateGUI("onClickLeft");
            runCalculation("onClickLeft");
        });

        findViewById(R.id.historybrowse_right).setOnClickListener(v -> {
            start += T.hours(rangeToDisplay).msecs();
            updateGUI("onClickRight");
            runCalculation("onClickRight");
        });

        findViewById(R.id.historybrowse_end).setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            start = calendar.getTimeInMillis();
            updateGUI("onClickEnd");
            runCalculation("onClickEnd");
        });

        findViewById(R.id.historybrowse_zoom).setOnClickListener(v -> {
            rangeToDisplay += 6;
            rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
            updateGUI("rangeChange");
        });

        findViewById(R.id.historybrowse_zoom).setOnLongClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(start);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            start = calendar.getTimeInMillis();
            updateGUI("resetToMidnight");
            runCalculation("onLongClickZoom");
            return true;
        });

        findViewById(R.id.historybrowse_date).setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(start));
            DatePickerDialog dpd = DatePickerDialog.newInstance(
                    (view, year, monthOfYear, dayOfMonth) -> {
                        Date date = new Date(0);
                        date.setYear(year - 1900);
                        date.setMonth(monthOfYear);
                        date.setDate(dayOfMonth);
                        date.setHours(0);
                        start = date.getTime();
                        updateGUI("onClickDate");
                        runCalculation("onClickDate");
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            dpd.setThemeDark(true);
            dpd.dismissOnPause(true);
            dpd.show(getSupportFragmentManager(), "Datepickerdialog");
        });

        bgGraph.getGridLabelRenderer().setGridColor(resourceHelper.gc(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(resourceHelper.gc(R.color.graphgrid));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(5);

        setupChartMenu();
    }

    @Override
    public void onPause() {
        super.onPause();
        disposable.clear();
        iobCobStaticCalculatorPlugin.stopCalculation("onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventAutosensCalculationFinished.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    if (event.getCause() == eventCustomCalculationFinished) {
                        aapsLogger.debug(LTag.AUTOSENS, "EventAutosensCalculationFinished");
                        synchronized (HistoryBrowseActivity.this) {
                            updateGUI("EventAutosensCalculationFinished");
                        }
                    }
                }, exception -> fabricPrivacy.logException(exception))
        );
        disposable.add(rxBus
                .toObservable(EventIobCalculationProgress.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    if (iobCalculationProgressView != null)
                        iobCalculationProgressView.setText(event.getProgress());
                }, exception -> fabricPrivacy.logException(exception))
        );
        // set start of current day
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        start = calendar.getTimeInMillis();
        runCalculation("onResume");
        SystemClock.sleep(1000);
        updateGUI("onResume");
    }

    private void runCalculation(String from) {
        long end = start + T.hours(rangeToDisplay).msecs();
        iobCobStaticCalculatorPlugin.stopCalculation(from);
        iobCobStaticCalculatorPlugin.clearCache();
        iobCobStaticCalculatorPlugin.runCalculation(from, end, true, false, eventCustomCalculationFinished);
    }

    void updateGUI(String from) {
        aapsLogger.debug(LTag.UI, "updateGUI from: " + from);

        if (noProfile == null || buttonDate == null || buttonZoom == null || bgGraph == null || iobGraph == null || seekBar == null)
            return;

        final PumpInterface pump = activePlugin.getActivePump();
        final Profile profile = profileFunction.getProfile();

        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }

        final double lowLine = defaultValueHelper.determineLowLine();
        final double highLine = defaultValueHelper.determineHighLine();

        buttonDate.setText(DateUtil.dateAndTimeString(start));
        buttonZoom.setText(String.valueOf(rangeToDisplay));

        final boolean showPrediction = false;

        showBasal = sp.getBoolean("hist_showbasals", true);
        showIob = sp.getBoolean("hist_showiob", true);
        showCob = sp.getBoolean("hist_showcob", true);
        showDev = sp.getBoolean("hist_showdeviations", false);
        showRat = sp.getBoolean("hist_showratios", false);
        showActPrim = sp.getBoolean("hist_showactivityprimary", false);
        showActSec = sp.getBoolean("hist_showactivitysecondary", false);
        showDevslope = sp.getBoolean("hist_showdevslope", false);

        //int hoursToFetch;
        final long toTime;
        final long fromTime;
        //if (showPrediction) {
        //int predHours = (int) (Math.ceil(((DetermineBasalResultAMA) finalLastRun.constraintsProcessed).getLatestPredictionsTime() - System.currentTimeMillis()) / (60 * 60 * 1000));
        //predHours = Math.min(2, predHours);
        //predHours = Math.max(0, predHours);
        //hoursToFetch = rangeToDisplay - predHours;
        //toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding - Graphview specific
        //fromTime = toTime - hoursToFetch * 60 * 60 * 1000L;
        //endTime = toTime + predHours * 60 * 60 * 1000L;
        //} else {
        fromTime = start + T.secs(100).msecs();
        toTime = start + T.hours(rangeToDisplay).msecs();
        //}

        aapsLogger.debug(LTag.UI, "Period: " + DateUtil.dateAndTimeString(fromTime) + " - " + DateUtil.dateAndTimeString(toTime));

        final long pointer = System.currentTimeMillis();

        //  ------------------ 1st graph

        final GraphData graphData = new GraphData(injector, bgGraph, iobCobStaticCalculatorPlugin);

        // **** In range Area ****
        graphData.addInRangeArea(fromTime, toTime, lowLine, highLine);

        // **** BG ****
        if (showPrediction)
//graphData.addBgReadings(fromTime, toTime, lowLine, highLine, (DetermineBasalResultAMA) finalLastRun.constraintsProcessed);
            ;
        else
            graphData.addBgReadings(fromTime, toTime, lowLine, highLine, null);

        // set manual x bounds to have nice steps
        graphData.formatAxis(fromTime, toTime);

        if (showActPrim) {
            graphData.addActivity(fromTime, toTime, false, 1d);
        }

        // Treatments
        graphData.addTreatments(fromTime, toTime);

        // add basal data
        if (pump.getPumpDescription().isTempBasalCapable && showBasal) {
            graphData.addBasals(fromTime, toTime, lowLine / graphData.getMaxY() / 1.2d);
        }

        // **** NOW line ****
        graphData.addNowLine(pointer);

        // ------------------ 2nd graph

        new Thread(() -> {
            final GraphData secondGraphData = new GraphData(injector, iobGraph, iobCobStaticCalculatorPlugin);

            boolean useIobForScale = false;
            boolean useCobForScale = false;
            boolean useDevForScale = false;
            boolean useRatioForScale = false;
            boolean useIAForScale = false;
            boolean useDSForScale = false;

            if (showIob) {
                useIobForScale = true;
            } else if (showCob) {
                useCobForScale = true;
            } else if (showDev) {
                useDevForScale = true;
            } else if (showRat) {
                useRatioForScale = true;
            } else if (showActSec) {
                useIAForScale = true;
            } else if (showDevslope) {
                useDSForScale = true;
            }

            if (showIob)
                secondGraphData.addIob(fromTime, toTime, useIobForScale, 1d, showPrediction);
            if (showCob)
                secondGraphData.addCob(fromTime, toTime, useCobForScale, useCobForScale ? 1d : 0.5d);
            if (showDev)
                secondGraphData.addDeviations(fromTime, toTime, useDevForScale, 1d);
            if (showRat)
                secondGraphData.addRatio(fromTime, toTime, useRatioForScale, 1d);
            if (showActSec)
                secondGraphData.addActivity(fromTime, toTime, useIAForScale, useIAForScale ? 2d : 1d);
            if (showDevslope)
                secondGraphData.addDeviationSlope(fromTime, toTime, useDSForScale, 1d);

            // **** NOW line ****
            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(fromTime, toTime);
            secondGraphData.addNowLine(pointer);

            // do GUI update
            runOnUiThread(() -> {
                if (showIob || showCob || showDev || showRat || showActSec || showDevslope) {
                    iobGraph.setVisibility(View.VISIBLE);
                } else {
                    iobGraph.setVisibility(View.GONE);
                }
                // finally enforce drawing of graphs
                graphData.performUpdate();
                if (showIob || showCob || showDev || showRat || showActSec || showDevslope)
                    secondGraphData.performUpdate();
            });
        }).start();
    }

    private void setupChartMenu() {
        chartButton = findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(v -> {
            MenuItem item, dividerItem;
            CharSequence title;
            int titleMaxChars = 0;
            SpannableString s;
            PopupMenu popup = new PopupMenu(v.getContext(), v);


            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.BAS.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_basals));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.basal, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showBasal);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.ACTPRIM.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showActPrim);

            dividerItem = popup.getMenu().add("");
            dividerItem.setEnabled(false);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.IOB.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_iob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.iob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showIob);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.COB.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_cob));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.cob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showCob);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.DEV.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_deviations));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.deviations, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showDev);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.SEN.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_sensitivity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.ratio, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showRat);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.ACTSEC.ordinal(), Menu.NONE, resourceHelper.gs(R.string.overview_show_activity));
            title = item.getTitle();
            if (titleMaxChars < title.length()) titleMaxChars = title.length();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.activity, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showActSec);


            if (buildHelper.isDev()) {
                item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.DEVSLOPE.ordinal(), Menu.NONE, "Deviation slope");
                title = item.getTitle();
                if (titleMaxChars < title.length()) titleMaxChars = title.length();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.devslopepos, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(showDevslope);
            }

            // Fairly good guestimate for required divider text size...
            title = new String(new char[titleMaxChars + 10]).replace("\0", "_");
            dividerItem.setTitle(title);

            popup.setOnMenuItemClickListener(item1 -> {
                if (item1.getItemId() == OverviewFragment.CHARTTYPE.BAS.ordinal()) {
                    sp.putBoolean("hist_showbasals", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.IOB.ordinal()) {
                    sp.putBoolean("hist_showiob", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.COB.ordinal()) {
                    sp.putBoolean("hist_showcob", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.DEV.ordinal()) {
                    sp.putBoolean("hist_showdeviations", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.SEN.ordinal()) {
                    sp.putBoolean("hist_showratios", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.ACTPRIM.ordinal()) {
                    sp.putBoolean("hist_showactivityprimary", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.ACTSEC.ordinal()) {
                    sp.putBoolean("hist_showactivitysecondary", !item1.isChecked());
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.DEVSLOPE.ordinal()) {
                    sp.putBoolean("hist_showdevslope", !item1.isChecked());
                }
                updateGUI("onGraphCheckboxesCheckedChanged");
                return true;
            });
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp);
            popup.setOnDismissListener(menu -> chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp));
            popup.show();
        });
    }

}
