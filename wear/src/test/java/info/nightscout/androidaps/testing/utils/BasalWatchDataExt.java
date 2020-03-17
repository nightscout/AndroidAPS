package info.nightscout.androidaps.testing.utils;

import androidx.annotation.Nullable;

import java.util.Objects;

import info.nightscout.androidaps.data.BasalWatchData;

import static info.nightscout.androidaps.testing.utils.ExtUtil.assertClassHaveSameFields;

public class BasalWatchDataExt extends BasalWatchData {

    private BasalWatchDataExt() {
        super();
    }

    public BasalWatchDataExt(BasalWatchData ref) {
        super();

        // since we do not want modify BasalWatchData - we use this wrapper class
        // but we make sure it has same fields
        assertClassHaveSameFields(BasalWatchData.class, "startTime,endTime,amount");

        this.startTime = ref.startTime;
        this.endTime = ref.endTime;
        this.amount = ref.amount;
    }

    public static BasalWatchDataExt build(long startTime, long endTime, double amount) {
        BasalWatchDataExt bwd = new BasalWatchDataExt();
        bwd.startTime = startTime;
        bwd.endTime = endTime;
        bwd.amount = amount;
        return bwd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if ((obj instanceof BasalWatchData)||(obj instanceof BasalWatchDataExt)) {
            return (this.startTime == ((BasalWatchData) obj).startTime)
                && (this.endTime == ((BasalWatchData) obj).endTime)
                && (this.amount == ((BasalWatchData) obj).amount);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return startTime+", "+endTime+", "+amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime, amount);
    }

}
