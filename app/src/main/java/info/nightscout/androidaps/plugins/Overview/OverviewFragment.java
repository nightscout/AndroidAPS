package info.nightscout.androidaps.plugins.Overview;

import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.ValueDependentColor;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.events.EventNewBG;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.events.EventRefreshOpenLoop;
import info.nightscout.androidaps.events.EventTempBasalChange;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.Objectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewExtendedBolusDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTempBasalDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.NewTreatmentDialog;
import info.nightscout.androidaps.plugins.Overview.Dialogs.WizardDialog;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.Round;


public class OverviewFragment extends Fragment implements PluginBase {
    private static Logger log = LoggerFactory.getLogger(OverviewFragment.class);

    TextView bgView;
    TextView timeAgoView;
    TextView deltaView;
    TextView runningTempView;
    TextView iobView;
    TextView apsModeView;
    GraphView bgGraph;

    LinearLayout cancelTempLayout;
    LinearLayout setTempLayout;
    LinearLayout acceptTempLayout;
    Button cancelTempButton;
    Button treatmentButton;
    Button wizardButton;
    Button setTempButton;
    Button setExtenedButton;
    Button acceptTempButton;

    boolean visibleNow = false;
    Handler loopHandler = new Handler();
    Runnable refreshLoop = null;

    Handler mHandler;
    public static HandlerThread mHandlerThread;

    public Double bgTargetLow = 80d;
    public Double bgTargetHigh = 180d;

    public OverviewFragment() {
        super();
        mHandlerThread = new HandlerThread(OverviewFragment.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        registerBus();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.overview);
    }

    @Override
    public boolean isEnabled(int type) {
        return true;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return true;
    }

    @Override
    public boolean canBeHidden(int type) {
        return false;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        // Always enabled
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        // Always visible
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    public static OverviewFragment newInstance() {
        OverviewFragment fragment = new OverviewFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (refreshLoop == null) {
            refreshLoop = new Runnable() {
                @Override
                public void run() {
                    if (visibleNow) {
                        Activity activity = getActivity();
                        if (activity != null)
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateGUI();
                                }
                            });
                    }
                    loopHandler.postDelayed(refreshLoop, 60 * 1000l);
                }
            };
            loopHandler.postDelayed(refreshLoop, 60 * 1000l);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_fragment, container, false);
        bgView = (TextView) view.findViewById(R.id.overview_bg);
        timeAgoView = (TextView) view.findViewById(R.id.overview_timeago);
        deltaView = (TextView) view.findViewById(R.id.overview_delta);
        runningTempView = (TextView) view.findViewById(R.id.overview_runningtemp);
        iobView = (TextView) view.findViewById(R.id.overview_iob);
        apsModeView = (TextView) view.findViewById(R.id.overview_apsmode);
        bgGraph = (GraphView) view.findViewById(R.id.overview_bggraph);
        cancelTempButton = (Button) view.findViewById(R.id.overview_canceltemp);
        treatmentButton = (Button) view.findViewById(R.id.overview_treatment);
        wizardButton = (Button) view.findViewById(R.id.overview_wizard);
        setExtenedButton = (Button) view.findViewById(R.id.overview_extendedbolus);
        setTempButton = (Button) view.findViewById(R.id.overview_settempbasal);
        cancelTempButton = (Button) view.findViewById(R.id.overview_canceltemp);
        setTempLayout = (LinearLayout) view.findViewById(R.id.overview_settemplayout);
        cancelTempLayout = (LinearLayout) view.findViewById(R.id.overview_canceltemplayout);
        acceptTempButton = (Button) view.findViewById(R.id.overview_accepttempbutton);
        acceptTempLayout = (LinearLayout) view.findViewById(R.id.overview_accepttemplayout);

        treatmentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager manager = getFragmentManager();
                NewTreatmentDialog treatmentDialogFragment = new NewTreatmentDialog();
                treatmentDialogFragment.show(manager, "TreatmentDialog");
            }
        });

        wizardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager manager = getFragmentManager();
                WizardDialog wizardDialog = new WizardDialog();
                wizardDialog.show(manager, "WizardDialog");
            }
        });

        cancelTempButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PumpInterface pump = MainApp.getConfigBuilder().getActivePump();
                if (pump.isTempBasalInProgress()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            pump.cancelTempBasal();
                            MainApp.bus().post(new EventTempBasalChange());
                        }
                    });
                }
            }
        });

        setTempButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager manager = getFragmentManager();
                NewTempBasalDialog newTempDialog = new NewTempBasalDialog();
                newTempDialog.show(manager, "NewTempDialog");
            }
        });

        setExtenedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentManager manager = getFragmentManager();
                NewExtendedBolusDialog newExtendedDialog = new NewExtendedBolusDialog();
                newExtendedDialog.show(manager, "NewExtendedDialog");
            }
        });

        acceptTempButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainApp.getConfigBuilder().getActiveLoop().invoke(false);
                final LoopFragment.LastRun finalLastRun = MainApp.getConfigBuilder().getActiveLoop().lastRun;
                if (finalLastRun != null && finalLastRun.lastAPSRun != null && finalLastRun.constraintsProcessed.changeRequested) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(getContext().getString(R.string.confirmation));
                    builder.setMessage(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
                    builder.setPositiveButton(getContext().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    PumpEnactResult applyResult = MainApp.getConfigBuilder().applyAPSRequest(finalLastRun.constraintsProcessed);
                                    if (applyResult.enacted) {
                                        finalLastRun.setByPump = applyResult;
                                        finalLastRun.lastEnact = new Date();
                                        finalLastRun.lastOpenModeAccept = new Date();
                                        MainApp.getConfigBuilder().uploadDeviceStatus();
                                        ObjectivesFragment objectivesFragment = (ObjectivesFragment) MainActivity.getSpecificPlugin(ObjectivesFragment.class);
                                        if (objectivesFragment != null) {
                                            objectivesFragment.manualEnacts++;
                                            objectivesFragment.saveProgress();
                                        }
                                    }
                                    updateGUI();
                                }
                            });
                        }
                    });
                    builder.setNegativeButton(getContext().getString(R.string.cancel), null);
                    builder.show();
                }
                updateGUI();
            }
        });

        updateGUI();
        return view;
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventPreferenceChange: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventRefreshGui: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventTreatmentChange ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventTreatmentChange: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventTempBasalChange ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventTempBasalChange: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventNewBG ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshOpenLoop ev) {
        Activity activity = getActivity();
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateGUI();
                }
            });
        else
            log.debug("EventNewBG: Activity is null");
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            updateGUI();
            visibleNow = true;
        } else {
            visibleNow = false;
        }
    }

    public void updateGUI() {
        DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
        BgReading actualBG = MainApp.getDbHelper().actualBg();
        BgReading lastBG = MainApp.getDbHelper().lastBg();
        if (MainApp.getConfigBuilder() == null || MainApp.getConfigBuilder().getActiveProfile() == null) // app not initialized yet
            return;

        // Skip if not initialized yet
        if (bgGraph == null)
            return;

        // open loop mode
        final LoopFragment.LastRun finalLastRun = MainApp.getConfigBuilder().getActiveLoop().lastRun;
        if (Config.APS) {
            apsModeView.setVisibility(View.VISIBLE);
            if (MainApp.getConfigBuilder().isClosedModeEnabled())
                apsModeView.setText(MainApp.sResources.getString(R.string.closedloop));
            else apsModeView.setText(MainApp.sResources.getString(R.string.openloop));
        } else {
            apsModeView.setVisibility(View.GONE);
        }

        boolean showAcceptButton = true;
        showAcceptButton = showAcceptButton && !MainApp.getConfigBuilder().isClosedModeEnabled(); // Open mode needed
        showAcceptButton = showAcceptButton && finalLastRun != null && finalLastRun.lastAPSRun != null; // aps result must exist
        showAcceptButton = showAcceptButton && (finalLastRun.lastOpenModeAccept == null || finalLastRun.lastOpenModeAccept.getTime() < finalLastRun.lastAPSRun.getTime()); // never accepted or before last result
        showAcceptButton = showAcceptButton && finalLastRun.constraintsProcessed.changeRequested; // change is requested

        if (showAcceptButton) {
            acceptTempLayout.setVisibility(View.VISIBLE);
            acceptTempButton.setText(getContext().getString(R.string.setbasalquestion) + "\n" + finalLastRun.constraintsProcessed);
        } else {
            acceptTempLayout.setVisibility(View.GONE);
        }

        // **** Temp button ****
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpInterface pump = MainApp.getConfigBuilder().getActivePump();

        if (pump.isTempBasalInProgress()) {
            TempBasal activeTemp = pump.getTempBasal();
            cancelTempLayout.setVisibility(View.VISIBLE);
            setTempLayout.setVisibility(View.GONE);
            cancelTempButton.setText(MainApp.instance().getString(R.string.cancel) + ": " + activeTemp.toString());
            runningTempView.setText(activeTemp.toString());
        } else {
            cancelTempLayout.setVisibility(View.GONE);
            setTempLayout.setVisibility(View.VISIBLE);
            Double currentBasal = pump.getBaseBasalRate();
            runningTempView.setText(formatNumber2decimalplaces.format(currentBasal) + " U/h");
        }

        if (profile == null) {
            // disable all treatment buttons because we are not able to check constraints without profile
            setExtenedButton.setVisibility(View.INVISIBLE);
            setTempLayout.setVisibility(View.INVISIBLE);
            wizardButton.setVisibility(View.INVISIBLE);
            treatmentButton.setVisibility(View.INVISIBLE);
            return;
        } else {
            setExtenedButton.setVisibility(View.VISIBLE);
            setTempLayout.setVisibility(View.VISIBLE);
            wizardButton.setVisibility(View.VISIBLE);
            treatmentButton.setVisibility(View.VISIBLE);
        }

        String units = profile.getUnits();

        // **** BG value ****
        if (profile != null && lastBG != null && bgView != null) {
            bgView.setText(lastBG.valueToUnitsToString(profile.getUnits()));
            DatabaseHelper.GlucoseStatus glucoseStatus = MainApp.getDbHelper().getGlucoseStatusData();
            if (glucoseStatus != null)
                deltaView.setText("Δ " + NSProfile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units);
            BgReading.units = profile.getUnits();
        } else
            return;

        Integer flag = bgView.getPaintFlags();
        if (actualBG == null) {
            flag |= Paint.STRIKE_THRU_TEXT_FLAG;
        } else
            flag &= ~Paint.STRIKE_THRU_TEXT_FLAG;
        bgView.setPaintFlags(flag);

        Long agoMsec = new Date().getTime() - lastBG.timeIndex;
        int agoMin = (int) (agoMsec / 60d / 1000d);
        timeAgoView.setText(agoMin + " " + getString(R.string.minago));

        // iob
        MainApp.getConfigBuilder().getActiveTreatments().updateTotalIOB();
        IobTotal bolusIob = MainApp.getConfigBuilder().getActiveTreatments().getLastCalculation();
        if (bolusIob == null) bolusIob = new IobTotal();
        MainApp.getConfigBuilder().getActiveTempBasals().updateTotalIOB();
        IobTotal basalIob = MainApp.getConfigBuilder().getActiveTempBasals().getLastCalculation();
        if (basalIob == null) basalIob = new IobTotal();
        IobTotal iobTotal = IobTotal.combine(bolusIob, basalIob).round();

        String iobtext = getString(R.string.treatments_iob_label_string) + " " + formatNumber2decimalplaces.format(iobTotal.iob) + "U ("
                + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(bolusIob.iob) + "U "
                + getString(R.string.basal) + ": " + formatNumber2decimalplaces.format(basalIob.basaliob) + "U)";
        iobView.setText(iobtext);

        // ****** GRAPH *******

        // allign to hours
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(new Date().getTime());
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.add(Calendar.HOUR, 1);

        int hoursToFetch = 6;
        long toTime = calendar.getTimeInMillis() + 100000; // little bit more to avoid wrong rounding
        long fromTime = toTime - hoursToFetch * 60 * 60 * 1000l;

        Double lowLine = NSProfile.fromMgdlToUnits(bgTargetLow, units);
        Double highLine = NSProfile.fromMgdlToUnits(bgTargetHigh, units);

        BarGraphSeries<DataPoint> basalsSeries = null;
        LineGraphSeries<DataPoint> seriesLow = null;
        LineGraphSeries<DataPoint> seriesHigh = null;
        LineGraphSeries<DataPoint> seriesNow = null;
        PointsGraphSeries<BgReading> seriesInRage = null;
        PointsGraphSeries<BgReading> seriesOutOfRange = null;

        // remove old data from graph
        bgGraph.removeAllSeries();

        // **** TEMP BASALS graph ****
        class BarDataPoint extends DataPoint {
            public BarDataPoint(double x, double y, boolean isTempBasal) {
                super(x, y);
                this.isTempBasal = isTempBasal;
            }

            public boolean isTempBasal = false;
        }

        Double maxAllowedBasal = MainApp.getConfigBuilder().applyBasalConstraints(Constants.basalAbsoluteOnlyForCheckLimit);

        long now = new Date().getTime();
        List<BarDataPoint> basalArray = new ArrayList<BarDataPoint>();
        for (long time = fromTime; time < now; time += 5 * 60 * 1000L) {
            TempBasal tb = MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(new Date(time));
            if (tb != null)
                basalArray.add(new BarDataPoint(time, tb.tempBasalConvertedToAbsolute(), true));
            else
                basalArray.add(new BarDataPoint(time, profile.getBasal(NSProfile.secondsFromMidnight(new Date(time))), false));
        }
        BarDataPoint[] basal = new BarDataPoint[basalArray.size()];
        basal = basalArray.toArray(basal);
        bgGraph.addSeries(basalsSeries = new BarGraphSeries<DataPoint>(basal));
        basalsSeries.setValueDependentColor(new ValueDependentColor<DataPoint>() {
            @Override
            public int get(DataPoint data) {
                BarDataPoint point = (BarDataPoint) data;
                if (point.isTempBasal) return Color.CYAN;
                else return Color.BLUE;
            }
        });

        // set second scale
        bgGraph.getSecondScale().addSeries(basalsSeries);
        bgGraph.getSecondScale().setMinY(0);
        bgGraph.getSecondScale().setMaxY(maxAllowedBasal * 4);
        bgGraph.getGridLabelRenderer().setVerticalLabelsSecondScaleColor(MainApp.instance().getResources().getColor(R.color.background_material_dark)); // same color as backround = hide


        // **** BG graph ****
        List<BgReading> bgReadingsArray = MainApp.getDbHelper().getDataFromTime(fromTime);
        List<BgReading> inRangeArray = new ArrayList<BgReading>();
        List<BgReading> outOfRangeArray = new ArrayList<BgReading>();

        if (bgReadingsArray.size() == 0)
            return;

        Iterator<BgReading> it = bgReadingsArray.iterator();
        Double maxBgValue = 0d;
        while (it.hasNext()) {
            BgReading bg = it.next();
            if (bg.value > maxBgValue) maxBgValue = bg.value;
            if (bg.valueToUnits(units) < lowLine || bg.valueToUnits(units) > highLine)
                outOfRangeArray.add(bg);
            else
                inRangeArray.add(bg);
        }
        maxBgValue = NSProfile.fromMgdlToUnits(maxBgValue, units);
        maxBgValue = units.equals(Constants.MGDL) ? Round.roundTo(maxBgValue, 40d) + 80 : Round.roundTo(maxBgValue, 2d) + 4;
        Integer numOfHorizLines = units.equals(Constants.MGDL) ? (int) (maxBgValue / 40 + 1) : (int) (maxBgValue / 2 + 1);

        BgReading[] inRange = new BgReading[inRangeArray.size()];
        BgReading[] outOfRange = new BgReading[outOfRangeArray.size()];
        inRange = inRangeArray.toArray(inRange);
        outOfRange = outOfRangeArray.toArray(outOfRange);


        if (inRange.length > 0) {
            bgGraph.addSeries(seriesInRage = new PointsGraphSeries<BgReading>(inRange));
            seriesInRage.setShape(PointsGraphSeries.Shape.POINT);
            seriesInRage.setSize(5);
            seriesInRage.setColor(Color.GREEN);
        }

        if (outOfRange.length > 0) {
            bgGraph.addSeries(seriesOutOfRange = new PointsGraphSeries<BgReading>(outOfRange));
            seriesOutOfRange.setShape(PointsGraphSeries.Shape.POINT);
            seriesOutOfRange.setSize(5);
            seriesOutOfRange.setColor(Color.RED);
        }


        // **** HIGH and LOW targets graph ****
        DataPoint[] lowDataPoints = new DataPoint[]{
                new DataPoint(fromTime, lowLine),
                new DataPoint(toTime, lowLine)
        };
        DataPoint[] highDataPoints = new DataPoint[]{
                new DataPoint(fromTime, highLine),
                new DataPoint(toTime, highLine)
        };
        bgGraph.addSeries(seriesLow = new LineGraphSeries<DataPoint>(lowDataPoints));
        seriesLow.setColor(Color.RED);
        bgGraph.addSeries(seriesHigh = new LineGraphSeries<DataPoint>(highDataPoints));
        seriesHigh.setColor(Color.RED);

        // **** NOW line ****
        DataPoint[] nowPoints = new DataPoint[]{
                new DataPoint(now, 0),
                new DataPoint(now, maxBgValue)
        };
        bgGraph.addSeries(seriesNow = new LineGraphSeries<DataPoint>(nowPoints));
        seriesNow.setColor(Color.GREEN);
        seriesNow.setDrawDataPoints(false);
        //seriesNow.setThickness(1);
        // custom paint to make a dotted line
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setPathEffect(new DashPathEffect(new float[]{4, 20}, 0));
        paint.setColor(Color.WHITE);
        seriesNow.setCustomPaint(paint);


        // set manual x bounds to have nice steps
        bgGraph.getViewport().setMaxX(toTime);
        bgGraph.getViewport().setMinX(fromTime);
        bgGraph.getViewport().setXAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setLabelFormatter(new TimeAsXAxisLabelFormatter(getActivity(), "HH"));
        bgGraph.getGridLabelRenderer().setNumHorizontalLabels(7); // only 7 because of the space

        // set manual y bounds to have nice steps
        bgGraph.getViewport().setMaxY(maxBgValue);
        bgGraph.getViewport().setMinY(0);
        bgGraph.getViewport().setYAxisBoundsManual(true);
        bgGraph.getGridLabelRenderer().setNumVerticalLabels(numOfHorizLines);
    }

}
