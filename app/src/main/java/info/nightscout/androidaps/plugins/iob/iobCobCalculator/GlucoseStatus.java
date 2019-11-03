package info.nightscout.androidaps.plugins.iob.iobCobCalculator;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.Round;

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
    public long date = 0L;


    public String log() {
        return "Glucose: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl " +
                "Delta: " + DecimalFormatter.to0Decimal(delta) + " mg/dl" +
                "Short avg. delta: " + " " + DecimalFormatter.to2Decimal(short_avgdelta) + " mg/dl " +
                "Long avg. delta: " + DecimalFormatter.to2Decimal(long_avgdelta) + " mg/dl";
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
    public static GlucoseStatus getGlucoseStatusData() {
        return getGlucoseStatusData(false);
    }

    @Nullable
    public static GlucoseStatus getGlucoseStatusData(boolean allowOldData) {
        // load 45min
        //long fromtime = DateUtil.now() - 60 * 1000L * 45;
        //List<BgReading> data = MainApp.getDbHelper().getBgreadingsDataFromTime(fromtime, false);

        synchronized (IobCobCalculatorPlugin.getPlugin().getDataLock()) {

            List<BgReading> data = IobCobCalculatorPlugin.getPlugin().getBgReadings();

            if (data == null) {
                if (L.isEnabled(L.GLUCOSE))
                    log.debug("data=null");
                return null;
            }

            int sizeRecords = data.size();
            if (sizeRecords == 0) {
                if (L.isEnabled(L.GLUCOSE))
                    log.debug("sizeRecords==0");
                return null;
            }

            if (data.get(0).date < DateUtil.now() - 7 * 60 * 1000L && !allowOldData) {
                if (L.isEnabled(L.GLUCOSE))
                    log.debug("olddata");
                return null;
            }

            BgReading now = data.get(0);
            long now_date = now.date;
            double change;

            if (sizeRecords == 1) {
                GlucoseStatus status = new GlucoseStatus();
                status.glucose = now.value;
                status.short_avgdelta = 0d;
                status.delta = 0d;
                status.long_avgdelta = 0d;
                status.avgdelta = 0d; // for OpenAPS MA
                status.date = now_date;
                if (L.isEnabled(L.GLUCOSE))
                    log.debug("sizeRecords==1");
                return status.round();
            }

            ArrayList<Double> now_value_list = new ArrayList<>();
            ArrayList<Double> last_deltas = new ArrayList<>();
            ArrayList<Double> short_deltas = new ArrayList<>();
            ArrayList<Double> long_deltas = new ArrayList<>();

            // Use the latest sgv value in the now calculations
            now_value_list.add(now.value);

            for (int i = 1; i < sizeRecords; i++) {
                if (data.get(i).value > 38) {
                    BgReading then = data.get(i);
                    long then_date = then.date;
                    double avgdelta;
                    long minutesago;

                    minutesago = Math.round((now_date - then_date) / (1000d * 60));
                    // multiply by 5 to get the same units as delta, i.e. mg/dL/5m
                    change = now.value - then.value;
                    avgdelta = change / minutesago * 5;

                    if (L.isEnabled(L.GLUCOSE))
                        log.debug(then.toString() + " minutesago=" + minutesago + " avgdelta=" + avgdelta);

                    // use the average of all data points in the last 2.5m for all further "now" calculations
                    if (0 < minutesago && minutesago < 2.5) {
                        // Keep and average all values within the last 2.5 minutes
                        now_value_list.add(then.value);
                        now.value = average(now_value_list);
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
                    } else {
                        // Do not process any more records after >= 42.5 minutes
                        break;
                    }
                }
            }

            GlucoseStatus status = new GlucoseStatus();
            status.glucose = now.value;
            status.date = now_date;

            status.short_avgdelta = average(short_deltas);

            if (last_deltas.isEmpty()) {
                status.delta = status.short_avgdelta;
            } else {
                status.delta = average(last_deltas);
            }

            status.long_avgdelta = average(long_deltas);
            status.avgdelta = status.short_avgdelta; // for OpenAPS MA

            if (L.isEnabled(L.GLUCOSE))
                log.debug(status.log());
            return status.round();
        }
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
