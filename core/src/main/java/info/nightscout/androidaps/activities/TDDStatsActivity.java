package info.nightscout.androidaps.activities;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.events.EventDanaRSyncStatus;
import info.nightscout.androidaps.events.EventPumpStatusChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class TDDStatsActivity extends NoSplashAppCompatActivity {
    @Inject AAPSLogger aapsLogger;
    @Inject ResourceHelper resourceHelper;
    @Inject RxBusWrapper rxBus;
    @Inject SP sp;
    @Inject ProfileFunction profileFunction;
    @Inject ActivePluginProvider activePlugin;
    @Inject CommandQueueProvider commandQueue;
    @Inject DatabaseHelperInterface databaseHelper;
    @Inject FabricPrivacy fabricPrivacy;

    private final CompositeDisposable disposable = new CompositeDisposable();

    TextView statusView, statsMessage, totalBaseBasal2;
    EditText totalBaseBasal;
    Button reloadButton;
    LinearLayoutManager llm;
    TableLayout tl, ctl, etl;
    String TBB;
    double magicNumber;
    DecimalFormat decimalFormat;

    List<TDD> historyList = new ArrayList<>();
    List<TDD> dummies;

    public TDDStatsActivity() {
        super();
    }

    @Override
    protected void onResume() {
        super.onResume();
        disposable.add(rxBus
                .toObservable(EventPumpStatusChanged.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> statusView.setText(event.getStatus(resourceHelper)), exception -> fabricPrivacy.logException(exception))
        );
        disposable.add(rxBus
                .toObservable(EventDanaRSyncStatus.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    aapsLogger.debug("EventDanaRSyncStatus: " + event.getMessage());
                    statusView.setText(event.getMessage());
                }, exception -> fabricPrivacy.logException(exception))
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        disposable.clear();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View myView = getCurrentFocus();
            if (myView instanceof EditText) {
                Rect rect = new Rect();
                myView.getGlobalVisibleRect(rect);
                if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    myView.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danar_statsactivity);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        statusView = findViewById(R.id.danar_stats_connection_status);
        reloadButton = findViewById(R.id.danar_statsreload);
        totalBaseBasal = findViewById(R.id.danar_stats_editTotalBaseBasal);
        totalBaseBasal2 = findViewById(R.id.danar_stats_editTotalBaseBasal2);
        statsMessage = findViewById(R.id.danar_stats_Message);

        statusView.setVisibility(View.GONE);
        statsMessage.setVisibility(View.GONE);

        totalBaseBasal2.setEnabled(false);
        totalBaseBasal2.setClickable(false);
        totalBaseBasal2.setFocusable(false);
        totalBaseBasal2.setInputType(0);

        decimalFormat = new DecimalFormat("0.000");
        llm = new LinearLayoutManager(this);

        TBB = sp.getString("TBB", "10.00");

        Profile profile = profileFunction.getProfile();
        if (profile != null) {
            double cppTBB = profile.baseBasalSum();
            TBB = decimalFormat.format(cppTBB);
            sp.putString("TBB", TBB);
        }
        totalBaseBasal.setText(TBB);

        if (!activePlugin.getActivePump().getPumpDescription().needsManualTDDLoad)
            reloadButton.setVisibility(View.GONE);

        // stats table
        tl = findViewById(R.id.main_table);
        TableRow tr_head = new TableRow(this);
        tr_head.setBackgroundColor(Color.DKGRAY);
        tr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_date = new TextView(this);
        label_date.setText(resourceHelper.gs(R.string.date));
        label_date.setTextColor(Color.WHITE);
        tr_head.addView(label_date);

        TextView label_basalrate = new TextView(this);
        label_basalrate.setText(resourceHelper.gs(R.string.basalrate));
        label_basalrate.setTextColor(Color.WHITE);
        tr_head.addView(label_basalrate);

        TextView label_bolus = new TextView(this);
        label_bolus.setText(resourceHelper.gs(R.string.bolus));
        label_bolus.setTextColor(Color.WHITE);
        tr_head.addView(label_bolus);

        TextView label_tdd = new TextView(this);
        label_tdd.setText(resourceHelper.gs(R.string.tdd));
        label_tdd.setTextColor(Color.WHITE);
        tr_head.addView(label_tdd);

        TextView label_ratio = new TextView(this);
        label_ratio.setText(resourceHelper.gs(R.string.ratio));
        label_ratio.setTextColor(Color.WHITE);
        tr_head.addView(label_ratio);

        // add stats headers to tables
        tl.addView(tr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        // cumulative table
        ctl = findViewById(R.id.cumulative_table);
        TableRow ctr_head = new TableRow(this);
        ctr_head.setBackgroundColor(Color.DKGRAY);
        ctr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_cum_amount_days = new TextView(this);
        label_cum_amount_days.setText(resourceHelper.gs(R.string.amount_days));
        label_cum_amount_days.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_amount_days);

        TextView label_cum_tdd = new TextView(this);
        label_cum_tdd.setText(resourceHelper.gs(R.string.tdd));
        label_cum_tdd.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_tdd);

        TextView label_cum_ratio = new TextView(this);
        label_cum_ratio.setText(resourceHelper.gs(R.string.ratio));
        label_cum_ratio.setTextColor(Color.WHITE);
        ctr_head.addView(label_cum_ratio);

        // add cummulative headers to tables
        ctl.addView(ctr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        // expontial table
        etl = findViewById(R.id.expweight_table);
        TableRow etr_head = new TableRow(this);
        etr_head.setBackgroundColor(Color.DKGRAY);
        etr_head.setLayoutParams(new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        TextView label_exp_weight = new TextView(this);
        label_exp_weight.setText(resourceHelper.gs(R.string.weight));
        label_exp_weight.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_weight);

        TextView label_exp_tdd = new TextView(this);
        label_exp_tdd.setText(resourceHelper.gs(R.string.tdd));
        label_exp_tdd.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_tdd);

        TextView label_exp_ratio = new TextView(this);
        label_exp_ratio.setText(resourceHelper.gs(R.string.ratio));
        label_exp_ratio.setTextColor(Color.WHITE);
        etr_head.addView(label_exp_ratio);

        // add expontial headers to tables
        etl.addView(etr_head, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT));

        reloadButton.setOnClickListener(v -> {
            runOnUiThread(() -> {
                reloadButton.setVisibility(View.GONE);
                statusView.setVisibility(View.VISIBLE);
                statsMessage.setVisibility(View.VISIBLE);
                statsMessage.setText(resourceHelper.gs(R.string.warning_Message));
            });
            commandQueue.loadTDDs(new Callback() {
                @Override
                public void run() {
                    loadDataFromDB();
                    runOnUiThread(() -> {
                        reloadButton.setVisibility(View.VISIBLE);
                        statusView.setVisibility(View.GONE);
                        statsMessage.setVisibility(View.GONE);
                    });
                }
            });
        });

        totalBaseBasal.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                totalBaseBasal.clearFocus();
                return true;
            }
            return false;
        });

        totalBaseBasal.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                totalBaseBasal.getText().clear();
            } else {
                sp.putString("TBB", totalBaseBasal.getText().toString());
                TBB = sp.getString("TBB", "");
                loadDataFromDB();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(totalBaseBasal.getWindowToken(), 0);
            }
        });

        loadDataFromDB();
    }

    private void loadDataFromDB() {
        historyList = databaseHelper.getTDDs();

        //only use newest 10
        historyList = historyList.subList(0, Math.min(10, historyList.size()));

        //fill single gaps
        dummies = new LinkedList<>();
        DateFormat df = new SimpleDateFormat("dd.MM.", Locale.getDefault());
        for (int i = 0; i < historyList.size() - 1; i++) {
            TDD elem1 = historyList.get(i);
            TDD elem2 = historyList.get(i + 1);

            if (!df.format(new Date(elem1.date)).equals(df.format(new Date(elem2.date + 25 * 60 * 60 * 1000)))) {
                TDD dummy = new TDD();
                dummy.date = elem1.date - 24 * 60 * 60 * 1000;
                dummy.basal = elem1.basal / 2;
                dummy.bolus = elem1.bolus / 2;
                dummies.add(dummy);
                elem1.basal /= 2;
                elem1.bolus /= 2;
            }
        }
        historyList.addAll(dummies);
        Collections.sort(historyList, (lhs, rhs) -> (int) (rhs.date - lhs.date));

        runOnUiThread(() -> {
            cleanTable(tl);
            cleanTable(ctl);
            cleanTable(etl);
            DateFormat df1 = new SimpleDateFormat("dd.MM.", Locale.getDefault());

            if (TextUtils.isEmpty(TBB)) {
                totalBaseBasal.setError("Please Enter Total Base Basal");
                return;
            } else {
                magicNumber = SafeParse.stringToDouble(TBB);
            }

            magicNumber *= 2;
            totalBaseBasal2.setText(decimalFormat.format(magicNumber));

            int i = 0;
            double sum = 0d;
            double weighted03 = 0d;
            double weighted05 = 0d;
            double weighted07 = 0d;


            //TDD table
            for (TDD record : historyList) {
                double tdd = record.getTotal();

                // Create the table row
                TableRow tr = new TableRow(TDDStatsActivity.this);
                if (i % 2 != 0) tr.setBackgroundColor(Color.DKGRAY);
                if (dummies.contains(record)) {
                    tr.setBackgroundColor(Color.argb(125, 255, 0, 0));
                }
                tr.setId(100 + i);
                tr.setLayoutParams(new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

                // Here create the TextView dynamically
                TextView labelDATE = new TextView(TDDStatsActivity.this);
                labelDATE.setId(200 + i);
                labelDATE.setText(df1.format(new Date(record.date)));
                labelDATE.setTextColor(Color.WHITE);
                tr.addView(labelDATE);

                TextView labelBASAL = new TextView(TDDStatsActivity.this);
                labelBASAL.setId(300 + i);
                labelBASAL.setText(resourceHelper.gs(R.string.formatinsulinunits, record.basal));
                labelBASAL.setTextColor(Color.WHITE);
                tr.addView(labelBASAL);

                TextView labelBOLUS = new TextView(TDDStatsActivity.this);
                labelBOLUS.setId(400 + i);
                labelBOLUS.setText(resourceHelper.gs(R.string.formatinsulinunits, record.bolus));
                labelBOLUS.setTextColor(Color.WHITE);
                tr.addView(labelBOLUS);

                TextView labelTDD = new TextView(TDDStatsActivity.this);
                labelTDD.setId(500 + i);
                labelTDD.setText(resourceHelper.gs(R.string.formatinsulinunits, tdd));
                labelTDD.setTextColor(Color.WHITE);
                tr.addView(labelTDD);

                TextView labelRATIO = new TextView(TDDStatsActivity.this);
                labelRATIO.setId(600 + i);
                labelRATIO.setText(Math.round(100 * tdd / magicNumber) + "%");
                labelRATIO.setTextColor(Color.WHITE);
                tr.addView(labelRATIO);

                // add stats rows to tables
                tl.addView(tr, new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

                i++;
            }

            i = 0;

            //cumulative TDDs
            for (TDD record : historyList) {
                if (!historyList.isEmpty() && df1.format(new Date(record.date)).equals(df1.format(new Date()))) {
                    //Today should not be included
                    continue;
                }
                i++;

                sum = sum + record.getTotal();

                // Create the cumtable row
                TableRow ctr = new TableRow(TDDStatsActivity.this);
                if (i % 2 == 0) ctr.setBackgroundColor(Color.DKGRAY);
                ctr.setId(700 + i);
                ctr.setLayoutParams(new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

                // Here create the TextView dynamically
                TextView labelDAYS = new TextView(TDDStatsActivity.this);
                labelDAYS.setId(800 + i);
                labelDAYS.setText("" + i);
                labelDAYS.setTextColor(Color.WHITE);
                ctr.addView(labelDAYS);

                TextView labelCUMTDD = new TextView(TDDStatsActivity.this);
                labelCUMTDD.setId(900 + i);
                labelCUMTDD.setText(resourceHelper.gs(R.string.formatinsulinunits, sum / i));
                labelCUMTDD.setTextColor(Color.WHITE);
                ctr.addView(labelCUMTDD);

                TextView labelCUMRATIO = new TextView(TDDStatsActivity.this);
                labelCUMRATIO.setId(1000 + i);
                labelCUMRATIO.setText(Math.round(100 * sum / i / magicNumber) + "%");
                labelCUMRATIO.setTextColor(Color.WHITE);
                ctr.addView(labelCUMRATIO);

                // add cummulative rows to tables
                ctl.addView(ctr, new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));
            }

            if (isOldData(historyList) && activePlugin.getActivePump().getPumpDescription().needsManualTDDLoad) {
                statsMessage.setVisibility(View.VISIBLE);
                statsMessage.setText(resourceHelper.gs(R.string.olddata_Message));

            } else {
                tl.setBackgroundColor(Color.TRANSPARENT);
            }

            if (!historyList.isEmpty() && df1.format(new Date(historyList.get(0).date)).equals(df1.format(new Date()))) {
                //Today should not be included
                historyList.remove(0);
            }

            Collections.reverse(historyList);

            i = 0;

            for (TDD record : historyList) {
                double tdd = record.getTotal();
                if (i == 0) {
                    weighted03 = tdd;
                    weighted05 = tdd;
                    weighted07 = tdd;

                } else {
                    weighted07 = (weighted07 * 0.3 + tdd * 0.7);
                    weighted05 = (weighted05 * 0.5 + tdd * 0.5);
                    weighted03 = (weighted03 * 0.7 + tdd * 0.3);
                }
                i++;
            }

            // Create the exptable row
            TableRow etr = new TableRow(TDDStatsActivity.this);
            if (i % 2 != 0) etr.setBackgroundColor(Color.DKGRAY);
            etr.setId(1100 + i);
            etr.setLayoutParams(new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));

            // Here create the TextView dynamically
            TextView labelWEIGHT = new TextView(TDDStatsActivity.this);
            labelWEIGHT.setId(1200 + i);
            labelWEIGHT.setText("0.3\n" + "0.5\n" + "0.7");
            labelWEIGHT.setTextColor(Color.WHITE);
            etr.addView(labelWEIGHT);

            TextView labelEXPTDD = new TextView(TDDStatsActivity.this);
            labelEXPTDD.setId(1300 + i);
            labelEXPTDD.setText(resourceHelper.gs(R.string.formatinsulinunits, weighted03) + "\n" +
                    resourceHelper.gs(R.string.formatinsulinunits, weighted05) + "\n" +
                    resourceHelper.gs(R.string.formatinsulinunits, weighted07));
            labelEXPTDD.setTextColor(Color.WHITE);
            etr.addView(labelEXPTDD);

            TextView labelEXPRATIO = new TextView(TDDStatsActivity.this);
            labelEXPRATIO.setId(1400 + i);
            labelEXPRATIO.setText(Math.round(100 * weighted03 / magicNumber) + "%\n"
                    + Math.round(100 * weighted05 / magicNumber) + "%\n"
                    + Math.round(100 * weighted07 / magicNumber) + "%");
            labelEXPRATIO.setTextColor(Color.WHITE);
            etr.addView(labelEXPRATIO);

            // add exponentail rows to tables
            etl.addView(etr, new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));
        });
    }

    private void cleanTable(TableLayout table) {
        int childCount = table.getChildCount();
        // Remove all rows except the first one
        if (childCount > 1) {
            table.removeViews(1, childCount - 1);
        }
    }

    public boolean isOldData(List<TDD> historyList) {

        PumpType type = activePlugin.getActivePump().getPumpDescription().pumpType;
        boolean startsYesterday = type == PumpType.DanaR || type == PumpType.DanaRS || type == PumpType.DanaRv2 || type == PumpType.DanaRKorean || type == PumpType.AccuChekInsight;

        DateFormat df = new SimpleDateFormat("dd.MM.", Locale.getDefault());
        return (historyList.size() < 3 || !(df.format(new Date(historyList.get(0).date)).equals(df.format(new Date(System.currentTimeMillis() - (startsYesterday ? 1000 * 60 * 60 * 24 : 0))))));
    }
}