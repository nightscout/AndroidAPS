package info.nightscout.androidaps;

import android.app.Activity;
import android.os.Bundle;
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
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewPlugin;
import info.nightscout.androidaps.plugins.Overview.graphData.GraphData;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

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

        setupChartMenu();

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

        if (profile == null) {
            noProfile.setVisibility(View.VISIBLE);
            return;
        } else {
            noProfile.setVisibility(View.GONE);
        }

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
        if (pump.getPumpDescription().isTempBasalCapable && showBasal) {
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
        boolean useDevSlopeForScale = false;

        if (showIob) {
            useIobForScale = true;
        } else if (showCob) {
            useCobForScale = true;
        } else if (showDev) {
            useDevForScale = true;
        } else if (showRat) {
            useRatioForScale = true;
        } else if (showDevslope) {
            useDevSlopeForScale = true;
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
            secondGraphData.addDeviationSlope(fromTime, toTime, useDevSlopeForScale, 1d);

        // **** NOW line ****
        // set manual x bounds to have nice steps
        secondGraphData.formatAxis(fromTime, toTime);
        secondGraphData.addNowLine(pointer);

        // do GUI update
        if (showIob || showCob || showDev || showRat || showDevslope) {
            iobGraph.setVisibility(View.VISIBLE);
        } else {
            iobGraph.setVisibility(View.GONE);
        }
        // finally enforce drawing of graphs
        graphData.performUpdate();
        secondGraphData.performUpdate();
    }

    private void setupChartMenu() {
        chartButton = (ImageButton) findViewById(R.id.overview_chartMenuButton);
        chartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                         if (item.getItemId() == OverviewFragment.CHARTTYPE.BAS.ordinal()) {
                            showBasal =  !item.isChecked();
                        } else if (item.getItemId() == OverviewFragment.CHARTTYPE.IOB.ordinal()) {
                             showIob =  !item.isChecked();
                        } else if (item.getItemId() == OverviewFragment.CHARTTYPE.COB.ordinal()) {
                             showCob =  !item.isChecked();
                        } else if (item.getItemId() == OverviewFragment.CHARTTYPE.DEV.ordinal()) {
                             showDev =  !item.isChecked();
                        } else if (item.getItemId() == OverviewFragment.CHARTTYPE.SEN.ordinal()) {
                             showRat =  !item.isChecked();
                         } else if (item.getItemId() == OverviewFragment.CHARTTYPE.DEVSLOPE.ordinal()) {
                             showDevslope = !item.isChecked();
                        }
                        updateGUI("onGraphCheckboxesCheckedChanged");
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
            }
        });
    }

}
