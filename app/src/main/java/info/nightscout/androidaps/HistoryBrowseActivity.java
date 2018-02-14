package info.nightscout.androidaps;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;

import com.jjoe64.graphview.GraphView;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventCustomCalculationFinished;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.IobCobCalculator.events.EventAutosensCalculationFinished;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.graphData.GraphData;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

public class HistoryBrowseActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(HistoryBrowseActivity.class);

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

    @BindView(R.id.overview_showprediction)
    CheckBox showPredictionCheckbox;
    @BindView(R.id.overview_showbasals)
    CheckBox showBasalsCheckbox;
    @BindView(R.id.overview_showiob)
    CheckBox showIobCheckbox;
    @BindView(R.id.overview_showcob)
    CheckBox showCobCheckbox;
    @BindView(R.id.overview_showdeviations)
    CheckBox showDeviationsCheckbox;
    @BindView(R.id.overview_showratios)
    CheckBox showRatiosCheckbox;

    private int rangeToDisplay = 24; // for graph
    private long start;

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

        bgGraph.getGridLabelRenderer().setGridColor(MainApp.sResources.getColor(R.color.graphgrid));
        bgGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setGridColor(MainApp.sResources.getColor(R.color.graphgrid));
        iobGraph.getGridLabelRenderer().reloadStyles();
        iobGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        bgGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setLabelVerticalWidth(50);
        iobGraph.getGridLabelRenderer().setNumVerticalLabels(5);

        // set start of current day
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        start = calendar.getTimeInMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGUI("onResume");
    }


    @OnClick(R.id.historybrowse_start)
    void onClickStart() {
    }

    @OnClick(R.id.historybrowse_left)
    void onClickLeft() {
        start -= rangeToDisplay * 60 * 60 * 1000L;
        updateGUI("left");
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation("onClickLeft", start, true, eventCustomCalculationFinished);
    }

    @OnClick(R.id.historybrowse_right)
    void onClickRight() {
        start += rangeToDisplay * 60 * 60 * 1000L;
        updateGUI("right");
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation("onClickRight", start, true, eventCustomCalculationFinished);
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
        updateGUI("resetToMidnight");
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation("onClickEnd", start, true, eventCustomCalculationFinished);
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
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation("onLongClickZoom", start, true, eventCustomCalculationFinished);
        return true;
    }

    @OnClick(R.id.historybrowse_date)
    void onClickDate() {
    }

    @OnClick({R.id.overview_showbasals, R.id.overview_showprediction, R.id.overview_showiob, R.id.overview_showcob, R.id.overview_showdeviations, R.id.overview_showratios})
    void onClickDate(View view) {
        //((CheckBox) view).toggle();
        updateGUI("checkboxToggle");
        iobCobCalculatorPlugin.clearCache();
        iobCobCalculatorPlugin.runCalculation("onClickDate", start, true, eventCustomCalculationFinished);
    }


    @Subscribe
    public void onStatusEvent(final EventAutosensCalculationFinished e) {
        Activity activity = this;
        if (activity != null && e.cause == eventCustomCalculationFinished) {
            log.debug("EventAutosensCalculationFinished");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI("EventAutosensCalculationFinished");
                }
            });
        }
    }

    void updateGUI(String from) {
        final PumpInterface pump = ConfigBuilderPlugin.getActivePump();
        final Profile profile = MainApp.getConfigBuilder().getProfile();
        final String units = profile.getUnits();

        double lowLineSetting = SP.getDouble("low_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetLow, units));
        double highLineSetting = SP.getDouble("high_mark", Profile.fromMgdlToUnits(OverviewPlugin.bgTargetHigh, units));

        if (lowLineSetting < 1)
            lowLineSetting = Profile.fromMgdlToUnits(76d, units);
        if (highLineSetting < 1)
            highLineSetting = Profile.fromMgdlToUnits(180d, units);

        final double lowLine = lowLineSetting;
        final double highLine = highLineSetting;

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
        fromTime = start + 100000;
        toTime = start + rangeToDisplay * 60 * 60 * 1000L;
        //}

        buttonDate.setText(DateUtil.dateAndTimeString(start));
        buttonZoom.setText(String.valueOf(rangeToDisplay));

        log.debug("Period: " + DateUtil.dateAndTimeString(fromTime) + " - " + DateUtil.dateAndTimeString(toTime));

        final long pointer = System.currentTimeMillis();

        //  ------------------ 1st graph

        final GraphData graphData = new GraphData(bgGraph, IobCobCalculatorPlugin.getPlugin());

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
        if (pump.getPumpDescription().isTempBasalCapable && showBasalsCheckbox.isChecked()) {
            graphData.addBasals(fromTime, toTime, lowLine / graphData.maxY / 1.2d);
        }

        // **** NOW line ****
        graphData.addNowLine(pointer);

        // ------------------ 2nd graph

        final GraphData secondGraphData = new GraphData(iobGraph, iobCobCalculatorPlugin);

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
            secondGraphData.addIob(fromTime, toTime, useIobForScale, 1d);
        if (showCobCheckbox.isChecked())
            secondGraphData.addCob(fromTime, toTime, useCobForScale, useCobForScale ? 1d : 0.5d);
        if (showDeviationsCheckbox.isChecked())
            secondGraphData.addDeviations(fromTime, toTime, useDevForScale, 1d);
        if (showRatiosCheckbox.isChecked())
            secondGraphData.addRatio(fromTime, toTime, useRatioForScale, 1d);

        // **** NOW line ****
        // set manual x bounds to have nice steps
        secondGraphData.formatAxis(fromTime, toTime);
        secondGraphData.addNowLine(pointer);

        // do GUI update
        if (showIobCheckbox.isChecked() || showCobCheckbox.isChecked() || showDeviationsCheckbox.isChecked() || showRatiosCheckbox.isChecked()) {
            iobGraph.setVisibility(View.VISIBLE);
        } else {
            iobGraph.setVisibility(View.GONE);
        }
        // finally enforce drawing of graphs
        graphData.performUpdate();
        secondGraphData.performUpdate();
    }
}
