package info.nightscout.androidaps.activities;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.squareup.otto.Subscribe;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventCustomCalculationFinished;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventIobCalculationProgress;
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment;
import info.nightscout.androidaps.plugins.general.overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.general.overview.graphData.GraphData;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;

public class HistoryBrowseActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(HistoryBrowseActivity.class);


    ImageButton chartButton;

    boolean showBasal = true;
    boolean showIob, showCob, showDev, showRat, showDevslope;


    @BindView(R.id.historybrowse_date)
    Button buttonDate;
    @BindView(R.id.historybrowse_zoom)
    Button buttonZoom;
    @BindView(R.id.historyybrowse_bggraph)
    GraphView bgGraph;
    @BindView(R.id.historybrowse_iobgraph)
    GraphView iobGraph;
    @BindView(R.id.historybrowse_seekBar)
    SeekBar seekBar;
    @BindView(R.id.historybrowse_noprofile)
    TextView noProfile;
    @BindView(R.id.overview_iobcalculationprogess)
    TextView iobCalculationProgressView;

    private int rangeToDisplay = 24; // for graph
    private long start = 0;

    IobCobCalculatorPlugin iobCobCalculatorPlugin;

    EventCustomCalculationFinished eventCustomCalculationFinished = new EventCustomCalculationFinished();

    public HistoryBrowseActivity() {
        iobCobCalculatorPlugin = new IobCobCalculatorPlugin();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historybrowse);

        ButterKnife.bind(this);

        bgGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(MainApp.gc(R.color.graphgrid));
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
        MainApp.bus().unregister(this);
        iobCobCalculatorPlugin.stopCalculation("onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        MainApp.bus().register(this);
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

    @OnClick(R.id.historybrowse_left)
    void onClickLeft() {
        start -= T.hours(rangeToDisplay).msecs();
        updateGUI("onClickLeft");
        runCalculation("onClickLeft");
    }

    @OnClick(R.id.historybrowse_right)
    void onClickRight() {
        start += T.hours(rangeToDisplay).msecs();
        updateGUI("onClickRight");
        runCalculation("onClickRight");
    }

    @OnClick(R.id.historybrowse_end)
    void onClickEnd() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        start = calendar.getTimeInMillis();
        updateGUI("onClickEnd");
        runCalculation("onClickEnd");
    }

    @OnClick(R.id.historybrowse_zoom)
    void onClickZoom() {
        rangeToDisplay += 6;
        rangeToDisplay = rangeToDisplay > 24 ? 6 : rangeToDisplay;
        updateGUI("rangeChange");
    }

    @OnLongClick(R.id.historybrowse_zoom)
    boolean onLongClickZoom() {
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
    }

    @OnClick(R.id.historybrowse_date)
    void onClickDate() {
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
        dpd.show(getFragmentManager(), "Datepickerdialog");
    }

    private void runCalculation(String from) {
        long end = start + T.hours(rangeToDisplay).msecs();
        iobCobCalculatorPlugin.stopCalculation(from);
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation(from, end, true, false, eventCustomCalculationFinished);
    }

    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished e) {
        if (e.cause == eventCustomCalculationFinished) {
            log.debug("EventAutosensCalculationFinished");
            runOnUiThread(() -> {
                synchronized (HistoryBrowseActivity.this) {
                    updateGUI("EventAutosensCalculationFinished");
                }
            });
        }
    }

    @Subscribe
    public void onStatusEvent(final EventIobCalculationProgress e) {
        runOnUiThread(() -> {
            if (iobCalculationProgressView != null)
                iobCalculationProgressView.setText(e.progress);
        });
    }

    void updateGUI(String from) {
        log.debug("updateGUI from: " + from);

        if (noProfile == null || buttonDate == null || buttonZoom == null || bgGraph == null || iobGraph == null || seekBar == null)
            return;

        final PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
        final Profile profile = ProfileFunctions.getInstance().getProfile();

        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }

        final String units = profile.getUnits();
        final double lowLine = OverviewPlugin.getPlugin().determineLowLine(units);
        final double highLine = OverviewPlugin.getPlugin().determineHighLine(units);

        buttonDate.setText(DateUtil.dateAndTimeString(start));
        buttonZoom.setText(String.valueOf(rangeToDisplay));

        final boolean showPrediction = false;

        int hoursToFetch;
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

        log.debug("Period: " + DateUtil.dateAndTimeString(fromTime) + " - " + DateUtil.dateAndTimeString(toTime));

        final long pointer = System.currentTimeMillis();

        //  ------------------ 1st graph

        final GraphData graphData = new GraphData(bgGraph, iobCobCalculatorPlugin);

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

        // Treatments
        graphData.addTreatments(fromTime, toTime);

        // add basal data
        if (pump.getPumpDescription().isTempBasalCapable && showBasal) {
            graphData.addBasals(fromTime, toTime, lowLine / graphData.maxY / 1.2d);
        }

        // **** NOW line ****
        graphData.addNowLine(pointer);

        // ------------------ 2nd graph

        new Thread(() -> {
            final GraphData secondGraphData = new GraphData(iobGraph, iobCobCalculatorPlugin);

            boolean useIobForScale = false;
            boolean useCobForScale = false;
            boolean useDevForScale = false;
            boolean useRatioForScale = false;
            boolean useDSForScale = false;

            if (showIob) {
                useIobForScale = true;
            } else if (showCob) {
                useCobForScale = true;
            } else if (showDev) {
                useDevForScale = true;
            } else if (showRat) {
                useRatioForScale = true;
            } else if (showDevslope) {
                useDSForScale = true;
            }

            if (showIob)
                secondGraphData.addIob(fromTime, toTime, useIobForScale, 1d);
            if (showCob)
                secondGraphData.addCob(fromTime, toTime, useCobForScale, useCobForScale ? 1d : 0.5d);
            if (showDev)
                secondGraphData.addDeviations(fromTime, toTime, useDevForScale, 1d);
            if (showRat)
                secondGraphData.addRatio(fromTime, toTime, useRatioForScale, 1d);
            if (showDevslope)
                secondGraphData.addDeviationSlope(fromTime, toTime, useDSForScale, 1d);

            // **** NOW line ****
            // set manual x bounds to have nice steps
            secondGraphData.formatAxis(fromTime, toTime);
            secondGraphData.addNowLine(pointer);

            // do GUI update
            runOnUiThread(() -> {
                if (showIob || showCob || showDev || showRat || showDevslope) {
                    iobGraph.setVisibility(View.VISIBLE);
                } else {
                    iobGraph.setVisibility(View.GONE);
                }
                // finally enforce drawing of graphs
                graphData.performUpdate();
                if (showIob || showCob || showDev || showRat || showDevslope)
                    secondGraphData.performUpdate();
            });
        }).start();
    }

    private void setupChartMenu() {
        chartButton = (ImageButton) findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(v -> {
            MenuItem item;
            CharSequence title;
            SpannableString s;
            PopupMenu popup = new PopupMenu(v.getContext(), v);


            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.BAS.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_basals));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.basal, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showBasal);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.IOB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_iob));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.iob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showIob);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.COB.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_cob));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.cob, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showCob);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.DEV.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_deviations));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.deviations, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showDev);

            item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.SEN.ordinal(), Menu.NONE, MainApp.gs(R.string.overview_show_sensitivity));
            title = item.getTitle();
            s = new SpannableString(title);
            s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.ratio, null)), 0, s.length(), 0);
            item.setTitle(s);
            item.setCheckable(true);
            item.setChecked(showRat);

            if (MainApp.devBranch) {
                item = popup.getMenu().add(Menu.NONE, OverviewFragment.CHARTTYPE.DEVSLOPE.ordinal(), Menu.NONE, "Deviation slope");
                title = item.getTitle();
                s = new SpannableString(title);
                s.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.devslopepos, null)), 0, s.length(), 0);
                item.setTitle(s);
                item.setCheckable(true);
                item.setChecked(showDevslope);
            }

            popup.setOnMenuItemClickListener(item1 -> {
                if (item1.getItemId() == OverviewFragment.CHARTTYPE.BAS.ordinal()) {
                    showBasal = !item1.isChecked();
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.IOB.ordinal()) {
                    showIob = !item1.isChecked();
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.COB.ordinal()) {
                    showCob = !item1.isChecked();
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.DEV.ordinal()) {
                    showDev = !item1.isChecked();
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.SEN.ordinal()) {
                    showRat = !item1.isChecked();
                } else if (item1.getItemId() == OverviewFragment.CHARTTYPE.DEVSLOPE.ordinal()) {
                    showDevslope = !item1.isChecked();
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
