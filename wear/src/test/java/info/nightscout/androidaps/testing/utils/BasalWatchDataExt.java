package info.nightscout.androidaps.testing.utils;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import info.nightscout.androidaps.data.BasalWatchData;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BasalWatchDataExt extends BasalWatchData {

    private BasalWatchDataExt() {
        super();
    }

    public BasalWatchDataExt(BasalWatchData ref) {
        super();

        Set<String> parentFields = new HashSet<>();
        for (Field f : BasalWatchData.class.getDeclaredFields()) {
            parentFields.add(f.getName());
        }

        Set<String> knownFields = new HashSet<>(Arrays.asList("startTime,endTime,amount".split(",")));

        // since we do not want modify BasalWatchData - we use this wrapper class
        // but we make sure it has same fields
        assertThat(parentFields, is(knownFields));

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
