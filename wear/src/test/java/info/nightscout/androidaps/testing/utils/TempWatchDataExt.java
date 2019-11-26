package info.nightscout.androidaps.testing.utils;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import info.nightscout.androidaps.data.TempWatchData;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class TempWatchDataExt extends TempWatchData {

    private TempWatchDataExt() {
        super();
    }

    public TempWatchDataExt(TempWatchData ref) {
        super();

        Set<String> parentFields = new HashSet<>();
        for (Field f : TempWatchData.class.getDeclaredFields()) {
            parentFields.add(f.getName());
        }

        Set<String> knownFields = new HashSet<>(Arrays.asList("startTime,startBasal,endTime,endBasal,amount".split(",")));

        // since we do not want modify TempWatchData - we use this wrapper class
        // but we make sure it has same fields
        assertThat(parentFields, is(knownFields));

        this.startTime = ref.startTime;
        this.startBasal = ref.startBasal;
        this.endTime = ref.endTime;
        this.endBasal = ref.endBasal;
        this.amount = ref.amount;
    }

    public static TempWatchDataExt build(long startTime, double startBasal, long endTime,
                                         double endBasal, double amount) {
        TempWatchDataExt twd = new TempWatchDataExt();
        twd.startTime = startTime;
        twd.startBasal = startBasal;
        twd.endTime = endTime;
        twd.endBasal = endBasal;
        twd.amount = amount;
        return twd;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if ((obj instanceof TempWatchData)||(obj instanceof TempWatchDataExt)) {
            return (this.startTime == ((TempWatchData) obj).startTime)
                && (this.startBasal == ((TempWatchData) obj).startBasal)
                && (this.endTime == ((TempWatchData) obj).endTime)
                && (this.endBasal == ((TempWatchData) obj).endBasal)
                && (this.amount == ((TempWatchData) obj).amount);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return startTime+", "+startBasal+", "+endTime+", "+endBasal+", "+amount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, startBasal, endTime, endBasal, amount);
    }

}
