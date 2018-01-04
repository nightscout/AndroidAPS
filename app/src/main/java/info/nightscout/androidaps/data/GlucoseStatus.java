package info.nightscout.androidaps.data;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.Html;
import android.text.Spanned;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;

/**
 * Created by mike on 04.01.2017.
 */

public class GlucoseStatus {
    private static Logger log = LoggerFactory.getLogger(GlucoseStatus.class);
    public double glucose = 0d;
    public double delta = 0d;
    public double avgdelta = 0d;
    public double short_avgdelta = 0d;
    public double long_avgdelta = 0d;


    @Override
    public String toString() {
        return MainApp.sResources.getString(R.string.glucose) + " " + DecimalFormatter.to0Decimal(glucose) + " mg/dl\n" +
                MainApp.sResources.getString(R.string.delta) + " " + DecimalFormatter.to0Decimal(delta) + " mg/dl\n" +
                MainApp.sResources.getString(R.string.short_avgdelta) + " " + DecimalFormatter.to2Decimal(short_avgdelta) + " mg/dl\n" +
                MainApp.sResources.getString(R.string.long_avgdelta) + " " + DecimalFormatter.to2Decimal(long_avgdelta) + " mg/dl";
    }

    public Spanned toSpanned() {
        return Html.fromHtml("<b>" + MainApp.sResources.getString(R.string.glucose) + "</b>: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl<br>" +
                "<b>" + MainApp.sResources.getString(R.string.delta) + "</b>: " + DecimalFormatter.to0Decimal(delta) + " mg/dl<br>" +
                "<b>" + MainApp.sResources.getString(R.string.short_avgdelta) + "</b>: " + DecimalFormatter.to2Decimal(short_avgdelta) + " mg/dl<br>" +
                "<b>" + MainApp.sResources.getString(R.string.long_avgdelta) + "</b>: " + DecimalFormatter.to2Decimal(long_avgdelta) + " mg/dl");
    }

    public GlucoseStatus() {
    }

    public GlucoseStatus round() {
        this.glucose = Round.roundTo(this.glucose, 0.1);
        this.delta = Round.roundTo(this.delta, 0.01);
        this.avgdelta = Round.roundTo(this.avgdelta, 0.01);
        this.short_avgdelta = Round.roundTo(this.short_avgdelta, 0.01);
        this.long_avgdelta = Round.roundTo(this.long_avgdelta, 0.01);
        return this;
    }



    @Nullable
    public static GlucoseStatus getGlucoseStatusData(){
        return getGlucoseStatusData(false);
    }

    @Nullable
    public static GlucoseStatus getGlucoseStatusData(boolean allowOldData) {
        // load 45min
        long fromtime = (long) (System.currentTimeMillis() - 60 * 1000L * 45);
        List<BgReading> data = MainApp.getDbHelper().getBgreadingsDataFromTime(fromtime, false);

        int sizeRecords = data.size();
        if (sizeRecords < 1 || (data.get(0).date < System.currentTimeMillis() - 7 * 60 * 1000L && !allowOldData)) {
            return null;
        }

        BgReading now = data.get(0);
        long now_date = now.date;
        double change;

        if (sizeRecords < 2) {
            GlucoseStatus status = new GlucoseStatus();
            status.glucose = now.value;
            status.short_avgdelta = 0d;
            status.delta = 0d;
            status.long_avgdelta = 0d;
            status.avgdelta = 0d; // for OpenAPS MA
            return status.round();
        }

        ArrayList<Double> last_deltas = new ArrayList<Double>();
        ArrayList<Double> short_deltas = new ArrayList<Double>();
        ArrayList<Double> long_deltas = new ArrayList<Double>();

        for (int i = 1; i < data.size(); i++) {
            if (data.get(i).value > 38) {
                BgReading then = data.get(i);
                long then_date = then.date;
                double avgdelta = 0;
                long minutesago;

                minutesago = Math.round((now_date - then_date) / (1000d * 60));
                // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                change = now.value - then.value;
                avgdelta = change / minutesago * 5;

                // use the average of all data points in the last 2.5m for all further "now" calculations
                if (0 < minutesago && minutesago < 2.5) {
                    now.value = (now.value + then.value) / 2;
                    now_date = (now_date + then_date) / 2;
                    // short_deltas are calculated from everything ~5-15 minutes ago
                } else if (2.5 < minutesago && minutesago < 17.5) {
                    //console.error(minutesago, avgdelta);
                    short_deltas.add(avgdelta);
                    // last_deltas are calculated from everything ~5 minutes ago
                    if (2.5 < minutesago && minutesago < 7.5) {
                        last_deltas.add(avgdelta);
                    }
                    // long_deltas are calculated from everything ~20-40 minutes ago
                } else if (17.5 < minutesago && minutesago < 42.5) {
                    long_deltas.add(avgdelta);
                }
            }
        }

        GlucoseStatus status = new GlucoseStatus();
        status.glucose = now.value;

        status.short_avgdelta = average(short_deltas);

        if (last_deltas.isEmpty()) {
            status.delta = status.short_avgdelta;
        } else {
            status.delta = average(last_deltas);
        }

        status.long_avgdelta = average(long_deltas);
        status.avgdelta = status.short_avgdelta; // for OpenAPS MA

        return status.round();
    }

    public static double average(ArrayList<Double> array) {
        double sum = 0d;

        if (array.size() == 0)
            return 0d;

        for (Double value : array) {
            sum += value;
        }
        return sum / array.size();
    }
}
