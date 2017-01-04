package info.nightscout.androidaps.data;

import android.text.Html;
import android.text.Spanned;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;

/**
 * Created by mike on 04.01.2017.
 */

public class GlucoseStatus {
    public double glucose = 0d;
    public double delta = 0d;
    public double avgdelta = 0d;
    public double short_avgdelta = 0d; // TODO: add calculation for AMA
    public double long_avgdelta = 0d; // TODO: add calculation for AMA

    @Override
    public String toString() {
        return MainApp.sResources.getString(R.string.glucose) + " " + DecimalFormatter.to0Decimal(glucose) + " mg/dl\n" +
                MainApp.sResources.getString(R.string.delta) + " " + DecimalFormatter.to0Decimal(delta) + " mg/dl\n" +
                MainApp.sResources.getString(R.string.avgdelta) + " " + DecimalFormatter.to2Decimal(avgdelta) + " mg/dl";
    }

    public Spanned toSpanned() {
        return Html.fromHtml("<b>" + MainApp.sResources.getString(R.string.glucose) + "</b>: " + DecimalFormatter.to0Decimal(glucose) + " mg/dl<br>" +
                "<b>" + MainApp.sResources.getString(R.string.delta) + "</b>: " + DecimalFormatter.to0Decimal(delta) + " mg/dl<br>" +
                "<b>" + MainApp.sResources.getString(R.string.avgdelta) + "</b>: " + DecimalFormatter.to2Decimal(avgdelta) + " mg/dl");
    }

    public GlucoseStatus() {
    }

    public GlucoseStatus(Double glucose, Double delta, Double avgdelta) {
        this.glucose = glucose;
        this.delta = delta;
        this.avgdelta = avgdelta;
    }

    public GlucoseStatus round() {
        this.glucose = Round.roundTo(this.glucose, 0.1);
        this.delta = Round.roundTo(this.delta, 0.01);
        this.avgdelta = Round.roundTo(this.avgdelta, 0.01);
        return this;
    }
}
