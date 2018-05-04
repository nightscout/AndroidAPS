package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.support.annotation.Nullable;

public class CobInfo {
    /** All COB up to now, including carbs not yet processed by IobCob calculation. */
    @Nullable
    public final Double displayCob;
    public final double futureCarbs;

    public CobInfo(@Nullable Double displayCob, double futureCarbs) {
        this.displayCob = displayCob;
        this.futureCarbs = futureCarbs;
    }
}
