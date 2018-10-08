package info.nightscout.androidaps.db;

/**
 * Created by Rumen Georgiev on 2/24/2018.
 */

public class BGDatum extends BgReading {
    //Added by Rumen for autotune
    public double deviation = 0d;
    public double BGI = 0d;
    public String mealAbsorption = "";
    public  int mealCarbs = 0;
    public String uamAbsorption = "";
    public long CRInitialCarbTime;
    public long CREndTime;
    public double CRInsulin;
    public double AvgDelta;

    public BGDatum() {
    }

    public BGDatum(BgReading bgReading) {
        // Used like from NS sgv
        date = bgReading.date;
        value = bgReading.value;
        raw = bgReading.raw;
        direction = bgReading.direction;
        _id = bgReading._id;
    }
}