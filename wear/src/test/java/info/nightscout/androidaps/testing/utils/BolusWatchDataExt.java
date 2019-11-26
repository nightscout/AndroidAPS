package info.nightscout.androidaps.testing.utils;

import androidx.annotation.Nullable;

import java.util.Objects;

import info.nightscout.androidaps.data.BolusWatchData;

import static info.nightscout.androidaps.testing.utils.ExtUtil.assertClassHaveSameFields;

public class BolusWatchDataExt extends BolusWatchData {

    private BolusWatchDataExt() {
        super();
    }

    public BolusWatchDataExt(BolusWatchData ref) {
        super();

        // since we do not want modify BolusWatchData - we use this wrapper class
        // but we make sure it has same fields
        assertClassHaveSameFields(BolusWatchData.class, "date,bolus,carbs,isSMB,isValid");

        this.date = ref.date;
        this.bolus = ref.bolus;
        this.carbs = ref.carbs;
        this.isSMB = ref.isSMB;
        this.isValid = ref.isValid;
    }

    public static BolusWatchDataExt build(long date, double bolus, double carbs, boolean isSMB, boolean isValid) {
        BolusWatchDataExt bwd = new BolusWatchDataExt();
        bwd.date = date;
        bwd.bolus = bolus;
        bwd.carbs = carbs;
        bwd.isSMB = isSMB;
        bwd.isValid = isValid;
        return bwd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if ((obj instanceof BolusWatchData)||(obj instanceof BolusWatchDataExt)) {
            return (this.date == ((BolusWatchData) obj).date)
                    && (this.bolus == ((BolusWatchData) obj).bolus)
                    && (this.carbs == ((BolusWatchData) obj).carbs)
                    && (this.isSMB == ((BolusWatchData) obj).isSMB)
                    && (this.isValid == ((BolusWatchData) obj).isValid);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return date+", "+bolus+", "+carbs+", "+isSMB+", "+isValid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, bolus, carbs, isSMB, isValid);
    }

}
