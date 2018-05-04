package info.nightscout.androidaps.plugins.IobCobCalculator;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import info.nightscout.utils.DecimalFormatter;

public class CobInfo {
    /** All COB up to now, including carbs not yet processed by IobCob calculation. */
    @Nullable
    public final Double displayCob;
    public final double futureCarbs;

    public CobInfo(@Nullable Double displayCob, double futureCarbs) {
        this.displayCob = displayCob;
        this.futureCarbs = futureCarbs;
    }

    @NonNull
    public String generateCOBString() {
        String cobStringResult = "--g";
        if (displayCob != null) {
            cobStringResult = DecimalFormatter.to0Decimal(displayCob);
            if (futureCarbs > 0) {
                cobStringResult += "(" + DecimalFormatter.to0Decimal(futureCarbs) + ")";
            }
            cobStringResult += "g";
        }
        return cobStringResult;
    }
}
